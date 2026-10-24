import { test } from "node:test";
import assert from "node:assert/strict";
import { STYLE_CATALOG, LLM_STYLE_IDS, styleById, type StyleDefinition } from "./styleCatalog";
import { buildStylePrompt } from "./stylePromptBuilder";

const EXPECTED_IDS = [
  "polish",
  "professional",
  "essayist",
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
  "ultra-hr",
  "silicon-valley-bro",
  "legal-contract",
  "academic-jargon",
  "gen-z-brainrot",
  "belligerent",
  "edgelord",
  "genius",
  "aristotle",
  "seneca",
  "marcus-aurelius",
  "socrates",
  "plato",
  "aquinas",
  "augustine",
  "oscar-wilde",
  "talkie",
  "the-colonel",
];

test("catalog has exactly 31 styles in expected order", () => {
  assert.equal(STYLE_CATALOG.length, 31);
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
    assert.ok(prompt.system.includes("INTENT, NOT WORDING"), `${style.id}: rewrite license`);
    assert.ok(prompt.system.includes("toolkit, not a template"), `${style.id}: license toolkit clause`);
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

test("BLO-158 tier 1: every style's prompt carries the facts clause", () => {
  for (const style of STYLE_CATALOG) {
    const prompt = buildStylePrompt(style, "my bicycle has a flat tire again");
    assert.ok(prompt.system.includes("FACTS ARE NOT THE JOKE"), `${style.id}: tier-1 clause`);
    assert.ok(
      prompt.system.includes("A changed fact is never the joke"),
      `${style.id}: changed-fact line`,
    );
  }
});

test("BLO-158 tier 2: serious styles ban invention, others take the license", () => {
  for (const id of ["polish", "professional", "essayist"]) {
    const style = styleById(id)!;
    const prompt = buildStylePrompt(style, "my bicycle has a flat tire again");
    assert.ok(prompt.system.includes("SERIOUS MEANS SERIOUS"), `${id}: tier-2 ban`);
    assert.ok(
      prompt.system.includes("The transcript is the whole universe"),
      `${id}: whole-universe line`,
    );
    assert.ok(!prompt.system.includes("LICENSED INVENTION"), `${id}: no license branch`);
  }
  // One specimen per non-serious category.
  for (const id of ["seneca", "dickens", "talkie", "attenborough", "corporate-linkedin"]) {
    const style = styleById(id)!;
    const prompt = buildStylePrompt(style, "my bicycle has a flat tire again");
    assert.ok(prompt.system.includes("LICENSED INVENTION"), `${id}: license clause`);
    assert.ok(
      prompt.system.includes("(a) nothing added contradicts a stated fact"),
      `${id}: limit (a)`,
    );
    assert.ok(
      prompt.system.includes("(b) nothing added impersonates a stated fact"),
      `${id}: limit (b)`,
    );
    assert.ok(!prompt.system.includes("SERIOUS MEANS SERIOUS"), `${id}: no tier-2 ban`);
  }
});

test("BLO-158: a category-custom style takes the license branch, never tier 2", () => {
  const custom: StyleDefinition = {
    id: "custom-noir",
    category: "custom",
    displayName: "My Noir",
    description: "My own hard-boiled voice.",
    sample: "The coffee was cold.",
    systemInstructions: "Write hard-boiled.",
    fewShots: [
      { raw: "i saw maria at the store", styled: "I saw Maria at the store." },
      { raw: "the bus was late again", styled: "The bus was late again." },
    ],
  };
  const prompt = buildStylePrompt(custom, "my bicycle has a flat tire again");
  assert.ok(prompt.system.includes("FACTS ARE NOT THE JOKE"), "custom: tier-1 clause");
  assert.ok(prompt.system.includes("LICENSED INVENTION"), "custom: license clause");
  assert.ok(!prompt.system.includes("SERIOUS MEANS SERIOUS"), "custom: no tier-2 ban");
});

test("BLO-158: fact clauses ride ahead of the only-text rule", () => {
  const serious = buildStylePrompt(styleById("professional")!, "hello there");
  const licensed = buildStylePrompt(styleById("dickens")!, "hello there");
  for (const [name, prompt] of [["serious", serious], ["licensed", licensed]] as const) {
    const onlyTextAt = prompt.system.indexOf("Return ONLY the transformed text");
    assert.ok(onlyTextAt >= 0, `${name}: only-text rule present`);
    assert.ok(
      prompt.system.indexOf("FACTS ARE NOT THE JOKE") < onlyTextAt,
      `${name}: tier-1 precedes only-text`,
    );
  }
  assert.ok(
    serious.system.indexOf("SERIOUS MEANS SERIOUS") <
      serious.system.indexOf("Return ONLY the transformed text"),
    "tier-2 precedes only-text",
  );
  assert.ok(
    licensed.system.indexOf("LICENSED INVENTION") <
      licensed.system.indexOf("Return ONLY the transformed text"),
    "license precedes only-text",
  );
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
