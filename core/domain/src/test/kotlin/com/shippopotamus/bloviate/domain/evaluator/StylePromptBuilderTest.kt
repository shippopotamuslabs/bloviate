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
    private val transatlantic = StyleCatalog.byId("talkie")

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
            // BLO-153: intent is preserved, wording is not — the rewrite
            // license rides every style beside the invariant.
            assertTrue(
                prompt.system.content.contains("INTENT, NOT WORDING"),
                "${style.id} system message lacks the rewrite license",
            )
            assertTrue(
                prompt.system.content.contains("toolkit, not a template"),
                "${style.id} system message lacks the license toolkit clause",
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
    fun `transatlantic prompt carries the period register for casual and technical dictation`() {
        // ENC-102's prompt fixtures through the real builder: the
        // era style's definitional material (1920s register, the
        // register-of-the-language directive, the few-shot pairs)
        // rides the system message for everyday and technical input
        // alike, and both user messages keep the demarcated shape.
        // ENC-144: the register is vernacular diction, and the
        // curtness pins ride no prompt anymore.
        listOf(CASUAL_TRANSCRIPT, TECHNICAL_TRANSCRIPT).forEach { transcript ->
            val prompt = builder.build(style = transatlantic, transcript = transcript)

            assertTrue(prompt.system.content.contains("1920s"), "transatlantic system lacks the period register")
            assertTrue(
                prompt.system.content.contains("transcript's own language"),
                "transatlantic system lacks the register-of-the-language directive",
            )
            assertTrue(
                prompt.system.content.contains("the era lives in the structure"),
                "transatlantic system lacks the period-structure rule",
            )
            assertTrue(
                !prompt.system.content.contains("no sentence runs past twelve words"),
                "transatlantic system still carries the sentence cap",
            )
            assertTrue(
                !prompt.system.content.contains("NEVER write a periodic sentence"),
                "transatlantic system still bans the periodic sentence",
            )
            transatlantic.fewShots.forEach { shot ->
                assertTrue(prompt.system.content.contains(shot.raw), "transatlantic system lacks example input")
                assertTrue(prompt.system.content.contains(shot.styled), "transatlantic system lacks example output")
            }
            assertTrue(prompt.user.content.contains("<transcript>"), "transatlantic user message lacks markers")
            assertTrue(prompt.user.content.contains(transcript), "transatlantic user message lacks the transcript")
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
    fun `the same transcript through both era styles carries diction against period`() {
        // ENC-104: the contrast lives in the assembled prompts, not
        // just the catalog data. Sean's 2026-10-22 tweak: the fork
        // is register emphasis, not a syntax ban — one transcript
        // through Transatlantic carries the vernacular diction bank
        // and the period-structure rule (the written broadsheet
        // voice braided in); through The Colonel it carries the
        // periodic mandate and the ban on clipping. Both keep
        // the demarcated shape.
        val colonel = StyleCatalog.byId("the-colonel")

        val transatlanticPrompt = builder.build(style = transatlantic, transcript = TRANSCRIPT)
        assertTrue(
            transatlanticPrompt.system.content.contains("on the level"),
            "transatlantic prompt lacks the diction bank",
        )
        assertTrue(
            transatlanticPrompt.system.content.contains("the era lives in the structure"),
            "transatlantic prompt lacks the period-structure rule",
        )
        assertTrue(
            !transatlanticPrompt.system.content.contains("no sentence runs past twelve words"),
            "transatlantic prompt still carries the sentence cap",
        )
        assertTrue(
            !transatlanticPrompt.system.content.contains("NEVER write a periodic sentence"),
            "transatlantic prompt still bans the periodic swell",
        )
        assertDemarcatedShape(transatlanticPrompt, transatlantic)

        val colonelPrompt = builder.build(style = colonel, transcript = TRANSCRIPT)
        assertTrue(
            colonelPrompt.system.content.contains("the main clause arriving only at the end"),
            "the colonel prompt lacks the periodic mandate",
        )
        assertTrue(
            colonelPrompt.system.content.contains("NEVER clip a thought into wire-service fragments"),
            "the colonel prompt lacks the ban on clipping",
        )
        assertDemarcatedShape(colonelPrompt, colonel)
    }

    @Test
    fun `the same transcript through all three philosophers carries each voice's anchors`() {
        // ENC-105: the philosopher contrast rides the assembled
        // prompts — one transcript through Seneca carries the
        // Gummere anchor and the ban on system-building; through
        // Marcus Aurelius, the Long anchor and the self-address
        // permission; through Aristotle, the Jowett anchor and the
        // ban on aphoristic moralizing. All three keep the
        // demarcated shape.
        listOf(
            Triple("seneca", "We suffer more often in imagination than in reality", "NEVER build a system"),
            Triple(
                "marcus-aurelius",
                "for the soul is dyed by the thoughts",
                "his own soul",
            ),
            Triple("aristotle", "man is by nature a political animal", "NEVER moralize in aphorisms"),
        ).forEach { (id, anchor, signature) ->
            val style = StyleCatalog.byId(id)
            val prompt = builder.build(style = style, transcript = TRANSCRIPT)

            assertTrue(prompt.system.content.contains(anchor), "$id prompt lacks its quote anchor")
            assertTrue(prompt.system.content.contains(signature), "$id prompt lacks its signature clause")
            assertDemarcatedShape(prompt, style)
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
