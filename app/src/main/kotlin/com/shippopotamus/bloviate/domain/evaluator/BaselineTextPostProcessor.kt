package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import javax.inject.Inject

/**
 * The Phase 2 baseline [TextPostProcessor] (`contracts/TRANSCRIPTION_RULES.md`
 * §5): keeps Whisper's punctuation and capitalization, strips standalone
 * filler tokens, repairs sentence case, and collapses whitespace. Pure and
 * deterministic — it never guesses, reorders, or drops real words.
 *
 * Deliberate limits (Phase 3 / future cloud territory, never heuristics
 * that guess): no spoken-punctuation commands, no personal dictionary,
 * no self-correction resolution ("Tuesday — no, Wednesday" stays literal).
 */
class BaselineTextPostProcessor @Inject constructor() : TextPostProcessor {

    override fun process(rawTranscript: String): String =
        repairSentenceCase(fixPunctuationSpacing(removeStandaloneFillers(rawTranscript)))

    /**
     * Drops tokens that are pure fillers — e.g. "um", "uh", "er", and their
     * punctuation-carrying forms ("Um,"). The match is on the token's
     * alphabetic core after stripping edge punctuation, so real words
     * containing those letters ("umbrella", "errand", "upper") survive.
     */
    private fun removeStandaloneFillers(text: String): String {
        if (text.isBlank()) return ""
        return text.trim().split(WHITESPACE).filterNot { token ->
            token.trim { char -> char in EDGE_PUNCTUATION }.lowercase() in FILLERS
        }.joinToString(" ")
    }

    /**
     * Removes space artifacts before punctuation ("hello ." → "hello."),
     * which filler removal or Whisper spacing can produce.
     */
    private fun fixPunctuationSpacing(text: String): String {
        var result = text
        for (punctuation in NO_PRECEDING_SPACE) {
            result = result.replace(" $punctuation", punctuation)
        }
        return result
    }

    /**
     * Uppercases the first letter of the utterance and the first letter
     * after sentence-ending punctuation (. ! ?). Everything else is left
     * exactly as Whisper wrote it — no lowercasing.
     */
    private fun repairSentenceCase(text: String): String {
        if (text.isBlank()) return text
        val chars = text.toCharArray()
        var capitalizeNext = true
        for (index in chars.indices) {
            val char = chars[index]
            when {
                char.isLetter() -> {
                    if (capitalizeNext) {
                        chars[index] = char.uppercaseChar()
                        capitalizeNext = false
                    }
                }
                char in SENTENCE_ENDERS -> capitalizeNext = true
                char.isDigit() -> capitalizeNext = false
            }
        }
        return String(chars)
    }

    private companion object {
        val WHITESPACE: Regex = Regex("\\s+")

        /** Filler tokens, per TRANSCRIPTION_RULES §5 ("um", "uh", "er"). */
        val FILLERS: Set<String> = setOf("um", "uh", "er")

        const val EDGE_PUNCTUATION: String = ".,!?;:\"'‘’“”()…"

        val SENTENCE_ENDERS: Set<Char> = setOf('.', '!', '?')

        val NO_PRECEDING_SPACE: List<String> = listOf(",", ".", "!", "?", ";", ":")
    }
}
