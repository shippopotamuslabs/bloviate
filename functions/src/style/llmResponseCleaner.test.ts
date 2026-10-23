import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanLlmResponse } from "./llmResponseCleaner";

test("strips markdown fences", () => {
  assert.equal(cleanLlmResponse("```\nhello world\n```"), "hello world");
  assert.equal(cleanLlmResponse("```text\nhello world\n```"), "hello world");
});

test("strips short preamble first line", () => {
  assert.equal(
    cleanLlmResponse("Here is the rewritten text:\nhello world"),
    "hello world",
  );
  assert.equal(cleanLlmResponse("Sure, here you go:\nhello world"), "hello world");
});

test("blank returns empty string", () => {
  assert.equal(cleanLlmResponse(""), "");
  assert.equal(cleanLlmResponse("   "), "");
  assert.equal(cleanLlmResponse("```\n```"), "");
});

test("plain text is unchanged", () => {
  assert.equal(cleanLlmResponse("hello world"), "hello world");
});
