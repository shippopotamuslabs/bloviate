package com.shippopotamus.bloviate.application.evaluator
import com.shippopotamus.bloviate.domain.evaluator.LowercaseCasualTransformer
import com.shippopotamus.bloviate.domain.evaluator.DictionaryCorrector
import com.shippopotamus.bloviate.domain.evaluator.BaselineTextPostProcessor

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeDictionaryPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StyleAwareTextPostProcessorTest {

    private val fakeDictionary = FakeDictionaryPort()
    private val fakeStyle = FakeStylePreferencePort()
    private val fakeCustoms = FakeCustomStyleStorePort()
    private val processor = StyleAwareTextPostProcessor(
        delegate = DictionaryAwareTextPostProcessor(
            baseline = BaselineTextPostProcessor(),
            dictionaryPort = fakeDictionary,
            corrector = DictionaryCorrector(),
        ),
        stylePreference = fakeStyle,
        lowercaseCasual = LowercaseCasualTransformer(),
        resolver = StyleResolver(store = fakeCustoms, scope = CoroutineScope(Dispatchers.Unconfined)),
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

    @Test
    fun `a cached custom style resolves instead of the catalog default`() = runTest {
        // Customs shadow the catalog in the resolver: a custom record
        // carrying a mechanical id takes effect on the sync local path.
        fakeCustoms.replaceSnapshot(
            listOf(
                CustomStyleRecord(
                    definition = StyleDefinition(
                        id = "lowercase-casual",
                        displayName = "Custom lower",
                        description = "A custom style.",
                        sample = "Yeah, tomorrow.",
                        systemInstructions = "My voice.",
                        fewShots = listOf(
                            FewShot(raw = "hear this", styled = "Hear this."),
                            FewShot(raw = "try again", styled = "Try again."),
                        ),
                        kind = StyleKind.MECHANICAL,
                        category = StyleCategory.CUSTOM,
                    ),
                    revision = 1L,
                    updatedAtEpochMillis = 0L,
                ),
            ),
        )
        fakeStyle.setSelected("lowercase-casual")
        assertEquals("hello world", processor.process("Hello World"))
    }

    @Test
    fun `a custom llm id passes through unchanged on the sync local path`() = runTest {
        fakeCustoms.replaceSnapshot(
            listOf(
                CustomStyleRecord(
                    definition = StyleDefinition(
                        id = "custom-mine",
                        displayName = "Mine",
                        description = "A custom style.",
                        sample = "Yeah, tomorrow.",
                        systemInstructions = "My voice.",
                        fewShots = listOf(
                            FewShot(raw = "hear this", styled = "Hear this."),
                            FewShot(raw = "try again", styled = "Try again."),
                        ),
                        kind = StyleKind.LLM,
                        category = StyleCategory.CUSTOM,
                    ),
                    revision = 1L,
                    updatedAtEpochMillis = 0L,
                ),
            ),
        )
        fakeStyle.setSelected("custom-mine")
        assertEquals("Hello world", processor.process("hello world"))
    }
}
