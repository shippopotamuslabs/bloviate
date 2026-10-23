package com.shippopotamus.bloviate.presentation

/** Presentation-only mapping of one style preset row — no domain types here. */
data class StyleOptionUi(
    val id: String,
    val displayName: String,
    val description: String,
    val sample: String,
    val requiresAi: Boolean,
    val isSelected: Boolean = false,
)

/**
 * Style tab state: every preset as a presentation row, exactly one marked
 * selected. The selected id always comes from the persisted preference, so
 * this state can never name a style the pipeline would not resolve.
 */
data class StyleUiState(
    val options: List<StyleOptionUi> = emptyList(),
)
