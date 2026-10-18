package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
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
 * the **effective** engine — the preference while its model is Ready, the
 * other engine while only its model is Ready — resolved at call time
 * through `EffectiveEngineResolver`. Both delegates are the real engines,
 * over faked native boundaries.
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

    // Starts on Whisper so the pre-flip tests name their starting engine.
    private val fakePreference = FakeEnginePreferencePort(DictationEngine.WHISPER)

    private val selector = SelectingTranscriptionEngine(
        whisperEngine,
        parakeetEngine,
        fakePreference,
        fakeWhisperStore,
        fakeParakeetStore,
    )

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
    fun `parakeet preference falls back to whisper while the parakeet model is not ready`() = runTest {
        fakeParakeetStore.setState(ModelState.Absent)
        fakePreference.setSelected(DictationEngine.PARAKEET)

        val text = selector.transcribe(FloatArray(1_600))

        assertEquals("whisper text", text)
        assertTrue(fakeParakeetFactory.createdWith.isEmpty())
    }

    @Test
    fun `whisper preference falls back to parakeet while the whisper model is not ready`() = runTest {
        fakeWhisperStore.setState(ModelState.Failed("disk gone"))

        val text = selector.transcribe(FloatArray(1_600))

        assertEquals("parakeet text", text)
        assertTrue(fakeWhisper.transcribedSampleCounts.isEmpty())
    }

    @Test
    fun `fallback lifts the moment the preferred model becomes ready`() = runTest {
        fakeParakeetStore.setState(ModelState.Downloading(progressFraction = 0.5f))
        fakePreference.setSelected(DictationEngine.PARAKEET)
        assertEquals("whisper text", selector.transcribe(FloatArray(1_600)))

        fakeParakeetStore.setReady()

        assertEquals("parakeet text", selector.transcribe(FloatArray(1_600)))
    }

    @Test
    fun `with neither model ready, delegation still targets the preference`() = runTest {
        // The session layer reports Unavailable in this state, so no call
        // should arrive — but if one does, the preferred engine's own load
        // gate is what fails, exactly as before the fallback existed. (The
        // fake stores keep their verified paths while not Ready, so the
        // delegates can run and prove which one was targeted.)
        fakeWhisperStore.setState(ModelState.Absent)
        fakeParakeetStore.setState(ModelState.Absent)

        assertEquals("whisper text", selector.transcribe(FloatArray(1_600)))

        fakePreference.setSelected(DictationEngine.PARAKEET)

        assertEquals("parakeet text", selector.transcribe(FloatArray(1_600)))
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
    fun `warm up warms the fallback engine while the preferred model is not ready`() = runTest {
        fakeParakeetStore.setState(ModelState.Absent)
        fakePreference.setSelected(DictationEngine.PARAKEET)

        selector.warmUp()

        assertTrue(fakeWhisper.isLoaded())
        assertTrue(fakeParakeetFactory.createdWith.isEmpty())
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
