package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.application.port.ModelStorePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Hand-written fake of [ModelStorePort]; drive [state] and [modelPath] directly. */
class FakeModelStorePort : ModelStorePort {

    private val mutableState = MutableStateFlow<ModelState>(ModelState.Absent)

    override val state: StateFlow<ModelState> = mutableState

    /** Path returned by [verifiedModelPath]; null simulates "no verified model". */
    var modelPath: String? = null

    var ensureModelCallCount: Int = 0
        private set

    override suspend fun ensureModel() {
        ensureModelCallCount++
    }

    override suspend fun verifiedModelPath(): String? = modelPath

    /** Marks the model ready and loadable at [path]. */
    fun setReady(path: String = FAKE_MODEL_PATH) {
        modelPath = path
        mutableState.value = ModelState.Ready
    }

    fun setState(state: ModelState) {
        mutableState.value = state
    }

    companion object {
        const val FAKE_MODEL_PATH: String = "/fake/models/ggml-base.en.bin"
    }
}
