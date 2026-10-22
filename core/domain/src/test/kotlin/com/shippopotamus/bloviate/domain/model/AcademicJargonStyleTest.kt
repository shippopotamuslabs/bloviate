package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcademicJargonStyleTest {

    @Test
    fun `academic jargon carries its id kind and non-blank display fields`() {
        assertEquals("academic-jargon", AcademicJargonStyle.id)
        assertEquals(StyleKind.LLM, AcademicJargonStyle.kind)
        assertTrue(AcademicJargonStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(AcademicJargonStyle.description.isNotBlank(), "description is blank")
        assertTrue(AcademicJargonStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(AcademicJargonStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `academic jargon instructions pin the register the contrast and the never-change clause`() {
        val instructions = AcademicJargonStyle.systemInstructions

        assertTrue(instructions.contains("problematises"), "instructions lack the problematises marker")
        assertTrue(instructions.contains("interrogates"), "instructions lack the interrogates marker")
        assertTrue(instructions.contains("sites of"), "instructions lack the sites-of marker")
        assertTrue(instructions.contains("discourses"), "instructions lack the discourses marker")
        assertTrue(
            instructions.contains("pushes the never-change guard hardest"),
            "instructions lack the fidelity warning",
        )
        assertTrue(
            instructions.contains("NEVER collapse into plain clear technical prose"),
            "instructions lack the contrastive NEVER against plain clarity",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `academic jargon few-shots keep every raw key fact`() {
        val casual = AcademicJargonStyle.fewShots.first { shot -> shot.raw.contains("priya") }
        assertTrue(casual.styled.contains("Priya"), "casual lost Priya: ${casual.styled}")
        assertTrue(casual.styled.contains("Tuesday"), "casual lost Tuesday: ${casual.styled}")
        assertTrue(casual.styled.contains("Rex"), "casual lost Rex: ${casual.styled}")
        assertTrue(casual.styled.contains("twenty-minute"), "casual lost the twenty minutes: ${casual.styled}")
        assertTrue(casual.styled.contains("park"), "casual lost the park: ${casual.styled}")

        val technical = AcademicJargonStyle.fewShots.first { shot -> shot.raw.contains("backup script") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = AcademicJargonStyle.fewShots.first { shot -> shot.raw.contains("dave") }
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
    fun `academic jargon instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = AcademicJargonStyle.systemInstructions

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
    fun `no academic jargon few-shot output addresses a listener the input did not`() {
        AcademicJargonStyle.fewShots.forEach { shot ->
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
