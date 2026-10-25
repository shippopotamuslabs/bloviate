package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.ParakeetModelFiles
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Hand-written fake of [ParakeetModelStorePort]; drive [state] and [modelFiles] directly. */
class FakeParakeetModelStorePort : ParakeetModelStorePort {

    private val mutableState = MutableStateFlow<ModelState>(ModelState.Absent)

    override val state: StateFlow<ModelState> = mutableState

    /** Files returned by [verifiedModelFiles]; null simulates "no verified set". */
    var modelFiles: ParakeetModelFiles? = null

    var ensureModelCallCount: Int = 0
        private set

    override suspend fun ensureModel() {
        ensureModelCallCount++
    }

    override suspend fun verifiedModelFiles(): ParakeetModelFiles? = modelFiles

    /** Marks the set ready and loadable at the fake paths. */
    fun setReady(files: ParakeetModelFiles = FAKE_MODEL_FILES) {
        modelFiles = files
        mutableState.value = ModelState.Ready
    }

    fun setState(state: ModelState) {
        mutableState.value = state
    }

    companion object {
        val FAKE_MODEL_FILES: ParakeetModelFiles = ParakeetModelFiles(
            encoderPath = "/fake/models/parakeet/encoder.int8.onnx",
            decoderPath = "/fake/models/parakeet/decoder.int8.onnx",
            joinerPath = "/fake/models/parakeet/joiner.int8.onnx",
            tokensPath = "/fake/models/parakeet/tokens.txt",
        )
    }
}
