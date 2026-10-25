/**
 * Custom-style CRUD callables (BLO-26 S3).
 *
 * - listCustomStyles: auth + google.com, no entitlement.
 * - saveCustomStyle: auth + google.com + styles entitlement before any write.
 * - deleteCustomStyle: auth + google.com, idempotent, no entitlement.
 *
 * Data parsers are exact-key strict like parseStyleTextRequest. Logs carry
 * uidHash + codes only — never definition, description, or few-shot content.
 */
import { randomUUID } from "node:crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { createLogger } from "../observability/structuredLog";
import { hashUid } from "./styleText";
import {
  adminCustomStyleStore,
  CUSTOM_STYLE_SCHEMA_VERSION,
  GOOGLE_PROVIDER_ID,
  isCustomStyleId,
  MAX_CUSTOM_STYLES_PER_USER,
  parseCustomStyleDefinition,
  toCanonicalCustomStyle,
  validateCustomStyleDoc,
  withSafetyClauses,
  type CanonicalCustomStyle,
  type ClientCustomStyleDefinition,
  type CustomStyleStore,
  type StoredCustomStyleFields,
} from "./customStyleStore";

export type CustomStyleAuth = {
  uid: string;
  signInProvider?: string;
};

export type CustomStyleRequest = {
  auth?: CustomStyleAuth | null;
  data: unknown;
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value) as unknown;
  return proto === Object.prototype || proto === null;
}

/** Auth required; provider must be google.com. Provider failures -> permission-denied. */
function requireGoogleUid(request: CustomStyleRequest): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  if (request.auth?.signInProvider !== GOOGLE_PROVIDER_ID) {
    throw new HttpsError("permission-denied", "Google sign-in required.");
  }
  return uid;
}

function parseListRequest(data: unknown): void {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  if (Object.keys(data).length !== 0) {
    throw new HttpsError("invalid-argument", "listCustomStyles takes no arguments.");
  }
}

function parseDeleteRequest(data: unknown): { id: string } {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  const keys = Object.keys(data);
  if (keys.length !== 1 || !keys.includes("id")) {
    throw new HttpsError("invalid-argument", "Only id is allowed.");
  }
  if (!isCustomStyleId(data.id)) {
    throw new HttpsError("invalid-argument", "Unknown custom style id.");
  }
  return { id: data.id };
}

export type SaveCustomStyleData = {
  definition: ClientCustomStyleDefinition;
  expectedRevision?: number;
};

function parseSaveRequest(data: unknown): SaveCustomStyleData {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  const keys = Object.keys(data);
  if (!keys.includes("definition")) {
    throw new HttpsError("invalid-argument", "definition is required.");
  }
  for (const k of keys) {
    if (k !== "definition" && k !== "expectedRevision") {
      throw new HttpsError("invalid-argument", `Unknown field: ${k}.`);
    }
  }
  const definition = parseCustomStyleDefinition(data.definition);
  let expectedRevision: number | undefined;
  if (data.expectedRevision !== undefined) {
    if (typeof data.expectedRevision !== "number" || !Number.isInteger(data.expectedRevision) ||
        data.expectedRevision < 1) {
      throw new HttpsError("invalid-argument", "expectedRevision must be a positive integer.");
    }
    expectedRevision = data.expectedRevision;
  }
  if (definition.id === undefined && expectedRevision !== undefined) {
    throw new HttpsError("invalid-argument", "expectedRevision requires an id.");
  }
  if (definition.id !== undefined && expectedRevision === undefined) {
    throw new HttpsError("invalid-argument", "expectedRevision is required to update a style.");
  }
  return { definition, expectedRevision };
}

export type CustomStylesDeps = {
  assertEntitled: (uid: string) => Promise<void>;
  store: CustomStyleStore;
  log: (record: Record<string, unknown>) => void;
  now?: () => number;
  newId?: () => string;
};

function fail(deps: CustomStylesDeps, uidHash: string, op: string, err: unknown): never {
  deps.log({ uidHash, op, outcome: err instanceof HttpsError ? err.code : "internal" });
  throw err;
}

export function createListCustomStylesHandler(deps: CustomStylesDeps) {
  return async (request: CustomStyleRequest): Promise<{
    styles: CanonicalCustomStyle[];
    syncedAtEpochMillis: number;
  }> => {
    const uid = requireGoogleUid(request);
    const uidHash = hashUid(uid);
    try {
      parseListRequest(request.data);
      const now = (deps.now ?? Date.now)();
      const docs = await deps.store.list(uid);
      const styles = docs
        .map((d) => validateCustomStyleDoc(d.id, d.data))
        .filter((s): s is CanonicalCustomStyle => s !== null)
        .sort((a, b) => b.updatedAtEpochMillis - a.updatedAtEpochMillis || (a.id < b.id ? -1 : 1));
      deps.log({ uidHash, op: "list", outcome: "ok", count: styles.length });
      return { styles, syncedAtEpochMillis: now };
    } catch (err) {
      fail(deps, uidHash, "list", err);
    }
  };
}

export function createSaveCustomStyleHandler(deps: CustomStylesDeps) {
  return async (request: CustomStyleRequest): Promise<{ style: CanonicalCustomStyle }> => {
    const uid = requireGoogleUid(request);
    const uidHash = hashUid(uid);
    try {
      const { definition, expectedRevision } = parseSaveRequest(request.data);
      // Entitlement precedes validation and any write.
      try {
        await deps.assertEntitled(uid);
      } catch (err) {
        deps.log({ uidHash, op: "save", outcome: err instanceof HttpsError ? err.code : "entitlement-error" });
        throw err;
      }
      const now = (deps.now ?? Date.now)();
      const newId = deps.newId ?? (() => `custom-${randomUUID()}`);
      if (definition.id === undefined) {
        const existing = await deps.store.list(uid);
        if (existing.length >= MAX_CUSTOM_STYLES_PER_USER) {
          throw new HttpsError("resource-exhausted", "Custom style limit reached.");
        }
        const id = newId();
        if (!isCustomStyleId(id)) {
          throw new HttpsError("internal", "Allocated style id is invalid.");
        }
        const fields: StoredCustomStyleFields = {
          schemaVersion: CUSTOM_STYLE_SCHEMA_VERSION,
          displayName: definition.displayName,
          description: definition.description,
          sample: definition.sample,
          systemInstructions: withSafetyClauses(definition.systemInstructions),
          fewShots: definition.fewShots,
          revision: 1,
          createdAtEpochMillis: now,
          updatedAtEpochMillis: now,
        };
        await deps.store.create(uid, id, fields);
        const style = toCanonicalCustomStyle(id, fields);
        deps.log({ uidHash, op: "save", outcome: "ok", created: true, revision: 1 });
        return { style };
      }
      const updated = await deps.store.update(uid, definition.id, expectedRevision!, {
        displayName: definition.displayName,
        description: definition.description,
        sample: definition.sample,
        systemInstructions: withSafetyClauses(definition.systemInstructions),
        fewShots: definition.fewShots,
        updatedAtEpochMillis: now,
      });
      const style = toCanonicalCustomStyle(definition.id, updated);
      deps.log({ uidHash, op: "save", outcome: "ok", created: false, revision: updated.revision });
      return { style };
    } catch (err) {
      if (err instanceof HttpsError && err.code === "permission-denied") {
        // Already logged at the entitlement site above; avoid double logging.
        throw err;
      }
      fail(deps, uidHash, "save", err);
    }
  };
}

export function createDeleteCustomStyleHandler(deps: CustomStylesDeps) {
  return async (request: CustomStyleRequest): Promise<{ deleted: boolean }> => {
    const uid = requireGoogleUid(request);
    const uidHash = hashUid(uid);
    try {
      const { id } = parseDeleteRequest(request.data);
      const deleted = await deps.store.delete(uid, id);
      deps.log({ uidHash, op: "delete", outcome: "ok", deleted });
      return { deleted };
    } catch (err) {
      fail(deps, uidHash, "delete", err);
    }
  };
}

const crudOptions = {
  // Same posture as styleText: no secrets needed (CRUD touches Firestore only),
  // App Check enforced (BLO-172).
  enforceAppCheck: true,
  timeoutSeconds: 30,
  memory: "512MiB" as const,
};

const crudLogger = createLogger({ service: "customStyles" });

export const listCustomStyles = onCall(crudOptions, async (request) => {
  const handler = createListCustomStylesHandler({
    assertEntitled: (uid) => assertUserEntitled(uid),
    store: adminCustomStyleStore(),
    log: (record) => crudLogger.info("listCustomStyles", record),
  });
  return handler({
    auth: request.auth
      ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
      : null,
    data: request.data,
  });
});

export const saveCustomStyle = onCall(crudOptions, async (request) => {
  const handler = createSaveCustomStyleHandler({
    assertEntitled: (uid) => assertUserEntitled(uid),
    store: adminCustomStyleStore(),
    log: (record) => crudLogger.info("saveCustomStyle", record),
  });
  return handler({
    auth: request.auth
      ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
      : null,
    data: request.data,
  });
});

export const deleteCustomStyle = onCall(crudOptions, async (request) => {
  const handler = createDeleteCustomStyleHandler({
    assertEntitled: (uid) => assertUserEntitled(uid),
    store: adminCustomStyleStore(),
    log: (record) => crudLogger.info("deleteCustomStyle", record),
  });
  return handler({
    auth: request.auth
      ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
      : null,
    data: request.data,
  });
});
