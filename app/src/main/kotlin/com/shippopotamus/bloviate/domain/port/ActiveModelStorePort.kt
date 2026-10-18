package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.ModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * The model store of the **currently selected** dictation engine (see
 * [EnginePreferencePort]): session availability and engine warm-up key off
 * the selected engine's model, not off any one engine's files. Switching the
 * preference re-points this port at the other engine's store; [ensureModel]
 * makes the selected engine's model ready.
 */
interface ActiveModelStorePort {

    /** Model lifecycle state of the selected engine. */
    val state: StateFlow<ModelState>

    /** Ensures the selected engine's model is downloaded and verified. */
    suspend fun ensureModel()
}
