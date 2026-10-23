export type StyleGuardVerdict = "pass" | "refusal" | "fidelity-loss";

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

export function classifyStyleOutput(source: string, styled: string): StyleGuardVerdict {
  if (styled.trim().length === 0) return "pass";
  const opening = styled.trim().toLowerCase().slice(0, OPENING_CHARS);
  if (isRefusal(opening, source, styled)) return "refusal";
  if (isWholesaleReplacement(source, styled)) return "fidelity-loss";
  return "pass";
}
