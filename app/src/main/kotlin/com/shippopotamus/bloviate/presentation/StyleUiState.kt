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

/** One headed section of the Styles screen; [headerRes] is a presentation string resource. */
data class StyleSectionUi(
    val headerRes: Int,
    val styles: List<StyleOptionUi> = emptyList(),
)

/**
 * Style tab state: every preset as a presentation row grouped into headed
 * sections, exactly one marked selected. The selected id always comes from
 * the persisted preference, so this state can never name a style the
 * pipeline would not resolve.
 */
data class StyleUiState(
    val sections: List<StyleSectionUi> = emptyList(),
)
