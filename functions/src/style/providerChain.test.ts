import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PRIMARY_MODEL,
  FALLBACK_MODEL,
  PRIMARY_PROVIDER_ORDER,
  estimateCostUsd,
  maxTokensForTranscript,
  normalizedTokens,
  runGenerationCompletion,
  runStyleChain,
  StyleChainError,
  type CompletionFn,
  type CompletionRequest,
} from "./providerChain";

function errWithStatus(status: number): Error {
  const e = new Error(`http ${status}`) as Error & { status: number };
  e.status = status;
  return e;
}

test("reasoning none on every attempt across full 4-attempt failure", async () => {
  const requests: CompletionRequest[] = [];
  const complete: CompletionFn = async (req) => {
    requests.push(req);
    throw errWithStatus(400);
  };
  let t = 0;
  const now = () => (t += 5);
  await assert.rejects(
    () =>
      runStyleChain({
        complete,
        system: "sys",
        user: "usr",
        sourceText: "hello world this is a test",
        now,
      }),
    (err: unknown) => {
      assert.ok(err instanceof StyleChainError);
      assert.equal(err.attempts.length, 4);
      assert.equal(err.reason, "http-400");
      return true;
    },
  );
  assert.equal(requests.length, 4);
  assert.deepEqual(
    requests.map((r) => r.model),
    [PRIMARY_MODEL, PRIMARY_MODEL, FALLBACK_MODEL, FALLBACK_MODEL],
  );
  assert.deepEqual(
    requests.map((r) => r.degraded),
    [false, true, false, true],
  );
  for (const r of requests) assert.equal(r.reasoningEffort, "none");
  // degraded fallback drops providerSort, full fallback carries it
  assert.equal(requests[2].providerSort, "latency");
  assert.equal(requests[3].providerSort, undefined);
});

test("cost estimate primary 1000/1000 approx 0.00075", () => {
  const cost = estimateCostUsd(PRIMARY_MODEL, { promptTokens: 1000, completionTokens: 1000 });
  assert.ok(Math.abs(cost - 0.00075) < 1e-9, `cost ${cost}`);
  assert.equal(estimateCostUsd(PRIMARY_MODEL, undefined), 0);
  assert.equal(estimateCostUsd(PRIMARY_MODEL), 0);
});

test("maxTokensForTranscript bounds", () => {
  assert.equal(maxTokensForTranscript("hi"), 1024);
  const mid = maxTokensForTranscript("x".repeat(12000));
  assert.ok(mid > 1024 && mid <= 8000, `mid ${mid}`);
  assert.equal(mid, 6000);
  assert.equal(maxTokensForTranscript("x".repeat(100000)), 8000);
});

test("styling requests carry no structured-output keys (byte-identical pin)", async () => {
  const requests: CompletionRequest[] = [];
  const complete: CompletionFn = async (req) => {
    requests.push(req);
    return { content: "Yeah so dinner tomorrow.", usage: { promptTokens: 10, completionTokens: 5 } };
  };
  let t = 0;
  await runStyleChain({
    complete,
    system: "sys",
    user: "usr",
    sourceText: "yeah so dinner tomorrow",
    now: () => (t += 5),
  });
  assert.equal(requests.length, 1);
  assert.equal("responseFormat" in requests[0], false);
  assert.equal("requireParameters" in requests[0], false);
});

test("normalizedTokens rebases to primary input price; reasoning counts as output", () => {
  // Primary: input 0.15 (-> x1), output 0.60 (-> x4).
  assert.equal(normalizedTokens(PRIMARY_MODEL, { promptTokens: 1000, completionTokens: 1000 }), 5000);
  assert.equal(
    normalizedTokens(PRIMARY_MODEL, { promptTokens: 100, completionTokens: 50, reasoningTokens: 0 }),
    300,
  );
  // Reasoning tokens bill at the output rate.
  assert.equal(normalizedTokens(PRIMARY_MODEL, { promptTokens: 0, completionTokens: 0, reasoningTokens: 100 }), 400);
  // Fallback: input 0.25 (-> x5/3), output 1.50 (-> x10).
  assert.equal(
    normalizedTokens(FALLBACK_MODEL, { promptTokens: 300, completionTokens: 100 }),
    Math.round((300 * 0.25) / 0.15 + (100 * 1.5) / 0.15),
  );
  assert.equal(normalizedTokens(PRIMARY_MODEL, undefined), 0);
  assert.equal(normalizedTokens("unknown-model", { promptTokens: 10, completionTokens: 10 }), 0);
});

function generationComplete(
  outcomes: (
    | { kind: "return"; content: string; usage?: { promptTokens: number; completionTokens: number; reasoningTokens?: number } }
    | { kind: "throw"; status?: number }
  )[],
) {
  const requests: CompletionRequest[] = [];
  const queue = [...outcomes];
  const complete: CompletionFn = async (req) => {
    requests.push(req);
    const o = queue.shift();
    if (!o) throw new Error("fake queue exhausted");
    if (o.kind === "throw") {
      const e = new Error(`http ${o.status}`) as Error & { status?: number };
      if (o.status !== undefined) e.status = o.status;
      throw e;
    }
    return { content: o.content, provider: "fake", usage: o.usage ?? { promptTokens: 10, completionTokens: 5 } };
  };
  return { requests, complete };
}

test("runGenerationCompletion returns raw content with per-model routing", async () => {
  const { requests, complete } = generationComplete([{ kind: "return", content: '  {"a": 1}  ' }]);
  let t = 0;
  const result = await runGenerationCompletion({
    complete,
    messages: [{ role: "system", content: "sys" }],
    temperature: 0.7,
    maxTokens: 3500,
    responseFormat: { type: "json_schema", name: "n", schema: { type: "object" }, strict: true },
    requireParameters: true,
    now: () => (t += 5),
  });
  // Raw: no cleaning, no guard — whitespace preserved byte-identical.
  assert.equal(result.content, '  {"a": 1}  ');
  assert.equal(result.model, PRIMARY_MODEL);
  assert.equal(result.attempts.length, 1);
  assert.equal(result.attempts[0].outcome, "ok");
  assert.equal(result.attempts[0].normalizedTokens, normalizedTokens(PRIMARY_MODEL, { promptTokens: 10, completionTokens: 5 }));
  assert.equal(requests.length, 1);
  assert.equal(requests[0].model, PRIMARY_MODEL);
  assert.equal(requests[0].temperature, 0.7);
  assert.equal(requests[0].maxTokens, 3500);
  assert.equal(requests[0].reasoningEffort, "none");
  assert.equal(requests[0].degraded, false);
  assert.deepEqual(requests[0].providerOrder, [...PRIMARY_PROVIDER_ORDER]);
  assert.deepEqual(requests[0].responseFormat, {
    type: "json_schema",
    name: "n",
    schema: { type: "object" },
    strict: true,
  });
  assert.equal(requests[0].requireParameters, true);
});

test("runGenerationCompletion advances on transport errors and blank content", async () => {
  const { requests, complete } = generationComplete([
    { kind: "throw", status: 500 },
    { kind: "return", content: "   " },
    { kind: "return", content: '{"ok": true}' },
  ]);
  let t = 0;
  const result = await runGenerationCompletion({
    complete,
    messages: [{ role: "user", content: "hi" }],
    models: [PRIMARY_MODEL, PRIMARY_MODEL, FALLBACK_MODEL],
    temperature: 0,
    maxTokens: 100,
    now: () => (t += 5),
  });
  assert.equal(result.content, '{"ok": true}');
  assert.equal(result.model, FALLBACK_MODEL);
  assert.deepEqual(result.attempts.map((a) => a.outcome), ["http-500", "empty-response", "ok"]);
  assert.deepEqual(result.attempts.map((a) => a.normalizedTokens), [0, result.attempts[1].normalizedTokens, result.attempts[2].normalizedTokens]);
  assert.deepEqual(requests.map((r) => r.model), [PRIMARY_MODEL, PRIMARY_MODEL, FALLBACK_MODEL]);
});

test("runGenerationCompletion throws StyleChainError with all attempts when every link fails", async () => {
  const { complete } = generationComplete([{ kind: "throw", status: 400 }, { kind: "throw" }]);
  let t = 0;
  await assert.rejects(
    () =>
      runGenerationCompletion({
        complete,
        messages: [{ role: "user", content: "hi" }],
        temperature: 0,
        maxTokens: 100,
        now: () => (t += 5),
      }),
    (err: unknown) => {
      assert.ok(err instanceof StyleChainError);
      assert.equal(err.attempts.length, 2);
      assert.deepEqual(err.attempts.map((a) => a.outcome), ["http-400", "transport-error"]);
      assert.equal(err.reason, "transport-error");
      return true;
    },
  );
});
