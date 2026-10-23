package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * Outcome of one LLM styling call (ENC-73). The pipeline never throws for
 * model or transport trouble — it falls back to the local text and says
 * why, so a dead key or a dead network can never lose a dictation.
 */
sealed interface LlmStyleOutcome {

    /**
     * The model returned usable styled text, plus its wall-clock cost.
     * [model] is the model that answered (ENC-80 telemetry) and
     * [provider] the OpenRouter upstream that served it, when known.
     */
    data class Styled(
        val text: String,
        val llmMs: Long,
        val model: String? = null,
        val provider: String? = null,
    ) : LlmStyleOutcome

    /**
     * The LLM path did not run or failed; the caller keeps the local
     * text. [model]/[provider] name the last attempt, when one ran.
     */
    data class FellBack(
        val reason: String,
        val model: String? = null,
        val provider: String? = null,
    ) : LlmStyleOutcome
}

/**
 * Styles one transcript via the Firebase Function (ENC-151). The model
 * receives only the transcript plus the style's material — never the
 * field, the app, or anything else. No key exists on device.
 */
interface LlmStylePort {

    /** Styles [text] in the voice of [style], or falls back with a reason. */
    suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome
}
