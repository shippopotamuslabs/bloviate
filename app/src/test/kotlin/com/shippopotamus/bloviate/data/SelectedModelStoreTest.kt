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
 * The active model store (ENC-54): availability follows the selected
 * engine, so the session layer never keys off the wrong engine's files.
 */
class SelectedModelStoreTest {

    // Unconfined so the flatMapLatest + stateIn chain settles eagerly and
    // deterministically inside runTest (UNIT_TESTING.md §3.3).
    private val storeScope = TestScope(UnconfinedTestDispatcher())

    private val fakeWhisperStore = FakeModelStorePort()
    private val fakeParakeetStore = FakeParakeetModelStorePort()
    private val fakePreference = FakeEnginePreferencePort()

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
    fun `flipping the preference re-points the state at the parakeet store`() = runTest {
        fakeWhisperStore.setReady()
        assertEquals(ModelState.Ready, store.state.value)

        fakePreference.setSelected(DictationEngine.PARAKEET)

        assertEquals(ModelState.Absent, store.state.value)

        fakeParakeetStore.setReady()

        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `ensureModel delegates to the selected engine's store`() = runTest {
        store.ensureModel()

        assertEquals(1, fakeWhisperStore.ensureModelCallCount)
        assertEquals(0, fakeParakeetStore.ensureModelCallCount)

        fakePreference.setSelected(DictationEngine.PARAKEET)
        store.ensureModel()

        assertEquals(1, fakeWhisperStore.ensureModelCallCount)
        assertEquals(1, fakeParakeetStore.ensureModelCallCount)
    }
}
