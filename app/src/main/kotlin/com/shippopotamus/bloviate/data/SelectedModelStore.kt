package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.ActiveModelStorePort
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import com.shippopotamus.bloviate.domain.port.ParakeetModelStorePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ActiveModelStorePort]: follows [EnginePreferencePort] and re-points at
 * the selected engine's store — session availability and warm-up therefore
 * always describe the engine the next dictation will actually use.
 */
@Singleton
class SelectedModelStore @Inject constructor(
    private val whisperStore: ModelStorePort,
    private val parakeetStore: ParakeetModelStorePort,
    private val enginePreference: EnginePreferencePort,
    scope: CoroutineScope,
) : ActiveModelStorePort {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: StateFlow<ModelState> =
        enginePreference.selectedEngine
            .flatMapLatest { engine ->
                when (engine) {
                    DictationEngine.WHISPER -> whisperStore.state
                    DictationEngine.PARAKEET -> parakeetStore.state
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = when (enginePreference.selectedEngine.value) {
                    DictationEngine.WHISPER -> whisperStore.state.value
                    DictationEngine.PARAKEET -> parakeetStore.state.value
                },
            )

    override suspend fun ensureModel() {
        when (enginePreference.selectedEngine.value) {
            DictationEngine.WHISPER -> whisperStore.ensureModel()
            DictationEngine.PARAKEET -> parakeetStore.ensureModel()
        }
    }
}
