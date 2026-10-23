import { createOpenRouterClient, buildOpenRouterChatCompletionExtras } from "../openrouterSession";
import { cleanLlmResponse } from "./llmResponseCleaner";
import { classifyStyleOutput } from "./styleOutputGuard";

export const PRIMARY_MODEL = "deepseek/deepseek-v4.1-flash-1017";
export const FALLBACK_MODEL = "google/gemini-3.1-flash-lite";
export const PRIMARY_PROVIDER_ORDER: readonly string[] = [
  "inference-net/fp8",
  "together",
  "baseten",
  "fireworks",
  "crusoe/fp8",
  "deepinfra/fp8",
];
export const PRIMARY_PROVIDER_ONLY: readonly string[] = PRIMARY_PROVIDER_ORDER;
export const FALLBACK_PROVIDER_ONLY: readonly string[] = [
  "google-vertex/global",
  "google-vertex/us",
  "google-vertex/eu",
];
export const FALLBACK_PROVIDER_SORT = "latency";
export const TEMPERATURE = 0.3;
export const REASONING_EFFORT = "none";

export const MODEL_PRICES_USD_PER_1M: Record<string, { input: number; output: number }> = {
  [PRIMARY_MODEL]: { input: 0.15, output: 0.60 },
  [FALLBACK_MODEL]: { input: 0.25, output: 1.50 },
};

export type ChatMessage = { role: string; content: string };

/**
 * Structured-output request for models that support it. `json_schema`
 * carries a strict schema; `json_object` asks for JSON without a schema
 * (retry path for endpoints that reject schema/parameterized requests).
 */
export type StructuredResponseFormat =
  | { type: "json_schema"; name: string; schema: Record<string, unknown>; strict: boolean }
  | { type: "json_object" };

export type CompletionRequest = {
  model: string;
  messages: ChatMessage[];
  providerOrder?: string[];
  providerOnly?: string[];
  providerSort?: string;
  reasoningEffort: string;
  degraded: boolean;
  maxTokens: number;
  temperature: number;
  responseFormat?: StructuredResponseFormat;
  requireParameters?: boolean;
};
export type CompletionUsage = {
  promptTokens?: number;
  completionTokens?: number;
  reasoningTokens?: number;
};
export type CompletionResult = {
  content: string;
  provider?: string;
  usage?: CompletionUsage;
};
export type CompletionFn = (req: CompletionRequest) => Promise<CompletionResult>;

export type AttemptRecord = {
  model: string;
  degraded: boolean;
  outcome: string;
  latencyMs: number;
  promptTokens?: number;
  completionTokens?: number;
  reasoningTokens?: number;
  estimatedCostUsd?: number;
  normalizedTokens?: number;
};

export class StyleChainError extends Error {
  readonly reason: string;
  readonly attempts: AttemptRecord[];
  constructor(reason: string, attempts: AttemptRecord[]) {
    super(reason);
    this.name = "StyleChainError";
    this.reason = reason;
    this.attempts = attempts;
  }
}

export function estimateCostUsd(model: string, usage?: CompletionUsage): number {
  if (!usage) return 0;
  const price = MODEL_PRICES_USD_PER_1M[model];
  if (!price) return 0;
  const prompt = usage.promptTokens ?? 0;
  const completion = usage.completionTokens ?? 0;
  return (prompt / 1_000_000) * price.input + (completion / 1_000_000) * price.output;
}

/**
 * Normalized token accounting: token usage re-based to the primary input
 * price ($0.15/1M) so usage sums are comparable across models. Reasoning
 * tokens count as completion (output) tokens.
 */
export function normalizedTokens(model: string, usage?: CompletionUsage): number {
  if (!usage) return 0;
  const price = MODEL_PRICES_USD_PER_1M[model];
  if (!price) return 0;
  const prompt = usage.promptTokens ?? 0;
  const completion = (usage.completionTokens ?? 0) + (usage.reasoningTokens ?? 0);
  return Math.round((prompt * price.input) / 0.15 + (completion * price.output) / 0.15);
}

export function maxTokensForTranscript(transcript: string): number {
  return Math.min(8000, Math.max(1024, Math.ceil(transcript.length / 4) * 2));
}

type Link = {
  model: string;
  providerOrder?: string[];
  providerOnly?: string[];
  providerSort?: string;
};

const LINKS: Link[] = [
  {
    model: PRIMARY_MODEL,
    providerOrder: [...PRIMARY_PROVIDER_ORDER],
    providerOnly: [...PRIMARY_PROVIDER_ORDER],
  },
  {
    model: FALLBACK_MODEL,
    providerOnly: [...FALLBACK_PROVIDER_ONLY],
    providerSort: FALLBACK_PROVIDER_SORT,
  },
];

function errorStatus(err: unknown): number | undefined {
  if (err && typeof err === "object" && "status" in err) {
    const s = (err as { status?: unknown }).status;
    if (typeof s === "number" && Number.isFinite(s)) return s;
  }
  return undefined;
}

export async function runStyleChain(deps: {
  complete: CompletionFn;
  system: string;
  user: string;
  sourceText: string;
  now?: () => number;
}): Promise<{ text: string; model: string; provider?: string; attempts: AttemptRecord[] }> {
  const now = deps.now ?? Date.now;
  const attempts: AttemptRecord[] = [];
  const maxTokens = maxTokensForTranscript(deps.sourceText);
  let lastReason = "transport-error";

  for (const link of LINKS) {
    // Full attempt, then at most one degraded retry on clientError.
    for (let attemptIndex = 0; attemptIndex < 2; attemptIndex++) {
      const degraded = attemptIndex === 1;
      const started = now();
      // Degraded drops only the optional providerSort; order/only pins and
      // reasoning effort "none" stay on every attempt (mirrors the Kotlin
      // degraded shape, which drops stream_options and keeps reasoning).
      const request: CompletionRequest = {
        model: link.model,
        messages: [
          { role: "system", content: deps.system },
          { role: "user", content: deps.user },
        ],
        providerOrder: link.providerOrder ? [...link.providerOrder] : undefined,
        providerOnly: link.providerOnly ? [...link.providerOnly] : undefined,
        providerSort: degraded ? undefined : link.providerSort,
        reasoningEffort: REASONING_EFFORT,
        degraded,
        maxTokens,
        temperature: TEMPERATURE,
      };
      let result: CompletionResult;
      try {
        result = await deps.complete(request);
      } catch (err) {
        const status = errorStatus(err);
        const outcome = status !== undefined ? `http-${status}` : "transport-error";
        const latencyMs = now() - started;
        attempts.push({ model: link.model, degraded, outcome, latencyMs, estimatedCostUsd: 0 });
        lastReason = outcome;
        const isClientError = status !== undefined && status >= 400 && status < 500;
        if (!degraded && isClientError) {
          continue; // one degraded retry on same model
        }
        break; // advance chain
      }
      const latencyMs = now() - started;
      const usage = result.usage;
      const baseRecord: AttemptRecord = {
        model: link.model,
        degraded,
        outcome: "",
        latencyMs,
        promptTokens: usage?.promptTokens,
        completionTokens: usage?.completionTokens,
        reasoningTokens: usage?.reasoningTokens,
        estimatedCostUsd: estimateCostUsd(link.model, usage),
      };
      const cleaned = cleanLlmResponse(result.content ?? "");
      if (cleaned.trim().length === 0) {
        baseRecord.outcome = "empty-response";
        attempts.push(baseRecord);
        lastReason = "empty-response";
        break;
      }
      const verdict = classifyStyleOutput(deps.sourceText, cleaned);
      if (verdict === "refusal") {
        baseRecord.outcome = "refusal";
        attempts.push(baseRecord);
        lastReason = "refusal";
        break;
      }
      if (verdict === "fidelity-loss") {
        baseRecord.outcome = "fidelity";
        attempts.push(baseRecord);
        lastReason = "fidelity";
        break;
      }
      baseRecord.outcome = "styled";
      attempts.push(baseRecord);
      return { text: cleaned, model: link.model, provider: result.provider, attempts };
    }
  }
  throw new StyleChainError(lastReason, attempts);
}

/**
 * Raw JSON-generation chain (custom-style generation). Unlike
 * runStyleChain this performs NO response cleaning and NO output-guard
 * classification: it returns the model's raw content for the caller to
 * parse and validate. Walks the primary link then the fallback link, one
 * attempt each (no degraded retry); transport/HTTP errors advance the
 * chain exactly like runStyleChain. Every attempt records usage plus
 * normalizedTokens for accounting. Blank content advances the chain with
 * an empty-response outcome. Throws StyleChainError when every link fails.
 */
export async function runGenerationCompletion(deps: {
  complete: CompletionFn;
  messages: ChatMessage[];
  models?: string[];
  responseFormat?: StructuredResponseFormat;
  requireParameters?: boolean;
  temperature: number;
  maxTokens: number;
  now?: () => number;
}): Promise<{
  content: string;
  model: string;
  provider?: string;
  attempts: AttemptRecord[];
}> {
  const now = deps.now ?? Date.now;
  const attempts: AttemptRecord[] = [];
  const models = deps.models ?? [PRIMARY_MODEL, FALLBACK_MODEL];
  let lastReason = "transport-error";

  for (const model of models) {
    const link = LINKS.find((l) => l.model === model);
    const started = now();
    const request: CompletionRequest = {
      model,
      messages: deps.messages.map((m) => ({ role: m.role, content: m.content })),
      providerOrder: link?.providerOrder ? [...link.providerOrder] : undefined,
      providerOnly: link?.providerOnly ? [...link.providerOnly] : undefined,
      providerSort: link?.providerSort,
      reasoningEffort: REASONING_EFFORT,
      degraded: false,
      maxTokens: deps.maxTokens,
      temperature: deps.temperature,
    };
    // Structured-output keys stay absent unless set, so plain styling
    // requests serialize byte-identically to before.
    if (deps.responseFormat !== undefined) request.responseFormat = deps.responseFormat;
    if (deps.requireParameters !== undefined) request.requireParameters = deps.requireParameters;
    let result: CompletionResult;
    try {
      result = await deps.complete(request);
    } catch (err) {
      const status = errorStatus(err);
      const outcome = status !== undefined ? `http-${status}` : "transport-error";
      attempts.push({
        model,
        degraded: false,
        outcome,
        latencyMs: now() - started,
        estimatedCostUsd: 0,
        normalizedTokens: 0,
      });
      lastReason = outcome;
      continue;
    }
    const latencyMs = now() - started;
    const usage = result.usage;
    const record: AttemptRecord = {
      model,
      degraded: false,
      outcome: "",
      latencyMs,
      promptTokens: usage?.promptTokens,
      completionTokens: usage?.completionTokens,
      reasoningTokens: usage?.reasoningTokens,
      estimatedCostUsd: estimateCostUsd(model, usage),
      normalizedTokens: normalizedTokens(model, usage),
    };
    if ((result.content ?? "").trim().length === 0) {
      record.outcome = "empty-response";
      attempts.push(record);
      lastReason = "empty-response";
      continue;
    }
    record.outcome = "ok";
    attempts.push(record);
    return { content: result.content, model, provider: result.provider, attempts };
  }
  throw new StyleChainError(lastReason, attempts);
}

export function buildOpenRouterCompletionFn(apiKey: string, sessionId: string): CompletionFn {
  const client = createOpenRouterClient(apiKey, sessionId);
  return async (req: CompletionRequest): Promise<CompletionResult> => {
    // Deviation: buildOpenRouterProviderRouting expresses only a single
    // preferredProvider order entry and no `only` allow-list, so the chain's
    // order-array + only + sort routing is built directly here, keeping the
    // helper's defaults in spirit (data_collection deny, zdr true).
    const provider: Record<string, unknown> = {
      allow_fallbacks: true,
      data_collection: "deny",
      zdr: true,
    };
    if (req.providerOrder) provider.order = req.providerOrder;
    if (req.providerOnly) provider.only = req.providerOnly;
    if (req.providerSort) provider.sort = req.providerSort;
    // Structured-output routing only when the caller set it: plain
    // styling requests must serialize exactly as before.
    if (req.requireParameters !== undefined) provider.require_parameters = req.requireParameters;
    const extras = buildOpenRouterChatCompletionExtras(sessionId);
    const body: Record<string, unknown> = {
      model: req.model,
      messages: req.messages,
      temperature: req.temperature,
      max_tokens: req.maxTokens,
      reasoning: { effort: req.reasoningEffort },
      provider,
      session_id: extras.session_id,
    };
    if (req.responseFormat !== undefined) {
      body.response_format =
        req.responseFormat.type === "json_schema"
          ? {
              type: "json_schema",
              json_schema: {
                name: req.responseFormat.name,
                strict: req.responseFormat.strict,
                schema: req.responseFormat.schema,
              },
            }
          : { type: "json_object" };
    }
    const response = (await (client.chat.completions.create as (...args: unknown[]) => Promise<unknown>)(
      body,
      { timeout: 20000, maxRetries: 0 },
    )) as {
      choices?: Array<{ message?: { content?: string | null } }>;
      provider?: string;
      usage?: {
        prompt_tokens?: number;
        completion_tokens?: number;
        completion_tokens_details?: { reasoning_tokens?: number };
      };
    };
    return {
      content: response.choices?.[0]?.message?.content ?? "",
      provider: response.provider,
      usage: response.usage
        ? {
            promptTokens: response.usage.prompt_tokens,
            completionTokens: response.usage.completion_tokens,
            reasoningTokens: response.usage.completion_tokens_details?.reasoning_tokens,
          }
        : undefined,
    };
  };
}
