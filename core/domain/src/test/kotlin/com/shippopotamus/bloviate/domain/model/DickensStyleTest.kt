package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DickensStyleTest {

    @Test
    fun `dickens is an llm style with non-blank display fields and sample`() {
        assertEquals("dickens", DickensStyle.id)
        assertEquals(StyleKind.LLM, DickensStyle.kind)
        assertTrue(DickensStyle.displayName.isNotBlank(), "dickens displayName is blank")
        assertTrue(DickensStyle.description.isNotBlank(), "dickens description is blank")
        assertTrue(DickensStyle.sample.isNotBlank(), "dickens sample is blank")
    }

    @Test
    fun `dickens instructions carry the register markers and the never-change clause`() {
        assertTrue(
            DickensStyle.systemInstructions.contains("moral weather"),
            "dickens names no moral-weather register",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains("grotesque-comic"),
            "dickens names no grotesque-comic register",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains("rolling periodic sentences"),
            "dickens names no periodic-sentence register",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "dickens lacks the never-change clause",
        )
    }

    @Test
    fun `dickens refuses the colonel register and carries the language directive`() {
        assertTrue(
            DickensStyle.systemInstructions.contains("NEVER retreat into courtly"),
            "dickens does not refuse the Colonel's courtly restraint",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains("Colonel"),
            "dickens does not name the Colonel contrast",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains("transcript's own language"),
            "dickens lacks the register-of-the-language directive",
        )
        assertTrue(
            DickensStyle.systemInstructions.contains("never translate"),
            "dickens does not forbid translating into English",
        )
    }

    @Test
    fun `dickens few-shots keep every raw key fact`() {
        val casual = DickensStyle.fewShots.first { shot -> shot.raw.contains("phone") }
        assertTrue(casual.styled.contains("phone"), "dickens renamed the speaker's phone: ${casual.styled}")
        assertTrue(casual.styled.contains("battery"), "dickens dropped the battery: ${casual.styled}")
        assertTrue(casual.styled.contains("children"), "dickens dropped the children: ${casual.styled}")
        assertTrue(casual.styled.contains("park"), "dickens dropped the park: ${casual.styled}")

        val technical = DickensStyle.fewShots.first { shot -> shot.raw.contains("database") }
        assertTrue(
            technical.styled.contains("backup script"),
            "dickens lost the script: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("third step"),
            "dickens lost the step count: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("database migration"),
            "dickens lost the migration: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "dickens lost the logs: ${technical.styled}",
        )

        val disagreement = DickensStyle.fewShots.first { shot -> shot.raw.contains("disagree") }
        assertTrue(
            disagreement.styled.contains("disagree"),
            "dickens lost the disagreement: ${disagreement.styled}",
        )
        assertTrue(disagreement.styled.contains("cost"), "dickens lost the cost: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("timeline"),
            "dickens lost the timeline: ${disagreement.styled}",
        )
    }

    @Test
    fun `dickens builds rolling periods — every example swells to a long sentence`() {
        // ENC-173's measurable architecture: the Dickensian period is a
        // long sentence carrying its main clause at the last, so every
        // styled example must contain at least one sentence of twenty
        // words or more — the same ruler the Colonel test uses.
        assertTrue(DickensStyle.fewShots.size >= 3, "dickens has fewer than three few-shots")
        DickensStyle.fewShots.forEach { shot ->
            val longest = sentencesOf(shot.styled).maxOf { sentence -> wordCount(sentence) }
            assertTrue(longest >= 20, "the dickens example never swells to a period: ${shot.styled}")
        }
    }

    @Test
    fun `no dickens few-shot output addresses a listener the input did not`() {
        // ENC-173 records no second-person exception: Dickensian reader
        // address stays "reader"-shaped and never takes a "you" the raw
        // dictation did not say, per the ENC-92 catalog audit.
        DickensStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "dickens few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
    }

    @Test
    fun `dickens few-shots exercise licensed invention without inventing facts`() {
        // BLO-158 licensed-invention pin: a non-SERIOUS style's examples
        // may add atmospheric texture absent from the raw side — the
        // parliament of pigeons was never dictated — while carrying no
        // invented number, date, name, or quote, and keeping every stated
        // fact (park, phone, battery) intact.
        val casual = DickensStyle.fewShots.first { shot -> shot.raw.contains("phone") }

        assertTrue(
            casual.styled.contains("parliament"),
            "dickens added no atmospheric texture: ${casual.styled}",
        )
        assertTrue(
            !casual.raw.contains("parliament"),
            "the texture word was already in the raw side",
        )
        assertTrue(casual.styled.contains("park"), "dickens dropped the park: ${casual.styled}")
        assertTrue(casual.styled.contains("phone"), "dickens renamed the phone: ${casual.styled}")
        assertTrue(casual.styled.contains("battery"), "dickens dropped the battery: ${casual.styled}")
        assertTrue(
            casual.styled.none { char -> char.isDigit() },
            "dickens invented a number: ${casual.styled}",
        )
        assertTrue(
            !casual.styled.contains("\""),
            "dickens invented a quote: ${casual.styled}",
        )
    }

    @Test
    fun `dickens first-person fixtures stay in the first person`() {
        DickensStyle.fewShots.forEach { shot ->
            if (FIRST_PERSON.containsMatchIn(shot.raw)) {
                assertTrue(
                    FIRST_PERSON.containsMatchIn(shot.styled),
                    "dickens first-person input lost its person: ${shot.styled}",
                )
                assertTrue(
                    !SECOND_PERSON.containsMatchIn(shot.styled),
                    "dickens first-person input came back addressed to a 'you': ${shot.styled}",
                )
            }
        }
    }

    private fun sentencesOf(text: String): List<String> =
        text.split(Regex("[.!?]")).map { part -> part.trim() }.filter { part -> part.isNotEmpty() }

    private fun wordCount(sentence: String): Int = sentence.split(Regex("\\s+")).size

    private companion object {
        val SECOND_PERSON: Regex = Regex("""\b(you|your|yours|yourself|yourselves)\b""", RegexOption.IGNORE_CASE)
        val FIRST_PERSON: Regex =
            Regex("""\b(i|i'm|i've|i'll|i'd|my|me|mine|we|our|ours|us)\b""", RegexOption.IGNORE_CASE)
    }
}
