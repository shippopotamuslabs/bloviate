import type { StyleDefinition } from "./styleCatalog";

export const SPEAKER_INVARIANT =
  "VOICE: The input is the speaker's own dictated words. Return them restyled " +
  "as the speaker's own text, preserving the speaker's person and point of view " +
  "exactly — where the speaker says 'I', the output says 'I'. NEVER address the " +
  "speaker as 'you', never write as though replying to them, and use no " +
  "salutations, letter framing, or counsel directed at the speaker. Change " +
  "only the voice, never who is speaking or who is spoken to.";

export const ONLY_TEXT_INSTRUCTION =
  "Return ONLY the transformed text — no quotation marks, no preamble, no explanation.";

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
  system += "\n\n" + SPEAKER_INVARIANT + " " + ONLY_TEXT_INSTRUCTION;
  const user =
    USER_LEAD_IN + "\n\n<transcript>\n" + transcript + "\n</transcript>\n\n" + USER_IMPERATIVE;
  return { system, user };
}
