package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeWhisperEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pipeline's engine selection (ENC-54): the one bound
 * [com.shippopotamus.bloviate.domain.port.TranscriptionEngine] delegates to
 * Whisper or Parakeet per the persisted preference, resolved at call time.
 * Both delegates are the real engines, over faked native boundaries.
 */
class SelectingTranscriptionEngineTest {

    private val fakeWhisper = FakeWhisperEngine().apply { transcript = "whisper text" }
    private val fakeWhisperStore = FakeModelStorePort().apply { setReady() }
    private val whisperEngine = OnDeviceWhisperTranscriptionEngine(fakeWhisper, fakeWhisperStore)

    private val fakeParakeetStore = FakeParakeetModelStorePort().apply { setReady() }
    private val fakeParakeetFactory = FakeParakeetRecognizerFactory().apply {
        nextTranscript = "parakeet text"
    }
    private val parakeetEngine = ParakeetTranscriptionEngine(fakeParakeetStore).apply {
        recognizerFactory = fakeParakeetFactory::create
    }

    private val fakePreference = FakeEnginePreferencePort()

    private val selector = SelectingTranscriptionEngine(whisperEngine, parakeetEngine, fakePreference)

    @Test
    fun `mode is on-device`() {
        assertEquals(ProcessingMode.ON_DEVICE, selector.mode)
    }

    @Test
    fun `transcribe uses whisper while whisper is selected`() = runTest {
        val text = selector.transcribe(FloatArray(1_600))

        assertEquals("whisper text", text)
        assertEquals(1, fakeWhisper.transcribedSampleCounts.size)
        assertTrue(fakeParakeetFactory.createdWith.isEmpty())
    }

    @Test
    fun `flipping the preference routes the next transcribe to parakeet`() = runTest {
        selector.transcribe(FloatArray(1_600))
        fakePreference.setSelected(DictationEngine.PARAKEET)

        val text = selector.transcribe(FloatArray(1_600))

        assertEquals("parakeet text", text)
        assertEquals(1, fakeWhisper.transcribedSampleCounts.size)
        assertEquals(1, fakeParakeetFactory.recognizers.single().transcribeCallCount)
    }

    @Test
    fun `warm up warms only the selected engine`() = runTest {
        selector.warmUp()

        assertTrue(fakeWhisper.isLoaded())
        assertTrue(fakeParakeetFactory.createdWith.isEmpty())
        assertTrue(selector.isWarm())

        fakePreference.setSelected(DictationEngine.PARAKEET)
        assertFalse(selector.isWarm())

        selector.warmUp()

        assertEquals(1, fakeParakeetFactory.createdWith.size)
        assertTrue(selector.isWarm())
    }

    @Test
    fun `release releases both engines, whichever is warm`() = runTest {
        selector.warmUp()
        fakePreference.setSelected(DictationEngine.PARAKEET)
        selector.warmUp()

        selector.release()

        assertEquals(1, fakeWhisper.closeCount)
        assertEquals(1, fakeParakeetFactory.recognizers.single().closeCount)
        assertFalse(selector.isWarm())
    }
}
