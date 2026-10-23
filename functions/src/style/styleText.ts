import { createHash } from "node:crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { createLogger } from "../observability/structuredLog";
import { styleTextSessionId } from "../openrouterSession";
import { styleById } from "./styleCatalog";
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
  if (typeof styleId !== "string" || !styleById(styleId)) {
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

export type StyleTextDeps = {
  assertEntitled: (uid: string) => Promise<void>;
  complete: CompletionFn;
  log: (record: Record<string, unknown>) => void;
  now?: () => number;
};

export function createStyleTextHandler(
  deps: StyleTextDeps,
): (request: { auth?: { uid: string } | null; data: unknown }) => Promise<{ styledText: string }> {
  return async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const { styleId, transcript } = parseStyleTextRequest(request.data);
    // Entitlement MUST precede any model call; its permission-denied propagates.
    await deps.assertEntitled(uid);
    const style = styleById(styleId)!;
    const prompt = buildStylePrompt(style, transcript);
    const uidHash = hashUid(uid);
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
    });
    return handler({ auth: request.auth ?? null, data: request.data });
  },
);
