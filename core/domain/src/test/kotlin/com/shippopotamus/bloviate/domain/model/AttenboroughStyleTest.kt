package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttenboroughStyleTest {

    @Test
    fun `attenborough carries its id kind and non-blank display fields`() {
        assertEquals("attenborough", AttenboroughStyle.id)
        assertEquals(StyleKind.LLM, AttenboroughStyle.kind)
        assertTrue(AttenboroughStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(AttenboroughStyle.description.isNotBlank(), "description is blank")
        assertTrue(AttenboroughStyle.sample.isNotBlank(), "sample is blank")
    }

    @Test
    fun `attenborough instructions carry register markers contrastive never and the never-change clause`() {
        val instructions = AttenboroughStyle.systemInstructions

        assertTrue(instructions.contains("Present tense"), "instructions lack the present-tense register marker")
        assertTrue(instructions.contains("wildlife spectacle"), "instructions lack the wildlife-spectacle register marker")
        assertTrue(
            instructions.contains("that dry scaffolding belongs to Aristotle"),
            "instructions lack the contrastive NEVER against Aristotle",
        )
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text"),
            "instructions lack the never-change clause",
        )
        assertTrue(
            instructions.contains("transcript's own language"),
            "instructions lack the register-of-the-language directive",
        )
        assertTrue(
            instructions.contains("never translate"),
            "instructions do not forbid translating into English",
        )
    }

    @Test
    fun `attenborough few-shots keep raw key facts`() {
        val shots = AttenboroughStyle.fewShots
        assertTrue(shots.size >= 3, "expected at least 3 few-shots, found ${shots.size}")

        val everyday = shots[0]
        assertTrue(everyday.styled.contains("coffee"), "everyday shot lost the coffee")
        assertTrue(everyday.styled.contains("twenty minutes"), "everyday shot lost the twenty minutes")
        assertTrue(everyday.styled.contains("rain"), "everyday shot lost the rain")

        val technical = shots[1]
        assertTrue(technical.styled.contains("Tuesday"), "technical shot lost Tuesday")
        assertTrue(technical.styled.contains("Sarah"), "technical shot lost Sarah")
        assertTrue(technical.styled.contains("three hours"), "technical shot lost the three hours")
        assertTrue(technical.styled.contains("database"), "technical shot lost the database")

        val emotional = shots[2]
        assertTrue(emotional.styled.contains("Mark"), "emotional shot lost Mark")
        assertTrue(emotional.styled.contains("Last night"), "emotional shot lost last night")
    }

    @Test
    fun `attenborough keeps the speaker in first person as observer-narrator`() {
        // ENC-175: the speaker stays the observer-narrator of their own life —
        // first-person facts intact, never recast as a third-person animal.
        assertTrue(
            AttenboroughStyle.systemInstructions.contains("first-person facts stay first-person"),
            "instructions lack the first-person speaker invariant",
        )
        val firstPerson = Regex("\\b(I|we|us|our|my|mine)\\b", RegexOption.IGNORE_CASE)
        for (shot in AttenboroughStyle.fewShots) {
            assertTrue(
                firstPerson.containsMatchIn(shot.styled),
                "styled output abandons the first-person speaker: ${shot.styled}",
            )
        }
    }

    @Test
    fun `attenborough styled outputs never address a listener`() {
        // ENC-175 records no second-person exception: styled outputs must not
        // contain you/your where the raw dictation has none.
        val secondPerson = Regex("\\b(you|your|yours|yourself)\\b", RegexOption.IGNORE_CASE)
        for (shot in AttenboroughStyle.fewShots) {
            assertTrue(
                !secondPerson.containsMatchIn(shot.raw),
                "raw side unexpectedly addresses a listener: ${shot.raw}",
            )
            assertTrue(
                !secondPerson.containsMatchIn(shot.styled),
                "styled output addresses a listener: ${shot.styled}",
            )
        }
    }
}
