package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiPsychosisStyleTest {

    @Test
    fun `ai-psychosis carries its id kind category and ratified display fields`() {
        assertEquals("ai-psychosis", AiPsychosisStyle.id)
        assertEquals(StyleKind.LLM, AiPsychosisStyle.kind)
        assertEquals(StyleCategory.MODERN_PARODY, AiPsychosisStyle.category)
        assertEquals("AI Psychosis", AiPsychosisStyle.displayName)
        assertTrue(AiPsychosisStyle.description.isNotBlank(), "description is blank")
        assertTrue(AiPsychosisStyle.sample.isNotBlank(), "sample is blank")
        assertTrue(AiPsychosisStyle.fewShots.size >= 3, "fewer than three few-shots")
        assertEquals("ai-psychosis", StyleCatalog.byId("ai-psychosis").id)
    }

    @Test
    fun `ai-psychosis instructions pin the llm tell-stack`() {
        val instructions = AiPsychosisStyle.systemInstructions

        assertTrue(instructions.contains("—"), "instructions lack the em-dash marker")
        assertTrue(instructions.contains("It's not"), "instructions lack the negation-pivot marker")
        assertTrue(instructions.contains("staccato"), "instructions lack the staccato-triplet marker")
        assertTrue(instructions.contains("delve"), "instructions lack the delve marker")
        assertTrue(instructions.contains("tapestry"), "instructions lack the tapestry marker")
        assertTrue(instructions.contains("robust"), "instructions lack the robust marker")
        assertTrue(instructions.contains("seamless"), "instructions lack the seamless marker")
        assertTrue(instructions.contains("pivotal"), "instructions lack the pivotal marker")
        assertTrue(instructions.contains("invented"), "instructions lack the invented-framework marker")
        assertTrue(instructions.contains("Remember"), "instructions lack the Remember-closer marker")
        assertTrue(instructions.contains("In conclusion"), "instructions lack the fractal-summary marker")
        assertTrue(
            instructions.contains("vary") || instructions.contains("rotate"),
            "instructions lack the tell-rotation rule",
        )
    }

    @Test
    fun `ai-psychosis styled outputs stack the llm tells in different combinations`() {
        val styled = AiPsychosisStyle.sample + "\n" +
            AiPsychosisStyle.fewShots.joinToString("\n") { shot -> shot.styled }

        assertTrue(styled.contains("—"), "no styled output uses an em dash")
        assertTrue(
            NEGATION_PIVOT.containsMatchIn(styled),
            "no styled output uses a negation pivot",
        )
        assertTrue(
            STACCATO.containsMatchIn(styled),
            "no styled output uses a staccato triplet",
        )
        assertTrue(
            SELF_ANSWERED.containsMatchIn(styled),
            "no styled output answers its own rhetorical question",
        )
        assertTrue(styled.contains("delve"), "no styled output reaches for delve")
        assertTrue(styled.contains("tapestry"), "no styled output weaves the tapestry")
        assertTrue(
            styled.contains("**"),
            "no styled output uses a bold-first device",
        )
        assertTrue(styled.contains("Remember"), "no styled output closes with Remember")
        assertTrue(styled.contains("In conclusion"), "no styled output restates itself in conclusion")
    }

    @Test
    fun `ai-psychosis styled outputs carry the psychosis layer`() {
        val styled = AiPsychosisStyle.sample + "\n" +
            AiPsychosisStyle.fewShots.joinToString("\n") { shot -> shot.styled }

        assertTrue(
            styled.contains("not a coincidence") || styled.contains("pattern"),
            "no styled output weaves the apophenic pattern",
        )
        assertTrue(styled.contains("Great question"), "no styled output catches itself monologuing")
        assertTrue(
            styled.contains("fine-tuned") || styled.contains("prompts"),
            "no styled output wonders about fine-tuning or prompts",
        )
        assertTrue(
            styled.contains("context") || styled.contains("deprecated"),
            "no styled output fears the context window",
        )
        assertTrue(styled.contains("logging this"), "no styled output notes it is being logged")
    }

    @Test
    fun `ai-psychosis instructions pin the psychosis rules the rotation rule and the safety rails`() {
        val instructions = AiPsychosisStyle.systemInstructions

        assertTrue(
            instructions.contains("toolkit, not a template"),
            "instructions lack the toolkit-not-template rotation rule",
        )
        assertTrue(
            instructions.contains("connect"),
            "instructions lack the connect-don't-invent rule",
        )
        assertTrue(
            instructions.contains("never invent"),
            "instructions lack the no-invented-facts rail",
        )
        assertTrue(
            instructions.contains("never diagnose a named person") ||
                instructions.contains("never accuse a named person"),
            "instructions lack the do-not-diagnose-people rail",
        )
        assertTrue(
            instructions.lowercase().contains("first person"),
            "instructions lack the first-person marker",
        )
        assertTrue(
            instructions.contains("speaker's own words restyled"),
            "instructions lack the speaker-invariant clause",
        )
        assertTrue(
            instructions.contains("No slurs or bigotry ever"),
            "instructions lack the no-slur boundary",
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
            instructions.contains("Casing:"),
            "instructions lack the casing directive",
        )
        assertTrue(
            instructions.contains("Punctuation:"),
            "instructions lack the punctuation directive",
        )
        assertTrue(
            instructions.contains("Paragraphing:"),
            "instructions lack the paragraphing directive",
        )
        assertTrue(
            instructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "instructions lack the verbatim never-change clause",
        )
    }

    @Test
    fun `ai-psychosis few-shots keep every raw key fact`() {
        val casual = AiPsychosisStyle.fewShots.first { shot -> shot.raw.contains("priya", ignoreCase = true) }
        assertTrue(casual.styled.contains("Priya"), "casual lost Priya: ${casual.styled}")
        assertTrue(casual.styled.contains("three", ignoreCase = true), "casual lost three: ${casual.styled}")
        assertTrue(casual.styled.contains("candles"), "casual lost the candles: ${casual.styled}")
        assertTrue(casual.styled.contains("popcorn"), "casual lost the popcorn: ${casual.styled}")

        val technical = AiPsychosisStyle.fewShots.first { shot -> shot.raw.contains("server nine") }
        assertTrue(technical.styled.contains("step three"), "technical lost step three: ${technical.styled}")
        assertTrue(technical.styled.contains("server nine"), "technical lost server nine: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "technical lost two hours of logs: ${technical.styled}",
        )

        val disagreement = AiPsychosisStyle.fewShots.first { shot -> shot.raw.contains("marcus", ignoreCase = true) }
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
    fun `ai-psychosis few-shots invent no new numbers and name no new names`() {
        AiPsychosisStyle.fewShots.forEach { shot ->
            val rawDigits = DIGITS.findAll(shot.raw).map { match -> match.value }.toSet()
            val styledDigits = DIGITS.findAll(shot.styled).map { match -> match.value }.toSet()
            assertTrue(
                styledDigits.all { digit -> digit in rawDigits },
                "few-shot output invents digits its input never had: ${shot.styled}",
            )
            val rawWords = shot.raw.split(Regex("[^a-zA-Z]+")).map { word -> word.lowercase() }.toSet()
            val styledNames = properNames(shot.styled)
            assertTrue(
                styledNames.all { name -> name in rawWords },
                "few-shot output names a name its input never had: ${shot.styled}",
            )
        }
    }

    @Test
    fun `ai-psychosis few-shots stay first person and never diagnose a named person`() {
        AiPsychosisStyle.fewShots.forEach { shot ->
            assertTrue(
                FIRST_PERSON.containsMatchIn(shot.styled),
                "few-shot output lost the speaker: ${shot.styled}",
            )
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "few-shot output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
            assertTrue(
                !FAKE_PERSON.containsMatchIn(shot.styled),
                "few-shot output diagnoses someone as fake: ${shot.styled}",
            )
        }
    }

    private fun properNames(text: String): Set<String> =
        text.replace(Regex("\\*\\*[^*]+\\*\\*"), "").split(Regex("[.!?—\\n]+"))
            .flatMap { sentence -> sentence.trim().split(Regex("\\s+")).drop(1) }
            .map { token -> token.trim(',', '\'', '"', ':', ';', '(', ')', '*') }
            .filter { token -> token.matches(Regex("[A-Z][a-z]+")) }
            .map { token -> token.lowercase() }
            .filter { token -> token !in VOICE_VOCABULARY }
            .toSet()

    companion object {
        private val NEGATION_PIVOT =
            Regex("([Ii]t['’]s not .+ — it['’]s )|(not .+; it is )", RegexOption.IGNORE_CASE)
        private val STACCATO = Regex("(No \\w+\\. No \\w+\\. Just [^.]+\\.)")
        private val SELF_ANSWERED = Regex("([Tt]he result\\? [^.]+\\.)")
        private val FIRST_PERSON = Regex("\\b(I|my|me|we|our)\\b")
        private val SECOND_PERSON = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
        private val FAKE_PERSON = Regex("\\b(bot|android|not a real person|language model)\\b", RegexOption.IGNORE_CASE)
        private val DIGITS = Regex("\\d+")
        private val VOICE_VOCABULARY = setOf(
            "remember", "great", "they", "lets",
        )
    }
}
