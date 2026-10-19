package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import javax.inject.Inject

/** One chat message: a role plus its text. Transport-agnostic. */
data class PromptMessage(
    val role: String,
    val content: String,
)

/** The (system, user) pair sent to the model for one styling call. */
data class StylePrompt(
    val system: PromptMessage,
    val user: PromptMessage,
)

/**
 * Builds the (system, user) messages for one LLM styling call (ENC-73).
 * The model receives ONLY the transcript plus the style's own material —
 * its system instructions and few-shot pairs. No field text, no app
 * package, no device context is ever included.
 */
class StylePromptBuilder @Inject constructor() {

    fun build(style: StyleDefinition, transcript: String): StylePrompt {
        val system = PromptMessage(
            role = ROLE_SYSTEM,
            content = style.systemInstructions + " " + ONLY_TEXT_INSTRUCTION,
        )
        val user = PromptMessage(
            role = ROLE_USER,
            content = buildUserContent(style.fewShots, transcript),
        )
        return StylePrompt(system = system, user = user)
    }

    private fun buildUserContent(fewShots: List<FewShot>, transcript: String): String =
        buildString {
            fewShots.forEach { shot ->
                append("Example input: ")
                append(shot.raw)
                append("\nExample output: ")
                append(shot.styled)
                append("\n\n")
            }
            append("Now transform this input, following the examples:\n")
            append(transcript)
        }

    private companion object {
        const val ROLE_SYSTEM: String = "system"
        const val ROLE_USER: String = "user"

        /**
         * The model must return ONLY the transformed text: no quotes, no
         * preamble, no explanation. [LlmResponseCleaner] still strips
         * fences and preambles defensively — the instruction reduces them,
         * it does not eliminate them.
         */
        const val ONLY_TEXT_INSTRUCTION: String =
            "Return ONLY the transformed text — no quotation marks, no preamble, no explanation."
    }
}
