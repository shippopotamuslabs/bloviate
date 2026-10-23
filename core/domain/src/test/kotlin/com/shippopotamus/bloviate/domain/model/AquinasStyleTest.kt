package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AquinasStyleTest {

    @Test
    fun `aquinas pins its identity as an llm style with non-blank display fields`() {
        assertEquals("aquinas", AquinasStyle.id)
        assertEquals("Aquinas", AquinasStyle.displayName)
        assertEquals("Objections heard, authorities weighed, the question settled.", AquinasStyle.description)
        assertEquals("It seems the standup is useless. On the contrary…", AquinasStyle.sample)
        assertEquals(StyleKind.LLM, AquinasStyle.kind)
        assertEquals(StyleCategory.PHILOSOPHERS, AquinasStyle.category)
        assertTrue(AquinasStyle.displayName.isNotBlank(), "aquinas display name is blank")
        assertTrue(AquinasStyle.description.isNotBlank(), "aquinas description is blank")
        assertTrue(AquinasStyle.sample.isNotBlank(), "aquinas sample is blank")
    }

    @Test
    fun `aquinas instructions carry the quaestio markers and the never-change clause`() {
        val instructions = AquinasStyle.systemInstructions

        assertTrue(instructions.contains("Whether"), "aquinas names no question")
        assertTrue(instructions.contains("It seems that..."), "aquinas names no objection marker")
        assertTrue(instructions.contains("On the contrary..."), "aquinas names no contrary marker")
        assertTrue(instructions.contains("I answer that..."), "aquinas names no answer marker")
        assertTrue(
            instructions.contains("are structural markers of the article, not quotations"),
            "aquinas does not mark its markers as structure, not quotation",
        )
        assertTrue(instructions.contains("settles"), "aquinas names no settled ending")
        assertTrue(instructions.contains("transcript's own language"), "aquinas lacks the own-language directive")
        assertTrue(instructions.contains("never translate"), "aquinas does not forbid translating into English")
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."),
            "aquinas lacks the never-change clause",
        )
    }

    @Test
    fun `aquinas settles where socrates ends and refuses the other four voices`() {
        val instructions = AquinasStyle.systemInstructions

        assertTrue(
            instructions.contains("NEVER end in confessed ignorance or aporia — that open ending belongs to Socrates"),
            "aquinas does not refuse Socrates' aporia",
        )
        assertTrue(
            instructions.contains("NEVER rise to Forms through dialogue or myth — that ascent belongs to Plato"),
            "aquinas does not refuse Plato's Forms and myth",
        )
        assertTrue(
            instructions.contains("NEVER lecture in kinds and causes for its own sake — that taxonomy belongs to Aristotle"),
            "aquinas does not refuse Aristotle's taxonomy",
        )
        assertTrue(
            instructions.contains("distinctions here serve only to reconcile the objections"),
            "aquinas does not bound distinctions to reconciling objections",
        )
        assertTrue(
            instructions.contains("NEVER moralize in a letter's aphorisms for an audience — that performance belongs to Seneca"),
            "aquinas does not refuse Seneca's letter aphorisms",
        )
        assertTrue(
            instructions.contains("NEVER command the self in a private notebook — that privacy belongs to Marcus Aurelius"),
            "aquinas does not refuse Marcus's notebook commands",
        )
    }

    @Test
    fun `aquinas few-shots keep every raw key fact`() {
        assertEquals(4, AquinasStyle.fewShots.size, "aquinas needs exactly four few-shots")

        val evenings = AquinasStyle.fewShots.first { shot -> shot.raw.contains("scrolling my phone") }
        assertTrue(evenings.styled.contains("scrolling my phone"), "aquinas dropped the phone: ${evenings.styled}")
        assertTrue(evenings.styled.contains("evenings"), "aquinas dropped the evenings: ${evenings.styled}")
        assertTrue(evenings.styled.contains("angry at myself"), "aquinas dropped the anger: ${evenings.styled}")

        val team = AquinasStyle.fewShots.first { shot -> shot.raw.contains("rewrite the app") }
        assertTrue(team.styled.contains("rewrite the app"), "aquinas dropped the rewrite: ${team.styled}")
        assertTrue(team.styled.contains("patch it"), "aquinas dropped the patch: ${team.styled}")
        assertTrue(team.styled.contains("nothing gets decided"), "aquinas dropped the indecision: ${team.styled}")

        val presentation = AquinasStyle.fewShots.first { shot -> shot.raw.contains("presentation on friday") }
        assertTrue(presentation.styled.contains("Friday"), "aquinas dropped Friday: ${presentation.styled}")
        assertTrue(presentation.styled.contains("judgment"), "aquinas dropped the judgment: ${presentation.styled}")

        val exercise = AquinasStyle.fewShots.first { shot -> shot.raw.contains("exercise every morning") }
        assertTrue(exercise.styled.contains("exercise every morning"), "aquinas dropped the exercise: ${exercise.styled}")
        assertTrue(exercise.styled.contains("never actually start"), "aquinas dropped the non-start: ${exercise.styled}")
    }

    @Test
    fun `aquinas few-shots follow the quaestio article and settle`() {
        AquinasStyle.fewShots.forEach { shot ->
            assertTrue(shot.styled.contains("Whether"), "aquinas opens no question: ${shot.styled}")
            assertTrue(shot.styled.contains("It seems"), "aquinas hears no objection: ${shot.styled}")
            assertTrue(shot.styled.contains("On the contrary"), "aquinas weighs no contrary: ${shot.styled}")
            assertTrue(shot.styled.contains("I answer that"), "aquinas gives no answer: ${shot.styled}")
        }
    }

    @Test
    fun `aquinas never addresses a listener the input did not name`() {
        // ENC-92 audit idiom: no second-person exception is recorded for
        // this voice, so neither the raw dictations nor the restyled
        // outputs may reach for a listener with second-person address.
        val secondPerson = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)

        AquinasStyle.fewShots.forEach { shot ->
            assertTrue(!secondPerson.containsMatchIn(shot.raw), "aquinas raw reaches for a listener: ${shot.raw}")
            assertTrue(!secondPerson.containsMatchIn(shot.styled), "aquinas styled reaches for a listener: ${shot.styled}")
        }
    }
}
