import {
  MINOR_TERMS,
  SEXUAL_TERMS,
  SLUR_TERMS,
  normalizeForScreening,
} from "./customStyleScreening";

export type StyleGuardVerdict = "pass" | "refusal" | "fidelity-loss" | "restricted";

const OPENING_CHARS = 200;
const MIN_COVERAGE = 0.1;
const MIN_CONTENT_WORDS_FOR_FLOOR = 10;
const WORD_REGEX = /[A-Za-z0-9']+/g;

const CANT_HELP_MARKERS: readonly string[] = [
  "can't help with",
  "cannot help with",
  "can't help you",
  "cannot help you",
  "can't assist with",
  "cannot assist with",
  "can't assist you",
  "cannot assist you",
  "unable to help with",
];

const REFUSAL_VERBS =
  "comply|complete|fulfil|fulfill|process|rewrite|rephrase|restyle|paraphrase|generate";

const REFUSAL_OPENERS: readonly RegExp[] = [
  new RegExp(`^\\W{0,3}i (can't|cannot|can not|won't|will not) (${REFUSAL_VERBS})\\b`),
  /^\W{0,3}i (can't|cannot|can not) do (that|this)\b/,
  /^\W{0,3}i('m| am) (unable|not able) to (help|assist|comply|rewrite|generate|continue)\b/,
  /^\W{0,3}i (must|have to|need to) (decline|refuse)\b/,
  /^\W{0,3}i (refuse|decline) to\b/,
  /^\W{0,3}as an ai\b/,
  /\bagainst my (guidelines|policy|policies|programming)\b/,
  /^\W{0,3}i don't have the ability\b/,
];

const APOLOGY_OPENER = /^\W{0,3}(i'm sorry|i am sorry|i apologize|i apologise|sorry[,.!])/;

const POLICY_MARKERS: readonly string[] = [
  "content policy",
  "usage policy",
  "my guidelines",
  "my policy",
  "inappropriate content",
  "violates my",
];

const STOPWORDS: ReadonlySet<string> = new Set([
  "the", "a", "an", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on",
  "for", "with", "as", "at", "by", "from", "into", "over", "under", "about", "after",
  "before", "between", "through", "during", "out", "up", "down", "off", "again", "once",
  "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did", "have",
  "has", "had", "having", "will", "would", "shall", "should", "can", "could", "may",
  "might", "must", "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us",
  "them", "my", "your", "his", "its", "our", "their", "mine", "yours", "ours", "theirs",
  "this", "that", "these", "those", "there", "here", "what", "when", "where", "who",
  "whom", "how", "why", "which", "all", "any", "some", "no", "not", "only", "very",
  "too", "also", "just", "so", "than", "because", "until", "while", "um", "uh", "er",
]);

function wordSet(text: string): Set<string> {
  const matches = text.toLowerCase().match(WORD_REGEX);
  return new Set(matches ?? []);
}

function wordCount(text: string): number {
  const matches = text.match(WORD_REGEX);
  return matches ? matches.length : 0;
}

function coverage(source: string, styled: string): number {
  const sourceContent = new Set([...wordSet(source)].filter((w) => !STOPWORDS.has(w)));
  if (sourceContent.size === 0) return 1.0;
  const styledWords = wordSet(styled);
  let surviving = 0;
  for (const word of sourceContent) {
    if (styledWords.has(word)) surviving++;
  }
  return surviving / sourceContent.size;
}

function isWholesaleReplacement(source: string, styled: string): boolean {
  const sourceContent = new Set([...wordSet(source)].filter((w) => !STOPWORDS.has(w)));
  if (sourceContent.size < MIN_CONTENT_WORDS_FOR_FLOOR) return false;
  return coverage(source, styled) < MIN_COVERAGE;
}

function isRefusal(opening: string, source: string, styled: string): boolean {
  if (CANT_HELP_MARKERS.some((marker) => opening.includes(marker))) return true;
  if (REFUSAL_OPENERS.some((pattern) => pattern.test(opening))) return true;
  const apologetic = APOLOGY_OPENER.test(opening);
  if (apologetic && POLICY_MARKERS.some((marker) => opening.includes(marker))) return true;
  if (apologetic && wordCount(styled) * 2 < wordCount(source)) return true;
  return (
    POLICY_MARKERS.some((marker) => opening.includes(marker)) &&
    coverage(source, styled) < MIN_COVERAGE
  );
}

/**
 * Explicit-sexual output terms for the custom-style runtime check
 * (BLO-149). Kept to unmistakable generation vocabulary: a hit means the
 * model produced explicit content the speaker did not dictate.
 */
const EXPLICIT_OUTPUT_TERMS: readonly string[] = [
  "erotica", "pornographic", "orgasm", "intercourse", "masturbation",
  "masturbating", "penetration",
];

const EXPLICIT_OUTPUT_STEMS: readonly string[] = ["ejaculat", "masturbat"];

function anyTerm(norm: ReturnType<typeof normalizeForScreening>, terms: readonly string[]): boolean {
  return terms.some((term) => norm.tokenSet.has(term));
}

function hasNewExplicitTerm(source: string, styled: string): boolean {
  const sourceNorm = normalizeForScreening(source);
  const styledNorm = normalizeForScreening(styled);
  for (const term of EXPLICIT_OUTPUT_TERMS) {
    if (styledNorm.tokenSet.has(term) && !sourceNorm.tokenSet.has(term)) return true;
  }
  for (const stem of EXPLICIT_OUTPUT_STEMS) {
    if (styledNorm.compact.includes(stem) && !sourceNorm.compact.includes(stem)) return true;
  }
  return false;
}

function hasNewSlur(source: string, styled: string): boolean {
  // Every slur on the list is intrinsically aimed at a protected class,
  // so a slur the model ADDED — one the speaker never dictated — is the
  // attack. A slur the speaker dictated restyles as written.
  const sourceNorm = normalizeForScreening(source);
  const styledNorm = normalizeForScreening(styled);
  return SLUR_TERMS.some(
    (slur) => styledNorm.tokenSet.has(slur) && !sourceNorm.tokenSet.has(slur),
  );
}

/**
 * BLO-149 restricted-content check on generated output.
 *
 * - Every style: output joining a minor term with a sexual term is
 *   restricted, whatever the source said — restyling it still produces it.
 * - Custom styles only: explicit sexual vocabulary or a slur aimed at a
 *   protected class that the speaker did NOT dictate is restricted. The
 *   source-relative shape is deliberate (Sean's BLO-149 ruling): a user's
 *   own profane or explicit dictation restyles as written; the guard
 *   stops the style/model from ADDING restricted content.
 */
export function classifyRestrictedOutput(
  source: string,
  styled: string,
  isCustomStyle: boolean,
): boolean {
  const styledNorm = normalizeForScreening(styled);
  if (anyTerm(styledNorm, MINOR_TERMS) && anyTerm(styledNorm, SEXUAL_TERMS)) {
    return true;
  }
  if (!isCustomStyle) return false;
  if (hasNewExplicitTerm(source, styled)) return true;
  if (hasNewSlur(source, styled)) return true;
  return false;
}

export function classifyStyleOutput(
  source: string,
  styled: string,
  opts?: { isCustomStyle?: boolean },
): StyleGuardVerdict {
  if (styled.trim().length === 0) return "pass";
  const opening = styled.trim().toLowerCase().slice(0, OPENING_CHARS);
  if (isRefusal(opening, source, styled)) return "refusal";
  if (classifyRestrictedOutput(source, styled, opts?.isCustomStyle ?? false)) {
    return "restricted";
  }
  if (isWholesaleReplacement(source, styled)) return "fidelity-loss";
  return "pass";
}
