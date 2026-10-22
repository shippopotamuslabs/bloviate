package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegalContractStyleTest {

    @Test
    fun `legal contract carries its id kind and non-blank display fields`() {
        assertEquals("legal-contract", LegalContractStyle.id)
        assertEquals(StyleKind.LLM, LegalContractStyle.kind)
        assertTrue(LegalContractStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(LegalContractStyle.description.isNotBlank(), "description is blank")
        assertTrue(LegalContractStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(LegalContractStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `legal contract instructions pin the register the contrast and the never-change clause`() {
        val instructions = LegalContractStyle.systemInstructions

        assertTrue(instructions.contains("WHEREAS"), "instructions lack the WHEREAS marker")
        assertTrue(instructions.contains("hereinafter"), "instructions lack the hereinafter marker")
        assertTrue(instructions.contains("defined terms"), "instructions lack the defined-terms marker")
        assertTrue(instructions.contains("obligation"), "instructions lack the obligation marker")
        assertTrue(
            instructions.contains("NEVER write a courtly letter of gracious sentiment"),
            "instructions lack the contrastive NEVER against courtly sentiment",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `legal contract few-shots keep every raw key fact`() {
        val casual = LegalContractStyle.fewShots.first { shot -> shot.raw.contains("milk") }
        assertTrue(casual.styled.contains("milk"), "casual lost milk: ${casual.styled}")
        assertTrue(casual.styled.contains("eggs"), "casual lost eggs: ${casual.styled}")
        assertTrue(casual.styled.contains("corner store"), "casual lost the corner store: ${casual.styled}")
        assertTrue(casual.styled.contains("Tuesday"), "casual lost Tuesday: ${casual.styled}")
        assertTrue(casual.styled.contains("three bucks"), "casual lost three bucks: ${casual.styled}")

        val technical = LegalContractStyle.fewShots.first { shot -> shot.raw.contains("backup") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("two am"), "technical lost the hour: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours"),
            "technical lost the two hours: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = LegalContractStyle.fewShots.first { shot -> shot.raw.contains("dave") }
        assertTrue(disagreement.styled.contains("Dave"), "disagreement lost Dave: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("forty thousand dollars"),
            "disagreement lost the sum: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("March fifteenth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `legal contract instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = LegalContractStyle.systemInstructions

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
            instructions.contains("No period slurs or bigotry ever"),
            "instructions lack the no-slur boundary",
        )
    }

    @Test
    fun `no legal contract few-shot output addresses a listener the input did not`() {
        LegalContractStyle.fewShots.forEach { shot ->
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
