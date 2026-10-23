package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonalDictionaryTest {

    @Test
    fun `adding a word appends its normalized form`() {
        val (result, updated) = PersonalDictionary.EMPTY.withWordAdded("  Bloviate  ")
        assertEquals(DictionaryEditResult.APPLIED, result)
        assertEquals(listOf("Bloviate"), updated.words)
    }

    @Test
    fun `adding a blank word is rejected and changes nothing`() {
        val (result, updated) = PersonalDictionary.SEED.withWordAdded("   ")
        assertEquals(DictionaryEditResult.BLANK, result)
        assertEquals(PersonalDictionary.SEED, updated)
    }

    @Test
    fun `adding a duplicate word in any case is rejected`() {
        val (result, updated) = PersonalDictionary.SEED.withWordAdded("bloviate")
        assertEquals(DictionaryEditResult.DUPLICATE, result)
        assertEquals(PersonalDictionary.SEED, updated)
    }

    @Test
    fun `removing a word matches case-insensitively`() {
        val (result, updated) = PersonalDictionary.SEED.withWordRemoved("PARAKEET")
        assertEquals(DictionaryEditResult.APPLIED, result)
        assertEquals(listOf("Bloviate", "Shippopotamus"), updated.words)
    }

    @Test
    fun `removing an absent word reports not found`() {
        val (result, updated) = PersonalDictionary.EMPTY.withWordRemoved("Bloviate")
        assertEquals(DictionaryEditResult.NOT_FOUND, result)
        assertEquals(PersonalDictionary.EMPTY, updated)
    }

    @Test
    fun `adding a correction appends the normalized pair`() {
        val (result, updated) = PersonalDictionary.EMPTY.withCorrectionAdded("  perikit ", " Parakeet ")
        assertEquals(DictionaryEditResult.APPLIED, result)
        assertEquals(
            listOf(PersonalDictionary.Correction(heardAs = "perikit", replacement = "Parakeet")),
            updated.corrections,
        )
    }

    @Test
    fun `adding a correction with a blank side is rejected`() {
        val blankHeard = PersonalDictionary.EMPTY.withCorrectionAdded(" ", "Parakeet")
        val blankReplacement = PersonalDictionary.EMPTY.withCorrectionAdded("perikit", "")
        assertEquals(DictionaryEditResult.BLANK, blankHeard.first)
        assertEquals(DictionaryEditResult.BLANK, blankReplacement.first)
        assertEquals(PersonalDictionary.EMPTY, blankHeard.second)
        assertEquals(PersonalDictionary.EMPTY, blankReplacement.second)
    }

    @Test
    fun `adding a correction whose sides match is a rejected self-correction`() {
        val (result, updated) = PersonalDictionary.EMPTY.withCorrectionAdded("Bloviate", "bloviate")
        assertEquals(DictionaryEditResult.SELF_CORRECTION, result)
        assertEquals(PersonalDictionary.EMPTY, updated)
    }

    @Test
    fun `adding a correction for an already-corrected hearing is a duplicate`() {
        val (result, updated) = PersonalDictionary.SEED.withCorrectionAdded("PERIKIT", "Something Else")
        assertEquals(DictionaryEditResult.DUPLICATE, result)
        assertEquals(PersonalDictionary.SEED, updated)
    }

    @Test
    fun `removing a correction matches its heard-as case-insensitively`() {
        val (result, updated) = PersonalDictionary.SEED.withCorrectionRemoved("Below V8")
        assertEquals(DictionaryEditResult.APPLIED, result)
        assertEquals(
            listOf("perikit", "blow-viate", "blow viate"),
            updated.corrections.map { correction -> correction.heardAs },
        )
    }

    @Test
    fun `removing an absent correction reports not found`() {
        val (result, _) = PersonalDictionary.EMPTY.withCorrectionRemoved("perikit")
        assertEquals(DictionaryEditResult.NOT_FOUND, result)
    }

    @Test
    fun `normalization collapses internal whitespace runs to single spaces`() {
        val (result, updated) = PersonalDictionary.EMPTY.withCorrectionAdded("below\t  v8", "Bloviate")
        assertEquals(DictionaryEditResult.APPLIED, result)
        assertEquals("below v8", updated.corrections.single().heardAs)
    }

    @Test
    fun `the seed carries exactly the agreed starters`() {
        assertEquals(listOf("Bloviate", "Parakeet", "Shippopotamus"), PersonalDictionary.SEED.words)
        assertEquals(
            listOf(
                PersonalDictionary.Correction("perikit", "Parakeet"),
                PersonalDictionary.Correction("below v8", "Bloviate"),
                PersonalDictionary.Correction("blow-viate", "Bloviate"),
                PersonalDictionary.Correction("blow viate", "Bloviate"),
            ),
            PersonalDictionary.SEED.corrections,
        )
    }
}
