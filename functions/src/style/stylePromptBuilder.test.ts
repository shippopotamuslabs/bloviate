import { test } from "node:test";
import assert from "node:assert/strict";
import { STYLE_CATALOG, LLM_STYLE_IDS, styleById } from "./styleCatalog";
import { buildStylePrompt } from "./stylePromptBuilder";

const EXPECTED_IDS = [
  "polish",
  "professional",
  "cicero",
  "shakespeare",
  "hemingway",
  "dickens",
  "noir-detective",
  "attenborough",
  "drill-sergeant",
  "town-crier",
  "pirate",
  "corporate-linkedin",
  "legal-contract",
  "academic-jargon",
  "gen-z-brainrot",
  "belligerent",
  "edgelord",
  "genius",
  "aristotle",
  "seneca",
  "marcus-aurelius",
  "oscar-wilde",
  "talkie",
  "the-colonel",
];

test("catalog has exactly 24 styles in expected order", () => {
  assert.equal(STYLE_CATALOG.length, 24);
  assert.deepEqual([...LLM_STYLE_IDS], EXPECTED_IDS);
  assert.deepEqual(
    STYLE_CATALOG.map((s) => s.id),
    EXPECTED_IDS,
  );
});

test("mechanical and unknown ids resolve to undefined", () => {
  assert.equal(styleById("lowercase-casual"), undefined);
  assert.equal(styleById("normal-caps-casual"), undefined);
  assert.equal(styleById("nonsense-id"), undefined);
});

test("every style builds a prompt carrying instructions, few-shots and invariants", () => {
  const transcript = "invented dictation about a Tuesday meeting";
  for (const style of STYLE_CATALOG) {
    const prompt = buildStylePrompt(style, transcript);
    assert.ok(prompt.system.includes(style.systemInstructions), `${style.id}: instructions`);
    for (const shot of style.fewShots) {
      assert.ok(prompt.system.includes(shot.raw), `${style.id}: raw few-shot`);
      assert.ok(prompt.system.includes(shot.styled), `${style.id}: styled few-shot`);
    }
    assert.ok(prompt.system.includes("NEVER address the speaker"), `${style.id}: invariant`);
    assert.ok(
      prompt.system.includes("Return ONLY the transformed text"),
      `${style.id}: only-text`,
    );
    assert.ok(
      prompt.user.startsWith(
        "Restyle the dictated text between the <transcript> markers, following the examples in the system message.",
      ),
      `${style.id}: lead-in`,
    );
    assert.ok(
      prompt.user.includes(`<transcript>\n${transcript}\n</transcript>`),
      `${style.id}: fenced transcript`,
    );
    assert.ok(
      prompt.user.endsWith(
        "Rewrite ONLY the text between the <transcript> markers, in the voice of the examples. Return only the rewritten text — nothing else.",
      ),
      `${style.id}: imperative`,
    );
    assert.ok(!prompt.user.includes("Example input:"), `${style.id}: no examples in user`);
  }
});

test("every style has non-blank instructions and at least 2 few-shots", () => {
  for (const style of STYLE_CATALOG) {
    assert.ok(style.systemInstructions.trim().length > 0, style.id);
    assert.ok(style.fewShots.length >= 2, style.id);
    for (const shot of style.fewShots) {
      assert.ok(shot.raw.trim().length > 0, style.id);
      assert.ok(shot.styled.trim().length > 0, style.id);
    }
  }
});
