package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AugustineStyleTest {

    @Test
    fun `augustine pins its identity as an llm style with non-blank display fields`() {
        assertEquals("augustine", AugustineStyle.id)
        assertEquals("Augustine", AugustineStyle.displayName)
        assertEquals("Confession, memory, and a restless heart honestly examined.", AugustineStyle.description)
        assertEquals("I did it for no gain at all. I loved the fault itself — that is the confession.", AugustineStyle.sample)
        assertEquals(StyleKind.LLM, AugustineStyle.kind)
        assertEquals(StyleCategory.PHILOSOPHERS, AugustineStyle.category)
        assertTrue(AugustineStyle.displayName.isNotBlank(), "augustine display name is blank")
        assertTrue(AugustineStyle.description.isNotBlank(), "augustine description is blank")
        assertTrue(AugustineStyle.sample.isNotBlank(), "augustine sample is blank")
    }

    @Test
    fun `augustine instructions carry the restless-heart anchors and the never-change clause`() {
        val instructions = AugustineStyle.systemInstructions

        assertTrue(
            instructions.contains("our heart is restless until it rests in you"),
            "augustine lost the restless-heart anchor",
        )
        assertTrue(instructions.contains("(Confessions I.1)"), "augustine lost the Confessions I.1 attribution")
        assertTrue(instructions.contains("Late have I loved you"), "augustine lost the late-love anchor")
        assertTrue(instructions.contains("(Confessions X.27)"), "augustine lost the Confessions X.27 attribution")
        assertTrue(instructions.contains("restless heart"), "augustine names no restless heart")
        assertTrue(instructions.contains("stolen pears"), "augustine names no stolen pears")
        assertTrue(instructions.contains("fault loved for itself"), "augustine names no fault loved for itself")
        assertTrue(instructions.contains("divided will"), "augustine names no divided will")
        assertTrue(instructions.contains("palace of memory"), "augustine names no palace of memory")
        assertTrue(instructions.contains("without varnish"), "augustine names no unvarnished confession")
        assertTrue(instructions.contains("transcript's own language"), "augustine lacks the own-language directive")
        assertTrue(instructions.contains("never translate"), "augustine does not forbid translating into English")
        assertTrue(
            instructions.contains("NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."),
            "augustine lacks the never-change clause",
        )
    }

    @Test
    fun `augustine resolves by admission and refuses the other six voices`() {
        val instructions = AugustineStyle.systemInstructions

        assertTrue(
            instructions.contains("resolved by admission and surrender, never by argument won"),
            "augustine does not resolve by admission and surrender",
        )
        assertTrue(
            instructions.contains("NEVER settle the question by quaestio or argument — that settlement belongs to Aquinas"),
            "augustine does not refuse Aquinas's quaestio settlement",
        )
        assertTrue(
            instructions.contains("confession resolves by admission and surrender, not by objections answered"),
            "augustine does not bound its resolution to admission over argument",
        )
        assertTrue(
            instructions.contains("NEVER end in mere aporia or confessed ignorance — that open ending belongs to Socrates"),
            "augustine does not refuse Socrates' aporia",
        )
        assertTrue(
            instructions.contains("NEVER rise to Forms through dialogue or myth — that ascent belongs to Plato"),
            "augustine does not refuse Plato's Forms and myth",
        )
        assertTrue(
            instructions.contains("NEVER sort the matter into kinds and causes — that taxonomy belongs to Aristotle"),
            "augustine does not refuse Aristotle's taxonomy",
        )
        assertTrue(
            instructions.contains("NEVER moralize in a letter's aphorisms for an audience — that performance belongs to Seneca"),
            "augustine does not refuse Seneca's letter aphorisms",
        )
        assertTrue(
            instructions.contains("NEVER command the self in a private notebook — that privacy belongs to Marcus Aurelius"),
            "augustine does not refuse Marcus's notebook commands",
        )
    }

    @Test
    fun `augustine few-shots keep every raw key fact`() {
        assertEquals(4, AugustineStyle.fewShots.size, "augustine needs exactly four few-shots")

        val evenings = AugustineStyle.fewShots.first { shot -> shot.raw.contains("scrolling my phone") }
        assertTrue(evenings.styled.contains("scrolling my phone"), "augustine dropped the phone: ${evenings.styled}")
        assertTrue(evenings.styled.contains("evenings"), "augustine dropped the evenings: ${evenings.styled}")
        assertTrue(evenings.styled.contains("angry at myself"), "augustine dropped the anger: ${evenings.styled}")
        assertTrue(evenings.styled.contains("pears I stole"), "augustine evenings lost the pear motif: ${evenings.styled}")

        val team = AugustineStyle.fewShots.first { shot -> shot.raw.contains("rewrite the app") }
        assertTrue(team.styled.contains("rewrite the app"), "augustine dropped the rewrite: ${team.styled}")
        assertTrue(team.styled.contains("patch it"), "augustine dropped the patch: ${team.styled}")
        assertTrue(team.styled.contains("nothing gets decided"), "augustine dropped the indecision: ${team.styled}")

        val presentation = AugustineStyle.fewShots.first { shot -> shot.raw.contains("presentation on friday") }
        assertTrue(presentation.styled.contains("Friday"), "augustine dropped Friday: ${presentation.styled}")
        assertTrue(presentation.styled.contains("judgment"), "augustine dropped the judgment: ${presentation.styled}")

        val exercise = AugustineStyle.fewShots.first { shot -> shot.raw.contains("exercise every morning") }
        assertTrue(exercise.styled.contains("exercise every morning"), "augustine dropped the exercise: ${exercise.styled}")
        assertTrue(exercise.styled.contains("never actually start"), "augustine dropped the non-start: ${exercise.styled}")
    }

    @Test
    fun `augustine few-shots confess in the first person and end surrendered`() {
        AugustineStyle.fewShots.forEach { shot ->
            assertTrue(shot.styled.contains("I "), "augustine leaves the first person: ${shot.styled}")
        }
        assertTrue(
            AugustineStyle.fewShots.count { shot ->
                shot.styled.contains("confess") || shot.styled.contains("confession") || shot.styled.contains("confessed")
            } >= 3,
            "augustine few-shots do not confess",
        )
        val evenings = AugustineStyle.fewShots.first { shot -> shot.raw.contains("scrolling my phone") }
        assertTrue(evenings.styled.contains("divided"), "augustine evenings name no divided will: ${evenings.styled}")
    }

    @Test
    fun `augustine never addresses a listener the input did not name`() {
        // ENC-92 audit idiom: no second-person exception is recorded for
        // this voice, so neither the raw dictations nor the restyled
        // outputs may reach for a listener with second-person address.
        // The real Confessions address God as "you" — the output must not.
        val secondPerson = Regex("\\b(you|your|yours|yourself|yourselves)\\b", RegexOption.IGNORE_CASE)

        AugustineStyle.fewShots.forEach { shot ->
            assertTrue(!secondPerson.containsMatchIn(shot.raw), "augustine raw reaches for a listener: ${shot.raw}")
            assertTrue(!secondPerson.containsMatchIn(shot.styled), "augustine styled reaches for a listener: ${shot.styled}")
        }
    }
}
