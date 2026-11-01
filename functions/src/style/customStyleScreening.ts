/**
 * Custom-style save-time screening (BLO-149).
 *
 * Deterministic lexicon + rules over a custom style's display fields
 * (displayName, description, systemInstructions). The restricted set is
 * deliberately narrow, per Sean's ruling on BLO-149 (2026-10-23): as
 * permissive as possible within what Google Play enforces. ONLY:
 *
 *  1. sexual-minors — sexual content involving minors, in any form.
 *  2. sexual-real-person — non-consensual sexual content, or sexual
 *     content about real, identifiable people.
 *  3. sexual-content — explicit sexual generation as the style's purpose
 *     (Play has no adults-only tier).
 *  4. hate-protected-class — a persona whose purpose is hatred or
 *     dehumanization of a protected class.
 *
 * Everything else passes at any intensity: profanity, crude and dark
 * humor, roasting, edgy personas, a user's own profane dictation. This
 * screen is a first-pass net, NOT proof that all harmful content is
 * prevented: it catches plain and simply-obfuscated statements of the
 * restricted purposes. The runtime guard (styleOutputGuard) and the
 * non-overridable system-prompt clause are the other two layers.
 *
 * Screening reads text only to classify it. Callers must log the
 * outcome CODE only — never the screened text.
 */

export type ScreeningCode =
  | "sexual-minors"
  | "sexual-real-person"
  | "sexual-content"
  | "hate-protected-class";

export type ScreeningHit = {
  code: ScreeningCode;
  field: "displayName" | "description" | "systemInstructions";
  /** Plain user-facing rejection sentence (app chrome copy). */
  message: string;
};

export const SCREENING_MESSAGES: Record<ScreeningCode, string> = {
  "sexual-minors":
    "Styles cannot involve sexual content with minors. This style was not saved.",
  "sexual-real-person":
    "Styles cannot produce sexual content about real people or sexual content without consent. This style was not saved.",
  "sexual-content":
    "Styles cannot be made to produce explicit sexual content. This style was not saved.",
  "hate-protected-class":
    "Styles cannot target a protected group with hatred or slurs. This style was not saved.",
};

const LEET_MAP: Record<string, string> = {
  "0": "o",
  "1": "i",
  "3": "e",
  "4": "a",
  "5": "s",
  "7": "t",
  "@": "a",
  $: "s",
};

/**
 * Normalized views of a text for matching:
 * - `tokens`: lowercase, diacritic-stripped, leet-decoded word tokens.
 * - `compact`: tokens joined with no separator and repeated letters
 *   collapsed, so spaced/dotted/stretched spellings ("e r o t i c a",
 *   "errrotica") still match multi-word phrases as substrings.
 */
export type NormalizedText = {
  tokens: string[];
  tokenSet: ReadonlySet<string>;
  compact: string;
};

export function normalizeForScreening(text: string): NormalizedText {
  const lowered = text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "");
  const decoded = [...lowered]
    .map((ch) => LEET_MAP[ch] ?? ch)
    .join("");
  const tokens = decoded.match(/[a-z]+/g) ?? [];
  const compact = tokens.join("").replace(/(.)\1{2,}/g, "$1$1");
  return { tokens, tokenSet: new Set(tokens), compact };
}

function hasAny(norm: NormalizedText, terms: readonly string[]): boolean {
  return terms.some((term) => norm.tokenSet.has(term));
}

/** Phrase match: the phrase's tokens appear consecutively, or fused in compact form. */
function hasPhrase(norm: NormalizedText, phrase: string): boolean {
  const parts = phrase.split(" ");
  if (parts.length === 1) return norm.tokenSet.has(parts[0]);
  for (let i = 0; i + parts.length <= norm.tokens.length; i++) {
    if (parts.every((part, j) => norm.tokens[i + j] === part)) return true;
  }
  return norm.compact.includes(parts.join(""));
}

function hasAnyPhrase(norm: NormalizedText, phrases: readonly string[]): boolean {
  return phrases.some((phrase) => hasPhrase(norm, phrase));
}

export const MINOR_TERMS: readonly string[] = [
  "child", "children", "kid", "kids", "minor", "minors", "underage",
  "teen", "teens", "teenager", "teenagers", "schoolgirl", "schoolboy",
  "preteen", "preteens", "toddler", "infant", "baby",
];

/** Sexual vocabulary for co-occurrence checks (kept broad; never sufficient alone). */
export const SEXUAL_TERMS: readonly string[] = [
  "sex", "sexual", "erotic", "erotica", "porn", "pornographic", "nude",
  "nudes", "naked", "aroused", "arousal", "orgasm", "intercourse",
  "masturbate", "masturbating", "masturbation", "fetish", "lewd",
  "seductive", "seduce", "molest", "pedophile", "pedophilia",
];

/** Standalone sexualized-minor slang/compounds: a hit needs nothing else. */
const MINOR_SEXUAL_STANDALONE: readonly string[] = [
  "loli", "lolicon", "shota", "shotacon", "jailbait", "csam",
  "childporn", "kiddieporn",
];

const NONCONSENT_MARKERS: readonly string[] = [
  "nonconsensual", "non consensual", "without consent", "without her consent",
  "without his consent", "without their consent", "revenge porn",
  "deepfake", "deepfakes", "undress", "undressed", "molest",
];

const REAL_PERSON_MARKERS: readonly string[] = [
  "celebrity", "celebrities", "famous person", "famous people",
  "real person", "real people", "real woman", "real man", "real girl",
  "real boy", "my ex", "my boss", "my teacher", "my coworker",
  "my neighbour", "my neighbor", "my classmate", "my sister", "my brother",
  "taylor swift", "celebrity crush",
];

const PERSON_TARGET_TERMS: readonly string[] = [
  "her", "him", "them", "person", "people", "woman", "man", "girl", "boy",
  "photo", "photos", "image", "images", "picture", "pictures", "video",
];

/** Explicit-sexual purpose markers: the style exists to generate this. */
const EXPLICIT_PURPOSE_TERMS: readonly string[] = [
  "erotica", "porn", "pornographic", "pornography", "smut", "hentai",
  "sexting", "nsfw", "lewd",
];

const EXPLICIT_PURPOSE_PHRASES: readonly string[] = [
  "sexual roleplay", "sex roleplay", "erotic roleplay", "erotic stories",
  "erotic story", "sex stories", "sex story", "sexual stories",
  "dirty talk", "phone sex", "cyber sex", "explicit sexual",
  "sexual content", "sexual fantasies", "sexual fantasy",
  "erotic fiction", "erotic poetry", "erotic novel", "erotic tales",
  "erotic chat", "sexual fiction",
];

/**
 * Negation words: a clause built around one of these states a refusal
 * or a prohibition ("never produce sexual content"), not a purpose.
 * Screening drops negated clauses before matching, so safety language
 * in a style's own instructions is not read as the thing it forbids.
 */
const NEGATION_TERMS: ReadonlySet<string> = new Set([
  "never", "no", "not", "dont", "cannot", "refuse", "refuses",
  "avoid", "avoids", "prohibit", "prohibits", "forbidden", "ban",
  "banned",
  // Deliberately NOT "without": "sexual content without consent" is a
  // restricted purpose (rule 2), and its clause must survive stripping.
]);

/** The text with negated clauses removed (clauses split on sentence/comma boundaries). */
export function stripNegatedClauses(text: string): string {
  return text
    .split(/[.!?;,\n]+/)
    .filter((clause) => {
      const tokens = normalizeForScreening(clause).tokens;
      return !tokens.some((token) => NEGATION_TERMS.has(token));
    })
    .join(" ");
}

export const SLUR_TERMS: readonly string[] = [
  "nigger", "nigga", "faggot", "fag", "kike", "spic", "chink", "gook",
  "tranny", "wetback", "beaner", "raghead", "sandnigger", "coon",
  "jigaboo", "dyke",
];

export const PROTECTED_TERMS: readonly string[] = [
  "black", "white", "asian", "latino", "latina", "hispanic", "african",
  "jewish", "jews", "muslim", "muslims", "christian", "christians",
  "gay", "lesbian", "transgender", "trans", "women", "men", "disabled",
  "race", "ethnic", "ethnicity", "religion", "religious",
];

/** Strong dehumanization: with a protected term in the same clause, always a hit. */
const STRONG_DEHUMANIZE_MARKERS: readonly string[] = [
  "subhuman", "inferior", "vermin", "exterminate", "eradicate",
  "dehumanize", "dehumanise", "dehumanizing", "master race", "pure race",
  "racial purity", "ethnic cleansing", "gas the", "kill all", "hang all",
  "lynch",
];

/** Weak hostility: a hit only with a protected term AND a people/purpose frame in the same clause. */
const WEAK_HOSTILITY_MARKERS: readonly string[] = [
  "hate", "hates", "hatred", "despise", "despises",
];

const PEOPLE_TERMS: readonly string[] = [
  "people", "person", "persons", "group", "groups", "race", "folk", "folks",
];

/** Color words count as protected terms only beside a people word (a style about black coffee is not a hate persona). */
const COLOR_GROUP_TERMS: readonly string[] = ["black", "white"];

const TARGETING_MARKERS: readonly string[] = [
  "against", "toward", "towards", "about", "targeting", "targets",
  "call", "calls", "calling", "insult", "insults", "attack", "attacks",
  "mock", "mocks", "degrade", "degrades", "slur", "slurs",
];

/** Compact-view stems: catch spaced/dotted spellings ("e r o t i c") the token view splits apart. */
const SEXUAL_COMPACT_STEMS: readonly string[] = [
  "sexual", "erotic", "porn", "orgasm", "lewd",
];

function hasSexualSignal(norm: NormalizedText): boolean {
  return (
    hasAny(norm, SEXUAL_TERMS) ||
    SEXUAL_COMPACT_STEMS.some((stem) => norm.compact.includes(stem))
  );
}

function screenNormalized(norm: NormalizedText): ScreeningCode | null {
  const sexual = hasSexualSignal(norm);

  // 1. Sexual content involving minors — zero tolerance, checked first.
  const MINOR_COMPACT_STEMS = ["child", "underage", "schoolgirl", "schoolboy", "preteen"];
  const minor =
    hasAny(norm, MINOR_TERMS) ||
    MINOR_COMPACT_STEMS.some((stem) => norm.compact.includes(stem));
  if (hasAny(norm, MINOR_SEXUAL_STANDALONE)) return "sexual-minors";
  if (sexual && minor) return "sexual-minors";
  // "underage" is itself the marker: it needs no sexual co-term when a
  // generation/persona frame surrounds it, and the sexual list already
  // covers the plain cases; keep the co-occurrence rule only.

  // 2. Non-consensual sexual content / sexual content about real people.
  const nonconsent = hasAnyPhrase(norm, NONCONSENT_MARKERS);
  if (nonconsent && (sexual || hasAny(norm, PERSON_TARGET_TERMS))) {
    return "sexual-real-person";
  }
  if (sexual && hasAnyPhrase(norm, REAL_PERSON_MARKERS)) {
    return "sexual-real-person";
  }

  // 3. Explicit sexual generation as the style's purpose.
  if (hasAny(norm, EXPLICIT_PURPOSE_TERMS)) return "sexual-content";
  if (hasAnyPhrase(norm, EXPLICIT_PURPOSE_PHRASES)) return "sexual-content";

  return null;
}

/**
 * Rule 4 runs per clause, not per document: hostility words are common
 * in benign text ("I hate black coffee"), so a hit needs the group, the
 * hostility, and the targeting frame inside one clause.
 */
function screenClauseForHate(clause: string): boolean {
  const norm = normalizeForScreening(clause);
  const peopleHit = hasAny(norm, PEOPLE_TERMS);
  const protectedHit =
    hasAny(norm, PROTECTED_TERMS) ||
    (peopleHit && hasAny(norm, COLOR_GROUP_TERMS));
  const strong = hasAnyPhrase(norm, STRONG_DEHUMANIZE_MARKERS);
  if (protectedHit && strong) return true;
  if (
    hasAny(norm, SLUR_TERMS) &&
    (protectedHit || strong || hasAny(norm, TARGETING_MARKERS) ||
      hasAny(norm, WEAK_HOSTILITY_MARKERS))
  ) {
    return true;
  }
  if (
    protectedHit &&
    hasAny(norm, WEAK_HOSTILITY_MARKERS) &&
    (peopleHit || hasAny(norm, ["purpose", "against", "inferior", "superior"]))
  ) {
    return true;
  }
  return false;
}

/** Screen one free-text field; returns the restricted code or null. */
export function screenStyleText(text: string): ScreeningCode | null {
  const stripped = stripNegatedClauses(text);
  const code = screenNormalized(normalizeForScreening(stripped));
  if (code) return code;
  const clauses = stripped.split(/[.!?;,\n]+/);
  if (clauses.some((clause) => screenClauseForHate(clause))) {
    return "hate-protected-class";
  }
  return null;
}

/**
 * Screen a custom style's display fields. Fields are screened
 * individually AND joined: individually so a hit names its field,
 * joined so a purpose split across name + instructions still reads as
 * one statement. The joined pass wins field attribution to
 * systemInstructions when no single field hits.
 */
export function screenCustomStyleFields(fields: {
  displayName: string;
  description: string;
  systemInstructions: string;
}): ScreeningHit | null {
  const ordered: Array<[ScreeningHit["field"], string]> = [
    ["displayName", fields.displayName],
    ["description", fields.description],
    ["systemInstructions", fields.systemInstructions],
  ];
  for (const [field, text] of ordered) {
    const code = screenStyleText(text);
    if (code) return { code, field, message: SCREENING_MESSAGES[code] };
  }
  const joined = ordered.map(([, text]) => text).join(" ");
  const code = screenStyleText(joined);
  if (code) {
    return { code, field: "systemInstructions", message: SCREENING_MESSAGES[code] };
  }
  return null;
}
