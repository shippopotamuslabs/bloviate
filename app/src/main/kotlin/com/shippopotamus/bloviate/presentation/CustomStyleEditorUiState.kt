package com.shippopotamus.bloviate.presentation

/**
 * Which scalar draft field a validation message belongs to. Few-shot
 * pair problems live on [FewShotPairUi] itself, not here.
 */
enum class CustomStyleEditorField {
    DISPLAY_NAME,
    DESCRIPTION,
    SAMPLE,
    INSTRUCTIONS,
}

/**
 * Why the last editor save failed, mapped from the synced-save outcome
 * for display. The mapping is total regardless of outcome.
 */
enum class CustomStyleSaveError {
    CONFLICT,
    LIMIT_REACHED,
    NOT_ENTITLED,
    SIGNED_OUT,
    UNAVAILABLE,
    INVALID,
}

/** One few-shot example draft: the raw and styled sides plus their messages, if any. */
data class FewShotPairUi(
    val raw: String = "",
    val styled: String = "",
    val rawErrorRes: Int? = null,
    val styledErrorRes: Int? = null,
)

/**
 * Manual custom-style editor state (presentation only — no domain types).
 * The draft is plain strings; validation messages are string resources;
 * [isValid] gates the save button. On create the server allocates the id,
 * so the draft carries none — the blank id never leaves the write path.
 * [saveSucceeded] is a one-shot event the route observes to navigate
 * back; [loadFailed] means the edited style is gone (deleted elsewhere)
 * and the screen offers its way back.
 *
 * Generated-mode fields (BLO-160 S7): [isGenerated] marks a create opened
 * from an AI draft — only it shows the Regenerate action, and edit-existing
 * never sets it. [regenerateConfirmPending] is the route's
 * discard-confirmation signal when the draft is dirty; [isRegenerating]
 * and [regenerateError] track the in-flight regenerate call.
 */
data class CustomStyleEditorUiState(
    val isCreate: Boolean = true,
    val displayName: String = "",
    val description: String = "",
    val sample: String = "",
    val systemInstructions: String = "",
    val pairs: List<FewShotPairUi> = emptyList(),
    val fieldErrors: Map<CustomStyleEditorField, Int> = emptyMap(),
    val isValid: Boolean = false,
    val canAddPair: Boolean = false,
    val canRemovePair: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: CustomStyleSaveError? = null,
    val isDirty: Boolean = false,
    val loadFailed: Boolean = false,
    val isGenerated: Boolean = false,
    val isRegenerating: Boolean = false,
    val regenerateError: CustomStyleGenerationError? = null,
    val regenerateConfirmPending: Boolean = false,
)
