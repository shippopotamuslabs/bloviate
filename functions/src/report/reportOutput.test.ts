import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";
import {
  createSubmitOutputReportHandler,
  reportDocumentId,
  type OutputReportRecord,
  type ReportStore,
} from "./reportOutput";

const UID = "user-report-1";
const GOOGLE_AUTH = { uid: UID, signInProvider: "google.com" };
const REPORT_ID = "123e4567-e89b-42d3-a456-426614174000";

function validData(overrides: Record<string, unknown> = {}) {
  return {
    clientReportId: REPORT_ID,
    styledOutput: "Some styled text.",
    sourceTranscript: "some styled text",
    reason: "offensive",
    styleId: "professional",
    provider: "deepinfra/fp8",
    model: "deepseek/deepseek-v4.1-flash-1017",
    appVersion: "1.0 (42)",
    ...overrides,
  };
}

function fakeStore() {
  const docs = new Map<string, OutputReportRecord>();
  const store: ReportStore = {
    async createIfAbsent(reportId, record) {
      if (docs.has(reportId)) return false;
      docs.set(reportId, record);
      return true;
    },
  };
  return { store, docs };
}

function handler(store: ReportStore, logs: Array<Record<string, unknown>> = []) {
  return createSubmitOutputReportHandler({
    store,
    log: (record) => logs.push(record),
  });
}

test("BLO-148: a valid report is stored with identity bound from the token", async () => {
  const { store, docs } = fakeStore();
  const res = await handler(store)({ auth: GOOGLE_AUTH, data: validData() });
  assert.equal(res.duplicate, false);
  assert.equal(res.reportId, reportDocumentId(UID, REPORT_ID));
  const doc = docs.get(res.reportId);
  assert.equal(doc?.reporterUid, UID);
  assert.equal(doc?.styledOutput, "Some styled text.");
  assert.equal(doc?.sourceTranscript, "some styled text");
  assert.equal(doc?.reason, "offensive");
  assert.equal(doc?.styleId, "professional");
  assert.equal(doc?.provider, "deepinfra/fp8");
  assert.equal(doc?.model, "deepseek/deepseek-v4.1-flash-1017");
  assert.equal(doc?.appVersion, "1.0 (42)");
  assert.equal(doc?.status, "new");
});

test("BLO-148: a retry with the same clientReportId is a duplicate, not a second doc", async () => {
  const { store, docs } = fakeStore();
  const h = handler(store);
  const first = await h({ auth: GOOGLE_AUTH, data: validData() });
  const second = await h({ auth: GOOGLE_AUTH, data: validData() });
  assert.equal(first.reportId, second.reportId);
  assert.equal(second.duplicate, true);
  assert.equal(docs.size, 1);
});

test("BLO-148: the same clientReportId from another user is a different doc", async () => {
  const { store, docs } = fakeStore();
  const h = handler(store);
  await h({ auth: GOOGLE_AUTH, data: validData() });
  const other = await h({
    auth: { uid: "user-report-2", signInProvider: "google.com" },
    data: validData(),
  });
  assert.equal(other.duplicate, false);
  assert.equal(docs.size, 2);
});

test("BLO-148: unauthenticated and non-Google callers are rejected", async () => {
  const { store, docs } = fakeStore();
  const h = handler(store);
  await assert.rejects(h({ auth: null, data: validData() }), (err: unknown) => {
    assert.ok(err instanceof HttpsError);
    assert.equal(err.code, "unauthenticated");
    return true;
  });
  await assert.rejects(
    h({ auth: { uid: UID, signInProvider: "anonymous" }, data: validData() }),
    (err: unknown) => {
      assert.ok(err instanceof HttpsError);
      assert.equal(err.code, "permission-denied");
      return true;
    },
  );
  assert.equal(docs.size, 0);
});

test("BLO-148: invalid payloads are rejected and store nothing", async () => {
  const { store, docs } = fakeStore();
  const h = handler(store);
  const badPayloads: Array<Record<string, unknown>> = [
    validData({ reason: "rude" }),
    validData({ clientReportId: "not-a-uuid" }),
    validData({ styledOutput: "" }),
    validData({ styledOutput: "x".repeat(20_001) }),
    validData({ sourceTranscript: 42 }),
    validData({ styleId: "nonsense-style" }),
    validData({ provider: "p".repeat(129) }),
    validData({ appVersion: "" }),
    validData({ uid: "spoofed-uid" }),
    validData({ extra: "field" }),
  ];
  for (const data of badPayloads) {
    await assert.rejects(h({ auth: GOOGLE_AUTH, data }), (err: unknown) => {
      assert.ok(err instanceof HttpsError);
      assert.equal(err.code, "invalid-argument");
      return true;
    });
  }
  assert.equal(docs.size, 0);
});

test("BLO-148: null provider/model are accepted (provenance can be unavailable)", async () => {
  const { store, docs } = fakeStore();
  const res = await handler(store)({
    auth: GOOGLE_AUTH,
    data: validData({ provider: null, model: null }),
  });
  const doc = docs.get(res.reportId);
  assert.equal(doc?.provider, null);
  assert.equal(doc?.model, null);
});

test("BLO-148: logs never carry report content", async () => {
  const { store } = fakeStore();
  const logs: Array<Record<string, unknown>> = [];
  await handler(store, logs)({ auth: GOOGLE_AUTH, data: validData() });
  const serialized = JSON.stringify(logs);
  assert.ok(!serialized.includes("Some styled text."));
  assert.ok(!serialized.includes("some styled text"));
  assert.ok(serialized.includes('"reason":"offensive"'));
});
