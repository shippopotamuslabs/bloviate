/**
 * Per-user custom styles store (BLO-26 S3).
 *
 * Firestore layout: users/{uid}/customStyles/{id}, schema v1:
 *   schemaVersion (=1), displayName, description, sample,
 *   systemInstructions, fewShots [{raw, styled}], revision (starts 1),
 *   createdAtEpochMillis, updatedAtEpochMillis.
 *
 * kind/category are derived ("llm"/"custom") and never stored.
 */
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { styleById, type FewShot, type StyleDefinition } from "./styleCatalog";

export const CUSTOM_STYLE_SCHEMA_VERSION = 1;
export const CUSTOM_STYLE_ID_PREFIX = "custom-";
export const MAX_CUSTOM_STYLES_PER_USER = 50;

export const CUSTOM_STYLE_ID_PATTERN = /^custom-[^/\\\s]+$/;

export const FACTS_CLAUSE =
  "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.";
export const LANGUAGE_CLAUSE =
  "Render this voice in the transcript's own language — restyle the text in its own tongue; never translate it into English.";

export const GOOGLE_PROVIDER_ID = "google.com";

export const MAX_DISPLAY_NAME = 40;
export const MAX_DESCRIPTION = 120;
export const MAX_SAMPLE = 180;
export const MAX_SYSTEM_INSTRUCTIONS = 2400;
export const MAX_FEW_SHOT_SIDE = 1500;
export const MIN_FEW_SHOTS = 2;
export const MAX_FEW_SHOTS = 5;

export type CustomStyleKind = "llm";
export type CustomStyleCategory = "custom";

export type CanonicalCustomStyle = StyleDefinition & {
  kind: CustomStyleKind;
  category: CustomStyleCategory;
  revision: number;
  createdAtEpochMillis: number;
  updatedAtEpochMillis: number;
};

export type StoredCustomStyleFields = {
  schemaVersion: number;
  displayName: string;
  description: string;
  sample: string;
  systemInstructions: string;
  fewShots: FewShot[];
  revision: number;
  createdAtEpochMillis: number;
  updatedAtEpochMillis: number;
};

export type RawCustomStyleDoc = { id: string; data: unknown };

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value) as unknown;
  return proto === Object.prototype || proto === null;
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

export function isCustomStyleId(value: unknown): value is string {
  return typeof value === "string" && CUSTOM_STYLE_ID_PATTERN.test(value);
}

/** Client-supplied definition shape: exactly the six style fields, id optional. */
export type ClientCustomStyleDefinition = {
  id?: string;
  displayName: string;
  description: string;
  sample: string;
  systemInstructions: string;
  fewShots: FewShot[];
};

function invalid(message: string): HttpsError {
  return new HttpsError("invalid-argument", message);
}

function checkTextField(
  value: unknown,
  field: string,
  max: number,
): string {
  if (!isNonBlankString(value)) {
    throw invalid(`${field} must be a non-blank string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length > max) {
    throw invalid(`${field} must be at most ${max} characters.`);
  }
  return trimmed;
}

function parseFewShots(value: unknown): FewShot[] {
  if (!Array.isArray(value)) {
    throw invalid("fewShots must be an array.");
  }
  if (value.length < MIN_FEW_SHOTS || value.length > MAX_FEW_SHOTS) {
    throw invalid(`fewShots must contain ${MIN_FEW_SHOTS}..${MAX_FEW_SHOTS} pairs.`);
  }
  return value.map((entry, i) => {
    if (!isPlainObject(entry)) {
      throw invalid(`fewShots[${i}] must be an object.`);
    }
    const keys = Object.keys(entry);
    if (keys.length !== 2 || !keys.includes("raw") || !keys.includes("styled")) {
      throw invalid(`fewShots[${i}] must contain only raw and styled.`);
    }
    return {
      raw: checkTextField(entry.raw, `fewShots[${i}].raw`, MAX_FEW_SHOT_SIDE),
      styled: checkTextField(entry.styled, `fewShots[${i}].styled`, MAX_FEW_SHOT_SIDE),
    };
  });
}

/**
 * Validate a client-supplied definition (mirror of the Android
 * CustomStyleValidator caps). Returns the trimmed definition. Length caps
 * apply to the client-sent values, before safety-clause appending.
 */
export function parseCustomStyleDefinition(value: unknown): ClientCustomStyleDefinition {
  if (!isPlainObject(value)) {
    throw invalid("definition must be an object.");
  }
  const keys = Object.keys(value);
  const allowed = ["id", "displayName", "description", "sample", "systemInstructions", "fewShots"];
  if (!keys.includes("displayName") || !keys.includes("description") || !keys.includes("sample") ||
      !keys.includes("systemInstructions") || !keys.includes("fewShots")) {
    throw invalid("definition must contain displayName, description, sample, systemInstructions and fewShots.");
  }
  for (const k of keys) {
    if (!allowed.includes(k)) {
      throw invalid(`Unknown definition field: ${k}.`);
    }
  }
  let id: string | undefined;
  if (value.id !== undefined) {
    if (!isCustomStyleId(value.id)) {
      throw invalid("id must look like custom-<name> without slashes or spaces.");
    }
    if (styleById(value.id)) {
      throw invalid("id must not equal a built-in style id.");
    }
    id = value.id;
  }
  const definition: ClientCustomStyleDefinition = {
    displayName: checkTextField(value.displayName, "displayName", MAX_DISPLAY_NAME),
    description: checkTextField(value.description, "description", MAX_DESCRIPTION),
    sample: checkTextField(value.sample, "sample", MAX_SAMPLE),
    systemInstructions: checkTextField(value.systemInstructions, "systemInstructions", MAX_SYSTEM_INSTRUCTIONS),
    fewShots: parseFewShots(value.fewShots),
  };
  if (id !== undefined) definition.id = id;
  return definition;
}

/**
 * Append the two mandatory safety clauses to system instructions, each once,
 * space-separated, only when absent (case-insensitive contains check).
 */
export function withSafetyClauses(systemInstructions: string): string {
  let out = systemInstructions;
  if (!out.toLowerCase().includes(FACTS_CLAUSE.toLowerCase())) {
    out += (out.length > 0 ? " " : "") + FACTS_CLAUSE;
  }
  if (!out.toLowerCase().includes(LANGUAGE_CLAUSE.toLowerCase())) {
    out += (out.length > 0 ? " " : "") + LANGUAGE_CLAUSE;
  }
  return out;
}

export function toCanonicalCustomStyle(id: string, fields: StoredCustomStyleFields): CanonicalCustomStyle {
  return {
    id,
    kind: "llm",
    category: "custom",
    displayName: fields.displayName,
    description: fields.description,
    sample: fields.sample,
    systemInstructions: fields.systemInstructions,
    fewShots: fields.fewShots.map((s) => ({ raw: s.raw, styled: s.styled })),
    revision: fields.revision,
    createdAtEpochMillis: fields.createdAtEpochMillis,
    updatedAtEpochMillis: fields.updatedAtEpochMillis,
  };
}

/**
 * Validate a stored doc on read. Returns the canonical style, or null when
 * the doc is malformed (malformed docs are skipped, never returned).
 */
export function validateCustomStyleDoc(id: string, data: unknown): CanonicalCustomStyle | null {
  if (!isCustomStyleId(id)) return null;
  if (styleById(id)) return null;
  if (!isPlainObject(data)) return null;
  const { schemaVersion, displayName, description, sample, systemInstructions, fewShots, revision,
    createdAtEpochMillis, updatedAtEpochMillis } = data;
  if (schemaVersion !== CUSTOM_STYLE_SCHEMA_VERSION) return null;
  if (typeof displayName !== "string" || displayName.trim().length === 0 ||
      displayName.trim().length > MAX_DISPLAY_NAME) return null;
  if (typeof description !== "string" || description.trim().length === 0 ||
      description.trim().length > MAX_DESCRIPTION) return null;
  if (typeof sample !== "string" || sample.trim().length === 0 ||
      sample.trim().length > MAX_SAMPLE) return null;
  if (typeof systemInstructions !== "string" || systemInstructions.trim().length === 0) return null;
  if (!Array.isArray(fewShots) || fewShots.length < MIN_FEW_SHOTS || fewShots.length > MAX_FEW_SHOTS) {
    return null;
  }
  for (const shot of fewShots) {
    if (!isPlainObject(shot) || !isNonBlankString(shot.raw) || !isNonBlankString(shot.styled)) {
      return null;
    }
    if (shot.raw.trim().length > MAX_FEW_SHOT_SIDE || shot.styled.trim().length > MAX_FEW_SHOT_SIDE) {
      return null;
    }
  }
  if (!isFiniteNumber(revision) || !Number.isInteger(revision) || revision < 1) return null;
  if (!isFiniteNumber(createdAtEpochMillis) || !isFiniteNumber(updatedAtEpochMillis)) return null;
  return toCanonicalCustomStyle(id, {
    schemaVersion,
    displayName,
    description,
    sample,
    systemInstructions,
    fewShots: (fewShots as FewShot[]).map((s) => ({
      raw: (s as unknown as Record<string, string>).raw,
      styled: (s as unknown as Record<string, string>).styled,
    })),
    revision,
    createdAtEpochMillis,
    updatedAtEpochMillis,
  });
}

export function customStyleCollectionPath(uid: string): string {
  return `users/${uid}/customStyles`;
}

export function customStyleDocPath(uid: string, id: string): string {
  return `${customStyleCollectionPath(uid)}/${id}`;
}

/** Test seam: storage behind the custom-style callables. Production uses Admin SDK. */
export type CustomStyleStore = {
  list(uid: string): Promise<RawCustomStyleDoc[]>;
  create(uid: string, id: string, fields: StoredCustomStyleFields): Promise<void>;
  /**
   * Optimistic-concurrency update: the stored revision must equal
   * expectedRevision, else failed-precondition. Returns the new stored fields.
   */
  update(
    uid: string,
    id: string,
    expectedRevision: number,
    fields: Pick<
      StoredCustomStyleFields,
      "displayName" | "description" | "sample" | "systemInstructions" | "fewShots" | "updatedAtEpochMillis"
    > & {
      createdAtEpochMillis?: number;
    },
  ): Promise<StoredCustomStyleFields>;
  /** Idempotent delete. Returns true when a doc existed. */
  delete(uid: string, id: string): Promise<boolean>;
  get(uid: string, id: string): Promise<unknown>;
};

export function adminCustomStyleStore(): CustomStyleStore {
  const db = getFirestore();
  return {
    async list(uid) {
      const snap = await db.collection(customStyleCollectionPath(uid)).get();
      return snap.docs.map((d) => ({ id: d.id, data: d.data() }));
    },
    async create(uid, id, fields) {
      await db.doc(customStyleDocPath(uid, id)).set({ ...fields });
    },
    async update(uid, id, expectedRevision, fields) {
      const ref = db.doc(customStyleDocPath(uid, id));
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) {
          throw new HttpsError("failed-precondition", "Custom style does not exist.");
        }
        const current = snap.data() as Record<string, unknown> | undefined;
        if (current?.revision !== expectedRevision) {
          throw new HttpsError("failed-precondition", "Custom style was modified; reload and retry.");
        }
        const createdAt = fields.createdAtEpochMillis ??
          (typeof current?.createdAtEpochMillis === "number" ? current.createdAtEpochMillis : fields.updatedAtEpochMillis);
        const next: StoredCustomStyleFields = {
          schemaVersion: CUSTOM_STYLE_SCHEMA_VERSION,
          displayName: fields.displayName,
          description: fields.description,
          sample: fields.sample,
          systemInstructions: fields.systemInstructions,
          fewShots: fields.fewShots,
          revision: expectedRevision + 1,
          createdAtEpochMillis: createdAt,
          updatedAtEpochMillis: fields.updatedAtEpochMillis,
        };
        tx.set(ref, { ...next });
        return next;
      });
    },
    async delete(uid, id) {
      const ref = db.doc(customStyleDocPath(uid, id));
      const snap = await ref.get();
      if (!snap.exists) return false;
      await ref.delete();
      return true;
    },
    async get(uid, id) {
      const snap = await db.doc(customStyleDocPath(uid, id)).get();
      return snap.exists ? snap.data() : null;
    },
  };
}

/** Production styleText seam: fetch one validated custom style, else null. */
export async function fetchCustomStyleDoc(uid: string, styleId: string): Promise<StyleDefinition | null> {
  const data = await adminCustomStyleStore().get(uid, styleId);
  if (data === null) return null;
  const canonical = validateCustomStyleDoc(styleId, data);
  if (!canonical) return null;
  return {
    id: canonical.id,
    displayName: canonical.displayName,
    description: canonical.description,
    sample: canonical.sample,
    systemInstructions: canonical.systemInstructions,
    fewShots: canonical.fewShots,
  };
}
