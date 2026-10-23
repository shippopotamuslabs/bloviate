package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgelordStyleTest {

    @Test
    fun `edgelord carries its id kind category and ratified display fields`() {
        assertEquals("edgelord", EdgelordStyle.id)
        assertEquals(StyleKind.LLM, EdgelordStyle.kind)
        assertEquals(StyleCategory.MODERN_PARODY, EdgelordStyle.category)
        assertEquals("Edgelord", EdgelordStyle.displayName)
        assertEquals("Deliberately shocking, deadpan, and proud of it.", EdgelordStyle.description)
        assertEquals(
            "They moved the meeting to three. Three. A war crime with a calendar invite.",
            EdgelordStyle.sample,
        )
        assertTrue(EdgelordStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(EdgelordStyle.description.isNotBlank(), "description is blank")
        assertTrue(EdgelordStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(EdgelordStyle.fewShots.size >= 3, "fewer than three few-shots")
        assertEquals("edgelord", StyleCatalog.byId("edgelord").id)
    }

    @Test
    fun `edgelord instructions pin the deliberate voice the contrasts and the never-change clause`() {
        val instructions = EdgelordStyle.systemInstructions

        assertTrue(instructions.contains("deliberately shocking"), "instructions lack the deliberate-shock marker")
        assertTrue(instructions.contains("deadpan"), "instructions lack the deadpan marker")
        assertTrue(instructions.contains("gallows"), "instructions lack the gallows marker")
        assertTrue(instructions.contains("taboo-adjacent"), "instructions lack the taboo-adjacent marker")
        assertTrue(instructions.contains("controlled"), "instructions lack the controlled-sentences marker")
        assertTrue(instructions.contains("grammatical"), "instructions lack the grammatical marker")
        assertTrue(instructions.contains("aimed"), "instructions lack the aimed marker")
        assertTrue(
            instructions.lowercase().contains("first person"),
            "instructions lack the first-person marker",
        )
        assertTrue(instructions.contains("framing ONLY"), "instructions lack the framing-only fact-safety pin")
        assertTrue(instructions.contains("never invent facts"), "instructions lack the no-invented-facts pin")
        assertTrue(
            instructions.contains("never aim cruelty at a named person"),
            "instructions lack the named-person cruelty boundary",
        )
        assertTrue(
            instructions.contains("belongs to Gen Z Brainrot"),
            "instructions lack the Gen Z Brainrot nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("screaming caps"),
            "instructions lack the screaming-caps chaos contrast pin",
        )
        assertTrue(
            instructions.contains("old Edgelord"),
            "instructions lack the old-voice contrast pin",
        )
        assertTrue(
            instructions.contains("belongs to Ultra HR"),
            "instructions lack the Ultra HR nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("belongs to Corporate LinkedIn"),
            "instructions lack the Corporate LinkedIn nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `edgelord few-shots keep every raw key fact`() {
        val casual = EdgelordStyle.fewShots.first { shot -> shot.raw.contains("store") }
        assertTrue(casual.styled.contains("store"), "casual lost the store: ${casual.styled}")
        assertTrue(casual.styled.contains("milk"), "casual lost the milk: ${casual.styled}")

        val meeting = EdgelordStyle.fewShots.first { shot -> shot.raw.contains("meeting") }
        assertTrue(meeting.styled.contains("meeting", ignoreCase = true), "meeting lost the meeting: ${meeting.styled}")
        assertTrue(meeting.styled.contains("three", ignoreCase = true), "meeting lost three: ${meeting.styled}")

        val disagreement = EdgelordStyle.fewShots.first { shot -> shot.raw.contains("Marcus") }
        assertTrue(
            disagreement.styled.contains("Marcus"),
            "disagreement lost Marcus: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("sixty thousand dollars", ignoreCase = true),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("June ninth", ignoreCase = true),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
    }

    @Test
    fun `edgelord instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = EdgelordStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("one aimed block"),
            "instructions lack the aimed-block architecture",
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
            instructions.contains("No slurs or bigotry ever"),
            "instructions lack the no-slur boundary",
        )
    }

    @Test
    fun `edgelord styled output is controlled and carries no brainrot lexicon or old chaos`() {
        assertTrue(
            !EdgelordStyle.sample.contains("feral"),
            "sample still carries the old feral voice: ${EdgelordStyle.sample}",
        )
        assertTrue(
            EdgelordStyle.description != "Chaotic, unhinged, extremely online.",
            "description still carries the old chaotic identity",
        )
        EdgelordStyle.fewShots.forEach { shot ->
            assertTrue(shot.styled.first().isUpperCase(), "styled leaves controlled casing: ${shot.styled}")
            assertTrue(shot.styled.endsWith("."), "styled lacks a deadpan full stop: ${shot.styled}")
            assertTrue(
                !BRAINROT_LEXICON.containsMatchIn(shot.styled),
                "styled carries brainrot lexicon: ${shot.styled}",
            )
            assertTrue(!shot.styled.contains("ABSOLUTELY"), "styled carries screaming caps: ${shot.styled}")
            assertTrue(!shot.styled.contains("tbh"), "styled carries the old voice: ${shot.styled}")
            assertTrue(!shot.styled.contains("feral"), "styled carries the old voice: ${shot.styled}")
        }
    }

    @Test
    fun `no edgelord few-shot output addresses a listener the input did not`() {
        EdgelordStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
        EdgelordStyle.fewShots.forEach { shot ->
            assertTrue(!SECOND_PERSON.containsMatchIn(shot.styled), "styled reaches for a listener: ${shot.styled}")
        }
    }

    companion object {
        private val SECOND_PERSON = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
        private val BRAINROT_LEXICON = Regex("\\b(skibidi|aura|lore|no cap|fr)\\b", RegexOption.IGNORE_CASE)
    }
}
