package com.shippopotamus.bloviate.presentation

/** Presentation-only classification of the focused field, mapped from the domain state. */
enum class FocusClassification {
    NONE,
    EDITABLE,
    PASSWORD,
}

/**
 * Home/status screen state: watcher enablement plus the live field-focus
 * classification. Carries no domain types and never any field content.
 */
data class MainUiState(
    val watcherEnabled: Boolean = false,
    val focusClassification: FocusClassification = FocusClassification.NONE,
    val focusPackageName: String? = null,
)
