import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";
import {
  createDeleteCustomStyleHandler,
  createListCustomStylesHandler,
  createSaveCustomStyleHandler,
} from "./customStyles";
import {
  CUSTOM_STYLE_SCHEMA_VERSION,
  customStyleCollectionPath,
  customStyleDocPath,
  FACTS_CLAUSE,
  LANGUAGE_CLAUSE,
  MAX_DESCRIPTION,
  MAX_SAMPLE,
  MAX_SYSTEM_INSTRUCTIONS,
  validateCustomStyleDoc,
  withSafetyClauses,
  type CustomStyleStore,
  type StoredCustomStyleFields,
} from "./customStyleStore";

const UID = "user-custom-1";
const OTHER_UID = "user-custom-2";
const GOOGLE_AUTH = { uid: UID, signInProvider: "google.com" };
const OTHER_GOOGLE_AUTH = { uid: OTHER_UID, signInProvider: "google.com" };

function validDefinition(overrides: Record<string, unknown> = {}) {
  return {
    displayName: "My Noir",
    description: "My own hard-boiled voice.",
    sample: "The coffee was cold.",
    systemInstructions: "Write hard-boiled.",
    fewShots: [
      { raw: "i saw maria at the store", styled: "I saw Maria at the store." },
      { raw: "the bus was late again", styled: "The bus was late again." },
    ],
    ...overrides,
  };
}

function storedFields(overrides: Partial<StoredCustomStyleFields> = {}): StoredCustomStyleFields {
  return {
    schemaVersion: CUSTOM_STYLE_SCHEMA_VERSION,
    displayName: "My Noir",
    description: "My own hard-boiled voice.",
    sample: "The coffee was cold.",
    systemInstructions: "Write hard-boiled.",
    fewShots: [
      { raw: "i saw maria at the store", styled: "I saw Maria at the store." },
      { raw: "the bus was late again", styled: "The bus was late again." },
    ],
    revision: 1,
    createdAtEpochMillis: 1000,
    updatedAtEpochMillis: 1000,
    ...overrides,
  };
}

/** In-memory fake with real OCC semantics, uid scoping, and write counting. */
function fakeStore(seed: Record<string, Record<string, StoredCustomStyleFields>> = {}) {
  const db = new Map<string, Map<string, StoredCustomStyleFields>>();
  for (const [uid, styles] of Object.entries(seed)) {
    db.set(uid, new Map(Object.entries(styles)));
  }
  let writes = 0;
  const store: CustomStyleStore = {
    async list(uid) {
      return [...(db.get(uid)?.entries() ?? [])].map(([id, fields]) => ({ id, data: { ...fields } }));
    },
    async create(uid, id, fields) {
      writes++;
      let scoped = db.get(uid);
      if (!scoped) {
        scoped = new Map();
        db.set(uid, scoped);
      }
      scoped.set(id, { ...fields });
    },
    async update(uid, id, expectedRevision, fields) {
      writes++;
      const scoped = db.get(uid);
      const current = scoped?.get(id);
      if (!current) {
        throw new HttpsError("failed-precondition", "Custom style does not exist.");
      }
      if (current.revision !== expectedRevision) {
        throw new HttpsError("failed-precondition", "Custom style was modified; reload and retry.");
      }
      const next: StoredCustomStyleFields = {
        schemaVersion: CUSTOM_STYLE_SCHEMA_VERSION,
        displayName: fields.displayName,
        description: fields.description,
        sample: fields.sample,
        systemInstructions: fields.systemInstructions,
        fewShots: fields.fewShots,
        revision: expectedRevision + 1,
        createdAtEpochMillis: current.createdAtEpochMillis,
        updatedAtEpochMillis: fields.updatedAtEpochMillis,
      };
      scoped!.set(id, next);
      return { ...next };
    },
    async delete(uid, id) {
      writes++;
      const scoped = db.get(uid);
      if (!scoped?.has(id)) return false;
      scoped.delete(id);
      return true;
    },
    async get(uid, id) {
      const found = db.get(uid)?.get(id);
      return found ? { ...found } : null;
    },
  };
  return { store, getWrites: () => writes };
}

function fakeLog() {
  const records: Record<string, unknown>[] = [];
  return { records, log: (r: Record<string, unknown>) => records.push(r) };
}

function deps(
  store: CustomStyleStore,
  opts?: { assertEntitled?: (uid: string) => Promise<void>; now?: () => number; newId?: () => string },
) {
  const log = fakeLog();
  return {
    records: log.records,
    assertEntitled: opts?.assertEntitled ?? (async () => {}),
    store,
    log: log.log,
    now: opts?.now ?? (() => 5000),
    newId: opts?.newId ?? (() => "custom-test-id"),
  };
}

function assertCode(err: unknown, code: string): boolean {
  assert.ok(err instanceof HttpsError, `expected HttpsError, got ${String(err)}`);
  assert.equal((err as HttpsError).code, code);
  return true;
}

const CANARY = "CANARY-STYLE-998877";

test("paths pin caller-uid scoping", () => {
  assert.equal(customStyleCollectionPath(UID), `users/${UID}/customStyles`);
  assert.equal(customStyleDocPath(UID, "custom-x"), `users/${UID}/customStyles/custom-x`);
  assert.ok(!customStyleDocPath(OTHER_UID, "custom-x").includes(UID));
});

test("list: unauthenticated rejected", async () => {
  const { store } = fakeStore();
  const d = deps(store);
  const handler = createListCustomStylesHandler(d);
  await assert.rejects(() => handler({ auth: null, data: {} }), (e: unknown) => assertCode(e, "unauthenticated"));
});

test("list: non-google provider rejected, no entitlement consulted", async () => {
  const { store } = fakeStore();
  let entitledCalls = 0;
  const d = deps(store, {
    assertEntitled: async () => {
      entitledCalls++;
      throw new HttpsError("permission-denied", "nope");
    },
  });
  const handler = createListCustomStylesHandler(d);
  // Even an entitlement that would deny is never reached: provider is checked first.
  await assert.rejects(
    () => handler({ auth: { uid: UID, signInProvider: "anonymous" }, data: {} }),
    (e: unknown) => assertCode(e, "permission-denied"),
  );
  assert.equal(entitledCalls, 0);
});

test("list: exact-key strict, needs no entitlement", async () => {
  const { store } = fakeStore();
  const d = deps(store, {
    assertEntitled: async () => {
      throw new HttpsError("permission-denied", "must not be consulted");
    },
  });
  const handler = createListCustomStylesHandler(d);
  for (const data of [{ foo: 1 }, { style: "x" }, null, [], "x"]) {
    await assert.rejects(() => handler({ auth: GOOGLE_AUTH, data }), (e: unknown) => assertCode(e, "invalid-argument"));
  }
  const res = await handler({ auth: GOOGLE_AUTH, data: {} });
  assert.deepEqual(res.styles, []);
  assert.equal(res.syncedAtEpochMillis, 5000);
});

test("list: sorted updatedAt desc, malformed skipped", async () => {
  const { store } = fakeStore({
    [UID]: {
      "custom-old": storedFields({ updatedAtEpochMillis: 100 }),
      "custom-new": storedFields({ updatedAtEpochMillis: 300 }),
      "custom-mid": storedFields({ updatedAtEpochMillis: 200 }),
      "custom-bad": storedFields({ revision: 0, updatedAtEpochMillis: 9999 } as Partial<StoredCustomStyleFields>),
    },
  });
  const d = deps(store);
  const handler = createListCustomStylesHandler(d);
  const res = await handler({ auth: GOOGLE_AUTH, data: {} });
  assert.deepEqual(res.styles.map((s) => s.id), ["custom-new", "custom-mid", "custom-old"]);
  for (const s of res.styles) {
    assert.equal(s.kind, "llm");
    assert.equal(s.category, "custom");
    assert.equal(typeof s.revision, "number");
    assert.equal(typeof s.updatedAtEpochMillis, "number");
  }
});

test("list: foreign-uid docs never leak", async () => {
  const { store } = fakeStore({
    [UID]: { "custom-mine": storedFields() },
    [OTHER_UID]: { "custom-theirs": storedFields() },
  });
  const d = deps(store);
  const handler = createListCustomStylesHandler(d);
  const res = await handler({ auth: GOOGLE_AUTH, data: {} });
  assert.deepEqual(res.styles.map((s) => s.id), ["custom-mine"]);
});

test("save: auth + provider + exact-key ordering", async () => {
  const { store, getWrites } = fakeStore();
  let entitledCalls = 0;
  const d = deps(store, {
    assertEntitled: async () => {
      entitledCalls++;
    },
  });
  const handler = createSaveCustomStyleHandler(d);
  await assert.rejects(
    () => handler({ auth: null, data: { definition: validDefinition() } }),
    (e: unknown) => assertCode(e, "unauthenticated"),
  );
  await assert.rejects(
    () => handler({ auth: { uid: UID, signInProvider: "password" }, data: { definition: validDefinition() } }),
    (e: unknown) => assertCode(e, "permission-denied"),
  );
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition(), bogus: 1 } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { expectedRevision: 1 } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  assert.equal(entitledCalls, 0);
  assert.equal(getWrites(), 0);
});

test("save: entitlement denied before validation and before any write", async () => {
  const { store, getWrites } = fakeStore();
  let entitledCalls = 0;
  const d = deps(store, {
    assertEntitled: async () => {
      entitledCalls++;
      throw new HttpsError("permission-denied", "Bloviate styles subscription required.");
    },
  });
  const handler = createSaveCustomStyleHandler(d);
  // Shape errors surface before entitlement (same as styleText's parse-first order).
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: { nope: true } } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  assert.equal(entitledCalls, 0);
  // Content validation precedes entitlement (parse-first, like styleText);
  // entitlement still precedes any write.
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition({ displayName: "   " }) } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  assert.equal(entitledCalls, 0);
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition() } }),
    (e: unknown) => assertCode(e, "permission-denied"),
  );
  assert.equal(entitledCalls, 1);
  assert.equal(getWrites(), 0);
});

test("save: create allocates id, revision 1, clauses appended", async () => {
  const { store, getWrites } = fakeStore();
  const d = deps(store, { newId: () => "custom-alloc-1" });
  const handler = createSaveCustomStyleHandler(d);
  const res = await handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition() } });
  assert.equal(res.style.id, "custom-alloc-1");
  assert.equal(res.style.kind, "llm");
  assert.equal(res.style.category, "custom");
  assert.equal(res.style.revision, 1);
  assert.equal(res.style.createdAtEpochMillis, 5000);
  assert.equal(res.style.updatedAtEpochMillis, 5000);
  assert.ok(res.style.systemInstructions.includes(FACTS_CLAUSE));
  assert.ok(res.style.systemInstructions.includes(LANGUAGE_CLAUSE));
  assert.equal(getWrites(), 1);
});

test("save: clauses appended once, case-insensitive present check", async () => {
  assert.equal(withSafetyClauses("Write hard-boiled."), `Write hard-boiled. ${FACTS_CLAUSE} ${LANGUAGE_CLAUSE}`);
  const already = `Voice. ${FACTS_CLAUSE} ${LANGUAGE_CLAUSE}`;
  assert.equal(withSafetyClauses(already), already);
  const lower = `Voice. ${FACTS_CLAUSE.toLowerCase()} ${LANGUAGE_CLAUSE.toLowerCase()}`;
  assert.equal(withSafetyClauses(lower), lower);
});

test("save: validation boundaries", async () => {
  const { store, getWrites } = fakeStore();
  const d = deps(store);
  const handler = createSaveCustomStyleHandler(d);
  const cases: { name: string; definition: Record<string, unknown>; ok: boolean }[] = [
    { name: "displayName 40 ok", definition: validDefinition({ displayName: "d".repeat(40) }), ok: true },
    { name: "displayName 41 reject", definition: validDefinition({ displayName: "d".repeat(41) }), ok: false },
    { name: "displayName blank reject", definition: validDefinition({ displayName: "   " }), ok: false },
    { name: "description 240 ok", definition: validDefinition({ description: "d".repeat(MAX_DESCRIPTION) }), ok: true },
    { name: "description 241 reject", definition: validDefinition({ description: "d".repeat(MAX_DESCRIPTION + 1) }), ok: false },
    { name: "sample 512 ok", definition: validDefinition({ sample: "s".repeat(MAX_SAMPLE) }), ok: true },
    { name: "sample 513 reject", definition: validDefinition({ sample: "s".repeat(MAX_SAMPLE + 1) }), ok: false },
    { name: "instructions 6000 ok", definition: validDefinition({ systemInstructions: "i".repeat(MAX_SYSTEM_INSTRUCTIONS) }), ok: true },
    { name: "instructions 6001 reject", definition: validDefinition({ systemInstructions: "i".repeat(MAX_SYSTEM_INSTRUCTIONS + 1) }), ok: false },
    {
      name: "fewShots 1 reject",
      definition: validDefinition({ fewShots: [{ raw: "a", styled: "b" }] }),
      ok: false,
    },
    {
      name: "fewShots 6 reject",
      definition: validDefinition({
        fewShots: [1, 2, 3, 4, 5, 6].map((n) => ({ raw: `a${n}`, styled: `b${n}` })),
      }),
      ok: false,
    },
    {
      name: "fewShots 5 ok",
      definition: validDefinition({
        fewShots: [1, 2, 3, 4, 5].map((n) => ({ raw: `a${n}`, styled: `b${n}` })),
      }),
      ok: true,
    },
    {
      name: "fewShot side 1500 ok",
      definition: validDefinition({ fewShots: [{ raw: "r".repeat(1500), styled: "s" }, { raw: "a", styled: "b" }] }),
      ok: true,
    },
    {
      name: "fewShot side 1501 reject",
      definition: validDefinition({ fewShots: [{ raw: "r".repeat(1501), styled: "s" }, { raw: "a", styled: "b" }] }),
      ok: false,
    },
    {
      name: "fewShot blank side reject",
      definition: validDefinition({ fewShots: [{ raw: "  ", styled: "s" }, { raw: "a", styled: "b" }] }),
      ok: false,
    },
    {
      name: "fewShot extra key reject",
      definition: validDefinition({ fewShots: [{ raw: "a", styled: "b", extra: 1 }, { raw: "c", styled: "d" }] }),
      ok: false,
    },
    { name: "definition extra key reject", definition: validDefinition({ kind: "llm" }), ok: false },
    { name: "definition missing field reject", definition: { displayName: "x" }, ok: false },
    { name: "bad id reject", definition: validDefinition({ id: "nope" }), ok: false },
    { name: "id with slash reject", definition: validDefinition({ id: "custom-a/b" }), ok: false },
    { name: "built-in id reject", definition: validDefinition({ id: "polish" }), ok: false },
  ];
  let creates = 0;
  for (const c of cases) {
    if (c.ok) {
      const res = await handler({ auth: GOOGLE_AUTH, data: { definition: c.definition } });
      assert.ok(res.style.id.startsWith("custom-"), c.name);
      creates++;
    } else {
      await assert.rejects(() => handler({ auth: GOOGLE_AUTH, data: { definition: c.definition } }), (e: unknown) => {
        assertCode(e, "invalid-argument");
        return true;
      }, c.name);
    }
  }
  assert.equal(getWrites(), creates);
});

test("save: create cap 50 enforced", async () => {
  const seed: Record<string, StoredCustomStyleFields> = {};
  for (let i = 0; i < 50; i++) seed[`custom-seed-${i}`] = storedFields();
  const { store } = fakeStore({ [UID]: seed });
  const d = deps(store);
  const handler = createSaveCustomStyleHandler(d);
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition() } }),
    (e: unknown) => assertCode(e, "resource-exhausted"),
  );
  delete seed["custom-seed-49"];
  const { store: store2 } = fakeStore({ [UID]: seed });
  const d2 = deps(store2);
  const ok = createSaveCustomStyleHandler(d2);
  const res = await ok({ auth: GOOGLE_AUTH, data: { definition: validDefinition() } });
  assert.equal(res.style.revision, 1);
});

test("save: update requires id + expectedRevision, bumps revision, preserves createdAt", async () => {
  const { store } = fakeStore({
    [UID]: { "custom-noir": storedFields({ revision: 2, createdAtEpochMillis: 111, updatedAtEpochMillis: 222 }) },
  });
  const d = deps(store, { now: () => 999 });
  const handler = createSaveCustomStyleHandler(d);
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition({ id: "custom-noir" }) } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  await assert.rejects(
    () => handler({ auth: GOOGLE_AUTH, data: { definition: validDefinition(), expectedRevision: 2 } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  const res = await handler({
    auth: GOOGLE_AUTH,
    data: { definition: validDefinition({ id: "custom-noir", sample: "Fresh sample." }), expectedRevision: 2 },
  });
  assert.equal(res.style.revision, 3);
  assert.equal(res.style.sample, "Fresh sample.");
  assert.equal(res.style.createdAtEpochMillis, 111);
  assert.equal(res.style.updatedAtEpochMillis, 999);
});

test("save: update conflict and missing doc -> failed-precondition", async () => {
  const { store } = fakeStore({ [UID]: { "custom-noir": storedFields({ revision: 2 }) } });
  const d = deps(store);
  const handler = createSaveCustomStyleHandler(d);
  await assert.rejects(
    () =>
      handler({
        auth: GOOGLE_AUTH,
        data: { definition: validDefinition({ id: "custom-noir" }), expectedRevision: 1 },
      }),
    (e: unknown) => assertCode(e, "failed-precondition"),
  );
  await assert.rejects(
    () =>
      handler({
        auth: GOOGLE_AUTH,
        data: { definition: validDefinition({ id: "custom-ghost" }), expectedRevision: 1 },
      }),
    (e: unknown) => assertCode(e, "failed-precondition"),
  );
});

test("save: update is caller-uid isolated", async () => {
  const { store } = fakeStore({ [OTHER_UID]: { "custom-noir": storedFields({ revision: 1 }) } });
  const d = deps(store);
  const handler = createSaveCustomStyleHandler(d);
  // Same id under another uid is invisible to the caller.
  await assert.rejects(
    () =>
      handler({
        auth: GOOGLE_AUTH,
        data: { definition: validDefinition({ id: "custom-noir" }), expectedRevision: 1 },
      }),
    (e: unknown) => assertCode(e, "failed-precondition"),
  );
  const res = await createListCustomStylesHandler(deps(store))({ auth: OTHER_GOOGLE_AUTH, data: {} });
  assert.deepEqual(res.styles.map((s) => s.id), ["custom-noir"]);
});

test("delete: exact-key, idempotent, no entitlement, uid isolated", async () => {
  const { store, getWrites } = fakeStore({ [UID]: { "custom-noir": storedFields() } });
  const d = deps(store, {
    assertEntitled: async () => {
      throw new HttpsError("permission-denied", "must not be consulted");
    },
  });
  const handler = createDeleteCustomStyleHandler(d);
  await assert.rejects(() => handler({ auth: null, data: { id: "custom-noir" } }), (e: unknown) =>
    assertCode(e, "unauthenticated"),
  );
  await assert.rejects(
    () => handler({ auth: { uid: UID, signInProvider: "anonymous" }, data: { id: "custom-noir" } }),
    (e: unknown) => assertCode(e, "permission-denied"),
  );
  await assert.rejects(() => handler({ auth: GOOGLE_AUTH, data: { id: "custom-noir", extra: 1 } }), (e: unknown) =>
    assertCode(e, "invalid-argument"),
  );
  await assert.rejects(() => handler({ auth: GOOGLE_AUTH, data: { id: "not-custom" } }), (e: unknown) =>
    assertCode(e, "invalid-argument"),
  );
  const first = await handler({ auth: GOOGLE_AUTH, data: { id: "custom-noir" } });
  assert.deepEqual(first, { deleted: true });
  const second = await handler({ auth: GOOGLE_AUTH, data: { id: "custom-noir" } });
  assert.deepEqual(second, { deleted: false });
  const foreign = await handler({ auth: OTHER_GOOGLE_AUTH, data: { id: "custom-noir" } });
  assert.deepEqual(foreign, { deleted: false });
  assert.equal(getWrites(), 3);
});

test("no content logging canary", async () => {
  const secret = {
    displayName: `Name ${CANARY}`,
    description: "desc",
    sample: "sample",
    systemInstructions: "instructions",
    fewShots: [
      { raw: "a", styled: "b" },
      { raw: "c", styled: "d" },
    ],
  };
  const { store } = fakeStore();
  const d = deps(store);
  const save = createSaveCustomStyleHandler(d);
  const list = createListCustomStylesHandler(d);
  const del = createDeleteCustomStyleHandler(d);
  const created = await save({ auth: GOOGLE_AUTH, data: { definition: secret } });
  await list({ auth: GOOGLE_AUTH, data: {} });
  await del({ auth: GOOGLE_AUTH, data: { id: created.style.id } });
  await assert.rejects(
    () => save({ auth: GOOGLE_AUTH, data: { definition: { nope: true } } }),
    (e: unknown) => assertCode(e, "invalid-argument"),
  );
  const blob = JSON.stringify(d.records);
  assert.ok(!blob.includes(CANARY), "definition content leaked to logs");
  assert.ok(!blob.includes(UID), "raw uid leaked to logs");
});

test("validateCustomStyleDoc: malformed docs skipped", () => {
  assert.equal(validateCustomStyleDoc("not-custom", storedFields()), null);
  assert.equal(validateCustomStyleDoc("custom-x", null), null);
  assert.equal(validateCustomStyleDoc("custom-x", { ...storedFields(), schemaVersion: 2 }), null);
  assert.equal(validateCustomStyleDoc("custom-x", { ...storedFields(), revision: 0 }), null);
  assert.equal(validateCustomStyleDoc("custom-x", { ...storedFields(), revision: 1.5 }), null);
  assert.equal(validateCustomStyleDoc("custom-x", { ...storedFields(), fewShots: [{ raw: "a", styled: "b" }] }), null);
  assert.equal(validateCustomStyleDoc("polish", storedFields()), null);
  const ok = validateCustomStyleDoc("custom-x", storedFields());
  assert.ok(ok && ok.id === "custom-x" && ok.kind === "llm" && ok.category === "custom");
});
