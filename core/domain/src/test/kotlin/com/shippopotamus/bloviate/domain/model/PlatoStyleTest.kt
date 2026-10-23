package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatoStyleTest {

    @Test
    fun `plato pins its identity as an llm style with non-blank display fields`() {
        assertEquals("plato", PlatoStyle.id)
        assertEquals("Plato", PlatoStyle.displayName)
        assertEquals("Shadows, Forms, and the sun outside the cave.", PlatoStyle.description)
        assertEquals("I speak of meetings, yet have I ever seen the Meeting itself?", PlatoStyle.sample)
        assertEquals(StyleKind.LLM, PlatoStyle.kind)
        assertEquals(StyleCategory.PHILOSOPHERS, PlatoStyle.category)
        assertTrue(PlatoStyle.displayName.isNotBlank(), "plato display name is blank")
        assertTrue(PlatoStyle.description.isNotBlank(), "plato description is blank")
        assertTrue(PlatoStyle.sample.isNotBlank(), "plato sample is blank")
    }

    @Test
    fun `plato instructions carry the verbatim anchors and the never-change clause`() {
        val instructions = PlatoStyle.systemInstructions

        assertTrue(
            instructions.contains("Like ourselves"),
            "plato lost the Jowett like-ourselves anchor",
        )
        assertTrue(
            instructions.contains(
                "they see only their own shadows, or the shadows of one another, " +
                    "which the fire throws on the opposite wall of the cave",
            ),
            "plato lost the Jowett cave-shadows anchor",
        )
        assertTrue(
            instructions.contains("(Jowett, Republic VII)"),
            "plato lost the Jowett Republic VII attribution",
        )
        assertTrue(
            instructions.contains("NEVER invent a quotation"),
            "plato does not forbid inventing a quotation",
        )
        assertTrue(instructions.contains("Shall we say...? — We shall."), "plato names no self-dialogue gesture")
        assertTrue(instructions.contains("Form itself"), "plato names no ascent to the Form itself")
        assertTrue(instructions.contains("ONE myth or cave image"), "plato names no single-myth limit")
        assertTrue(instructions.contains("transcript's own language"), "plato lacks the own-language directive")
        assertTrue(instructions.contains("never translate"), "plato does not forbid translating into English")
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."),
            "plato lacks the never-change clause",
        )
    }

    @Test
    fun `plato answers where socrates ends and refuses the other four voices`() {
        val instructions = PlatoStyle.systemInstructions

        assertTrue(
            instructions.contains("Plato ANSWERS with the Form and the myth where Socrates ends in aporia"),
            "plato does not answer where Socrates ends in aporia",
        )
        assertTrue(
            instructions.contains("NEVER leave the matter merely unexamined or end in aporia — that confessed bewilderment belongs to Socrates."),
            "plato does not refuse Socrates' aporia",
        )
        assertTrue(
            instructions.contains("NEVER sort the matter into kinds and causes — that lecturing taxonomy belongs to Aristotle."),
            "plato does not refuse Aristotle's kinds and causes",
        )
        assertTrue(
            instructions.contains("NEVER moralize in a letter's aphorisms for an audience — that performance belongs to Seneca."),
            "plato does not refuse Seneca's letter aphorisms",
        )
        assertTrue(
            instructions.contains("NEVER command the self in a private notebook — that privacy belongs to Marcus Aurelius."),
            "plato does not refuse Marcus's notebook commands",
        )
        assertTrue(
            instructions.contains("NEVER shatter the thought into clipped fragments"),
            "plato does not refuse clipped fragments",
        )
    }

    @Test
    fun `plato few-shots keep every raw key fact`() {
        assertEquals(4, PlatoStyle.fewShots.size, "plato needs exactly four few-shots")

        val evenings = PlatoStyle.fewShots.first { shot -> shot.raw.contains("scrolling my phone") }
        assertTrue(evenings.styled.contains("scrolling"), "plato dropped the scrolling: ${evenings.styled}")
        assertTrue(evenings.styled.contains("evenings"), "plato dropped the evenings: ${evenings.styled}")
        assertTrue(evenings.styled.contains("shadows"), "plato dropped the cave shadows: ${evenings.styled}")

        val team = PlatoStyle.fewShots.first { shot -> shot.raw.contains("rewrite the app") }
        assertTrue(team.styled.contains("rewrite the app"), "plato dropped the rewrite: ${team.styled}")
        assertTrue(team.styled.contains("patch it"), "plato dropped the patch: ${team.styled}")
        assertTrue(team.styled.contains("nothing gets decided"), "plato dropped the indecision: ${team.styled}")

        val presentation = PlatoStyle.fewShots.first { shot -> shot.raw.contains("presentation on friday") }
        assertTrue(presentation.styled.contains("presentation on Friday"), "plato dropped Friday: ${presentation.styled}")
        assertTrue(presentation.styled.contains("might think it is bad"), "plato dropped the feared judgment: ${presentation.styled}")

        val exercise = PlatoStyle.fewShots.first { shot -> shot.raw.contains("exercise every morning") }
        assertTrue(exercise.styled.contains("exercise every morning"), "plato dropped the exercise: ${exercise.styled}")
        assertTrue(exercise.styled.contains("never actually start"), "plato dropped the non-start: ${exercise.styled}")
    }

    @Test
    fun `plato never addresses a listener the input did not name`() {
        // ENC-92 audit idiom: no second-person exception is recorded for
        // this voice, so neither the raw dictations nor the restyled
        // outputs may reach for a listener with second-person address.
        val secondPerson = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)

        PlatoStyle.fewShots.forEach { shot ->
            assertTrue(!secondPerson.containsMatchIn(shot.raw), "plato raw reaches for a listener: ${shot.raw}")
            assertTrue(!secondPerson.containsMatchIn(shot.styled), "plato styled reaches for a listener: ${shot.styled}")
        }
    }
}
