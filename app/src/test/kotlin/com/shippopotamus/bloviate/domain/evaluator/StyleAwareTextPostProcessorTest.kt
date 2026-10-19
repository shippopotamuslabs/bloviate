package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.testing.FakeDictionaryPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StyleAwareTextPostProcessorTest {

    private val fakeDictionary = FakeDictionaryPort()
    private val fakeStyle = FakeStylePreferencePort()
    private val processor = StyleAwareTextPostProcessor(
        delegate = DictionaryAwareTextPostProcessor(
            baseline = BaselineTextPostProcessor(),
            dictionaryPort = fakeDictionary,
            corrector = DictionaryCorrector(),
        ),
        stylePreference = fakeStyle,
        lowercaseCasual = LowercaseCasualTransformer(),
    )

    @Test
    fun `the default style is the baseline output unchanged`() {
        fakeStyle.setSelected("normal-caps-casual")
        assertEquals("Hello world", processor.process("hello world"))
    }

    @Test
    fun `lowercase-casual applies after the baseline`() {
        fakeStyle.setSelected("lowercase-casual")
        // The baseline repairs sentence case and completes the period;
        // the style then flattens everything back down.
        assertEquals("hello world", processor.process("hello world"))
    }

    @Test
    fun `lowercase-casual applies after dictionary corrections`() {
        fakeStyle.setSelected("lowercase-casual")
        assertEquals("parakeet heard bloviate", processor.process("perikit heard bloviate"))
    }

    @Test
    fun `an llm style passes the cleaned text through unchanged`() {
        fakeStyle.setSelected("seneca")
        assertEquals("Hello world", processor.process("hello world"))
    }

    @Test
    fun `an unknown style id falls back to the default behavior`() {
        fakeStyle.setSelected("not-a-style")
        assertEquals("Hello world", processor.process("hello world"))
    }

    @Test
    fun `the selection is read fresh on every call`() {
        fakeStyle.setSelected("normal-caps-casual")
        assertEquals("Hello world", processor.process("hello world"))

        fakeStyle.setSelected("lowercase-casual")
        assertEquals("hello world", processor.process("hello world"))
    }

    @Test
    fun `dictionary edits made after construction still flow through the style`() {
        fakeStyle.setSelected("lowercase-casual")
        fakeDictionary.setDictionary(PersonalDictionary.EMPTY)
        assertEquals("i saw a perikit", processor.process("i saw a perikit"))
    }
}
