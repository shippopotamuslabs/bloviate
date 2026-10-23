package com.shippopotamus.bloviate.domain.model

/**
 * The user's personal dictionary (ENC-74): the spellings and fixes the
 * cleanup pipeline applies because the speech engines cannot be taught
 * them acoustically. Two collections:
 *
 * - [words]: entries whose canonical spelling *and capitalization* are
 *   enforced wherever they occur ("Bloviate", "Parakeet").
 * - [corrections]: "heard as → should be" pairs for systematic
 *   mis-hearings ("perikit" → "Parakeet", "below v8" → "Bloviate").
 *
 * Application lives in `DictionaryCorrector` (corrections first, then
 * casing); this type owns the *edit rules* — normalization, blank and
 * duplicate rejection — as pure functions, so the persisted store, the
 * test fake, and any future editor all enforce exactly the same rules.
 * Edits return a [DictionaryEditResult] plus the resulting dictionary;
 * on any non-[DictionaryEditResult.APPLIED] result the returned
 * dictionary is the receiver, unchanged.
 */
data class PersonalDictionary(
    val words: List<String>,
    val corrections: List<Correction>,
) {

    /** One "heard as → should be" pair. Both sides are stored normalized (see [normalizeEntry]). */
    data class Correction(
        val heardAs: String,
        val replacement: String,
    )

    /** Adds [rawWord] to [words]; rejects blanks and case-insensitive duplicates. */
    fun withWordAdded(rawWord: String): Pair<DictionaryEditResult, PersonalDictionary> {
        val word = normalizeEntry(rawWord)
        if (word.isEmpty()) return DictionaryEditResult.BLANK to this
        if (words.any { existing -> existing.equals(word, ignoreCase = true) }) {
            return DictionaryEditResult.DUPLICATE to this
        }
        return DictionaryEditResult.APPLIED to copy(words = words + word)
    }

    /** Removes the word matching [rawWord] (case-insensitive); [DictionaryEditResult.NOT_FOUND] when absent. */
    fun withWordRemoved(rawWord: String): Pair<DictionaryEditResult, PersonalDictionary> {
        val word = normalizeEntry(rawWord)
        val index = words.indexOfFirst { existing -> existing.equals(word, ignoreCase = true) }
        if (index < 0) return DictionaryEditResult.NOT_FOUND to this
        return DictionaryEditResult.APPLIED to copy(words = words.filterIndexed { i, _ -> i != index })
    }

    /**
     * Adds a correction pair; rejects a blank side, a pair whose sides are
     * equal ignoring case (a self-correction can never fire usefully), and
     * a "heard as" that already has a correction (edit it by removing and
     * re-adding, so the list never holds two fates for one mis-hearing).
     */
    fun withCorrectionAdded(
        rawHeardAs: String,
        rawReplacement: String,
    ): Pair<DictionaryEditResult, PersonalDictionary> {
        val heardAs = normalizeEntry(rawHeardAs)
        val replacement = normalizeEntry(rawReplacement)
        if (heardAs.isEmpty() || replacement.isEmpty()) return DictionaryEditResult.BLANK to this
        if (heardAs.equals(replacement, ignoreCase = true)) {
            return DictionaryEditResult.SELF_CORRECTION to this
        }
        if (corrections.any { existing -> existing.heardAs.equals(heardAs, ignoreCase = true) }) {
            return DictionaryEditResult.DUPLICATE to this
        }
        return DictionaryEditResult.APPLIED to
            copy(corrections = corrections + Correction(heardAs = heardAs, replacement = replacement))
    }

    /** Removes the correction whose "heard as" matches [rawHeardAs] (case-insensitive). */
    fun withCorrectionRemoved(rawHeardAs: String): Pair<DictionaryEditResult, PersonalDictionary> {
        val heardAs = normalizeEntry(rawHeardAs)
        val index = corrections.indexOfFirst { existing ->
            existing.heardAs.equals(heardAs, ignoreCase = true)
        }
        if (index < 0) return DictionaryEditResult.NOT_FOUND to this
        return DictionaryEditResult.APPLIED to
            copy(corrections = corrections.filterIndexed { i, _ -> i != index })
    }

    companion object {

        /** No words, no corrections: the corrector is the identity over it. */
        val EMPTY: PersonalDictionary = PersonalDictionary(words = emptyList(), corrections = emptyList())

        /**
         * The starter dictionary seeded on first run (ENC-74): the
         * product's own name and engine names, plus the mis-hearings
         * observed on-device during the ENC-54 A/B. Seeds are ordinary
         * data — the user can edit or delete every one of them.
         */
        val SEED: PersonalDictionary = PersonalDictionary(
            words = listOf("Bloviate", "Parakeet", "Shippopotamus"),
            corrections = listOf(
                Correction(heardAs = "perikit", replacement = "Parakeet"),
                Correction(heardAs = "below v8", replacement = "Bloviate"),
                Correction(heardAs = "blow-viate", replacement = "Bloviate"),
                Correction(heardAs = "blow viate", replacement = "Bloviate"),
            ),
        )

        /**
         * Entry normalization: trims, and collapses every internal
         * whitespace run (spaces, tabs, newlines) to a single space, so a
         * stored phrase aligns with the single-spaced text engines emit —
         * and so the persisted encoding (newline/tab separated) can never
         * be corrupted by an entry.
         */
        fun normalizeEntry(raw: String): String = raw.trim().replace(WHITESPACE_RUN, " ")

        private val WHITESPACE_RUN: Regex = Regex("\\s+")
    }
}

/** The outcome of a dictionary edit (see [PersonalDictionary]); anything but [APPLIED] leaves the dictionary unchanged. */
enum class DictionaryEditResult {
    APPLIED,
    BLANK,
    DUPLICATE,
    SELF_CORRECTION,
    NOT_FOUND,
}
