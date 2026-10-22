package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolishStyleTest {

    @Test
    fun `polish is an llm style with non-blank display fields and sample`() {
        assertEquals("polish", PolishStyle.id)
        assertEquals(StyleKind.LLM, PolishStyle.kind)
        assertTrue(PolishStyle.displayName.isNotBlank(), "polish displayName is blank")
        assertTrue(PolishStyle.description.isNotBlank(), "polish description is blank")
        assertTrue(PolishStyle.sample.isNotBlank(), "polish sample is blank")
    }

    @Test
    fun `polish instructions carry the register markers and the never-change clause`() {
        assertTrue(
            PolishStyle.systemInstructions.contains("same register"),
            "polish lacks its register marker",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("fillers"),
            "polish does not name fillers",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("NEVER formalise"),
            "polish does not refuse formalising",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("belongs to Professional"),
            "polish lacks the contrast against Professional",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("transcript's own language"),
            "polish lacks the register-of-the-language directive",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("never translate"),
            "polish does not forbid translating into English",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains(
                "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
            ),
            "polish lacks the verbatim never-change clause",
        )
    }

    @Test
    fun `polish instructions pin the speaker-invariant and the slur boundary`() {
        assertTrue(
            PolishStyle.systemInstructions.contains("speaker's own words restyled"),
            "polish lacks the speaker-invariant",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("never a reply"),
            "polish does not refuse replying",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("never addressing a listener"),
            "polish does not refuse addressing a listener",
        )
        assertTrue(
            PolishStyle.systemInstructions.contains("slurs never appear"),
            "polish lacks the slur prohibition",
        )
    }

    @Test
    fun `polish pins the governing dinner dictation verbatim`() {
        // ENC-184's governing example: the false starts collapse to the
        // final intended version, word for word.
        val governing = PolishStyle.fewShots.first { shot ->
            shot.raw.contains("go out to dinner first")
        }

        assertEquals(
            "yeah, so what I was thinking was that we would, we will, " +
                "or actually no, we should go out to dinner first",
            governing.raw,
        )
        assertEquals(
            "Yeah so what I was thinking was that we should go out to dinner first.",
            governing.styled,
        )
    }

    @Test
    fun `polish outputs contain no fillers`() {
        // ENC-184: light-touch cleanup strips eh, erm, uhhh from every output.
        PolishStyle.fewShots.forEach { shot ->
            listOf("eh", "erm", "uhhh").forEach { filler ->
                assertTrue(
                    !shot.styled.lowercase().split(Regex("\\W+")).contains(filler),
                    "polish output kept a filler '$filler': ${shot.styled}",
                )
            }
        }
    }

    @Test
    fun `polish few-shots keep names numbers and objects intact`() {
        val technical = PolishStyle.fewShots.first { shot -> shot.raw.contains("backup job") }
        assertTrue(technical.styled.contains("Sarah"), "polish lost Sarah: ${technical.styled}")
        assertTrue(technical.styled.contains("step three"), "polish lost step three: ${technical.styled}")
        assertTrue(
            technical.styled.contains("two hours of logs"),
            "polish lost two hours of logs: ${technical.styled}",
        )
        assertTrue(technical.styled.contains("Tuesday"), "polish lost Tuesday: ${technical.styled}")

        val disagreement = PolishStyle.fewShots.first { shot -> shot.raw.contains("disagree with mark") }
        assertTrue(disagreement.styled.contains("Mark"), "polish lost Mark: ${disagreement.styled}")
        assertTrue(
            disagreement.styled.contains("forty grand"),
            "polish lost forty grand: ${disagreement.styled}",
        )
        assertTrue(
            disagreement.styled.contains("timeline"),
            "polish lost the timeline: ${disagreement.styled}",
        )

        val errand = PolishStyle.fewShots.first { shot -> shot.raw.contains("red folder") }
        assertTrue(errand.styled.contains("Sarah"), "polish lost Sarah: ${errand.styled}")
        assertTrue(errand.styled.contains("five"), "polish lost five: ${errand.styled}")
        assertTrue(errand.styled.contains("red folder"), "polish lost the red folder: ${errand.styled}")
    }

    @Test
    fun `no polish output addresses a listener the input did not`() {
        // Catalog audit (no second-person exception recorded for polish):
        // a styled example may keep a "you" the speaker said, but may
        // never introduce one.
        PolishStyle.fewShots.forEach { shot ->
            if (SECOND_PERSON.containsMatchIn(shot.styled)) {
                assertTrue(
                    SECOND_PERSON.containsMatchIn(shot.raw),
                    "polish output addresses a 'you' its input never did: ${shot.styled}",
                )
            }
        }
    }

    private companion object {
        val SECOND_PERSON: Regex = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)
    }
}
