import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";
import {
  createDeleteAccountHandler,
  RECENT_LOGIN_WINDOW_SECONDS,
  type AccountDeletionStore,
} from "./deleteAccount";

const UID = "user-delete-1";
const NOW_MS = 1_800_000_000_000;
const NOW_S = Math.floor(NOW_MS / 1000);

function googleAuth(authTimeSeconds: number) {
  return { uid: UID, signInProvider: "google.com", authTimeSeconds };
}

type StepName = "tombstone" | "user-subtree" | "reports" | "revoke" | "auth";

function fakeStore(failAt?: StepName) {
  const calls: StepName[] = [];
  const maybeFail = (step: StepName) => {
    calls.push(step);
    if (failAt === step) throw new Error(`boom-${step}`);
  };
  const store: AccountDeletionStore = {
    async writeTombstone() {
      maybeFail("tombstone");
    },
    async deleteUserSubtree() {
      maybeFail("user-subtree");
    },
    async deleteReportsByUser() {
      maybeFail("reports");
      return 3;
    },
    async revokeRefreshTokens() {
      maybeFail("revoke");
    },
    async deleteAuthUser() {
      maybeFail("auth");
    },
  };
  return { store, calls };
}

function handler(store: AccountDeletionStore, logs: Array<Record<string, unknown>> = []) {
  return createDeleteAccountHandler({
    store,
    log: (record) => logs.push(record),
    now: () => NOW_MS,
  });
}

test("BLO-178: deletion runs tombstone, subtree, reports, then auth — in that order", async () => {
  const { store, calls } = fakeStore();
  const logs: Array<Record<string, unknown>> = [];
  const res = await handler(store, logs)({ auth: googleAuth(NOW_S - 30), data: {} });
  assert.deepEqual(res, { deleted: true });
  assert.deepEqual(calls, ["tombstone", "user-subtree", "reports", "revoke", "auth"]);
  assert.equal(logs[logs.length - 1]?.outcome, "ok");
  assert.equal(logs[logs.length - 1]?.reportsDeleted, 3);
});

test("BLO-178: null data is accepted; any payload field is rejected", async () => {
  const { store } = fakeStore();
  const h = handler(store);
  await h({ auth: googleAuth(NOW_S), data: null });
  await assert.rejects(
    h({ auth: googleAuth(NOW_S), data: { uid: "someone-else" } }),
    (err: unknown) => {
      assert.ok(err instanceof HttpsError);
      assert.equal(err.code, "invalid-argument");
      return true;
    },
  );
});

test("BLO-178: the target uid comes from the token, never the payload", async () => {
  const seen: string[] = [];
  const store: AccountDeletionStore = {
    async writeTombstone(uid) {
      seen.push(uid);
    },
    async deleteUserSubtree(uid) {
      seen.push(uid);
    },
    async deleteReportsByUser(uid) {
      seen.push(uid);
      return 0;
    },
    async revokeRefreshTokens(uid) {
      seen.push(uid);
    },
    async deleteAuthUser(uid) {
      seen.push(uid);
    },
  };
  await handler(store)({ auth: googleAuth(NOW_S), data: {} });
  assert.ok(seen.length > 0 && seen.every((u) => u === UID));
});

test("BLO-178: a stale or missing auth_time fails closed as requires-recent-login", async () => {
  const { store, calls } = fakeStore();
  const h = handler(store);
  for (const auth of [
    googleAuth(NOW_S - RECENT_LOGIN_WINDOW_SECONDS - 1),
    { uid: UID, signInProvider: "google.com" },
  ]) {
    await assert.rejects(h({ auth, data: {} }), (err: unknown) => {
      assert.ok(err instanceof HttpsError);
      assert.equal(err.code, "failed-precondition");
      assert.equal(err.message, "requires-recent-login");
      return true;
    });
  }
  assert.deepEqual(calls, []);
});

test("BLO-178: an auth_time exactly at the window edge is accepted", async () => {
  const { store } = fakeStore();
  await handler(store)({
    auth: googleAuth(NOW_S - RECENT_LOGIN_WINDOW_SECONDS),
    data: {},
  });
});

test("BLO-178: unauthenticated and non-Google callers are rejected before any step", async () => {
  const { store, calls } = fakeStore();
  const h = handler(store);
  await assert.rejects(h({ auth: null, data: {} }), (err: unknown) => {
    assert.ok(err instanceof HttpsError);
    assert.equal(err.code, "unauthenticated");
    return true;
  });
  await assert.rejects(
    h({ auth: { uid: UID, signInProvider: "anonymous", authTimeSeconds: NOW_S }, data: {} }),
    (err: unknown) => {
      assert.ok(err instanceof HttpsError);
      assert.equal(err.code, "permission-denied");
      return true;
    },
  );
  assert.deepEqual(calls, []);
});

for (const failAt of ["tombstone", "user-subtree", "reports", "revoke", "auth"] as StepName[]) {
  test(`BLO-178: failure at ${failAt} reports the step and stays retryable`, async () => {
    const { store, calls } = fakeStore(failAt);
    const logs: Array<Record<string, unknown>> = [];
    await assert.rejects(
      handler(store, logs)({ auth: googleAuth(NOW_S), data: {} }),
      (err: unknown) => {
        assert.ok(err instanceof HttpsError);
        assert.equal(err.code, "unavailable");
        assert.equal(err.message, "Account deletion did not finish. Try again.");
        return true;
      },
    );
    const failure = logs.find((l) => l.outcome === "failed");
    const expectedStep =
      failAt === "revoke" || failAt === "auth" ? "auth" : failAt;
    assert.equal(failure?.step, expectedStep);
    // Auth deletion is only ever attempted after every earlier step ran,
    // so the retry path (the user's own sign-in) survives any failure.
    if (failAt !== "auth" && failAt !== "revoke") {
      assert.ok(!calls.includes("auth"));
    }
  });
}

test("BLO-178: a retry after a partial failure completes (steps are idempotent)", async () => {
  const { store } = fakeStore("reports");
  const h = handler(store);
  await assert.rejects(h({ auth: googleAuth(NOW_S), data: {} }));
  const { store: healthy } = fakeStore();
  const res = await handler(healthy)({ auth: googleAuth(NOW_S), data: {} });
  assert.deepEqual(res, { deleted: true });
});
