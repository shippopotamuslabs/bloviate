package com.shippopotamus.bloviate.presentation

/** Presentation-only mapping of one style preset row — no domain types here. */
data class StyleOptionUi(
    val id: String,
    val displayName: String,
    val description: String,
    val sample: String,
    val requiresAi: Boolean,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
)

/**
 * Style tab state: every preset as a presentation row, exactly one marked
 * selected. The selected id always comes from the persisted preference, so
 * this state can never name a style the pipeline would not resolve.
 * [apiKeyDraft] is transient — it is never persisted — and [hasApiKey]
 * only says whether a key is stored, never the key itself.
 */
data class StyleUiState(
    val options: List<StyleOptionUi> = emptyList(),
    val apiKeyDraft: String = "",
    val hasApiKey: Boolean = false,
)
