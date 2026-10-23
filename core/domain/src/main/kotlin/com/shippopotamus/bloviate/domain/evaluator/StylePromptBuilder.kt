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
 *
 * Prompt shape (ENC-98): the system message carries everything
 * definitional — the style's instructions, its few-shot examples,
 * the speaker invariant, and the only-text rule. The user message is
 * purely task + input: a one-line orientation, the transcript
 * demarcated between `<transcript>` markers, and the operative
 * imperative AFTER the transcript, so the last thing the model
 * reads is the task on this input. The previous shape (few-shots in
 * the user message, transcript appended bare at the end) worked for
 * Grok and Haiku, but Llama 3.3 on device treated it as a pattern to
 * continue — it answered with generic persona-flavored text
 * containing nothing from the dictation, and only the ENC-82
 * fidelity guard caught it. Demarcation keeps the input from
 * blurring into the examples; the trailing imperative makes the
 * transform, not the pattern, the instruction.
 */
class StylePromptBuilder @Inject constructor() {

    fun build(style: StyleDefinition, transcript: String): StylePrompt {
        val system = PromptMessage(
            role = ROLE_SYSTEM,
            content = buildSystemContent(style),
        )
        val user = PromptMessage(
            role = ROLE_USER,
            content = buildUserContent(transcript),
        )
        return StylePrompt(system = system, user = user)
    }

    private fun buildSystemContent(style: StyleDefinition): String =
        buildString {
            append(style.systemInstructions)
            if (style.fewShots.isNotEmpty()) {
                append("\n\n")
                append(EXAMPLES_HEADER)
                style.fewShots.forEach { shot ->
                    appendExample(shot)
                }
            }
            append("\n\n")
            append(SPEAKER_INVARIANT)
            append(" ")
            append(REWRITE_LICENSE)
            append(" ")
            append(ONLY_TEXT_INSTRUCTION)
        }

    private fun StringBuilder.appendExample(shot: FewShot) {
        append("\nExample input: ")
        append(shot.raw)
        append("\nExample output: ")
        append(shot.styled)
    }

    private fun buildUserContent(transcript: String): String =
        buildString {
            append(USER_LEAD_IN)
            append("\n\n")
            append(TRANSCRIPT_OPEN)
            append("\n")
            append(transcript)
            append("\n")
            append(TRANSCRIPT_CLOSE)
            append("\n\n")
            append(USER_IMPERATIVE)
        }

    private companion object {
        const val ROLE_SYSTEM: String = "system"
        const val ROLE_USER: String = "user"

        /** Heads the few-shot block in the system message (ENC-98). */
        const val EXAMPLES_HEADER: String =
            "Examples of the transformation — each dictated input restyled in this voice:"

        /** The transcript's demarcation in the user message (ENC-98). */
        const val TRANSCRIPT_OPEN: String = "<transcript>"
        const val TRANSCRIPT_CLOSE: String = "</transcript>"

        /** One-line orientation ahead of the demarcated transcript (ENC-98). */
        const val USER_LEAD_IN: String =
            "Restyle the dictated text between the <transcript> markers, " +
                "following the examples in the system message."

        /**
         * The operative imperative, placed AFTER the transcript so it
         * is the last thing the model reads (ENC-98): rewrite only
         * what the markers hold, in the examples' voice, and nothing
         * else.
         */
        const val USER_IMPERATIVE: String =
            "Rewrite ONLY the text between the <transcript> markers, in the voice " +
                "of the examples. Return only the rewritten text — nothing else."

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
         * The rewrite license (BLO-153), shared by every style: what is
         * preserved is the speaker's INTENT — facts, names, numbers,
         * dates, quoted text — never their exact phrasing. Before this
         * clause existed, the shared layer only prohibited changes, and
         * most styles drifted into copy-editing: same sentences, tidier
         * punctuation. The license makes recasting the default and
         * demotes signature formatting (lesson lists, content notes,
         * hashtag tails) from mandatory skeleton to toolkit. How far the
         * recast goes stays the voice's own call — Polish stays close by
         * design; the personas rewrite fully.
         */
        const val REWRITE_LICENSE: String =
            "INTENT, NOT WORDING: Keep what the speaker means — their intent, facts, " +
                "names, numbers, dates, and quoted text — never their exact phrasing. " +
                "Recast freely into the voice: new sentence shapes, new verbs, new order, " +
                "compression or expansion where the voice demands it. How far the recast " +
                "goes is set by the voice itself: a cleanup voice stays close to the " +
                "words, a persona voice rewrites fully — but no voice is a mere " +
                "copy-editor unless its own instructions say so. A voice's signature " +
                "formatting devices (lesson lists, content notes, hashtag tails, " +
                "sign-offs) are a toolkit, not a template: deploy one where it lands " +
                "for this text, skip it where it does not, and never force the same " +
                "closing structure onto every output."

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
