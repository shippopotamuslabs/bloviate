package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleCatalogTest {

    @Test
    fun `the catalog holds exactly eleven styles`() {
        assertEquals(11, StyleCatalog.ALL.size)
    }

    @Test
    fun `talkie is an llm style in the 1920s register`() {
        // ENC-102: the era-family entry inspired by Talkie-1930 — a
        // prompt-layer register on the existing chain, not a model.
        val talkie = StyleCatalog.byId("talkie")

        assertEquals("Talkie", talkie.displayName)
        assertEquals(StyleKind.LLM, talkie.kind)
        assertTrue(talkie.systemInstructions.contains("1920s"), "talkie names no period register")
    }

    @Test
    fun `talkie instructions carry the register-of-the-language directive and the form-only law`() {
        // ENC-102's multilingual note: the pass restyles whatever
        // language the transcript is in, in THAT language's 1920s
        // register — never Anglicized. And period flavor is form
        // only: modern referents keep the speaker's names, and no
        // period bigotry the speaker did not use may appear.
        val talkie = StyleCatalog.byId("talkie")

        assertTrue(
            talkie.systemInstructions.contains("transcript's own language"),
            "talkie lacks the register-of-the-language directive",
        )
        assertTrue(
            talkie.systemInstructions.contains("never translate"),
            "talkie does not forbid translating into English",
        )
        assertTrue(
            talkie.systemInstructions.contains("FORM ONLY"),
            "talkie lacks the form-only law",
        )
        assertTrue(
            talkie.systemInstructions.contains("my phone stays my phone"),
            "talkie does not pin the modern-referent rule",
        )
    }

    @Test
    fun `talkie few-shots restyle casual and technical dictation with facts intact`() {
        // ENC-102's fixture pairs: a casual dictation whose modern
        // referent survives unnamed-by-costume (the phone stays a
        // phone), and a technical one whose load-bearing facts —
        // the database migration, the third step — survive the
        // register untouched.
        val talkie = StyleCatalog.byId("talkie")

        val casual = talkie.fewShots.first { shot -> shot.raw.contains("phone") }
        assertTrue(casual.styled.contains("phone"), "talkie renamed the speaker's phone: ${casual.styled}")
        assertTrue(casual.styled.contains("battery"), "talkie dropped the battery: ${casual.styled}")

        val technical = talkie.fewShots.first { shot -> shot.raw.contains("database") }
        assertTrue(technical.styled.contains("database migration"), "talkie lost the migration: ${technical.styled}")
        assertTrue(technical.styled.contains("third step"), "talkie lost the step count: ${technical.styled}")
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
    fun `talkie clips — a word cap, short declaratives, and the periodic swell forbidden`() {
        // ENC-104: Sean's ear test found Talkie and The Colonel
        // indistinguishable — both merely "old." The sharpening
        // defines Talkie AGAINST the Colonel: sentence architecture,
        // not vocabulary, is the differentiator, and the mandates
        // are pinned as instruction content.
        val talkie = StyleCatalog.byId("talkie")

        assertTrue(
            talkie.systemInstructions.contains("CLIP, never swell"),
            "talkie lacks the clip mandate",
        )
        assertTrue(
            talkie.systemInstructions.contains("no sentence runs past twelve words"),
            "talkie lacks the sentence-length cap",
        )
        assertTrue(
            talkie.systemInstructions.contains("NEVER write a periodic sentence"),
            "talkie does not forbid the Colonel's periodic swell",
        )
        assertTrue(
            talkie.systemInstructions.contains("press room"),
            "talkie lacks the newsroom idiom directive",
        )
    }

    @Test
    fun `the colonel dilates — periodic sentences mandated and clipping forbidden`() {
        // ENC-104's other half: the Colonel is defined against
        // Talkie — every sentence a period, the main clause landing
        // last, wire-service haste expressly forbidden.
        val colonel = StyleCatalog.byId("the-colonel")

        assertTrue(
            colonel.systemInstructions.contains("DILATE, never clip"),
            "the colonel lacks the dilate mandate",
        )
        assertTrue(
            colonel.systemInstructions.contains("the main clause arriving only at the end"),
            "the colonel lacks the periodic-structure mandate",
        )
        assertTrue(
            colonel.systemInstructions.contains("NEVER clip a thought into wire-service fragments"),
            "the colonel does not forbid Talkie's clipped fragments",
        )
        assertTrue(
            colonel.systemInstructions.contains("courtesy formulae"),
            "the colonel lacks the courtesy-formulae directive",
        )
    }

    @Test
    fun `talkie and the colonel are minimal pairs over the same dictations`() {
        // ENC-104: both era styles restyle the SAME source dictations
        // so the fork between the registers is visible in the
        // examples themselves — and the architecture difference is
        // measurable in the outputs: Talkie's sentences all fit the
        // cap, while every Colonel output carries at least one full
        // period of twenty words or more.
        val talkie = StyleCatalog.byId("talkie")
        val colonel = StyleCatalog.byId("the-colonel")

        assertEquals(
            talkie.fewShots.map { shot -> shot.raw },
            colonel.fewShots.map { shot -> shot.raw },
            "the era styles no longer share their source dictations",
        )
        talkie.fewShots.forEach { shot ->
            val longest = sentencesOf(shot.styled).maxOf { sentence -> wordCount(sentence) }
            assertTrue(longest <= 12, "talkie example sentence runs past the cap: ${shot.styled}")
        }
        colonel.fewShots.forEach { shot ->
            val longest = sentencesOf(shot.styled).maxOf { sentence -> wordCount(sentence) }
            assertTrue(longest >= 20, "the colonel example never swells to a period: ${shot.styled}")
        }
    }

    /** Splits styled example text into its sentences for word-count measurement. */
    private fun sentencesOf(text: String): List<String> =
        text.split(Regex("[.!?]")).map { part -> part.trim() }.filter { part -> part.isNotEmpty() }

    /** Words in one sentence, counted on whitespace. */
    private fun wordCount(sentence: String): Int = sentence.split(Regex("\\s+")).size

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
