package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UltraHrStyleTest {

    @Test
    fun `ultra hr carries its id kind and non-blank display fields`() {
        assertEquals("ultra-hr", UltraHrStyle.id)
        assertEquals(StyleKind.LLM, UltraHrStyle.kind)
        assertEquals(StyleCategory.MODERN_PARODY, UltraHrStyle.category)
        assertEquals("Ultra HR", UltraHrStyle.displayName)
        assertEquals("Holding space for a more inclusive restyle of your message.", UltraHrStyle.description)
        assertEquals("Just circling back to hold space for a kinder framing of this deadline.", UltraHrStyle.sample)
        assertTrue(UltraHrStyle.displayName.isNotBlank(), "displayName is blank")
        assertTrue(UltraHrStyle.description.isNotBlank(), "description is blank")
        assertTrue(UltraHrStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(UltraHrStyle.fewShots.size >= 3, "fewer than three few-shots")
    }

    @Test
    fun `ultra hr instructions pin the register the contrasts and the never-change clause`() {
        val instructions = UltraHrStyle.systemInstructions

        assertTrue(instructions.contains("performative care"), "instructions lack the performative-care marker")
        assertTrue(instructions.contains("holding space"), "instructions lack the holding-space marker")
        assertTrue(instructions.contains("kind reminder"), "instructions lack the kind-reminder marker")
        assertTrue(
            instructions.contains("lived experience"),
            "instructions lack the lived-experience marker",
        )
        assertTrue(
            instructions.contains("impact over intent"),
            "instructions lack the impact-over-intent marker",
        )
        assertTrue(instructions.contains("content note"), "instructions lack the content-note marker")
        assertTrue(instructions.contains("support resources"), "instructions lack the support-resources marker")
        assertTrue(
            instructions.lowercase().contains("first person"),
            "instructions lack the first-person marker",
        )
        assertTrue(
            instructions.contains("master becomes main"),
            "instructions lack the master-main treadmill pin",
        )
        assertTrue(
            instructions.contains("blackout becomes blockout"),
            "instructions lack the blackout-blockout treadmill pin",
        )
        assertTrue(
            instructions.contains("guys becomes folks"),
            "instructions lack the guys-folks treadmill pin",
        )
        assertTrue(
            instructions.contains("in the telling only"),
            "instructions lack the telling-only substitution boundary",
        )
        assertTrue(
            instructions.contains("NEVER substitute inside quoted text"),
            "instructions lack the quoted-text substitution prohibition",
        )
        assertTrue(
            instructions.contains("NEVER hustle"),
            "instructions lack the contrastive NEVER against Corporate LinkedIn hustle",
        )
        assertTrue(
            instructions.contains("belongs to Corporate LinkedIn"),
            "instructions lack the Corporate LinkedIn nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("NEVER let a plain sentence pass un-softened"),
            "instructions lack the contrastive NEVER against plain Professional reporting",
        )
        assertTrue(
            instructions.contains("belongs to Professional"),
            "instructions lack the Professional nearest-neighbour pin",
        )
        assertTrue(
            instructions.contains("NEVER chase shock"),
            "instructions lack the contrastive NEVER against Edgelord shock",
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
    fun `ultra hr few-shots keep every raw key fact`() {
        val outage = UltraHrStyle.fewShots.first { shot -> shot.raw.contains("blackout") }
        assertTrue(outage.styled.contains("main branch"), "outage lost the main branch: ${outage.styled}")
        assertTrue(outage.styled.contains("blockout"), "outage lost the blockout: ${outage.styled}")
        assertTrue(outage.styled.contains("demo"), "outage lost the demo: ${outage.styled}")
        assertTrue(outage.styled.contains("Friday"), "outage lost Friday: ${outage.styled}")
        assertTrue(outage.styled.contains("repair"), "outage lost the repair request: ${outage.styled}")
        assertTrue(
            outage.styled.contains("holding space"),
            "outage lost the care framing: ${outage.styled}",
        )

        val deadline = UltraHrStyle.fewShots.first { shot -> shot.raw.contains("report is due") }
        assertTrue(deadline.styled.contains("report"), "deadline lost the report: ${deadline.styled}")
        assertTrue(deadline.styled.contains("Friday"), "deadline lost Friday: ${deadline.styled}")
        assertTrue(deadline.styled.contains("numbers"), "deadline lost the numbers: ${deadline.styled}")
        assertTrue(deadline.styled.contains("Sarah"), "deadline lost Sarah: ${deadline.styled}")
        assertTrue(deadline.styled.contains("Thursday morning"), "deadline lost Thursday morning: ${deadline.styled}")

        val disagreement = UltraHrStyle.fewShots.first { shot -> shot.raw.contains("priya") }
        assertTrue(disagreement.styled.contains("Priya"), "disagreement lost Priya: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.lowercase().contains("forty thousand dollar"),
            "disagreement lost the budget: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.lowercase().contains("march fifteenth"),
            "disagreement lost the deadline: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("disagreement"),
            "disagreement lost the dissent: ${disagreement.styled}",
        )
    }

    @Test
    fun `ultra hr instructions carry the measurable casing punctuation paragraphing and language architecture`() {
        val instructions = UltraHrStyle.systemInstructions

        assertTrue(instructions.contains("Casing:"), "instructions lack the casing directive")
        assertTrue(instructions.contains("Punctuation:"), "instructions lack the punctuation directive")
        assertTrue(instructions.contains("Paragraphing:"), "instructions lack the paragraphing directive")
        assertTrue(
            instructions.contains("one caring block"),
            "instructions lack the caring-block architecture",
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
    fun `no ultra hr few-shot output addresses a listener the input did not`() {
        UltraHrStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
        // ENC-92 strict idiom for this voice: no second-person address and
        // no folks-address on either side, so the audit passes cleanly.
        UltraHrStyle.fewShots.forEach { shot ->
            assertTrue(!SECOND_PERSON.containsMatchIn(shot.raw), "raw reaches for a listener: ${shot.raw}")
            assertTrue(!SECOND_PERSON.containsMatchIn(shot.styled), "styled reaches for a listener: ${shot.styled}")
            assertTrue(!shot.styled.contains("folks"), "styled uses folks-address: ${shot.styled}")
        }
        UltraHrStyle.fewShots.forEach { shot ->
            assertTrue(shot.styled.contains("I "), "styled leaves the first person: ${shot.styled}")
        }
    }

    @Test
    fun `ultra hr content note and support resources are conditional not ritual`() {
        // BLO-153: the content note opens only where the matter genuinely
        // bites and support resources close only where they could help —
        // the examples must not bolt the frame onto every message.
        val instructions = UltraHrStyle.systemInstructions
        assertTrue(
            !instructions.contains("whether asked for or not"),
            "instructions still mandate support resources on every output",
        )
        assertEquals(
            1,
            UltraHrStyle.fewShots.count { shot -> shot.styled.startsWith("Content note") },
            "exactly the heavy example should open with a content note",
        )
        assertEquals(
            1,
            UltraHrStyle.fewShots.count { shot -> shot.styled.contains("support resources") },
            "support resources should appear in exactly one example, not as a closing ritual",
        )
    }

    companion object {
        private val SECOND_PERSON = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
    }
}
