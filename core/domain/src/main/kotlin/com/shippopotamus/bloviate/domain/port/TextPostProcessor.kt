package com.shippopotamus.bloviate.domain.port

/**
 * The cleanup seam (`contracts/TRANSCRIPTION_RULES.md` §5): turns a raw
 * engine transcript into the text that gets inserted. Deterministic and
 * rule-based — never an on-device LLM. The Phase 2 baseline keeps Whisper's
 * punctuation, strips standalone fillers, and repairs sentence case;
 * richer cleanup (spoken punctuation commands, personal dictionary) layers
 * behind this same seam in Phase 3, and a future opt-in cloud processor
 * would sit behind it too.
 */
interface TextPostProcessor {

    /** Cleans [rawTranscript] for insertion. May return a blank string when nothing insertable remains. */
    fun process(rawTranscript: String): String
}
