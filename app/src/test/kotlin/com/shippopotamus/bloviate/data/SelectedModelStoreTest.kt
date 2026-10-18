package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The active model store (ENC-54): availability follows the **effective**
 * engine — the preference while its model is Ready, the fallback engine
 * while only its model is Ready — so the session layer never keys off an
 * engine whose model is not on the device, and never reports Unavailable
 * while a fallback could transcribe.
 */
class SelectedModelStoreTest {

    // Unconfined so the combine + stateIn chain settles eagerly and
    // deterministically inside runTest (UNIT_TESTING.md §3.3).
    private val storeScope = TestScope(UnconfinedTestDispatcher())

    private val fakeWhisperStore = FakeModelStorePort()
    private val fakeParakeetStore = FakeParakeetModelStorePort()

    // Starts on Whisper so each test names its starting preference.
    private val fakePreference = FakeEnginePreferencePort(DictationEngine.WHISPER)

    private val store = SelectedModelStore(
        whisperStore = fakeWhisperStore,
        parakeetStore = fakeParakeetStore,
        enginePreference = fakePreference,
        scope = storeScope,
    )

    @Test
    fun `state follows the whisper store while whisper is selected`() = runTest {
        assertEquals(ModelState.Absent, store.state.value)

        fakeWhisperStore.setReady()

        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `flipping the preference holds the fallback state until parakeet is ready`() = runTest {
        fakeWhisperStore.setReady()
        fakePreference.setSelected(DictationEngine.PARAKEET)
        // Parakeet is not Ready yet: the fallback (Whisper) is effective,
        // so the state stays Ready rather than dropping to Parakeet's Absent.
        assertEquals(ModelState.Ready, store.state.value)

        fakeParakeetStore.setState(ModelState.Failed("corrupt joiner"))
        assertEquals(ModelState.Ready, store.state.value)

        fakeParakeetStore.setReady()
        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `parakeet preference with only whisper ready reports the whisper store`() = runTest {
        fakePreference.setSelected(DictationEngine.PARAKEET)
        fakeWhisperStore.setReady()

        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `whisper preference with only parakeet ready reports the parakeet store`() = runTest {
        fakeParakeetStore.setReady()

        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `with neither model ready, state follows the preferred engine's store`() = runTest {
        fakePreference.setSelected(DictationEngine.PARAKEET)
        fakeWhisperStore.setState(ModelState.Failed("whisper boom"))
        fakeParakeetStore.setState(ModelState.Downloading(progressFraction = 0.25f))

        // The preference's own progress is what the user is waiting on.
        assertEquals(ModelState.Downloading(progressFraction = 0.25f), store.state.value)

        fakePreference.setSelected(DictationEngine.WHISPER)

        assertEquals(ModelState.Failed("whisper boom"), store.state.value)
    }

    @Test
    fun `ensureModel delegates to the selected engine's store while nothing is ready`() = runTest {
        store.ensureModel()

        assertEquals(1, fakeWhisperStore.ensureModelCallCount)
        assertEquals(0, fakeParakeetStore.ensureModelCallCount)

        fakePreference.setSelected(DictationEngine.PARAKEET)
        store.ensureModel()

        assertEquals(1, fakeWhisperStore.ensureModelCallCount)
        assertEquals(1, fakeParakeetStore.ensureModelCallCount)
    }

    @Test
    fun `ensureModel targets the effective engine's store while a fallback is in use`() = runTest {
        // Parakeet preferred but not downloaded; Whisper is the effective
        // engine, so ensure maintains Whisper rather than kicking off the
        // ~640 MiB Parakeet download unbidden.
        fakePreference.setSelected(DictationEngine.PARAKEET)
        fakeWhisperStore.setReady()

        store.ensureModel()

        assertEquals(1, fakeWhisperStore.ensureModelCallCount)
        assertEquals(0, fakeParakeetStore.ensureModelCallCount)
    }

    @Test
    fun `ensureModel targets the preference again once its model is ready`() = runTest {
        fakePreference.setSelected(DictationEngine.PARAKEET)
        fakeWhisperStore.setReady()
        fakeParakeetStore.setReady()

        store.ensureModel()

        assertEquals(0, fakeWhisperStore.ensureModelCallCount)
        assertEquals(1, fakeParakeetStore.ensureModelCallCount)
    }
}
