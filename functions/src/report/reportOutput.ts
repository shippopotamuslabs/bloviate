/**
 * User-initiated AI-output reporting (BLO-148).
 *
 * submitOutputReport is the ONLY path that uploads dictation content,
 * and only because the user tapped a reason and submitted. Nothing in
 * generation, history, logging, or analytics sends content anywhere.
 *
 * Reports land in the top-level `reports` collection, written only by
 * this callable (Admin SDK). Identity comes from the verified token —
 * the payload carries no uid. The document id is
 * sha256(uid + ":" + clientReportId), so a double-tap or a retried call
 * with the same clientReportId is a no-op duplicate, never a second doc.
 *
 * Submitting a report changes nothing about the reporter's account: no
 * automatic revocation, no entitlement write. Review is a human reading
 * the collection in the Firebase console (see the release runbook).
 *
 * Logs carry uidHash + outcome + reason code only — never the reported
 * content, the transcript, or the style's text.
 */
import { createHash } from "node:crypto";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { createLogger } from "../observability/structuredLog";
import { GOOGLE_PROVIDER_ID, isCustomStyleId } from "../style/customStyleStore";
import { styleById } from "../style/styleCatalog";
import { hashUid } from "../style/styleText";

export const REPORT_REASONS = ["offensive", "sexual", "hateful", "wrong", "other"] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

export const MAX_REPORT_CONTENT_CHARS = 20_000;
export const MAX_REPORT_META_CHARS = 128;
export const MAX_APP_VERSION_CHARS = 64;

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type OutputReportRecord = {
  reporterUid: string;
  styledOutput: string;
  sourceTranscript: string;
  reason: ReportReason;
  styleId: string;
  provider: string | null;
  model: string | null;
  appVersion: string;
  clientReportId: string;
  status: "new";
};

export type ReportStore = {
  /** Creates the doc iff absent. Resolves true when created, false when it already existed. */
  createIfAbsent(reportId: string, record: OutputReportRecord): Promise<boolean>;
};

export function adminReportStore(): ReportStore {
  return {
    async createIfAbsent(reportId, record) {
      try {
        await getFirestore()
          .doc(`reports/${reportId}`)
          .create({ ...record, createdAt: FieldValue.serverTimestamp() });
        return true;
      } catch (err) {
        if ((err as { code?: unknown })?.code === 6) return false; // ALREADY_EXISTS
        throw err;
      }
    },
  };
}

export function reportDocumentId(uid: string, clientReportId: string): string {
  return createHash("sha256").update(`${uid}:${clientReportId}`).digest("hex");
}

export type ReportAuth = {
  uid: string;
  signInProvider?: string;
};

export type ReportRequest = {
  auth?: ReportAuth | null;
  data: unknown;
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value) as unknown;
  return proto === Object.prototype || proto === null;
}

function requireBoundedString(
  data: Record<string, unknown>,
  key: string,
  maxChars: number,
): string {
  const value = data[key];
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxChars) {
    throw new HttpsError("invalid-argument", `Field ${key} is invalid.`);
  }
  return value;
}

function optionalMeta(data: Record<string, unknown>, key: string): string | null {
  const value = data[key];
  if (value === null) return null;
  if (typeof value !== "string" || value.length > MAX_REPORT_META_CHARS) {
    throw new HttpsError("invalid-argument", `Field ${key} is invalid.`);
  }
  return value;
}

const REPORT_KEYS = [
  "clientReportId",
  "styledOutput",
  "sourceTranscript",
  "reason",
  "styleId",
  "provider",
  "model",
  "appVersion",
] as const;

export function parseReportRequest(data: unknown): Omit<OutputReportRecord, "reporterUid" | "status"> {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  const keys = Object.keys(data);
  if (keys.length !== REPORT_KEYS.length || !REPORT_KEYS.every((k) => keys.includes(k))) {
    throw new HttpsError("invalid-argument", "Report fields are invalid.");
  }
  const clientReportId = data.clientReportId;
  if (typeof clientReportId !== "string" || !UUID_PATTERN.test(clientReportId)) {
    throw new HttpsError("invalid-argument", "clientReportId is invalid.");
  }
  const reason = data.reason;
  if (typeof reason !== "string" || !(REPORT_REASONS as readonly string[]).includes(reason)) {
    throw new HttpsError("invalid-argument", "reason is invalid.");
  }
  const styleId = data.styleId;
  if (
    typeof styleId !== "string" ||
    (!styleById(styleId) && !isCustomStyleId(styleId))
  ) {
    throw new HttpsError("invalid-argument", "styleId is invalid.");
  }
  return {
    clientReportId,
    styledOutput: requireBoundedString(data, "styledOutput", MAX_REPORT_CONTENT_CHARS),
    sourceTranscript: requireBoundedString(data, "sourceTranscript", MAX_REPORT_CONTENT_CHARS),
    reason: reason as ReportReason,
    styleId,
    provider: optionalMeta(data, "provider"),
    model: optionalMeta(data, "model"),
    appVersion: requireBoundedString(data, "appVersion", MAX_APP_VERSION_CHARS),
  };
}

export type SubmitOutputReportDeps = {
  store: ReportStore;
  log: (record: Record<string, unknown>) => void;
};

export function createSubmitOutputReportHandler(deps: SubmitOutputReportDeps) {
  return async (
    request: ReportRequest,
  ): Promise<{ reportId: string; duplicate: boolean }> => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    if (request.auth?.signInProvider !== GOOGLE_PROVIDER_ID) {
      throw new HttpsError("permission-denied", "Google sign-in required.");
    }
    const uidHash = hashUid(uid);
    try {
      const parsed = parseReportRequest(request.data);
      const record: OutputReportRecord = { ...parsed, reporterUid: uid, status: "new" };
      const reportId = reportDocumentId(uid, parsed.clientReportId);
      const created = await deps.store.createIfAbsent(reportId, record);
      deps.log({
        uidHash,
        op: "report",
        outcome: created ? "ok" : "duplicate",
        reason: parsed.reason,
      });
      return { reportId, duplicate: !created };
    } catch (err) {
      deps.log({
        uidHash,
        op: "report",
        outcome: err instanceof HttpsError ? err.code : "internal",
      });
      throw err;
    }
  };
}

const reportLogger = createLogger({ service: "submitOutputReport" });

export const submitOutputReport = onCall(
  {
    // Same posture as the other callables: App Check enforced (BLO-172),
    // Firestore-only work, no secrets.
    enforceAppCheck: true,
    timeoutSeconds: 30,
    memory: "512MiB" as const,
  },
  async (request) => {
    const handler = createSubmitOutputReportHandler({
      store: adminReportStore(),
      log: (record) => reportLogger.info("submitOutputReport", record),
    });
    return handler({
      auth: request.auth
        ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
        : null,
      data: request.data,
    });
  },
);
