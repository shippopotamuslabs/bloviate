package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleCatalogTest {

    @Test
    fun `the catalog holds exactly ten styles`() {
        assertEquals(10, StyleCatalog.ALL.size)
    }

    @Test
    fun `the colonel is an llm style in the southern aristocratic register`() {
        // ENC-103: the era-family entry beside Talkie — the educated
        // nineteenth-century Southern written voice as a prompt-layer
        // register (default display name per the ticket: The Colonel).
        val colonel = StyleCatalog.byId("the-colonel")

        assertEquals("The Colonel", colonel.displayName)
        assertEquals(StyleKind.LLM, colonel.kind)
        assertTrue(
            colonel.systemInstructions.contains("nineteenth-century Southern"),
            "the colonel names no register",
        )
        assertTrue(
            colonel.systemInstructions.contains("transcript's own language"),
            "the colonel lacks the register-of-the-language directive",
        )
    }

    @Test
    fun `the colonel boundary is pinned in its instructions`() {
        // ENC-103's explicit boundary, pinned as instruction content:
        // the style takes the period's syntax, diction, and courtesy
        // ONLY — never its racial vocabulary or Lost Cause
        // apologetics, on any input. It is also a voice, not a
        // letter: no salutations or sign-offs.
        val colonel = StyleCatalog.byId("the-colonel")

        assertTrue(
            colonel.systemInstructions.contains("racial vocabulary"),
            "the colonel lacks the racial-vocabulary prohibition",
        )
        assertTrue(
            colonel.systemInstructions.contains("Lost Cause apologetics"),
            "the colonel lacks the Lost Cause prohibition",
        )
        assertTrue(
            colonel.systemInstructions.contains("no salutations"),
            "the colonel does not forbid letter framing",
        )
    }

    @Test
    fun `the colonel few-shots restyle casual and technical dictation with facts intact`() {
        // ENC-103's fixture pairs: a courteous everyday dictation
        // (photographs, children, a fine visit) and a technical one
        // (backup script, filled disk, two hours of logs) — every
        // load-bearing fact survives the register.
        val colonel = StyleCatalog.byId("the-colonel")

        val casual = colonel.fewShots.first { shot -> shot.raw.contains("photos") }
        assertTrue(casual.styled.contains("photographs"), "the colonel lost the photographs: ${casual.styled}")
        assertTrue(casual.styled.contains("children"), "the colonel lost the children: ${casual.styled}")

        val technical = colonel.fewShots.first { shot -> shot.raw.contains("backup script") }
        assertTrue(technical.styled.contains("backup script"), "the colonel lost the script: ${technical.styled}")
        assertTrue(technical.styled.contains("disk"), "the colonel lost the disk: ${technical.styled}")
        assertTrue(technical.styled.contains("two hours of logs"), "the colonel lost the logs: ${technical.styled}")
    }

    @Test
    fun `every style id is unique`() {
        val ids = StyleCatalog.ALL.map { style -> style.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every style has non-blank display fields and sample`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.displayName.isNotBlank(), "${style.id} displayName is blank")
            assertTrue(style.description.isNotBlank(), "${style.id} description is blank")
            assertTrue(style.sample.isNotBlank(), "${style.id} sample is blank")
        }
    }

    @Test
    fun `every style has non-blank instructions containing a never-change clause`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.systemInstructions.isNotBlank(), "${style.id} instructions are blank")
            assertTrue(
                style.systemInstructions.lowercase().contains("never"),
                "${style.id} instructions carry no NEVER-change clause",
            )
        }
    }

    @Test
    fun `every style has at least two few-shots with non-blank sides`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.fewShots.size >= 2, "${style.id} has fewer than two few-shots")
            style.fewShots.forEach { fewShot ->
                assertTrue(fewShot.raw.isNotBlank(), "${style.id} has a blank few-shot raw side")
                assertTrue(fewShot.styled.isNotBlank(), "${style.id} has a blank few-shot styled side")
            }
        }
    }

    @Test
    fun `no few-shot output addresses a listener the input did not`() {
        // ENC-92 audit: a styled example may keep a "you" the speaker
        // themselves said (quoting their own words back is fidelity),
        // but it may never INTRODUCE second-person address — that is
        // the epistolary framing the personas taught the model. This
        // audit failed on Seneca (both pairs) and Marcus Aurelius
        // before their rewrites.
        StyleCatalog.ALL.forEach { style ->
            style.fewShots.forEach { shot ->
                if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                    assertTrue(
                        SECOND_PERSON.containsMatchIn(shot.raw),
                        "${style.id} few-shot output addresses a 'you' its input never did: ${shot.styled}",
                    )
                }
            }
        }
    }

    @Test
    fun `seneca and aristotle first-person fixtures stay in the first person`() {
        // ENC-92's named fixtures: a first-person dictation through
        // the epistolary personas must come back as the speaker's own
        // first-person text — the shape of Sean's repro ("You have
        // noticed one last sort of bug...") is what must not return.
        listOf("seneca", "aristotle").forEach { id ->
            val style = StyleCatalog.byId(id)
            style.fewShots.forEach { shot ->
                if (FIRST_PERSON.containsMatchIn(shot.raw)) {
                    assertTrue(
                        FIRST_PERSON.containsMatchIn(shot.styled),
                        "$id first-person input lost its person: ${shot.styled}",
                    )
                    assertTrue(
                        !SECOND_PERSON.containsMatchIn(shot.styled),
                        "$id first-person input came back addressed to a 'you': ${shot.styled}",
                    )
                }
            }
        }
    }

    @Test
    fun `exactly the two casual styles are mechanical`() {
        val mechanicalIds = StyleCatalog.ALL
            .filter { style -> style.kind == StyleKind.MECHANICAL }
            .map { style -> style.id }
            .toSet()
        assertEquals(setOf("lowercase-casual", "normal-caps-casual"), mechanicalIds)
    }

    @Test
    fun `the default is normal-caps-casual and resolves through lookup`() {
        assertEquals("normal-caps-casual", StyleCatalog.DEFAULT.id)
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId("normal-caps-casual"))
    }

    @Test
    fun `an unknown id resolves to the default`() {
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId("not-a-style"))
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId(""))
    }

    private companion object {
        /** Second-person pronouns — the mark of output addressed back at the speaker (ENC-92). */
        val SECOND_PERSON: Regex = Regex("""\b(you|your|yours|yourself|yourselves)\b""", RegexOption.IGNORE_CASE)

        /** First-person pronouns — the speaker's own voice, which a restyle must preserve (ENC-92). */
        val FIRST_PERSON: Regex =
            Regex("""\b(i|i'm|i've|i'll|i'd|my|me|mine|we|our|ours|us)\b""", RegexOption.IGNORE_CASE)
    }
}
