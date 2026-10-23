package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SiliconValleyBroStyleTest {

    @Test
    fun `silicon valley bro carries its id kind and non-blank display fields`() {
        assertEquals("silicon-valley-bro", SiliconValleyBroStyle.id)
        assertEquals(StyleKind.LLM, SiliconValleyBroStyle.kind)
        assertEquals(StyleCategory.MODERN_PARODY, SiliconValleyBroStyle.category)
        assertEquals("Silicon Valley Bro", SiliconValleyBroStyle.displayName)
        assertEquals("Shipping apps, lifting heavy, bootstrapping the dream.", SiliconValleyBroStyle.description)
        assertEquals(
            "Just a guy in South Carolina shipping apps, hitting the gym, and turning a goofy voice transcriber into a real business.",
            SiliconValleyBroStyle.sample,
        )
        assertTrue(SiliconValleyBroStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(SiliconValleyBroStyle.description.isNotBlank(), "description is blank")
        assertTrue(SiliconValleyBroStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(SiliconValleyBroStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `silicon valley bro instructions pin the register the contrasts and the never-change clause`() {
        val instructions = SiliconValleyBroStyle.systemInstructions

        assertTrue(instructions.contains("product-market fit"), "instructions lack the product-market-fit marker")
        assertTrue(instructions.contains("progressive overload"), "instructions lack the progressive-overload marker")
        assertTrue(instructions.contains("NEVER invent traction"), "instructions lack the never-invent-traction marker")
        assertTrue(
            instructions.contains("belongs to Corporate LinkedIn"),
            "instructions lack the Corporate LinkedIn nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("belongs to Ultra HR"),
            "instructions lack the Ultra HR nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("belongs to Gen Z Brainrot"),
            "instructions lack the Gen Z Brainrot nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("belongs to Edgelord"),
            "instructions lack the Edgelord nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `silicon valley bro few-shots keep every raw key fact`() {
        val shipping = SiliconValleyBroStyle.fewShots.first { shot -> shot.raw.contains("login bug") }
        assertTrue(shipping.styled.contains("login bug"), "shipping lost the login bug: ${shipping.styled}")
        assertTrue(shipping.styled.contains("Friday"), "shipping lost Friday: ${shipping.styled}")
        assertTrue(shipping.styled.contains("testers"), "shipping lost the testers: ${shipping.styled}")

        val gym = SiliconValleyBroStyle.fewShots.first { shot -> shot.raw.contains("gym before work") }
        assertTrue(gym.styled.contains("gym"), "gym lost the gym: ${gym.styled}")
        assertTrue(gym.styled.contains("budget"), "gym lost the budget: ${gym.styled}")
        assertTrue(
            gym.styled.lowercase().contains("emails"),
            "gym lost the budget emails: ${gym.styled}",
        )

        val traction = SiliconValleyBroStyle.fewShots.first { shot -> shot.raw.contains("twelve new users") }
        assertTrue(
            traction.styled.lowercase().contains("twelve"),
            "traction lost the twelve users: ${traction.styled}",
        )
        assertTrue(
            traction.styled.lowercase().contains("five dollars"),
            "traction lost the five dollars: ${traction.styled}",
        )
    }

    @Test
    fun `silicon valley bro instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = SiliconValleyBroStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("one shipping block"),
            "instructions lack the shipping-block architecture",
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
    }

    @Test
    fun `no silicon valley bro few-shot output addresses a listener the input did not`() {
        SiliconValleyBroStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
        SiliconValleyBroStyle.fewShots.forEach { shot ->
            assertTrue(!SECOND_PERSON.containsMatchIn(shot.raw), "raw reaches for a listener: ${shot.raw}")
            assertTrue(!SECOND_PERSON.containsMatchIn(shot.styled), "styled reaches for a listener: ${shot.styled}")
        }
    }

    companion object {
        private val SECOND_PERSON = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
    }
}
