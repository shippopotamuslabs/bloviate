package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrillSergeantStyleTest {

    @Test
    fun `drill sergeant carries its id kind and non-blank display fields`() {
        assertEquals("drill-sergeant", DrillSergeantStyle.id)
        assertEquals(StyleKind.LLM, DrillSergeantStyle.kind)
        assertTrue(DrillSergeantStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(DrillSergeantStyle.description.isNotBlank(), "description is blank")
        assertTrue(DrillSergeantStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(DrillSergeantStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `drill sergeant instructions pin the register the contrast and the never-change clause`() {
        val instructions = DrillSergeantStyle.systemInstructions

        assertTrue(instructions.contains("clipped"), "instructions lack the clipped marker")
        assertTrue(instructions.contains("move move move"), "instructions lack the move-move-move marker")
        assertTrue(instructions.contains("countdown"), "instructions lack the countdown marker")
        assertTrue(
            instructions.contains("NEVER rant profanity"),
            "instructions lack the contrastive NEVER against belligerence",
        )
        assertTrue(
            instructions.contains("disciplined Sergeant"),
            "instructions lack the disciplined pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `drill sergeant few-shots keep every raw key fact`() {
        val casual = DrillSergeantStyle.fewShots.first { shot -> shot.raw.contains("laundry") }
        assertTrue(casual.styled.contains("ten"), "casual lost ten: ${casual.styled}")
        assertTrue(casual.styled.contains("Bed made"), "casual lost the bed: ${casual.styled}")
        assertTrue(casual.styled.lowercase().contains("laundry"), "casual lost the laundry: ${casual.styled}")

        val technical = DrillSergeantStyle.fewShots.first { shot -> shot.raw.contains("deploy") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        val keptHour = technical.styled.contains("two am") ||
            technical.styled.contains("oh-two-hundred")
        assertTrue(keptHour, "technical lost the hour: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")

        val disagreement = DrillSergeantStyle.fewShots.first { shot -> shot.raw.contains("dave") }
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
    fun `drill sergeant instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = DrillSergeantStyle.systemInstructions

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
    fun `drill sergeant records the second-person exception by design`() {
        assertTrue(
            DrillSergeantStyle.systemInstructions.contains(
                "Second-person address is permitted by design for this style",
            ),
            "instructions lack the recorded second-person exception",
        )
    }
}
