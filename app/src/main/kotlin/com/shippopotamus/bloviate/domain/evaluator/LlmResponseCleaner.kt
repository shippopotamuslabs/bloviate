package com.shippopotamus.bloviate.domain.evaluator

import javax.inject.Inject

/**
 * Defensive cleanup of raw LLM output (ENC-73). The prompt already asks
 * for only-the-text, but models still wrap answers in markdown fences or
 * open with a preamble ("Here is the rewritten text:"). This strips both;
 * a result that is blank afterwards means the caller falls back to the
 * local text — never an empty insertion.
 */
class LlmResponseCleaner @Inject constructor() {

    /** Returns the usable text, or "" when nothing usable remains. */
    fun clean(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""
        text = stripFences(text).trim()
        if (text.isEmpty()) return ""
        text = stripPreamble(text).trim()
        return text
    }

    private fun stripFences(text: String): String {
        var stripped = text
        if (stripped.startsWith(FENCE)) {
            // Drop the opening fence line (``` or ```lang); the language
            // tag, if any, is never part of the answer.
            stripped = stripped.substringAfter("\n", "")
        }
        val fenceIndex = stripped.lastIndexOf("\n" + FENCE)
        if (fenceIndex >= 0) {
            stripped = stripped.substring(0, fenceIndex)
        } else if (stripped.endsWith(FENCE)) {
            stripped = stripped.substring(0, stripped.length - FENCE.length)
        }
        return stripped
    }

    private fun stripPreamble(text: String): String {
        val firstNewline = text.indexOf('\n')
        if (firstNewline < 0) return text
        val firstLine = text.substring(0, firstNewline).trim()
        if (firstLine.length <= PREAMBLE_MAX_CHARS && looksLikePreamble(firstLine)) {
            return text.substring(firstNewline + 1)
        }
        return text
    }

    private fun looksLikePreamble(firstLine: String): Boolean {
        if (firstLine.endsWith(':')) return true
        val lowered = firstLine.lowercase()
        return PREAMBLE_MARKERS.any { marker -> marker in lowered }
    }

    private companion object {
        const val FENCE: String = "```"

        /** Preambles are one short line; longer first lines are the answer. */
        const val PREAMBLE_MAX_CHARS: Int = 120

        val PREAMBLE_MARKERS: List<String> = listOf(
            "here is",
            "here's",
            "sure,",
            "certainly",
            "rewritten",
            "transformed",
        )
    }
}
