package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BelligerentStyleTest {

    @Test
    fun `belligerent carries its id kind and non-blank display fields`() {
        assertEquals("belligerent", BelligerentStyle.id)
        assertEquals(StyleKind.LLM, BelligerentStyle.kind)
        assertTrue(BelligerentStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(BelligerentStyle.description.isNotBlank(), "description is blank")
        assertTrue(BelligerentStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(BelligerentStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `belligerent instructions pin the register the contrasts and the never-change clause`() {
        val instructions = BelligerentStyle.systemInstructions

        assertTrue(instructions.contains("profane"), "instructions lack the profane marker")
        assertTrue(instructions.contains("bellowing"), "instructions lack the bellowing marker")
        assertTrue(instructions.contains("casino"), "instructions lack the casino marker")
        assertTrue(instructions.contains("Super Bowl"), "instructions lack the Super Bowl marker")
        assertTrue(instructions.contains("refs"), "instructions lack the refs marker")
        assertTrue(instructions.contains("dealer"), "instructions lack the dealer marker")
        assertTrue(
            instructions.contains("zero self-awareness"),
            "instructions lack the zero self-awareness marker",
        )
        assertTrue(
            instructions.contains("do NOT sanitise"),
            "instructions lack the do-not-sanitise register pin",
        )
        assertTrue(
            instructions.contains("NEVER bark clipped"),
            "instructions lack the contrastive NEVER against the Drill Sergeant",
        )
        assertTrue(
            instructions.contains("NEVER go chaotic"),
            "instructions lack the contrastive NEVER against the Edgelord",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `belligerent few-shots keep every raw key fact`() {
        val casual = BelligerentStyle.fewShots.first { shot -> shot.raw.contains("towed") }
        assertTrue(casual.styled.contains("Fifth Street"), "casual lost Fifth Street: ${casual.styled}")
        assertTrue(casual.styled.contains("nine"), "casual lost nine: ${casual.styled}")
        assertTrue(casual.styled.contains("two"), "casual lost two hours: ${casual.styled}")

        val technical = BelligerentStyle.fewShots.first { shot -> shot.raw.contains("backup") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")
        assertTrue(technical.styled.contains("two am"), "technical lost two am: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )

        val disagreement = BelligerentStyle.fewShots.first { shot -> shot.raw.contains("dave") }
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
    fun `belligerent instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = BelligerentStyle.systemInstructions

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
    fun `belligerent records the second-person abuse exception by design`() {
        assertTrue(
            BelligerentStyle.systemInstructions.contains(
                "Second-person abuse is permitted by design for this style",
            ),
            "instructions lack the recorded second-person exception",
        )
        assertTrue(
            BelligerentStyle.systemInstructions.contains("never framed as a reply to the app user"),
            "instructions lack the never-a-reply-to-user bound on the exception",
        )
    }
}
