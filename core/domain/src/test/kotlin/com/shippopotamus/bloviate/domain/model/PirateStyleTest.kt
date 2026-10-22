package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PirateStyleTest {

    @Test
    fun `pirate carries its id kind and non-blank display fields`() {
        assertEquals("pirate", PirateStyle.id)
        assertEquals(StyleKind.LLM, PirateStyle.kind)
        assertTrue(PirateStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(PirateStyle.description.isNotBlank(), "description is blank")
        assertTrue(PirateStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(PirateStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `pirate instructions pin the register the contrast and the never-change clause`() {
        val instructions = PirateStyle.systemInstructions

        assertTrue(instructions.contains("Nautical metaphors"), "instructions lack the nautical marker")
        assertTrue(instructions.contains("quarterdeck"), "instructions lack the quarterdeck marker")
        assertTrue(instructions.contains("at most one arr"), "instructions lack the arr ration cap")
        assertTrue(
            instructions.contains("NEVER lapse into bellowing tavern threats or generic fantasy-quest bombast"),
            "instructions lack the contrastive NEVER against tavern brawling and quest bombast",
        )
        assertTrue(
            instructions.contains("belongs to the dungeon, not the quarterdeck"),
            "instructions lack the dungeon-versus-quarterdeck pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `pirate few-shots keep every raw key fact`() {
        val casual = PirateStyle.fewShots.first { shot -> shot.raw.contains("photos") }
        assertTrue(casual.styled.contains("park"), "casual lost the park: ${casual.styled}")
        assertTrue(casual.styled.contains("phone"), "casual lost the phone: ${casual.styled}")
        assertTrue(casual.styled.contains("battery"), "casual lost the battery: ${casual.styled}")
        assertTrue(casual.styled.contains("whole day long"), "casual lost the all-day battery: ${casual.styled}")

        val technical = PirateStyle.fewShots.first { shot -> shot.raw.contains("backup script") }
        assertTrue(technical.styled.contains("step three"), "technical lost the step: ${technical.styled}")
        assertTrue(
            technical.styled.contains("database migration"),
            "technical lost the migration: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost the logs: ${technical.styled}",
        )

        val disagreement = PirateStyle.fewShots.first { shot -> shot.raw.contains("dave") }
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
    fun `pirate instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = PirateStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("grammar stays shipshape and intelligible"),
            "instructions lack the intelligible-grammar pin",
        )
        assertTrue(
            instructions.contains("a phone stays a phone, never a period apparatus"),
            "instructions lack the flavour-form-only pin",
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
    fun `pirate few-shots ration arr to at most one per output`() {
        PirateStyle.fewShots.forEach { shot ->
            val arrs = ARR.findAll(shot.styled).count()
            assertTrue(arrs <= 1, "few-shot output spends more than one arr: ${shot.styled}")
        }
    }

    @Test
    fun `no pirate few-shot output addresses a listener the input did not`() {
        PirateStyle.fewShots.forEach { shot ->
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
        private val ARR = Regex("\\barr\\b", RegexOption.IGNORE_CASE)
    }
}
