package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DictionaryCorrectorTest {

    private val corrector = DictionaryCorrector()

    private fun dictionary(
        words: List<String> = emptyList(),
        corrections: List<Pair<String, String>> = emptyList(),
    ): PersonalDictionary = PersonalDictionary(
        words = words,
        corrections = corrections.map { (heardAs, replacement) ->
            PersonalDictionary.Correction(heardAs = heardAs, replacement = replacement)
        },
    )

    @Test
    fun `an empty dictionary is the identity`() {
        val text = "perikit said below v8 runs on bloviate"
        assertEquals(text, corrector.apply(text, PersonalDictionary.EMPTY))
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", corrector.apply("", PersonalDictionary.SEED))
    }

    @Test
    fun `a correction replaces the mis-hearing with the stored replacement`() {
        val result = corrector.apply(
            "i dictated that with perikit",
            dictionary(corrections = listOf("perikit" to "Parakeet")),
        )
        assertEquals("i dictated that with Parakeet", result)
    }

    @Test
    fun `correction matching is case-insensitive and the replacement is verbatim`() {
        val result = corrector.apply(
            "PERIKIT is loud. Perikit is loud.",
            dictionary(corrections = listOf("perikit" to "Parakeet")),
        )
        assertEquals("Parakeet is loud. Parakeet is loud.", result)
    }

    @Test
    fun `a multi-word correction phrase matches across its spaces`() {
        val result = corrector.apply(
            "that take had real below v8 energy",
            dictionary(corrections = listOf("below v8" to "Bloviate")),
        )
        assertEquals("that take had real Bloviate energy", result)
    }

    @Test
    fun `every occurrence is corrected, not just the first`() {
        val result = corrector.apply(
            "perikit met perikit at noon",
            dictionary(corrections = listOf("perikit" to "Parakeet")),
        )
        assertEquals("Parakeet met Parakeet at noon", result)
    }

    @Test
    fun `the longest overlapping correction wins over its own tail`() {
        val result = corrector.apply(
            "below v8 and a plain v8",
            dictionary(corrections = listOf("v8" to "V-8", "below v8" to "Bloviate")),
        )
        assertEquals("Bloviate and a plain V-8", result)
    }

    @Test
    fun `a correction never fires inside a larger word`() {
        val result = corrector.apply(
            "bloviated takes and the unbloviate manifesto",
            dictionary(corrections = listOf("bloviate" to "REDACTED")),
        )
        assertEquals("bloviated takes and the unbloviate manifesto", result)
    }

    @Test
    fun `a digit flank also blocks a match`() {
        val result = corrector.apply(
            "the v80 build and a v8",
            dictionary(corrections = listOf("v8" to "V-8")),
        )
        assertEquals("the v80 build and a V-8", result)
    }

    @Test
    fun `punctuation and string edges are valid boundaries`() {
        val result = corrector.apply(
            "(perikit), said perikit.",
            dictionary(corrections = listOf("perikit" to "Parakeet")),
        )
        assertEquals("(Parakeet), said Parakeet.", result)
    }

    @Test
    fun `correction output is not re-scanned by another correction in the same pass`() {
        val result = corrector.apply(
            "say a b today",
            dictionary(corrections = listOf("a b" to "c d", "c d" to "e f")),
        )
        assertEquals("say c d today", result)
    }

    @Test
    fun `equal-length overlaps resolve to the earliest match`() {
        val result = corrector.apply(
            "a b c",
            dictionary(corrections = listOf("a b" to "X", "b c" to "Y")),
        )
        assertEquals("X c", result)
    }

    @Test
    fun `casing restores the canonical form of a word in any case`() {
        val result = corrector.apply(
            "i use bloviate and PARAKEET and BlOvIaTe daily",
            dictionary(words = listOf("Bloviate", "Parakeet")),
        )
        assertEquals("i use Bloviate and Parakeet and Bloviate daily", result)
    }

    @Test
    fun `casing never fires inside a larger word`() {
        val result = corrector.apply(
            "bloviated software",
            dictionary(words = listOf("Bloviate")),
        )
        assertEquals("bloviated software", result)
    }

    @Test
    fun `a hyphen is a boundary, so a compound's segment gets its casing`() {
        val result = corrector.apply(
            "a bloviate-powered bubble",
            dictionary(words = listOf("Bloviate")),
        )
        assertEquals("a Bloviate-powered bubble", result)
    }

    @Test
    fun `a correction producing a lowercase dictionary word is cased by the casing pass`() {
        val result = corrector.apply(
            "that was blow v8 behavior",
            dictionary(
                words = listOf("Bloviate"),
                corrections = listOf("blow v8" to "bloviate"),
            ),
        )
        assertEquals("that was Bloviate behavior", result)
    }

    @Test
    fun `the seeded starters fix the observed on-device mis-hearings`() {
        val result = corrector.apply(
            "perikit heard it as below v8, or blow-viate, or blow viate",
            PersonalDictionary.SEED,
        )
        assertEquals("Parakeet heard it as Bloviate, or Bloviate, or Bloviate", result)
    }
}
