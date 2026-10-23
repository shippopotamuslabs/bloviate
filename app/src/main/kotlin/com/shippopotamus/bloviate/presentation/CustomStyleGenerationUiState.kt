package com.shippopotamus.bloviate.presentation

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.GeneratedStyleDraft

/**
 * Why the last style-draft generation failed, mapped from the generation
 * outcome for display. The mapping is total regardless of outcome: the
 * client-side [InvalidInput] gate only disables the button, so the Start
 * screen never shows an error for it.
 */
enum class CustomStyleGenerationError {
    NOT_A_STYLE,
    QUOTA_REACHED,
    NOT_ENTITLED,
    SIGNED_OUT,
    INVALID_GENERATION,
    UNAVAILABLE,
}

/**
 * One generated draft crossing from the Start screen to the shared editor
 * (BLO-160 S7): the validated [draft] plus the [sourceDescription] that
 * produced it, retained so the editor can regenerate from the same words.
 * Transient route state only — never persisted (see the schema lock).
 */
data class GeneratedHandoff(
    val draft: GeneratedStyleDraft,
    val sourceDescription: String,
)

/**
 * AI style-draft Start state (presentation only — no domain types). The
 * description is a plain string; validation messages are string resources;
 * [canGenerate] gates the button. [generateSucceeded] is a one-shot event
 * the route observes to open the shared editor prefilled.
 */
/**
 * Every generation failure maps to exactly one documented message. Shared
 * by the Start screen and the editor's regenerate line: an unparseable
 * classifier verdict reads as a bad draft either way (retry), and the
 * client-side [InvalidInput] gate only disables buttons, so it lands on
 * the generic server message defensively.
 */
internal fun CustomStyleGenerationFailure.toGenerationError(): CustomStyleGenerationError =
    when (this) {
        CustomStyleGenerationFailure.NotAStyleDescription -> CustomStyleGenerationError.NOT_A_STYLE
        CustomStyleGenerationFailure.QuotaReached -> CustomStyleGenerationError.QUOTA_REACHED
        CustomStyleGenerationFailure.NotEntitled -> CustomStyleGenerationError.NOT_ENTITLED
        CustomStyleGenerationFailure.SignedOut -> CustomStyleGenerationError.SIGNED_OUT
        CustomStyleGenerationFailure.GenerationInvalid,
        CustomStyleGenerationFailure.ClassifierUnavailable,
        -> CustomStyleGenerationError.INVALID_GENERATION
        CustomStyleGenerationFailure.InvalidInput,
        CustomStyleGenerationFailure.Unavailable,
        -> CustomStyleGenerationError.UNAVAILABLE
    }

data class CustomStyleGenerationUiState(
    val description: String = "",
    val isGenerating: Boolean = false,
    val error: CustomStyleGenerationError? = null,
    val canGenerate: Boolean = false,
)
