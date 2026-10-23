import { createHash } from "node:crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { createLogger } from "../observability/structuredLog";
import { styleTextSessionId } from "../openrouterSession";
import { fetchCustomStyleDoc, GOOGLE_PROVIDER_ID } from "./customStyleStore";
import { styleById, type StyleDefinition } from "./styleCatalog";
import { buildStylePrompt } from "./stylePromptBuilder";
import {
  buildOpenRouterCompletionFn,
  runStyleChain,
  StyleChainError,
  type CompletionFn,
} from "./providerChain";

export type StyleTextRequestData = { styleId: string; transcript: string };

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value) as unknown;
  return proto === Object.prototype || proto === null;
}

export function parseStyleTextRequest(data: unknown): StyleTextRequestData {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  const keys = Object.keys(data);
  // No-free-form-prompt law: exactly styleId + transcript, any extra key rejected.
  if (keys.length !== 2 || !keys.includes("styleId") || !keys.includes("transcript")) {
    throw new HttpsError("invalid-argument", "Only styleId and transcript are allowed.");
  }
  const styleId = data.styleId;
  // Shape check only: built-in ids resolve against the catalog, while
  // custom- ids defer to per-user Firestore resolution after entitlement.
  if (typeof styleId !== "string" || styleId.length === 0) {
    throw new HttpsError("invalid-argument", "Unknown styleId.");
  }
  if (!styleId.startsWith("custom-") && !styleById(styleId)) {
    throw new HttpsError("invalid-argument", "Unknown styleId.");
  }
  const transcript = data.transcript;
  if (typeof transcript !== "string" || transcript.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Transcript is required.");
  }
  // No character-length check of any kind (amended ruling: no per-call cap).
  return { styleId, transcript };
}

export function hashUid(uid: string): string {
  return createHash("sha256").update(uid).digest("hex");
}

export type StyleTextAuth = {
  uid: string;
  signInProvider?: string;
};

export type StyleTextDeps = {
  assertEntitled: (uid: string) => Promise<void>;
  complete: CompletionFn;
  log: (record: Record<string, unknown>) => void;
  now?: () => number;
  fetchCustomStyle: (uid: string, styleId: string) => Promise<StyleDefinition | null>;
};

export function createStyleTextHandler(
  deps: StyleTextDeps,
): (request: { auth?: StyleTextAuth | null; data: unknown }) => Promise<{ styledText: string }> {
  return async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const { styleId, transcript } = parseStyleTextRequest(request.data);
    const uidHash = hashUid(uid);
    // Entitlement MUST precede any model call; its permission-denied propagates.
    // A rejection is logged with the uid hash before propagating (ENC-187):
    // without this record a rejected call leaves zero structured function
    // logs, and a rotated anonymous uid becomes undiagnosable — the hash is
    // what an operator matches against candidate uids to re-grant access.
    try {
      await deps.assertEntitled(uid);
    } catch (err) {
      deps.log({
        uidHash,
        styleId,
        outcome: err instanceof HttpsError ? err.code : "entitlement-error",
      });
      throw err;
    }
    // Catalog resolution, after entitlement so no provider/model call happens
    // on any reject path below.
    let style = styleById(styleId);
    if (!style && styleId.startsWith("custom-")) {
      if (request.auth?.signInProvider !== GOOGLE_PROVIDER_ID) {
        throw new HttpsError("permission-denied", "Google sign-in required for custom styles.");
      }
      const custom = await deps.fetchCustomStyle(uid, styleId);
      if (!custom) {
        throw new HttpsError("invalid-argument", "Unknown styleId.");
      }
      style = custom;
    }
    if (!style) {
      throw new HttpsError("invalid-argument", "Unknown styleId.");
    }
    const prompt = buildStylePrompt(style, transcript);
    let result: Awaited<ReturnType<typeof runStyleChain>>;
    try {
      result = await runStyleChain({
        complete: deps.complete,
        system: prompt.system,
        user: prompt.user,
        sourceText: transcript,
        now: deps.now,
      });
    } catch (err) {
      if (err instanceof StyleChainError) {
        for (const a of err.attempts) {
          deps.log({
            uidHash,
            styleId,
            model: a.model,
            provider: undefined,
            degraded: a.degraded,
            outcome: a.outcome,
            promptTokens: a.promptTokens,
            completionTokens: a.completionTokens,
            reasoningTokens: a.reasoningTokens,
            latencyMs: a.latencyMs,
            estimatedCostUsd: a.estimatedCostUsd,
          });
        }
        // Today's client contract: FellBack -> keep local text; a server chain
        // failure maps to this single code (ENC-151 will treat it as such).
        throw new HttpsError("unavailable", "style-unavailable");
      }
      throw err;
    }
    for (const a of result.attempts) {
      deps.log({
        uidHash,
        styleId,
        model: a.model,
        provider: a.outcome === "styled" ? result.provider : undefined,
        degraded: a.degraded,
        outcome: a.outcome,
        promptTokens: a.promptTokens,
        completionTokens: a.completionTokens,
        reasoningTokens: a.reasoningTokens,
        latencyMs: a.latencyMs,
        estimatedCostUsd: a.estimatedCostUsd,
      });
    }
    const last = result.attempts[result.attempts.length - 1];
    if (last) {
      deps.log({
        uidHash,
        styleId,
        model: result.model,
        provider: result.provider,
        degraded: last.degraded,
        outcome: last.outcome,
        promptTokens: last.promptTokens,
        completionTokens: last.completionTokens,
        reasoningTokens: last.reasoningTokens,
        latencyMs: last.latencyMs,
        estimatedCostUsd: last.estimatedCostUsd,
      });
    }
    return { styledText: result.text };
  };
}

const logger = createLogger({ service: "styleText" });

export const styleText = onCall(
  {
    secrets: ["OPENROUTER_API_KEY"],
    timeoutSeconds: 120,
    memory: "512MiB",
    // App Check is NOT enforced yet: the client does not mint App Check tokens
    // (no Play Integrity / debug provider wiring), so enforcing it here rejects
    // every legitimate call as UNAUTHENTICATED. Auth + entitlement still gate
    // every call. ENC-151's remainder (with ENC-150 abuse controls) wires App
    // Check into the client and re-enables enforcement. Handler tests exercise
    // the factory, not this wrapper.
    enforceAppCheck: false,
  },
  async (request) => {
    let cachedComplete: CompletionFn | undefined;
    const complete: CompletionFn = async (req) => {
      if (!cachedComplete) {
        const apiKey = process.env.OPENROUTER_API_KEY;
        if (!apiKey) {
          throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
        }
        // Built lazily after the unauthenticated check inside the handler path.
        cachedComplete = buildOpenRouterCompletionFn(
          apiKey,
          styleTextSessionId(request.auth?.uid ?? ""),
        );
      }
      return cachedComplete(req);
    };
    const handler = createStyleTextHandler({
      assertEntitled: (uid) => assertUserEntitled(uid),
      complete,
      log: (record) => logger.info("styleText", record),
      fetchCustomStyle: (uid, styleId) => fetchCustomStyleDoc(uid, styleId),
    });
    return handler({
      auth: request.auth
        ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
        : null,
      data: request.data,
    });
  },
);
