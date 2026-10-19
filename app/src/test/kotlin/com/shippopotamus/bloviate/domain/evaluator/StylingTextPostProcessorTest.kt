package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeLlmStylePort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.FakeTextPostProcessor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StylingTextPostProcessorTest {

    private val fakeLocal = FakeTextPostProcessor()
    private val fakeStyles = FakeStylePreferencePort()
    private val fakeLlm = FakeLlmStylePort()
    private val fakeKeys = FakeApiKeyPort()

    private val processor = StylingTextPostProcessor(
        local = fakeLocal,
        stylePreference = fakeStyles,
        llm = fakeLlm,
        apiKeys = fakeKeys,
    )

    @Test
    fun `a mechanical style returns the local text with no llm call`() = runTest {
        fakeStyles.setSelected("lowercase-casual")
        fakeKeys.setKey("test-key")
        fakeLocal.transform = { it.lowercase() }

        val result = processor.process("Hello There")

        assertEquals("hello there", result.text)
        assertEquals("lowercase-casual", result.styleId)
        assertNull(result.llmMs)
        assertFalse(result.fellBack)
        assertEquals(0, fakeLlm.callCount)
    }

    @Test
    fun `an llm style without a key returns the local text with no llm call`() = runTest {
        fakeStyles.setSelected("professional")

        val result = processor.process("raw words")

        assertEquals("raw words", result.text)
        assertEquals("professional", result.styleId)
        assertNull(result.llmMs)
        assertFalse(result.fellBack)
        assertEquals(0, fakeLlm.callCount)
    }

    @Test
    fun `an llm style with a key returns the styled text and its cost`() = runTest {
        fakeStyles.setSelected("professional")
        fakeKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.Styled(text = "Polished words.", llmMs = 900L)

        val result = processor.process("raw words")

        assertEquals("Polished words.", result.text)
        assertEquals("professional", result.styleId)
        assertEquals(900L, result.llmMs)
        assertFalse(result.fellBack)
        assertEquals(listOf("professional" to "raw words"), fakeLlm.inputs)
    }

    @Test
    fun `an llm miss returns the local text marked as fallen back`() = runTest {
        fakeStyles.setSelected("professional")
        fakeKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.FellBack(reason = "timeout")

        val result = processor.process("raw words")

        assertEquals("raw words", result.text)
        assertEquals("professional", result.styleId)
        assertNull(result.llmMs)
        assertTrue(result.fellBack)
    }

    @Test
    fun `a selection change applies to the next send`() = runTest {
        fakeKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.Styled(text = "Polished.", llmMs = 10L)
        fakeStyles.setSelected("normal-caps-casual")

        val before = processor.process("raw words")
        assertEquals(0, fakeLlm.callCount)
        assertEquals("normal-caps-casual", before.styleId)

        fakeStyles.setSelected("seneca")

        val after = processor.process("raw words")
        assertEquals(1, fakeLlm.callCount)
        assertEquals("seneca", after.styleId)
        assertEquals("Polished.", after.text)
    }
}
