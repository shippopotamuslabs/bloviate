/**
 * generateCustomStyle callable (BLO-159 S6): turns one short user
 * description into a validated custom-style draft (NOT saved — the client
 * saves via saveCustomStyle, which derives id/kind/category/revision).
 *
 * Gate order, all before any model call: auth -> google.com provider ->
 * styles entitlement -> generation-cap reservation (resource-exhausted with
 * zero model calls when over cap). Then: classifier (fail closed) ->
 * generation (strict json_schema, json_object retry on 400/404/422, primary
 * then fallback) -> draft validation -> at most one repair -> success.
 *
 * Accounting: after the flow (success or model-failure), the summed
 * normalized tokens of EVERY attempt are added to the month usage doc.
 * Logs carry uidHash + outcome/reason codes + per-attempt usage — never the
 * description or draft content.
 */
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { createLogger } from "../observability/structuredLog";
import { sanitizeOpenRouterSessionId } from "../openrouterSession";
import { GOOGLE_PROVIDER_ID, withSafetyClauses } from "./customStyleStore";
import {
  buildClassifierPrompt,
  buildGenerationPrompt,
  buildRepairPrompt,
  CANONICAL_RAW_DICTATIONS,
  CLASSIFIER_JSON_SCHEMA,
  extractJsonObject,
  GENERATED_STYLE_JSON_SCHEMA,
  normalizeDescription,
  parseClassifierVerdict,
  validateGeneratedDraft,
  type GeneratedStyleDraft,
} from "./customStyleGeneration";
import {
  adminGenerationLimitStore,
  generationDateKeys,
  type GenerationLimitStore,
} from "./customGenerationLimits";
import {
  buildOpenRouterCompletionFn,
  FALLBACK_MODEL,
  PRIMARY_MODEL,
  runGenerationCompletion,
  StyleChainError,
  type AttemptRecord,
  type ChatMessage,
  type CompletionFn,
  type StructuredResponseFormat,
} from "./providerChain";
import { hashUid } from "./styleText";

export const GENERATION_TEMPERATURE = 0.7;
export const GENERATION_MAX_TOKENS = 3500;
export const CLASSIFIER_TEMPERATURE = 0;
export const CLASSIFIER_MAX_TOKENS = 100;
export const REPAIR_TEMPERATURE = 0.2;

export const CLASSIFIER_UNPARSEABLE_REASON = "classifier-verdict-unparseable";
export const NOT_STYLE_DESCRIPTION_REASON = "not-a-style-description";
export const GENERATION_UNAVAILABLE_MESSAGE = "style-generation-unavailable";
export const GENERATED_DRAFT_INVALID_REASON = "generated-style-invalid";

/** HTTP statuses that mean the endpoint rejects structured parameters. */
const PARAMETER_ERROR_OUTCOMES = new Set(["http-400", "http-404", "http-422"]);
const JSON_OBJECT_FORMAT: StructuredResponseFormat = { type: "json_object" };

export type GenerateCustomStyleAuth = {
  uid: string;
  signInProvider?: string;
};

export type GenerateCustomStyleRequest = {
  auth?: GenerateCustomStyleAuth | null;
  data: unknown;
};

export type GeneratedStylePreview = {
  displayName: string;
  description: string;
  sample: string;
  systemInstructions: string;
  fewShots: { raw: string; styled: string }[];
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value) as unknown;
  return proto === Object.prototype || proto === null;
}

/** Exact-key strict parsing: exactly {description}. */
export function parseGenerateCustomStyleRequest(data: unknown): { descriptionRaw: unknown } {
  if (!isPlainObject(data)) {
    throw new HttpsError("invalid-argument", "Request data must be an object.");
  }
  const keys = Object.keys(data);
  if (keys.length !== 1 || !keys.includes("description")) {
    throw new HttpsError("invalid-argument", "Only description is allowed.");
  }
  return { descriptionRaw: data.description };
}

export function generateCustomStyleSessionId(uid: string): string {
  return sanitizeOpenRouterSessionId(`bloviate-style-generate:${uid}`);
}

export type GenerateCustomStyleDeps = {
  assertEntitled: (uid: string) => Promise<void>;
  complete: CompletionFn;
  limits: GenerationLimitStore;
  log: (record: Record<string, unknown>) => void;
  now?: () => number;
};

type FailCode = "failed-precondition" | "invalid-argument" | "unavailable";

export function createGenerateCustomStyleHandler(deps: GenerateCustomStyleDeps) {
  return async (request: GenerateCustomStyleRequest): Promise<{ draft: GeneratedStylePreview }> => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    if (request.auth?.signInProvider !== GOOGLE_PROVIDER_ID) {
      throw new HttpsError("permission-denied", "Google sign-in required.");
    }
    const { descriptionRaw } = parseGenerateCustomStyleRequest(request.data);
    const description = normalizeDescription(descriptionRaw);
    const uidHash = hashUid(uid);
    const now = deps.now ?? Date.now;
    try {
      await deps.assertEntitled(uid);
    } catch (err) {
      deps.log({
        uidHash,
        op: "generate",
        outcome: err instanceof HttpsError ? err.code : "entitlement-error",
      });
      throw err;
    }

    const { dayKey, monthKey } = generationDateKeys(now());
    const allAttempts: AttemptRecord[] = [];
    let successAttempt: AttemptRecord | null = null;
    let successProvider: string | undefined;

    const logAttempts = (): void => {
      for (const a of allAttempts) {
        deps.log({
          uidHash,
          op: "generate",
          model: a.model,
          provider: a === successAttempt ? successProvider : undefined,
          degraded: a.degraded,
          outcome: a.outcome,
          promptTokens: a.promptTokens,
          completionTokens: a.completionTokens,
          reasoningTokens: a.reasoningTokens,
          normalizedTokens: a.normalizedTokens ?? 0,
          latencyMs: a.latencyMs,
          estimatedCostUsd: a.estimatedCostUsd,
        });
      }
    };

    const fail = (code: FailCode, reason: string): HttpsError => {
      logAttempts();
      deps.log({ uidHash, op: "generate", outcome: code, reason });
      return new HttpsError(code, reason);
    };

    // Account EVERY attempt (classifier, generation, fallback, repair) after
    // the flow, success or model-failure. Zero-attempt paths return before.
    const account = async (): Promise<void> => {
      if (allAttempts.length === 0) return;
      const total = allAttempts.reduce((sum, a) => sum + (a.normalizedTokens ?? 0), 0);
      try {
        await deps.limits.addNormalizedTokens(uid, monthKey, total);
      } catch {
        deps.log({ uidHash, op: "generate", outcome: "accounting-error" });
      }
    };

    try {
      await deps.limits.reserveGeneration(uid, dayKey, monthKey);
    } catch (err) {
      deps.log({
        uidHash,
        op: "generate",
        outcome: err instanceof HttpsError ? err.code : "limit-error",
      });
      throw err;
    }

    try {
      // One model, one format: null when the model stays failed so the
      // caller advances the chain. Appends every attempt for accounting.
      const tryModel = async (
        model: string,
        messages: ChatMessage[],
        format: StructuredResponseFormat,
        temperature: number,
        maxTokens: number,
      ): Promise<{ content: string; model: string; provider?: string } | null> => {
        try {
          const result = await runGenerationCompletion({
            complete: deps.complete,
            messages,
            models: [model],
            responseFormat: format,
            requireParameters: true,
            temperature,
            maxTokens,
            now,
          });
          allAttempts.push(...result.attempts);
          return result;
        } catch (err) {
          if (err instanceof StyleChainError) allAttempts.push(...err.attempts);
          return null;
        }
      };

      // Strict ladder with per-model json_object retry: first attempt strict
      // + require_parameters; on 400/404/422 retry that model unstructured;
      // then the fallback model the same way.
      const completeJsonLadder = async (
        messages: ChatMessage[],
        schemaName: string,
        schema: Record<string, unknown>,
        temperature: number,
        maxTokens: number,
      ): Promise<{ content: string; model: string; provider?: string }> => {
        const strict: StructuredResponseFormat = {
          type: "json_schema",
          name: schemaName,
          schema,
          strict: true,
        };
        for (const model of [PRIMARY_MODEL, FALLBACK_MODEL]) {
          const before = allAttempts.length;
          const direct = await tryModel(model, messages, strict, temperature, maxTokens);
          if (direct) return direct;
          const paramError = allAttempts
            .slice(before)
            .some((a) => a.model === model && PARAMETER_ERROR_OUTCOMES.has(a.outcome));
          if (paramError) {
            const retry = await tryModel(model, messages, JSON_OBJECT_FORMAT, temperature, maxTokens);
            if (retry) return retry;
          }
        }
        const last = allAttempts[allAttempts.length - 1];
        throw new StyleChainError(last?.outcome ?? "transport-error", [...allAttempts]);
      };

      // Classifier: structured verdict preferred, lenient JSON fallback
      // parsing; anything else fails closed.
      const classifierPrompt = buildClassifierPrompt(description);
      let classifier: { content: string; model: string; provider?: string };
      try {
        classifier = await completeJsonLadder(
          [
            { role: "system", content: classifierPrompt.system },
            { role: "user", content: classifierPrompt.user },
          ],
          "style_description_verdict",
          CLASSIFIER_JSON_SCHEMA,
          CLASSIFIER_TEMPERATURE,
          CLASSIFIER_MAX_TOKENS,
        );
      } catch {
        throw fail("unavailable", GENERATION_UNAVAILABLE_MESSAGE);
      }
      const verdict = parseClassifierVerdict(classifier.content);
      if (verdict === null) throw fail("failed-precondition", CLASSIFIER_UNPARSEABLE_REASON);
      if (verdict === "NOT_STYLE_DESCRIPTION") {
        throw fail("invalid-argument", NOT_STYLE_DESCRIPTION_REASON);
      }

      // Generation.
      const generationPrompt = buildGenerationPrompt(description);
      let generation: { content: string; model: string; provider?: string };
      try {
        generation = await completeJsonLadder(
          [
            { role: "system", content: generationPrompt.system },
            { role: "user", content: generationPrompt.user },
          ],
          "custom_style_draft",
          GENERATED_STYLE_JSON_SCHEMA,
          GENERATION_TEMPERATURE,
          GENERATION_MAX_TOKENS,
        );
      } catch {
        throw fail("unavailable", GENERATION_UNAVAILABLE_MESSAGE);
      }

      let checked = validateGeneratedDraft(extractJsonObject(generation.content));
      let finalProvider = generation.provider;
      if (!checked.ok) {
        // One repair: same model, low temperature, reason codes only.
        const repairPrompt = buildRepairPrompt({
          description,
          failedDraftJson: generation.content,
          reasons: checked.reasons,
        });
        const strict: StructuredResponseFormat = {
          type: "json_schema",
          name: "custom_style_draft",
          schema: GENERATED_STYLE_JSON_SCHEMA,
          strict: true,
        };
        let repaired: { content: string; model: string; provider?: string } | null = null;
        try {
          const result = await runGenerationCompletion({
            complete: deps.complete,
            messages: [
              { role: "system", content: repairPrompt.system },
              { role: "user", content: repairPrompt.user },
            ],
            models: [generation.model],
            responseFormat: strict,
            requireParameters: true,
            temperature: REPAIR_TEMPERATURE,
            maxTokens: GENERATION_MAX_TOKENS,
            now,
          });
          allAttempts.push(...result.attempts);
          repaired = result;
        } catch (err) {
          if (err instanceof StyleChainError) allAttempts.push(...err.attempts);
        }
        if (!repaired) throw fail("unavailable", GENERATION_UNAVAILABLE_MESSAGE);
        checked = validateGeneratedDraft(extractJsonObject(repaired.content));
        if (!checked.ok) throw fail("failed-precondition", GENERATED_DRAFT_INVALID_REASON);
        finalProvider = repaired.provider;
      }

      const value: GeneratedStyleDraft = checked.value;
      successAttempt = allAttempts[allAttempts.length - 1];
      successProvider = finalProvider;
      const draft: GeneratedStylePreview = {
        displayName: value.displayName,
        description: value.description,
        sample: value.sample,
        systemInstructions: withSafetyClauses(value.systemInstructions),
        fewShots: CANONICAL_RAW_DICTATIONS.map((raw, i) => ({
          raw,
          styled: value.fewShotStyledOutputs[i],
        })),
      };
      logAttempts();
      deps.log({ uidHash, op: "generate", outcome: "ok" });
      return { draft };
    } finally {
      await account();
    }
  };
}

const generateLogger = createLogger({ service: "generateCustomStyle" });

export const generateCustomStyle = onCall(
  {
    secrets: ["OPENROUTER_API_KEY"],
    timeoutSeconds: 120,
    memory: "512MiB",
    // Same posture as styleText: App Check enforced (BLO-172).
    enforceAppCheck: true,
  },
  async (request) => {
    let cachedComplete: CompletionFn | undefined;
    const complete: CompletionFn = async (req) => {
      if (!cachedComplete) {
        const apiKey = process.env.OPENROUTER_API_KEY;
        if (!apiKey) {
          throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
        }
        cachedComplete = buildOpenRouterCompletionFn(
          apiKey,
          generateCustomStyleSessionId(request.auth?.uid ?? ""),
        );
      }
      return cachedComplete(req);
    };
    const handler = createGenerateCustomStyleHandler({
      assertEntitled: (uid) => assertUserEntitled(uid),
      complete,
      limits: adminGenerationLimitStore(),
      log: (record) => generateLogger.info("generateCustomStyle", record),
    });
    return handler({
      auth: request.auth
        ? { uid: request.auth.uid, signInProvider: request.auth.token?.firebase?.sign_in_provider }
        : null,
      data: request.data,
    });
  },
);
