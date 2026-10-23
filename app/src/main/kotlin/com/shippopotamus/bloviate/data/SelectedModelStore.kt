package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.application.port.ActiveModelStorePort
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import com.shippopotamus.bloviate.application.port.ModelStorePort
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ActiveModelStorePort]: publishes the model state of the **effective**
 * engine, resolved through [EffectiveEngineResolver] from the preference
 * and both stores' states — session availability and warm-up therefore
 * always describe the engine the next dictation will actually use (the
 * same resolution [SelectingTranscriptionEngine] delegates by). While
 * neither model is Ready, the state follows the preferred engine's store,
 * so a pending or failed download of the engine the user is waiting on is
 * what surfaces — and [ensureModel] targets that store. Once an engine is
 * effective, [ensureModel] targets the effective engine's store.
 */
@Singleton
class SelectedModelStore @Inject constructor(
    private val whisperStore: ModelStorePort,
    private val parakeetStore: ParakeetModelStorePort,
    private val enginePreference: EnginePreferencePort,
    scope: CoroutineScope,
) : ActiveModelStorePort {

    override val state: StateFlow<ModelState> =
        combine(
            enginePreference.selectedEngine,
            whisperStore.state,
            parakeetStore.state,
        ) { preference, whisperState, parakeetState ->
            activeState(preference, whisperState, parakeetState)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = activeState(
                preference = enginePreference.selectedEngine.value,
                whisperState = whisperStore.state.value,
                parakeetState = parakeetStore.state.value,
            ),
        )

    override suspend fun ensureModel() {
        when (targetEngine()) {
            DictationEngine.WHISPER -> whisperStore.ensureModel()
            DictationEngine.PARAKEET -> parakeetStore.ensureModel()
        }
    }

    /** The store [state] currently reports and [ensureModel] targets. */
    private fun targetEngine(): DictationEngine {
        val preference = enginePreference.selectedEngine.value
        return EffectiveEngineResolver.resolve(
            preference = preference,
            whisperState = whisperStore.state.value,
            parakeetState = parakeetStore.state.value,
        ) ?: preference
    }

    private fun activeState(
        preference: DictationEngine,
        whisperState: ModelState,
        parakeetState: ModelState,
    ): ModelState {
        val target = EffectiveEngineResolver.resolve(preference, whisperState, parakeetState)
            ?: preference
        return EffectiveEngineResolver.stateOf(target, whisperState, parakeetState)
    }
}
