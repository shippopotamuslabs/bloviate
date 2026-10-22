package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CiceroStyleTest {

    @Test
    fun `cicero pins its identity as an llm style with non-blank display fields`() {
        assertEquals("cicero", CiceroStyle.id)
        assertEquals(StyleKind.LLM, CiceroStyle.kind)
        assertTrue(CiceroStyle.displayName.isNotBlank(), "cicero display name is blank")
        assertTrue(CiceroStyle.description.isNotBlank(), "cicero description is blank")
        assertTrue(CiceroStyle.sample.isNotBlank(), "cicero sample is blank")
    }

    @Test
    fun `cicero instructions carry the register markers the contrastive never and the never-change clause`() {
        val instructions = CiceroStyle.systemInstructions

        assertTrue(instructions.contains("tricolons"), "cicero names no tricolons")
        assertTrue(instructions.contains("anaphora"), "cicero names no anaphora")
        assertTrue(instructions.contains("rhetorical questions"), "cicero names no rhetorical questions")
        assertTrue(instructions.contains("periodic"), "cicero names no periodic architecture")
        assertTrue(instructions.contains("transcript's own language"), "cicero lacks the own-language directive")
        assertTrue(instructions.contains("never translate"), "cicero does not forbid translating into English")
        assertTrue(instructions.contains("Aristotle"), "cicero never contrasts against Aristotle")
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."),
            "cicero lacks the never-change clause",
        )
    }

    @Test
    fun `cicero few-shots keep every raw key fact`() {
        assertTrue(CiceroStyle.fewShots.size >= 3, "cicero needs at least three few-shots")

        val casual = CiceroStyle.fewShots.first { shot -> shot.raw.contains("Fifth street", ignoreCase = true) }
        assertTrue(casual.styled.contains("Fifth Street"), "cicero renamed the street: ${casual.styled}")
        assertTrue(casual.styled.contains("milk"), "cicero dropped the milk: ${casual.styled}")
        assertTrue(casual.styled.contains("bread"), "cicero dropped the bread: ${casual.styled}")
        assertTrue(casual.styled.contains("eggs"), "cicero dropped the eggs: ${casual.styled}")
        assertTrue(casual.styled.contains("twelve dollars"), "cicero dropped the price: ${casual.styled}")

        val technical = CiceroStyle.fewShots.first { shot -> shot.raw.contains("database") }
        assertTrue(technical.styled.contains("third step"), "cicero lost the step count: ${technical.styled}")
        assertTrue(technical.styled.contains("database migration"), "cicero lost the migration: ${technical.styled}")
        assertTrue(technical.styled.contains("two hours of logs"), "cicero lost the logs: ${technical.styled}")

        val disagreement = CiceroStyle.fewShots.first { shot -> shot.raw.contains("fifty thousand") }
        assertTrue(disagreement.styled.contains("fifty thousand dollars"), "cicero dropped the cost: ${disagreement.styled}")
        assertTrue(disagreement.styled.contains("six weeks"), "cicero dropped the timeline: ${disagreement.styled}")
    }

    @Test
    fun `cicero builds its architecture from tricolon anaphora rhetorical questions and periodic sentences`() {
        // The ticket's measurable architecture: tricolons, anaphora, and
        // rhetorical questions raised on periodic Latin architecture — and
        // every styled shot performs at least one of its moves.
        val instructions = CiceroStyle.systemInstructions

        assertTrue(instructions.contains("tricolon"), "cicero architecture names no tricolon")
        assertTrue(instructions.contains("anaphora"), "cicero architecture names no anaphora")
        assertTrue(instructions.contains("rhetorical question"), "cicero architecture names no rhetorical questions")
        assertTrue(instructions.contains("periodic"), "cicero architecture names no periodic sentences")

        val moves = CiceroStyle.fewShots.all { shot ->
            shot.styled.contains("?") || shot.styled.contains(";")
        }
        assertTrue(moves, "a cicero shot performs none of its signature moves")
    }

    @Test
    fun `cicero never addresses a listener the input did not name`() {
        // No second-person exception is recorded for this voice, so neither
        // the raw dictations nor the restyled outputs may reach for a
        // listener with second-person address.
        val secondPerson = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)

        CiceroStyle.fewShots.forEach { shot ->
            assertTrue(!secondPerson.containsMatchIn(shot.raw), "cicero raw reaches for a listener: ${shot.raw}")
            assertTrue(!secondPerson.containsMatchIn(shot.styled), "cicero styled reaches for a listener: ${shot.styled}")
        }
    }
}
