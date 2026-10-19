package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StylePromptBuilderTest {

    private val builder = StylePromptBuilder()
    private val professional = StyleCatalog.byId("professional")
    private val seneca = StyleCatalog.byId("seneca")
    private val lowercaseCasual = StyleCatalog.byId("lowercase-casual")
    private val talkie = StyleCatalog.byId("talkie")

    @Test
    fun `system message carries the style instructions plus the only-text rule`() {
        val prompt = builder.build(style = professional, transcript = "hello there")

        assertEquals("system", prompt.system.role)
        assertTrue(prompt.system.content.contains(professional.systemInstructions))
        assertTrue(prompt.system.content.contains("ONLY"))
    }

    @Test
    fun `every style's system message carries the speaker invariant`() {
        // ENC-92: dictation is the speaker's own words, never a reply
        // addressed back at them — the invariant rides every style's
        // system message, personas included.
        StyleCatalog.ALL.forEach { style ->
            val prompt = builder.build(style = style, transcript = "i noticed the bug this morning")

            assertTrue(
                prompt.system.content.contains("NEVER address the speaker"),
                "${style.id} system message lacks the speaker invariant",
            )
            assertTrue(
                prompt.system.content.contains("speaker's own"),
                "${style.id} system message lacks the own-words clause",
            )
        }
    }

    @Test
    fun `system message carries every few-shot pair`() {
        // ENC-98: the examples moved into the system message — they
        // are definitional material, like the instructions.
        val prompt = builder.build(style = professional, transcript = TRANSCRIPT)

        professional.fewShots.forEach { shot ->
            assertTrue(prompt.system.content.contains(shot.raw), "system lacks example input: ${shot.raw}")
            assertTrue(prompt.system.content.contains(shot.styled), "system lacks example output: ${shot.styled}")
        }
    }

    @Test
    fun `user message demarcates the transcript and ends with the operative imperative`() {
        // ENC-98's shape: the transcript sits between the markers, the
        // operative imperative follows it (the last thing the model
        // reads is the task on this input), and NO example text leaks
        // into the user message — the blur that let Llama 3.3 treat
        // the call as a pattern to continue instead of a transform.
        val prompt = builder.build(style = professional, transcript = TRANSCRIPT)

        assertEquals("user", prompt.user.role)
        assertDemarcatedShape(prompt, professional)
    }

    @Test
    fun `seneca prompt follows the same demarcated shape`() {
        // ENC-98 named pin: the persona whose device failure filed
        // the ticket gets the new shape like every other style.
        val prompt = builder.build(style = seneca, transcript = TRANSCRIPT)

        seneca.fewShots.forEach { shot ->
            assertTrue(prompt.system.content.contains(shot.styled), "seneca system lacks example output")
        }
        assertDemarcatedShape(prompt, seneca)
    }

    @Test
    fun `a mechanical style follows the same demarcated shape`() {
        // ENC-98 named pin: the builder is catalog-wide — a
        // mechanical style's prompt is shaped identically.
        val prompt = builder.build(style = lowercaseCasual, transcript = TRANSCRIPT)

        lowercaseCasual.fewShots.forEach { shot ->
            assertTrue(prompt.system.content.contains(shot.styled), "lowercase-casual system lacks example output")
        }
        assertDemarcatedShape(prompt, lowercaseCasual)
    }

    @Test
    fun `talkie prompt carries the period register for casual and technical dictation`() {
        // ENC-102's prompt fixtures through the real builder: the
        // era style's definitional material (1920s register, the
        // register-of-the-language directive, the few-shot pairs)
        // rides the system message for everyday and technical input
        // alike, and both user messages keep the demarcated shape.
        listOf(CASUAL_TRANSCRIPT, TECHNICAL_TRANSCRIPT).forEach { transcript ->
            val prompt = builder.build(style = talkie, transcript = transcript)

            assertTrue(prompt.system.content.contains("1920s"), "talkie system lacks the period register")
            assertTrue(
                prompt.system.content.contains("transcript's own language"),
                "talkie system lacks the register-of-the-language directive",
            )
            talkie.fewShots.forEach { shot ->
                assertTrue(prompt.system.content.contains(shot.raw), "talkie system lacks example input")
                assertTrue(prompt.system.content.contains(shot.styled), "talkie system lacks example output")
            }
            assertTrue(prompt.user.content.contains("<transcript>"), "talkie user message lacks markers")
            assertTrue(prompt.user.content.contains(transcript), "talkie user message lacks the transcript")
        }
    }

    @Test
    fun `the colonel prompt carries the southern register for casual and technical dictation`() {
        // ENC-103's prompt fixtures through the real builder: the
        // era style's definitional material (Southern register, the
        // register-of-the-language directive, the boundary, the
        // few-shot pairs) rides the system message for everyday and
        // technical input alike, and both user messages keep the
        // demarcated shape.
        val colonel = StyleCatalog.byId("the-colonel")
        val transcripts = listOf(
            "remind me to call mom about sunday dinner",
            "the nightly build broke on the auth module again",
        )
        transcripts.forEach { transcript ->
            val prompt = builder.build(style = colonel, transcript = transcript)

            assertTrue(
                prompt.system.content.contains("nineteenth-century Southern"),
                "the colonel system lacks the register",
            )
            assertTrue(
                prompt.system.content.contains("racial vocabulary"),
                "the colonel system lacks the boundary",
            )
            colonel.fewShots.forEach { shot ->
                assertTrue(prompt.system.content.contains(shot.raw), "the colonel system lacks example input")
                assertTrue(prompt.system.content.contains(shot.styled), "the colonel system lacks example output")
            }
            assertTrue(prompt.user.content.contains("<transcript>"), "the colonel user message lacks markers")
            assertTrue(prompt.user.content.contains(transcript), "the colonel user message lacks the transcript")
        }
    }

    @Test
    fun `every style's user message is task plus demarcated input only`() {
        StyleCatalog.ALL.forEach { style ->
            val prompt = builder.build(style = style, transcript = TRANSCRIPT)

            assertDemarcatedShape(prompt, style)
            style.fewShots.forEach { shot ->
                assertTrue(
                    prompt.system.content.contains(shot.raw) && prompt.system.content.contains(shot.styled),
                    "${style.id} system message lacks a few-shot pair",
                )
            }
        }
    }

    @Test
    fun `prompt carries no field or app context`() {
        val prompt = builder.build(style = professional, transcript = "plain words")

        val combined = prompt.system.content + "\n" + prompt.user.content
        assertTrue("package" !in combined.lowercase())
        assertTrue("password" !in combined.lowercase())
        assertTrue("application" !in combined.lowercase())
    }

    /**
     * The ENC-98 user-message shape for [style]: the transcript sits
     * exactly once between the `<transcript>` markers, the operative
     * imperative follows the closing marker at the very end, and no
     * few-shot text appears in the user message at all.
     */
    private fun assertDemarcatedShape(prompt: StylePrompt, style: StyleDefinition) {
        val user = prompt.user.content
        val open = user.indexOf("<transcript>")
        val close = user.indexOf("</transcript>")
        val heard = user.indexOf(TRANSCRIPT)
        assertTrue(open >= 0 && close > open, "${style.id} user message lacks transcript markers")
        assertTrue(heard > open && heard < close, "${style.id} transcript is not between the markers")
        assertEquals(heard, user.lastIndexOf(TRANSCRIPT), "${style.id} transcript appears more than once")
        assertTrue(
            user.indexOf("Rewrite ONLY") > close,
            "${style.id} operative imperative does not follow the transcript",
        )
        assertTrue(user.trimEnd().endsWith("nothing else."), "${style.id} user message does not end with the imperative")
        style.fewShots.forEach { shot ->
            assertTrue(!user.contains(shot.raw), "${style.id} example input leaked into the user message")
            assertTrue(!user.contains(shot.styled), "${style.id} example output leaked into the user message")
        }
    }

    private companion object {
        /** A transcript no catalog few-shot contains, so leakage assertions cannot false-positive. */
        const val TRANSCRIPT: String = "my bicycle has a flat tire again"

        /** ENC-102 fixtures: an everyday dictation and a technical one through the era style. */
        const val CASUAL_TRANSCRIPT: String = "remind me to call mom about sunday dinner"
        const val TECHNICAL_TRANSCRIPT: String = "the nightly build broke on the auth module again"
    }
}
