const FENCE = "```";
const PREAMBLE_MAX_CHARS = 120;
const PREAMBLE_MARKERS: readonly string[] = [
  "here is",
  "here's",
  "sure,",
  "certainly",
  "rewritten",
  "transformed",
];

export function cleanLlmResponse(raw: string): string {
  let text = raw.trim();
  if (text.length === 0) return "";
  text = stripFences(text).trim();
  if (text.length === 0) return "";
  text = stripPreamble(text).trim();
  return text;
}

function stripFences(text: string): string {
  let stripped = text;
  if (stripped.startsWith(FENCE)) {
    const nl = stripped.indexOf("\n");
    stripped = nl >= 0 ? stripped.substring(nl + 1) : "";
  }
  const fenceIndex = stripped.lastIndexOf("\n" + FENCE);
  if (fenceIndex >= 0) {
    stripped = stripped.substring(0, fenceIndex);
  } else if (stripped.endsWith(FENCE)) {
    stripped = stripped.substring(0, stripped.length - FENCE.length);
  }
  return stripped;
}

function stripPreamble(text: string): string {
  const firstNewline = text.indexOf("\n");
  if (firstNewline < 0) return text;
  const firstLine = text.substring(0, firstNewline).trim();
  if (firstLine.length <= PREAMBLE_MAX_CHARS && looksLikePreamble(firstLine)) {
    return text.substring(firstNewline + 1);
  }
  return text;
}

function looksLikePreamble(firstLine: string): boolean {
  if (firstLine.endsWith(":")) return true;
  const lowered = firstLine.toLowerCase();
  return PREAMBLE_MARKERS.some((marker) => lowered.includes(marker));
}
