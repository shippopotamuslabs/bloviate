package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.testing.FakeDictionaryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DictionaryAwareTextPostProcessorTest {

    private val fakeDictionary = FakeDictionaryPort()
    private val processor = DictionaryAwareTextPostProcessor(
        baseline = BaselineTextPostProcessor(),
        dictionaryPort = fakeDictionary,
        corrector = DictionaryCorrector(),
    )

    @Test
    fun `corrections and casing apply to the raw transcript`() {
        // The seeds are live: Parakeet's observed mis-hearings come out right.
        assertEquals(
            "Parakeet heard Bloviate",
            processor.process("perikit heard bloviate"),
        )
    }

    @Test
    fun `the baseline still runs after the dictionary - fillers stripped, sentence case repaired`() {
        assertEquals(
            "Bloviate is great",
            processor.process("um bloviate is great"),
        )
    }

    @Test
    fun `the dictionary is read at send time - emptying it changes the very next call`() {
        assertEquals("I saw a Parakeet", processor.process("i saw a perikit"))

        fakeDictionary.setDictionary(PersonalDictionary.EMPTY)

        assertEquals("I saw a perikit", processor.process("i saw a perikit"))
    }

    @Test
    fun `a word added after construction is honored without rebuilding the processor`() {
        fakeDictionary.setDictionary(PersonalDictionary.EMPTY)
        assertEquals("I met tensor today", processor.process("i met tensor today"))

        fakeDictionary.setDictionary(
            PersonalDictionary(words = listOf("Tensor"), corrections = emptyList()),
        )

        assertEquals("I met Tensor today", processor.process("i met tensor today"))
    }
}
