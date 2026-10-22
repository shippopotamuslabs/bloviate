package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoirDetectiveStyleTest {

    @Test
    fun `noir detective carries its id kind and non-blank display fields`() {
        assertEquals("noir-detective", NoirDetectiveStyle.id)
        assertEquals(StyleKind.LLM, NoirDetectiveStyle.kind)
        assertTrue(NoirDetectiveStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(NoirDetectiveStyle.description.isNotBlank(), "description is blank")
        assertTrue(NoirDetectiveStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(NoirDetectiveStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `noir detective instructions pin the register the contrast and the never-change clause`() {
        val instructions = NoirDetectiveStyle.systemInstructions

        assertTrue(instructions.contains("cheap whiskey"), "instructions lack the simile marker")
        assertTrue(instructions.contains("laconic"), "instructions lack the laconic marker")
        assertTrue(instructions.lowercase().contains("world-weary"), "instructions lack the world-weary marker")
        assertTrue(instructions.contains("first-person"), "instructions lack the first-person marker")
        assertTrue(
            instructions.contains("NEVER rant, shout, or swear up a storm"),
            "instructions lack the contrastive NEVER against loud belligerence",
        )
        assertTrue(
            instructions.contains("cool laconic detective"),
            "instructions lack the cool-laconic pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `noir detective few-shots keep every raw key fact`() {
        val casual = NoirDetectiveStyle.fewShots.first { shot -> shot.raw.contains("maria") }
        assertTrue(casual.styled.contains("Maria"), "casual lost Maria: ${casual.styled}")
        assertTrue(casual.styled.contains("corner store"), "casual lost the corner store: ${casual.styled}")
        assertTrue(casual.styled.contains("Tuesday"), "casual lost Tuesday: ${casual.styled}")
        assertTrue(casual.styled.contains("twenty-minute"), "casual lost the twenty minutes: ${casual.styled}")
        assertTrue(casual.styled.contains("Rex"), "casual lost Rex: ${casual.styled}")

        val technical = NoirDetectiveStyle.fewShots.first { shot -> shot.raw.contains("deploy") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("two am"), "technical lost the hour: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = NoirDetectiveStyle.fewShots.first { shot -> shot.raw.contains("dave") }
        assertTrue(disagreement.styled.contains("Dave"), "disagreement lost Dave: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("Forty thousand dollars"),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("March fifteenth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `noir detective instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = NoirDetectiveStyle.systemInstructions

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
    fun `no noir detective few-shot output addresses a listener the input did not`() {
        NoirDetectiveStyle.fewShots.forEach { shot ->
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
