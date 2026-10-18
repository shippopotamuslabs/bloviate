package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.ModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * The model store of the **effective** dictation engine (see
 * `EffectiveEngineResolver`): the [EnginePreferencePort] choice while its
 * model is Ready, otherwise the fallback engine while *its* model is
 * Ready. Session availability and engine warm-up key off this port, so
 * they always describe the engine the next dictation will actually use —
 * never an engine whose model is not on the device. When neither model is
 * Ready, [state] follows the preferred engine's store (its download
 * progress or failure is what the user is waiting on) and [ensureModel]
 * targets that store; once an engine is effective, [ensureModel] targets
 * the effective engine's store.
 */
interface ActiveModelStorePort {

    /** Model lifecycle state of the effective engine (or of the preference while nothing is Ready). */
    val state: StateFlow<ModelState>

    /** Ensures the effective engine's model is downloaded and verified (the preference's while nothing is Ready). */
    suspend fun ensureModel()
}
