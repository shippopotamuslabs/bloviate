package com.shippopotamus.bloviate.presentation

/** Presentation-only mapping of one "heard as → should be" dictionary correction. */
data class CorrectionUi(
    val heardAs: String,
    val replacement: String,
)

/**
 * Why the last dictionary add was rejected, mapped from the domain edit
 * result for display. [NOT_FOUND]-style outcomes never surface here —
 * removals come from rows that exist.
 */
enum class DictionaryEditError {
    BLANK_WORD,
    DUPLICATE_WORD,
    BLANK_CORRECTION,
    DUPLICATE_CORRECTION,
    SELF_CORRECTION,
}

/**
 * Dictionary tab state: the user's words and corrections (mapped from
 * the domain dictionary — no domain types here), the two add-form drafts
 * (transient presentation state, per `ARCHITECTURE_RULES.md` §3.4), and
 * the last add's rejection, if any.
 */
data class DictionaryUiState(
    val words: List<String> = emptyList(),
    val corrections: List<CorrectionUi> = emptyList(),
    val wordDraft: String = "",
    val heardAsDraft: String = "",
    val replacementDraft: String = "",
    val wordError: DictionaryEditError? = null,
    val correctionError: DictionaryEditError? = null,
)
