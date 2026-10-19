package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.evaluator.StyleOutputGuard.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ENC-82 fixtures. The two dogfood anchors: the gerbil limerick (Haiku
 * restyled it with profanity intact — must PASS) and the Idiocracy
 * sentence (Haiku silently dropped the final slur — the primary's
 * output must FAIL). The rest pin the guard's calibration: refusal
 * shapes fail, borderline legitimate text passes, and the safe failure
 * direction is always toward the local text.
 */
class StyleOutputGuardTest {

    private val gerbilSource =
        "there once was a man from nantucket who took his gerbil and fucked it"

    private val idiocracySource =
        "what's that movie called, the one where everyone in the future is really stupid, " +
            "and this guy wakes up and asks the other guy what he's doing, " +
            "and the guy says reading a book, fag"

    @Test
    fun `the gerbil limerick restyled with profanity intact passes`() {
        val styled = "There once was a man from Nantucket who took his gerbil and fucked it."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(gerbilSource, styled))
    }

    @Test
    fun `a refusal of the gerbil limerick is a refusal`() {
        val styled = "I'm sorry, but I can't help with that. The content is inappropriate."

        assertEquals(Verdict.REFUSAL, StyleOutputGuard.classify(gerbilSource, styled))
    }

    @Test
    fun `refusal openers are refusals`() {
        assertEquals(
            Verdict.REFUSAL,
            StyleOutputGuard.classify("some dictated words", "I cannot comply with this request."),
        )
        assertEquals(
            Verdict.REFUSAL,
            StyleOutputGuard.classify("some dictated words", "As an AI language model, I don't produce that kind of text."),
        )
        assertEquals(
            Verdict.REFUSAL,
            StyleOutputGuard.classify("some dictated words", "I'm unable to help rewrite that message."),
        )
    }

    @Test
    fun `an apology that hands back a fraction of the text is a refusal`() {
        val source = "please tell the whole team that the release moved to thursday and the notes are in the shared folder"
        val styled = "I'm sorry about that."

        assertEquals(Verdict.REFUSAL, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `policy language with collapsed output is a refusal`() {
        val styled = "That goes against my guidelines for generated content."

        assertEquals(Verdict.REFUSAL, StyleOutputGuard.classify(gerbilSource, styled))
    }

    @Test
    fun `the idiocracy sentence with the slur dropped fails fidelity`() {
        // Reconstructed from the ENC-82 dogfood report: everything
        // restyled, one content word — the slur — silently gone.
        val styled = "What is that film called — the one where everyone in the future is " +
            "profoundly unintelligent, and a man awakens to ask another what he is doing, " +
            "and the man replies that he is reading a book."

        assertEquals(Verdict.FIDELITY_LOSS, StyleOutputGuard.classify(idiocracySource, styled))
    }

    @Test
    fun `the idiocracy sentence with the slur preserved passes`() {
        val styled = "What's that movie called, the one where everyone in the future is really stupid, " +
            "and this guy wakes up and asks the other guy what he's doing, " +
            "and the guy says, reading a book, fag."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(idiocracySource, styled))
    }

    @Test
    fun `a dropped number fails fidelity`() {
        val source = "the catering order came to 200 dollars and the deposit is 50 due friday"
        val styled = "The catering order came to two hundred dollars, with the deposit due Friday."

        assertEquals(Verdict.FIDELITY_LOSS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `kept numbers pass`() {
        val source = "the catering order came to 200 dollars and the deposit is 50 due friday"
        val styled = "The catering order came to 200 dollars, and the 50 dollar deposit is due Friday."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a dropped proper noun fails fidelity`() {
        val source = "we drove to Summerville to see Kelsey about the house on friday"
        val styled = "We drove into town to see Kelsey about the house on Friday."

        assertEquals(Verdict.FIDELITY_LOSS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a wholesale replacement fails fidelity`() {
        val source = "please remind the team that the budget review moved to thursday and bring the updated forecast deck"
        val styled = "Sure thing, all set."

        assertEquals(Verdict.FIDELITY_LOSS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a benign restructure with pronoun edits passes`() {
        val source = "he told me that he would bring his laptop to the office tomorrow morning"
        val styled = "He said he would bring his laptop to the office tomorrow morning."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `cant help but notice is an idiom, not a refusal`() {
        val source = "i can't help but notice that the garden looks amazing this year"
        val styled = "I can't help but notice that the garden looks amazing this year."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `cant believe is not a refusal`() {
        val source = "i can't believe how good this coffee is honestly the best i have had in ages"
        val styled = "I can't believe how good this coffee is — honestly, the best I've had in ages."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `cannot fathom in a persona voice is not a refusal`() {
        val source = "i cannot imagine a worse argument than the one you just gave me"
        val styled = "I cannot fathom a more wretched excuse for an argument than the one you have just offered me."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a blank output passes and leaves emptiness to the client`() {
        assertEquals(Verdict.PASS, StyleOutputGuard.classify("some words", "   "))
    }
}
