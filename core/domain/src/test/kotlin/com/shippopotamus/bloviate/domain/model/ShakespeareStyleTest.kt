package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShakespeareStyleTest {

    @Test
    fun `shakespeare is an llm style with non-blank display fields`() {
        // ENC-171: id 'shakespeare', LLM kind, human display name, one-line
        // description, and a mundane in-voice sample.
        assertEquals("shakespeare", ShakespeareStyle.id)
        assertEquals(StyleKind.LLM, ShakespeareStyle.kind)
        assertTrue(ShakespeareStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(ShakespeareStyle.description.isNotBlank(), "description is blank")
        assertTrue(ShakespeareStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(ShakespeareStyle.fewShots.size >= 3, "fewer than 3 few-shots")
        ShakespeareStyle.fewShots.forEach { shot ->
            assertTrue(shot.raw.isNotBlank(), "a few-shot raw is blank")
            assertTrue(shot.styled.isNotBlank(), "a few-shot styled is blank")
        }
    }

    @Test
    fun `shakespeare instructions pin the early modern grammar rules`() {
        // ENC-171 measurable: thou subject, thee object, thy/thine possessive,
        // -est endings — pinned as literal instruction content.
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("thou"),
            "instructions never name thou",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("thee"),
            "instructions never name thee",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("thy"),
            "instructions never name thy",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("-est"),
            "instructions never name the -est endings",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("thee-thou soup"),
            "instructions never forbid thee-thou soup",
        )
    }

    @Test
    fun `shakespeare instructions carry the contrastive never and the never-change clause`() {
        // ENC-171: contrastive NEVER against the nearest neighbour (the King
        // James pulpit cadence) plus the shared never-change clause verbatim.
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("NEVER"),
            "instructions carry no contrastive NEVER",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("King James"),
            "instructions name no nearest neighbour",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("transcript's own language"),
            "instructions lack the register-of-the-language directive",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("never translate"),
            "instructions do not forbid translating into English",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("never a reply"),
            "instructions lack the speaker-invariant clause",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("no period slur"),
            "instructions lack the period-slur prohibition",
        )
    }

    @Test
    fun `shakespeare instructions pin the ticket architecture`() {
        // ENC-171 measurable architecture: blank-verse music in prose,
        // soliloquy intimacy, metaphor density, short attributed anchors.
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("blank-verse"),
            "instructions never name blank-verse music",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("Soliloquy"),
            "instructions never name soliloquy intimacy",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("metaphor"),
            "instructions never name metaphor density",
        )
        assertTrue(
            ShakespeareStyle.systemInstructions.contains("(Hamlet)"),
            "instructions carry no attributed anchor",
        )
    }

    @Test
    fun `shakespeare few-shots keep names numbers and objects intact`() {
        // ENC-171: every styled shot keeps its raw key facts — styling the
        // telling, never the truth.
        val casual = ShakespeareStyle.fewShots.first { shot -> shot.raw.contains("maya") }
        assertTrue(casual.styled.contains("Maya"), "casual shot lost Maya: ${casual.styled}")
        assertTrue(casual.styled.contains("coffee"), "casual shot lost the coffee: ${casual.styled}")
        assertTrue(casual.styled.contains("park"), "casual shot lost the park: ${casual.styled}")
        assertTrue(casual.styled.contains("an hour"), "casual shot lost the hour: ${casual.styled}")

        val technical = ShakespeareStyle.fewShots.first { shot -> shot.raw.contains("database") }
        assertTrue(
            technical.styled.contains("step three"),
            "technical shot lost the step count: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("database migration"),
            "technical shot lost the migration: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("half the disk"),
            "technical shot lost the disk: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical shot lost the logs: ${technical.styled}",
        )

        val emotional = ShakespeareStyle.fewShots.first { shot -> shot.raw.contains("marcus") }
        assertTrue(emotional.styled.contains("Marcus"), "emotional shot lost Marcus: ${emotional.styled}")
        assertTrue(emotional.styled.contains("Friday"), "emotional shot lost Friday: ${emotional.styled}")
        assertTrue(
            emotional.styled.contains("deadline"),
            "emotional shot lost the deadline: ${emotional.styled}",
        )
    }

    @Test
    fun `shakespeare styled shots never address a listener the transcript did not`() {
        // No second-person exception is recorded for this style, so where the
        // raw has no you/your, the styled side must have none either.
        val secondPerson = Regex("\\byou\\b|\\byour\\b")
        ShakespeareStyle.fewShots.forEach { shot ->
            assertFalse(
                secondPerson.containsMatchIn(shot.raw),
                "raw unexpectedly addresses a listener: ${shot.raw}",
            )
            assertFalse(
                secondPerson.containsMatchIn(shot.styled),
                "styled addresses a listener the transcript did not: ${shot.styled}",
            )
        }
    }
}
