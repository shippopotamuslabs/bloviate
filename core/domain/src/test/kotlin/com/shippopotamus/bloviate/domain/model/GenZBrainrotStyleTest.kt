package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenZBrainrotStyleTest {

    @Test
    fun `gen z brainrot carries its id kind and non-blank display fields`() {
        assertEquals("gen-z-brainrot", GenZBrainrotStyle.id)
        assertEquals(StyleKind.LLM, GenZBrainrotStyle.kind)
        assertTrue(GenZBrainrotStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(GenZBrainrotStyle.description.isNotBlank(), "description is blank")
        assertTrue(GenZBrainrotStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(GenZBrainrotStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `gen z brainrot instructions pin the register the contrast and the never-change clause`() {
        val instructions = GenZBrainrotStyle.systemInstructions

        assertTrue(instructions.contains("no cap"), "instructions lack the no-cap marker")
        assertTrue(instructions.contains("lowercase"), "instructions lack the lowercase marker")
        assertTrue(instructions.contains("lore"), "instructions lack the lore marker")
        assertTrue(instructions.contains("aura"), "instructions lack the aura marker")
        assertTrue(instructions.contains("slang rotates"), "instructions lack the slang-rotation pin")
        assertTrue(instructions.contains("intelligibility"), "instructions lack the intelligibility floor")
        assertTrue(
            instructions.contains("belongs to Edgelord"),
            "instructions lack the contrastive NEVER naming Edgelord",
        )
        assertTrue(
            instructions.contains("screaming caps"),
            "instructions lack the screaming-caps contrast pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `gen z brainrot few-shots keep every raw key fact`() {
        val casual = GenZBrainrotStyle.fewShots.first { shot -> shot.raw.contains("priya") }
        assertTrue(casual.styled.contains("priya", ignoreCase = true), "casual lost Priya: ${casual.styled}")
        assertTrue(casual.styled.contains("saturday", ignoreCase = true), "casual lost Saturday: ${casual.styled}")
        assertTrue(
            casual.styled.contains("three candles"),
            "casual lost the three candles: ${casual.styled}",
        )
        assertTrue(casual.styled.contains("popcorn"), "casual lost the popcorn: ${casual.styled}")

        val technical = GenZBrainrotStyle.fewShots.first { shot -> shot.raw.contains("deploy") }
        assertTrue(technical.styled.contains("step four"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("one am"), "technical lost the hour: ${technical.styled}")
        assertTrue(
            technical.styled.contains("ninety minutes"),
            "technical lost the minutes: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server twelve"), "technical lost server twelve: ${technical.styled}")

        val disagreement = GenZBrainrotStyle.fewShots.first { shot -> shot.raw.contains("marcus") }
        assertTrue(
            disagreement.styled.contains("marcus", ignoreCase = true),
            "disagreement lost Marcus: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("sixty thousand dollars"),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("june ninth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `gen z brainrot instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = GenZBrainrotStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
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
            instructions.contains("No slurs or bigotry ever"),
            "instructions lack the no-slur boundary",
        )
    }

    @Test
    fun `no gen z brainrot few-shot output addresses a listener the input did not`() {
        GenZBrainrotStyle.fewShots.forEach { shot ->
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
