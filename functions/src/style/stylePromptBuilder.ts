import type { StyleDefinition } from "./styleCatalog";

export const SPEAKER_INVARIANT =
  "VOICE: The input is the speaker's own dictated words. Return them restyled " +
  "as the speaker's own text, preserving the speaker's person and point of view " +
  "exactly — where the speaker says 'I', the output says 'I'. NEVER address the " +
  "speaker as 'you', never write as though replying to them, and use no " +
  "salutations, letter framing, or counsel directed at the speaker. Change " +
  "only the voice, never who is speaking or who is spoken to.";

export const REWRITE_LICENSE =
  "INTENT, NOT WORDING: Keep what the speaker means — their intent, facts, " +
  "names, numbers, dates, and quoted text — never their exact phrasing. " +
  "Recast freely into the voice: new sentence shapes, new verbs, new order, " +
  "compression or expansion where the voice demands it. How far the recast " +
  "goes is set by the voice itself: a cleanup voice stays close to the " +
  "words, a persona voice rewrites fully — but no voice is a mere " +
  "copy-editor unless its own instructions say so. A voice's signature " +
  "formatting devices (lesson lists, content notes, hashtag tails, " +
  "sign-offs) are a toolkit, not a template: deploy one where it lands " +
  "for this text, skip it where it does not, and never force the same " +
  "closing structure onto every output.";

export const ONLY_TEXT_INSTRUCTION =
  "Return ONLY the transformed text — no quotation marks, no preamble, no explanation.";

// BLO-158 two-tier facts rule, tier 1: universal, every style.
// Byte-identical to the Kotlin StylePromptBuilder's FACTS_TIER1.
export const FACTS_TIER1 =
  "FACTS ARE NOT THE JOKE: Never change or contradict a stated fact — names, " +
  "numbers, dates, quoted text, who did what. A changed fact is never the joke, " +
  "it is just a wrong message.";

// BLO-158 tier 2: SERIOUS styles only — never ADD facts either.
// Byte-identical to the Kotlin StylePromptBuilder's SERIOUS_NO_INVENTION.
export const SERIOUS_NO_INVENTION =
  "SERIOUS MEANS SERIOUS: Never add facts either — no invented details, no " +
  "connective tissue, no scene-setting. The transcript is the whole universe: " +
  "say only what the speaker said, cleaner.";

// BLO-158 license branch: every non-SERIOUS style (custom included).
// Byte-identical to the Kotlin StylePromptBuilder's LICENSED_INVENTION.
export const LICENSED_INVENTION =
  "LICENSED INVENTION: Atmospheric texture, inflated framing, connective tissue, " +
  "and scene-setting may be added under two limits: (a) nothing added contradicts " +
  "a stated fact; (b) nothing added impersonates a stated fact — no invented numbers, " +
  "dates, names, or quotes dressed up as the speaker's.";

const EXAMPLES_HEADER =
  "Examples of the transformation — each dictated input restyled in this voice:";
const USER_LEAD_IN =
  "Restyle the dictated text between the <transcript> markers, " +
  "following the examples in the system message.";
const USER_IMPERATIVE =
  "Rewrite ONLY the text between the <transcript> markers, in the voice " +
  "of the examples. Return only the rewritten text — nothing else.";

export type StylePrompt = { system: string; user: string };

export function buildStylePrompt(style: StyleDefinition, transcript: string): StylePrompt {
  let system = style.systemInstructions;
  if (style.fewShots.length > 0) {
    system += "\n\n" + EXAMPLES_HEADER;
    for (const shot of style.fewShots) {
      system += "\nExample input: " + shot.raw + "\nExample output: " + shot.styled;
    }
  }
  const factsBranch = style.category === "serious" ? SERIOUS_NO_INVENTION : LICENSED_INVENTION;
  system +=
    "\n\n" +
    SPEAKER_INVARIANT +
    " " +
    REWRITE_LICENSE +
    " " +
    FACTS_TIER1 +
    " " +
    factsBranch +
    " " +
    ONLY_TEXT_INSTRUCTION;
  const user =
    USER_LEAD_IN + "\n\n<transcript>\n" + transcript + "\n</transcript>\n\n" + USER_IMPERATIVE;
  return { system, user };
}
