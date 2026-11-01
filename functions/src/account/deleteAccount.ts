/**
 * In-app account deletion (BLO-178).
 *
 * Deletes the caller's Bloviate account end to end: the tombstone, the
 * whole `users/{uid}` subtree (entitlement, custom styles, generation
 * limits, usage counters), the user's submitted output reports, and
 * finally the Firebase Auth user itself.
 *
 * Safety shape:
 * - The uid comes ONLY from the verified token. The request payload is
 *   required to be empty; a caller-supplied uid is never read.
 * - A recent login is required: the token's auth_time must be within
 *   RECENT_LOGIN_WINDOW_SECONDS. Missing auth_time fails closed. The
 *   client re-authenticates with Google before calling.
 * - Steps are ordered so a partial failure never strands the account:
 *   every step is idempotent, and the Auth user — the only credential
 *   that can retry this callable — is deleted LAST.
 * - The tombstone at `deletedAccounts/{uid}` is written FIRST and lives
 *   outside the deleted subtree. assertEntitled treats a tombstoned uid
 *   as never entitled, so a stale cached entitlement or a delayed
 *   billing/backend write cannot restore cloud access after deletion.
 *   A person who signs up again with the same Google account gets a
 *   NEW uid from Firebase Auth and is unaffected.
 * - Deleting the account does NOT cancel a Google Play subscription;
 *   the in-app copy says so plainly, matching the published deletion
 *   page. Billing cancellation stays with Google Play.
 *
 * Logs carry uidHash + step + outcome codes only — never content.
 */
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { createLogger } from "../observability/structuredLog";
import { GOOGLE_PROVIDER_ID } from "../style/customStyleStore";
import { hashUid } from "../style/styleText";

export const RECENT_LOGIN_WINDOW_SECONDS = 600;
export const REQUIRES_RECENT_LOGIN_MESSAGE = "requires-recent-login";
export const DELETION_INCOMPLETE_MESSAGE =
  "Account deletion did not finish. Try again.";

export type AccountDeletionStore = {
  writeTombstone(uid: string): Promise<void>;
  deleteUserSubtree(uid: string): Promise<void>;
  /** Deletes every report doc whose reporterUid is uid; resolves the count deleted. */
  deleteReportsByUser(uid: string): Promise<number>;
  revokeRefreshTokens(uid: string): Promise<void>;
  /** Deleting an already-absent Auth user counts as success. */
  deleteAuthUser(uid: string): Promise<void>;
};

export function adminAccountDeletionStore(): AccountDeletionStore {
  const db = () => getFirestore();
  return {
    async writeTombstone(uid) {
      await db().doc(`deletedAccounts/${uid}`).set({
        deletedAt: FieldValue.serverTimestamp(),
        source: "in-app",
      });
    },
    async deleteUserSubtree(uid) {
      await db().recursiveDelete(db().doc(`users/${uid}`));
    },
    async deleteReportsByUser(uid) {
      let deleted = 0;
      for (;;) {
        const snap = await db()
          .collection("reports")
          .where("reporterUid", "==", uid)
          .limit(500)
          .get();
        if (snap.empty) return deleted;
        const batch = db().batch();
        for (const doc of snap.docs) batch.delete(doc.ref);
        await batch.commit();
        deleted += snap.size;
        if (snap.size < 500) return deleted;
      }
    },
    async revokeRefreshTokens(uid) {
      await getAuth().revokeRefreshTokens(uid);
    },
    async deleteAuthUser(uid) {
      try {
        await getAuth().deleteUser(uid);
      } catch (err) {
        if ((err as { code?: unknown })?.code === "auth/user-not-found") return;
        throw err;
      }
    },
  };
}

export type DeleteAccountAuth = {
  uid: string;
  signInProvider?: string;
  /** Seconds since epoch from the verified token's auth_time claim. */
  authTimeSeconds?: number;
};

export type DeleteAccountRequest = {
  auth?: DeleteAccountAuth | null;
  data: unknown;
};

export type DeleteAccountDeps = {
  store: AccountDeletionStore;
  log: (record: Record<string, unknown>) => void;
  now?: () => number;
};

type DeletionStep = "tombstone" | "user-subtree" | "reports" | "auth";

export function createDeleteAccountHandler(deps: DeleteAccountDeps) {
  return async (request: DeleteAccountRequest): Promise<{ deleted: true }> => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    if (request.auth?.signInProvider !== GOOGLE_PROVIDER_ID) {
      throw new HttpsError("permission-denied", "Google sign-in required.");
    }
    // The payload must be empty: there is nothing the caller may specify.
    const data = request.data;
    const isEmpty =
      data === null ||
      (typeof data === "object" &&
        !Array.isArray(data) &&
        Object.keys(data as Record<string, unknown>).length === 0);
    if (!isEmpty) {
      throw new HttpsError("invalid-argument", "deleteAccount takes no arguments.");
    }
    const nowSeconds = Math.floor((deps.now ?? Date.now)() / 1000);
    const authTime = request.auth?.authTimeSeconds;
    if (
      typeof authTime !== "number" ||
      !Number.isFinite(authTime) ||
      nowSeconds - authTime > RECENT_LOGIN_WINDOW_SECONDS ||
      authTime > nowSeconds + 60
    ) {
      throw new HttpsError("failed-precondition", REQUIRES_RECENT_LOGIN_MESSAGE);
    }

    const uidHash = hashUid(uid);
    const steps: Array<[DeletionStep, () => Promise<unknown>]> = [
      ["tombstone", () => deps.store.writeTombstone(uid)],
      ["user-subtree", () => deps.store.deleteUserSubtree(uid)],
      ["reports", () => deps.store.deleteReportsByUser(uid)],
      // Auth last: until this step lands, the user can sign in and retry.
      [
        "auth",
        async () => {
          await deps.store.revokeRefreshTokens(uid);
          await deps.store.deleteAuthUser(uid);
        },
      ],
    ];
    let reportsDeleted = 0;
    for (const [step, run] of steps) {
      try {
        const result = await run();
        if (step === "reports" && typeof result === "number") reportsDeleted = result;
      } catch (err) {
        deps.log({ uidHash, op: "delete-account", outcome: "failed", step });
        throw new HttpsError("unavailable", DELETION_INCOMPLETE_MESSAGE);
      }
    }
    deps.log({ uidHash, op: "delete-account", outcome: "ok", reportsDeleted });
    return { deleted: true };
  };
}

const deletionLogger = createLogger({ service: "deleteAccount" });

export const deleteAccount = onCall(
  {
    enforceAppCheck: true,
    timeoutSeconds: 120,
    memory: "512MiB" as const,
  },
  async (request) => {
    const handler = createDeleteAccountHandler({
      store: adminAccountDeletionStore(),
      log: (record) => deletionLogger.info("deleteAccount", record),
    });
    return handler({
      auth: request.auth
        ? {
            uid: request.auth.uid,
            signInProvider: request.auth.token?.firebase?.sign_in_provider,
            authTimeSeconds: request.auth.token?.auth_time,
          }
        : null,
      data: request.data,
    });
  },
);
