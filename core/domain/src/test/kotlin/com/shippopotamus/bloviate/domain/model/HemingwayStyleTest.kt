package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HemingwayStyleTest {

    @Test
    fun `hemingway is an llm style with non-blank display fields`() {
        assertEquals("hemingway", HemingwayStyle.id)
        assertEquals(StyleKind.LLM, HemingwayStyle.kind)
        assertTrue(HemingwayStyle.displayName.isNotBlank(), "hemingway displayName is blank")
        assertTrue(HemingwayStyle.description.isNotBlank(), "hemingway description is blank")
        assertTrue(HemingwayStyle.sample.isNotBlank(), "hemingway sample is blank")
    }

    @Test
    fun `hemingway instructions carry the register and the never-change clause`() {
        assertTrue(
            HemingwayStyle.systemInstructions.contains("Parataxis"),
            "hemingway names no paratactic register",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains("iceberg"),
            "hemingway lacks the iceberg directive",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains("NEVER write a periodic sentence"),
            "hemingway does not pin the anti-Colonel ban",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains("that dilation belongs to the Colonel"),
            "hemingway does not name the Colonel as the contrast",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains("transcript's own language"),
            "hemingway lacks the register-of-the-language directive",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains("never translate"),
            "hemingway does not forbid translating into English",
        )
        assertTrue(
            HemingwayStyle.systemInstructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "hemingway lacks the never-change clause",
        )
    }

    @Test
    fun `hemingway few-shots keep names numbers and objects intact`() {
        val casual = HemingwayStyle.fewShots.first { shot -> shot.raw.contains("maria") }
        assertTrue(casual.styled.contains("Maria"), "hemingway lost Maria: ${casual.styled}")
        assertTrue(casual.styled.contains("milk"), "hemingway lost the milk: ${casual.styled}")
        assertTrue(casual.styled.contains("bread"), "hemingway lost the bread: ${casual.styled}")
        assertTrue(casual.styled.contains("twelve bucks"), "hemingway lost the price: ${casual.styled}")

        val technical = HemingwayStyle.fewShots.first { shot -> shot.raw.contains("database migration") }
        assertTrue(
            technical.styled.contains("database migration"),
            "hemingway lost the migration: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("users table"),
            "hemingway lost the users table: ${technical.styled}",
        )
        assertTrue(
            technical.styled.contains("two in the morning"),
            "hemingway lost the time: ${technical.styled}",
        )

        val emotional = HemingwayStyle.fewShots.first { shot -> shot.raw.contains("david") }
        assertTrue(emotional.styled.contains("David"), "hemingway lost David: ${emotional.styled}")
        assertTrue(
            emotional.styled.contains("budget is too high"),
            "hemingway lost the budget: ${emotional.styled}",
        )
        assertTrue(emotional.styled.contains("Friday"), "hemingway lost Friday: ${emotional.styled}")
    }

    @Test
    fun `hemingway few-shots are short declarative sentences without periodic swell`() {
        // ENC-172's measurable shape: parataxis, not period — no styled
        // sentence runs past twelve words, and no semicolon smuggles a
        // subordinate clause back in.
        assertTrue(HemingwayStyle.fewShots.size >= 3, "hemingway has fewer than three few-shots")
        HemingwayStyle.fewShots.forEach { shot ->
            val sentences = shot.styled.split(Regex("[.!?]"))
                .map { part -> part.trim() }
                .filter { part -> part.isNotEmpty() }
            assertTrue(sentences.isNotEmpty(), "hemingway styled side has no sentences: ${shot.styled}")
            sentences.forEach { sentence ->
                val words = sentence.split(Regex("\\s+")).size
                assertTrue(words <= 12, "hemingway sentence runs long ($words words): $sentence")
            }
            assertTrue(!shot.styled.contains(";"), "hemingway styled side uses a semicolon: ${shot.styled}")
        }
    }

    @Test
    fun `hemingway outputs stay in the speaker's own voice`() {
        // No recorded second-person exception: a styled example may keep a
        // "you" the speaker said, but never introduce one — and the output
        // is the speaker's own words restyled, never a reply.
        assertTrue(
            HemingwayStyle.systemInstructions.contains("never addressing a listener"),
            "hemingway lacks the speaker-invariant directive",
        )
        HemingwayStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "hemingway few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
    }

    private companion object {
        /** Second-person pronouns — the mark of output addressed back at the speaker. */
        val SECOND_PERSON: Regex = Regex("""\b(you|your|yours|yourself|yourselves)\b""", RegexOption.IGNORE_CASE)
    }
}
