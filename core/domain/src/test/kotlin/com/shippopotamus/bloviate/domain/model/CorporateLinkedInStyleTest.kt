package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorporateLinkedInStyleTest {

    @Test
    fun `corporate linkedin carries its id kind and non-blank display fields`() {
        assertEquals("corporate-linkedin", CorporateLinkedInStyle.id)
        assertEquals(StyleKind.LLM, CorporateLinkedInStyle.kind)
        assertTrue(CorporateLinkedInStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(CorporateLinkedInStyle.description.isNotBlank(), "description is blank")
        assertTrue(CorporateLinkedInStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(CorporateLinkedInStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `corporate linkedin instructions pin the register the contrast and the never-change clause`() {
        val instructions = CorporateLinkedInStyle.systemInstructions

        assertTrue(instructions.contains("humble-brag"), "instructions lack the humble-brag marker")
        assertTrue(instructions.contains("hashtag"), "instructions lack the hashtag marker")
        assertTrue(instructions.contains("Agree?"), "instructions lack the Agree marker")
        assertTrue(
            instructions.lowercase().contains("first person"),
            "instructions lack the first-person marker",
        )
        assertTrue(
            instructions.contains("NEVER write a dry status memo"),
            "instructions lack the contrastive NEVER against plain Professional",
        )
        assertTrue(
            instructions.contains("belongs to Professional"),
            "instructions lack the nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `corporate linkedin few-shots keep every raw key fact`() {
        val casual = CorporateLinkedInStyle.fewShots.first { shot -> shot.raw.contains("coffee") }
        assertTrue(casual.styled.contains("coffee"), "casual lost coffee: ${casual.styled}")
        assertTrue(casual.styled.contains("shirt"), "casual lost the shirt: ${casual.styled}")
        assertTrue(casual.styled.contains("8:15"), "casual lost the bus time: ${casual.styled}")

        val technical = CorporateLinkedInStyle.fewShots.first { shot -> shot.raw.contains("deploy") }
        assertTrue(technical.styled.contains("Acme Corp"), "technical lost Acme Corp: ${technical.styled}")
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("Friday"), "technical lost Friday: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = CorporateLinkedInStyle.fewShots.first { shot -> shot.raw.contains("dave") }
        assertTrue(disagreement.styled.contains("Dave"), "disagreement lost Dave: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.lowercase().contains("forty thousand dollar"),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.lowercase().contains("march fifteenth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `corporate linkedin instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = CorporateLinkedInStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("line break after every sentence"),
            "instructions lack the line-break architecture",
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
    fun `no corporate linkedin few-shot output addresses a listener the input did not`() {
        CorporateLinkedInStyle.fewShots.forEach { shot ->
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
