package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocratesStyleTest {

    @Test
    fun `socrates pins its identity as an llm style with non-blank display fields`() {
        assertEquals("socrates", SocratesStyle.id)
        assertEquals("Socrates", SocratesStyle.displayName)
        assertEquals(StyleKind.LLM, SocratesStyle.kind)
        assertEquals(StyleCategory.ANCIENT_PHILOSOPHERS, SocratesStyle.category)
        assertTrue(SocratesStyle.displayName.isNotBlank(), "socrates display name is blank")
        assertTrue(SocratesStyle.description.isNotBlank(), "socrates description is blank")
        assertTrue(SocratesStyle.sample.isNotBlank(), "socrates sample is blank")
    }

    @Test
    fun `socrates instructions carry the verbatim anchors and the never-change clause`() {
        val instructions = SocratesStyle.systemInstructions

        assertTrue(
            instructions.contains("the unexamined life is not worth living"),
            "socrates lost the unexamined-life anchor",
        )
        assertTrue(
            instructions.contains("(Apology)"),
            "socrates lost the Apology attribution",
        )
        assertTrue(
            instructions.contains("he knows nothing, and thinks that he knows. I neither know nor think that I know"),
            "socrates lost the Jowett knows-nothing anchor",
        )
        assertTrue(
            instructions.contains("(Jowett)"),
            "socrates lost the Jowett attribution",
        )
        assertTrue(instructions.contains("elenchus"), "socrates names no elenchus")
        assertTrue(instructions.contains("aporia"), "socrates names no aporia")
        assertTrue(instructions.contains("transcript's own language"), "socrates lacks the own-language directive")
        assertTrue(instructions.contains("never translate"), "socrates does not forbid translating into English")
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."),
            "socrates lacks the never-change clause",
        )
    }

    @Test
    fun `the tidy knowing-nothing paraphrase never stands as a quotation`() {
        assertTrue(
            !SocratesStyle.systemInstructions.contains("I know that I know nothing"),
            "the later paraphrase is standing in the socrates instructions",
        )
        SocratesStyle.fewShots.forEach { shot ->
            assertTrue(
                !shot.styled.contains("I know that I know nothing"),
                "the later paraphrase is standing in a socrates few-shot: ${shot.styled}",
            )
        }
    }

    @Test
    fun `socrates refuses the other four voices`() {
        val instructions = SocratesStyle.systemInstructions

        assertTrue(
            instructions.contains("NEVER settle the matter with a finished system of kinds and causes — that lecture belongs to Aristotle."),
            "socrates does not refuse Aristotle's finished system",
        )
        assertTrue(
            instructions.contains("NEVER moralize in a letter's aphorisms for an audience — that performance belongs to Seneca."),
            "socrates does not refuse Seneca's letter aphorisms",
        )
        assertTrue(
            instructions.contains("NEVER command the self in clipped notebook fragments — that privacy belongs to Marcus Aurelius."),
            "socrates does not refuse Marcus's notebook fragments",
        )
        assertTrue(
            instructions.contains("NEVER ask questions whose answers are loaded in advance"),
            "socrates does not refuse Cicero's loaded questions",
        )
        assertTrue(
            instructions.contains("Cicero's rhetorical questions answer themselves"),
            "socrates does not name Cicero's self-answering questions",
        )
    }

    @Test
    fun `socrates few-shots keep every raw key fact`() {
        assertEquals(4, SocratesStyle.fewShots.size, "socrates needs exactly four few-shots")

        val evenings = SocratesStyle.fewShots.first { shot -> shot.raw.contains("scrolling my phone") }
        assertTrue(evenings.styled.contains("scrolling my phone"), "socrates dropped the phone: ${evenings.styled}")
        assertTrue(evenings.styled.contains("angry at myself"), "socrates dropped the anger: ${evenings.styled}")
        assertTrue(evenings.styled.contains("evenings"), "socrates dropped the evenings: ${evenings.styled}")

        val team = SocratesStyle.fewShots.first { shot -> shot.raw.contains("rewrite the app") }
        assertTrue(team.styled.contains("rewrite the app"), "socrates dropped the rewrite: ${team.styled}")
        assertTrue(team.styled.contains("patch it"), "socrates dropped the patch: ${team.styled}")
        assertTrue(team.styled.contains("nothing gets decided"), "socrates dropped the indecision: ${team.styled}")

        val presentation = SocratesStyle.fewShots.first { shot -> shot.raw.contains("presentation on friday") }
        assertTrue(presentation.styled.contains("presentation on Friday"), "socrates dropped Friday: ${presentation.styled}")
        assertTrue(presentation.styled.contains("bad"), "socrates dropped the feared judgment: ${presentation.styled}")

        val exercise = SocratesStyle.fewShots.first { shot -> shot.raw.contains("exercise every morning") }
        assertTrue(exercise.styled.contains("exercise every morning"), "socrates dropped the exercise: ${exercise.styled}")
        assertTrue(exercise.styled.contains("never actually start"), "socrates dropped the non-start: ${exercise.styled}")
    }

    @Test
    fun `socrates never addresses a listener the input did not name`() {
        // ENC-92 audit idiom: no second-person exception is recorded for
        // this voice, so neither the raw dictations nor the restyled
        // outputs may reach for a listener with second-person address.
        val secondPerson = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)

        SocratesStyle.fewShots.forEach { shot ->
            assertTrue(!secondPerson.containsMatchIn(shot.raw), "socrates raw reaches for a listener: ${shot.raw}")
            assertTrue(!secondPerson.containsMatchIn(shot.styled), "socrates styled reaches for a listener: ${shot.styled}")
        }
    }
}
