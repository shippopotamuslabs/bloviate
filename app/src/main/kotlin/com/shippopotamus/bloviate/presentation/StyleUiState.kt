package com.shippopotamus.bloviate.presentation

/** Presentation-only mapping of one style preset row — no domain types here. */
data class StyleOptionUi(
    val id: String,
    val displayName: String,
    val description: String,
    val sample: String,
    val requiresAi: Boolean,
    val isSelected: Boolean = false,
    val isCustom: Boolean = false,
)

/** One headed section of the Styles screen; [headerRes] is a presentation string resource. */
data class StyleSectionUi(
    val headerRes: Int,
    val styles: List<StyleOptionUi> = emptyList(),
)

/** A custom-style delete awaiting confirmation: the style's id and display name for the dialog. */
data class PendingStyleDelete(
    val styleId: String,
    val displayName: String,
)

/**
 * Why the last custom-style delete failed, mapped from the sync failure
 * for display. The mapping is total regardless of outcome.
 */
enum class CustomStyleDeleteError {
    SIGNED_OUT,
    NOT_ENTITLED,
    INVALID,
    CONFLICT,
    LIMIT_REACHED,
    UNAVAILABLE,
}

/**
 * Style tab state: every preset as a presentation row grouped into headed
 * sections, exactly one marked selected. The selected id always comes from
 * the persisted preference, so this state can never name a style the
 * pipeline would not resolve. The My-styles section is always present —
 * even with zero customs — so the create entry never disappears.
 * [pendingDelete] is the delete awaiting confirmation; [deleteError] is
 * the last delete's rejection, until dismissed.
 */
data class StyleUiState(
    val sections: List<StyleSectionUi> = emptyList(),
    val pendingDelete: PendingStyleDelete? = null,
    val deleteError: CustomStyleDeleteError? = null,
)
