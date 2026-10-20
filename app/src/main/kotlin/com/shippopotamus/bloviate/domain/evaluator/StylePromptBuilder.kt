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
            content = style.systemInstructions + " " + SPEAKER_INVARIANT + " " + ONLY_TEXT_INSTRUCTION,
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
         * The speaker invariant (ENC-92), shared by every style: the
         * output is the SPEAKER'S OWN words in the style's voice, never
         * a reply to them. The epistolary personas (Seneca above all,
         * whose surviving work is letters) otherwise drag the model
         * into correspondence mode — "You have noticed..." — addressing
         * the dictation back at the person who just spoke it. Person
         * and point of view are facts of the text, and like the
         * NEVER-change clause they are not the style's to alter.
         */
        const val SPEAKER_INVARIANT: String =
            "VOICE: The input is the speaker's own dictated words. Return them restyled " +
                "as the speaker's own text, preserving the speaker's person and point of view " +
                "exactly — where the speaker says 'I', the output says 'I'. NEVER address the " +
                "speaker as 'you', never write as though replying to them, and use no " +
                "salutations, letter framing, or counsel directed at the speaker. Change " +
                "only the voice, never who is speaking or who is spoken to."

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
