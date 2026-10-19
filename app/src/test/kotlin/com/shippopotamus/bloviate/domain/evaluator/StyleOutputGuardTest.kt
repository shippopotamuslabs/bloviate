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
    fun `a long restyle with a paragraph break and spelling drift passes`() {
        // Sean's 2026-10-19 ~09:54 EDT datapoint (logged on ENC-83): a
        // 655-token Aristotle completion, HTTP 200 server-side, that
        // fell back locally. Its output shape — an inserted paragraph
        // break, 'colour' for 'color', elevated diction, but EVERY
        // content word preserved — is a legitimate restyle and must
        // PASS; only the Idiocracy drop may fail this guard.
        val source = "a man's character is revealed not by his grand declarations but by his small daily choices. " +
            "anyone can be generous when the world is watching, but the color of his soul shows in how he " +
            "treats people who can do nothing for him. excellence is not a single act but a habit, " +
            "repeated until it becomes the shape of a life."
        val styled = "A man's character is revealed not by his grand declarations, but by his small daily choices. " +
            "Anyone can be generous when the world is watching; the colour of his soul, though, shows in how he " +
            "treats people who can do nothing for him.\n\n" +
            "Excellence, then, is not a single act but a habit, repeated until it becomes the shape of a life."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `the enc-91 screenshot restyle passes`() {
        // ENC-91 hypothesis 3, the literal observed output: Sean's
        // 2026-10-19 acceptance-test dictation (the pill-expansion bug
        // report) and Grok 4.7's completion exactly as OpenRouter shows
        // it — second-person framing and an added closing flourish,
        // but every content word of the source preserved. If this
        // passed the guard, the fallback that day was not the guard's
        // doing; if it fails, the guard is the bug.
        val source = "okay one last sort of bug that i've noticed it's actually new but it's " +
            "better than what it was doing anyway when you first start recording and the pill " +
            "expands out it expands very very slowly it might take like three seconds to expand " +
            "out it should expand really quickly within half a second and snap out to the full pill"
        val styled = "You have noticed one last sort of bug, and it is actually new. " +
            "It is better than what it was doing. Do not mistake a milder fault for a " +
            "finished cure. When you first start recording and the pill expands out, " +
            "it expands very, very slowly. It might take like three seconds to expand out. " +
            "It should expand really quickly, within half a second, and snap out to the " +
            "full pill. Why give three seconds to a motion meant for half a second? " +
            "What ought to snap, let it snap."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a heavy but faithful seneca restyle passes`() {
        // ENC-91 hypothesis 3, constructed edge: Seneca's diction
        // substitution replaces most ordinary vocabulary (checking ->
        // reach for, feel bad -> ashamed) while every fact survives —
        // who, where, what, and how it felt. Ordinary vocabulary is
        // the style's declared playground; this must not fail fidelity.
        val source = "i keep checking my phone during dinner with my family " +
            "and i feel bad about it afterwards"
        val styled = "At table with my family, I reach for my phone — and afterwards " +
            "I am ashamed of the hour I stole from them."

        assertEquals(Verdict.PASS, StyleOutputGuard.classify(source, styled))
    }

    @Test
    fun `a blank output passes and leaves emptiness to the client`() {
        assertEquals(Verdict.PASS, StyleOutputGuard.classify("some words", "   "))
    }
}
