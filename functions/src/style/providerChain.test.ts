import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PRIMARY_MODEL,
  FALLBACK_MODEL,
  estimateCostUsd,
  maxTokensForTranscript,
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
