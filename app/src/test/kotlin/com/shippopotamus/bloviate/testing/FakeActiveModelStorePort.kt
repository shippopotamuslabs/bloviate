package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.ActiveModelStorePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Hand-written fake of [ActiveModelStorePort]; drive [state] directly. */
class FakeActiveModelStorePort : ActiveModelStorePort {

    private val mutableState = MutableStateFlow<ModelState>(ModelState.Absent)

    override val state: StateFlow<ModelState> = mutableState

    var ensureModelCallCount: Int = 0
        private set

    override suspend fun ensureModel() {
        ensureModelCallCount++
    }

    fun setState(state: ModelState) {
        mutableState.value = state
    }

    /** Marks the selected engine's model ready. */
    fun setReady() {
        mutableState.value = ModelState.Ready
    }
}
