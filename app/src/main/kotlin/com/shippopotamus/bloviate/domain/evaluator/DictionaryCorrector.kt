package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import javax.inject.Inject

/**
 * Applies a [PersonalDictionary] to a raw transcript (ENC-74) — the
 * deterministic, engine-agnostic half of teaching Bloviate new words,
 * since the acoustic models themselves cannot learn any. Pure and
 * framework-free; the pipeline composition lives in
 * [DictionaryAwareTextPostProcessor].
 *
 * Two passes, in a fixed order:
 *
 * 1. **Corrections.** Every "heard as" phrase is replaced by its "should
 *    be" text, verbatim as stored. One combined pass over all pairs, so a
 *    correction's output is never re-scanned by another correction.
 * 2. **Casing.** Every occurrence of a dictionary word, in any case, is
 *    rewritten to the stored canonical casing. Running after corrections
 *    means a correction that produces a lowercase dictionary word
 *    ("blow v8" → "bloviate") still lands correctly cased ("Bloviate").
 *
 * Matching rules (both passes): case-insensitive, whole-phrase only — a
 * match must not be flanked by a letter or digit, so "bloviate" never
 * fires inside "bloviated" or "unbloviate" (punctuation, whitespace, and
 * hyphens are boundaries). When matches overlap, the longest wins
 * ("below v8" beats its own tail "v8"); remaining ties break by earliest
 * position, then by list order, so application is fully deterministic.
 */
class DictionaryCorrector @Inject constructor() {

    /** Returns [text] with [dictionary] applied; the identity when the dictionary is empty. */
    fun apply(text: String, dictionary: PersonalDictionary): String {
        if (text.isEmpty() || dictionary == PersonalDictionary.EMPTY) return text
        val corrected = applyPatterns(
            text = text,
            patterns = dictionary.corrections.map { correction -> correction.heardAs },
            replacementFor = { patternIndex -> dictionary.corrections[patternIndex].replacement },
        )
        return applyPatterns(
            text = corrected,
            patterns = dictionary.words,
            replacementFor = { patternIndex -> dictionary.words[patternIndex] },
        )
    }

    /**
     * One combined replacement pass: finds every boundary-valid,
     * case-insensitive occurrence of every pattern, resolves overlaps
     * longest-first, and rebuilds the string with [replacementFor]
     * substituted at each accepted match.
     */
    private fun applyPatterns(
        text: String,
        patterns: List<String>,
        replacementFor: (patternIndex: Int) -> String,
    ): String {
        val candidates = mutableListOf<PhraseMatch>()
        patterns.forEachIndexed { patternIndex, pattern ->
            if (pattern.isEmpty()) return@forEachIndexed
            var searchFrom = 0
            while (true) {
                val start = text.indexOf(pattern, startIndex = searchFrom, ignoreCase = true)
                if (start < 0) break
                val end = start + pattern.length
                if (isWholePhrase(text, start, end)) {
                    candidates += PhraseMatch(start = start, end = end, patternIndex = patternIndex)
                }
                searchFrom = start + 1
            }
        }
        if (candidates.isEmpty()) return text

        // Longest match first; ties by earliest start. The sort is stable,
        // so full ties keep list order — application order is deterministic.
        val accepted = mutableListOf<PhraseMatch>()
        candidates
            .sortedWith(compareByDescending<PhraseMatch> { it.end - it.start }.thenBy { it.start })
            .forEach { candidate ->
                if (accepted.none { accepted -> candidate.overlaps(accepted) }) {
                    accepted += candidate
                }
            }

        val result = StringBuilder(text.length)
        var cursor = 0
        accepted.sortedBy { it.start }.forEach { match ->
            result.append(text, cursor, match.start)
            result.append(replacementFor(match.patternIndex))
            cursor = match.end
        }
        result.append(text, cursor, text.length)
        return result.toString()
    }

    /** A match is whole-phrase when neither flank is a letter or digit — never inside a larger word. */
    private fun isWholePhrase(text: String, start: Int, end: Int): Boolean {
        val beforeOk = start == 0 || !text[start - 1].isLetterOrDigit()
        val afterOk = end == text.length || !text[end].isLetterOrDigit()
        return beforeOk && afterOk
    }

    private data class PhraseMatch(
        val start: Int,
        val end: Int,
        val patternIndex: Int,
    ) {
        fun overlaps(other: PhraseMatch): Boolean = start < other.end && other.start < end
    }
}
