package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TownCrierStyleTest {

    @Test
    fun `town crier carries its id kind and non-blank display fields`() {
        assertEquals("town-crier", TownCrierStyle.id)
        assertEquals(StyleKind.LLM, TownCrierStyle.kind)
        assertTrue(TownCrierStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(TownCrierStyle.description.isNotBlank(), "description is blank")
        assertTrue(TownCrierStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(TownCrierStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `town crier instructions pin the register the contrast and the never-change clause`() {
        val instructions = TownCrierStyle.systemInstructions

        assertTrue(instructions.contains("proclamation"), "instructions lack the proclamation marker")
        assertTrue(
            instructions.contains("Dates, announcements, and lists get cried"),
            "instructions lack the cried-dates-and-lists marker",
        )
        assertTrue(
            instructions.contains("NEVER open with 'Hear ye' or 'Oyez'"),
            "instructions lack the hear-ye removal pin",
        )
        assertTrue(
            instructions.contains("never frame the output as a crier"),
            "instructions lack the no-crowd-framing pin",
        )
        assertTrue(
            instructions.contains("that haste belongs to the newsroom"),
            "instructions lack the contrastive NEVER against the newsroom",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `town crier few-shots keep every raw key fact`() {
        val casual = TownCrierStyle.fewShots.first { shot -> shot.raw.contains("maria") }
        assertTrue(casual.styled.contains("Maria"), "casual lost Maria: ${casual.styled}")
        assertTrue(casual.styled.contains("corner store"), "casual lost the corner store: ${casual.styled}")
        assertTrue(casual.styled.contains("Tuesday"), "casual lost Tuesday: ${casual.styled}")
        assertTrue(
            casual.styled.contains("farmers market"),
            "casual lost the farmers market: ${casual.styled}",
        )
        assertTrue(
            casual.styled.contains("two jars of honey"),
            "casual lost the honey: ${casual.styled}",
        )

        val technical = TownCrierStyle.fewShots.first { shot -> shot.raw.contains("deploy") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("two am"), "technical lost the hour: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = TownCrierStyle.fewShots.first { shot -> shot.raw.contains("dave") }
        assertTrue(disagreement.styled.contains("Dave"), "disagreement lost Dave: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("forty thousand dollars"),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("March fifteenth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `town crier instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = TownCrierStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("cadence only"),
            "instructions lack the cadence-only measurable pin",
        )
        assertTrue(
            instructions.contains("transcript's own language"),
            "instructions lack the register-of-the-language directive",
        )
        assertTrue(
            instructions.contains("never translate"),
            "instructions do not forbid translating into English",
        )
        assertTrue(
            instructions.contains("speaker's own words restyled"),
            "instructions lack the speaker-invariant clause",
        )
        assertTrue(
            instructions.contains("No period slurs or bigotry ever"),
            "instructions lack the no-slur boundary",
        )
    }

    @Test
    fun `no town crier few-shot output addresses a listener the input did not`() {
        TownCrierStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
    }

    companion object {
        private val SECOND_PERSON = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
    }
}
