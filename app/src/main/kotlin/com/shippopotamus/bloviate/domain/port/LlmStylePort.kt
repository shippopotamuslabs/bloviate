package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * Outcome of one LLM styling call (ENC-73). The pipeline never throws for
 * model or transport trouble — it falls back to the local text and says
 * why, so a dead key or a dead network can never lose a dictation.
 */
sealed interface LlmStyleOutcome {

    /** The model returned usable styled text, plus its wall-clock cost. */
    data class Styled(
        val text: String,
        val llmMs: Long,
    ) : LlmStyleOutcome

    /** The LLM path did not run or failed; the caller keeps the local text. */
    data class FellBack(
        val reason: String,
    ) : LlmStyleOutcome
}

/**
 * Styles one transcript with the user's own OpenRouter key (BYOK). The
 * model receives only the transcript plus the style's material (see
 * `StylePromptBuilder`) — never the field, the app, or anything else.
 */
interface LlmStylePort {

    /** Styles [text] in the voice of [style], or falls back with a reason. */
    suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome
}
