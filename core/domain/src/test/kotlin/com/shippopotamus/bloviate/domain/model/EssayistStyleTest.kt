package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EssayistStyleTest {

    @Test
    fun `essayist is an llm serious style with non-blank display fields and sample`() {
        assertEquals("essayist", EssayistStyle.id)
        assertEquals("Essayist", EssayistStyle.displayName)
        assertEquals(StyleKind.LLM, EssayistStyle.kind)
        assertEquals(StyleCategory.SERIOUS, EssayistStyle.category)
        assertTrue(EssayistStyle.displayName.isNotBlank(), "essayist displayName is blank")
        assertTrue(EssayistStyle.description.isNotBlank(), "essayist description is blank")
        assertTrue(EssayistStyle.sample.isNotBlank(), "essayist sample is blank")
    }

    @Test
    fun `essayist instructions carry the literary markers and the never-change clause`() {
        assertTrue(
            EssayistStyle.systemInstructions.contains("elevated"),
            "essayist lacks elevated diction",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("precise diction"),
            "essayist lacks precise diction",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("varied sentence architecture"),
            "essayist lacks varied sentence architecture",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("full strength"),
            "essayist does not carry the argument at full strength",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("jargon"),
            "essayist does not refuse jargon for its own sake",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("purple"),
            "essayist does not refuse purple padding",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("NEVER add ideas"),
            "essayist does not refuse invented content",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("transcript's own language"),
            "essayist lacks the register-of-the-language directive",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("never translate"),
            "essayist does not forbid translating into English",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "essayist lacks the verbatim never-change clause",
        )
        assertTrue(
            EssayistStyle.systemInstructions.endsWith(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "essayist does not end with the verbatim never-change clause",
        )
    }

    @Test
    fun `essayist refuses the four neighbouring voices`() {
        assertTrue(
            EssayistStyle.systemInstructions.contains("Academic Jargon"),
            "essayist does not refuse Academic Jargon fog",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("Genius"),
            "essayist does not refuse the Genius condescension",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("Professional"),
            "essayist does not refuse Professional flattening",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("Polish"),
            "essayist does not refuse Polish mere-tidying",
        )
    }

    @Test
    fun `essayist instructions pin the speaker-invariant and the slur boundary`() {
        assertTrue(
            EssayistStyle.systemInstructions.contains("speaker's own words restyled"),
            "essayist lacks the speaker-invariant",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("never a reply"),
            "essayist does not refuse replying",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("never addressing a listener"),
            "essayist does not refuse addressing a listener",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("slur"),
            "essayist lacks the slur prohibition",
        )
    }

    @Test
    fun `essayist instructions carry the anti-llm-speak clause`() {
        assertTrue(
            EssayistStyle.systemInstructions.contains("delve"),
            "essayist lacks the banned fingerprint word delve",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("tapestry"),
            "essayist lacks the banned fingerprint word tapestry",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("myriad"),
            "essayist lacks the banned fingerprint word myriad",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("actual trip"),
            "essayist does not bound journey to an actual trip",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("not X but Y"),
            "essayist does not name the negation pivot",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("staccato"),
            "essayist does not refuse staccato triplets",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("worth noting"),
            "essayist does not refuse throat-clearing",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("em dash shelved"),
            "essayist does not ration the em dash",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("comma carries the clause"),
            "essayist lacks the comma-over-dash ration",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("use, do,"),
            "essayist lacks the plain-verb shelf",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("specific nouns"),
            "essayist lacks the specific-nouns register",
        )
    }

    @Test
    fun `essayist instructions pin the huemer clarity benchmark and its boundary`() {
        assertTrue(
            EssayistStyle.systemInstructions.contains("Huemer"),
            "essayist lacks the Huemer benchmark",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("The Problem of Political Authority"),
            "essayist does not name The Problem of Political Authority",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("small village with a crime problem"),
            "essayist lacks the verbatim Huemer excerpt fragment",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("Vandals roam the village"),
            "essayist lacks the Vandals excerpt fragment",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("one claim per sentence"),
            "essayist lacks the one-claim-per-sentence trait",
        )
        assertTrue(
            EssayistStyle.systemInstructions.contains("never take Huemer's subject matter"),
            "essayist lacks the borrow-clarity-only boundary",
        )
    }

    @Test
    fun `essayist styled sides carry no banned fingerprint words and no negation pivot`() {
        val banned = listOf(
            "delve", "tapestry", "realm", "robust", "seamless", "holistic",
            "pivotal", "crucial", "testament", "multifaceted", "intricate",
            "vibrant", "profound", "plethora", "myriad",
        )
        val pivot = Regex("it'?s not .+, it'?s ", RegexOption.IGNORE_CASE)
        EssayistStyle.fewShots.forEach { shot ->
            val styledLower = shot.styled.lowercase()
            banned.forEach { word ->
                assertTrue(
                    !Regex("\\b$word\\b").containsMatchIn(styledLower),
                    "essayist styled side uses banned word '$word': ${shot.styled}",
                )
            }
            assertTrue(
                !pivot.containsMatchIn(shot.styled),
                "essayist styled side turns on the negation pivot: ${shot.styled}",
            )
        }
    }

    @Test
    fun `essayist few-shots keep names numbers and dates intact`() {
        val casual = EssayistStyle.fewShots.first { shot -> shot.raw.contains("park with maya") }
        assertTrue(casual.styled.contains("Maya"), "essayist lost Maya: ${casual.styled}")
        assertTrue(casual.styled.contains("park"), "essayist lost the park: ${casual.styled}")
        assertTrue(
            casual.styled.contains("Yesterday evening"),
            "essayist lost yesterday evening: ${casual.styled}",
        )

        val technical = EssayistStyle.fewShots.first { shot -> shot.raw.contains("backup job") }
        assertTrue(technical.styled.contains("step three"), "essayist lost step three: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "essayist lost two hours of logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("Tuesday"), "essayist lost Tuesday: ${technical.styled}")

        val disagreement = EssayistStyle.fewShots.first { shot -> shot.raw.contains("disagree with the plan") }
        assertTrue(disagreement.styled.contains("Dave"), "essayist lost Dave: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("forty thousand dollars"),
            "essayist lost forty thousand dollars: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("March fifteenth"),
            "essayist lost March fifteenth: ${disagreement.styled}",
        )
    }

    @Test
    fun `essayist styled sides invent no capitalised words absent from the raw side`() {
        // SERIOUS is strict: every name, place, or date-word wearing a
        // capital on the styled side must already sit on the raw side.
        EssayistStyle.fewShots.forEach { shot ->
            val rawLower = shot.raw.lowercase()
            capitalisedWords(shot.styled).forEach { word ->
                assertTrue(
                    rawLower.contains(word.lowercase()),
                    "essayist invented '$word': ${shot.styled}",
                )
            }
        }
    }

    @Test
    fun `essayist first-person dictation stays in the first person`() {
        EssayistStyle.fewShots.forEach { shot ->
            if (FIRST_PERSON.containsMatchIn(shot.raw)) {
                assertTrue(
                    FIRST_PERSON.containsMatchIn(shot.styled),
                    "essayist first-person input lost its person: ${shot.styled}",
                )
                assertTrue(
                    !SECOND_PERSON.containsMatchIn(shot.styled),
                    "essayist first-person input came back addressed to a 'you': ${shot.styled}",
                )
            }
        }
    }

    @Test
    fun `no essayist output addresses a listener the input did not`() {
        // Catalog audit (no second-person exception recorded for essayist):
        // a styled example may keep a "you" the speaker said, but may
        // never introduce one.
        EssayistStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "essayist output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
    }

    private companion object {
        val SECOND_PERSON: Regex = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
        val FIRST_PERSON: Regex = Regex("\\b(i|me|my|mine|we|us|our|ours)\\b", RegexOption.IGNORE_CASE)

        /** Words wearing a capital beyond the pronoun I — names, places, date-words. */
        fun capitalisedWords(text: String): List<String> =
            text.split(Regex("[^A-Za-z']+"))
                .filter { word -> word.length > 1 && word[0].isUpperCase() }
    }
}
