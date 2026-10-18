package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.domain.port.RecordingSessionPort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hand-written fake of [RecordingSessionPort]; drive [state] directly and
 * inspect the recorded calls. [stopResult] is what [stop] returns; set
 * [stopFailure] to make it throw instead.
 */
class FakeRecordingSessionPort : RecordingSessionPort {

    private val mutableState = MutableStateFlow<RecordingSessionState>(RecordingSessionState.Idle)
    override val state: StateFlow<RecordingSessionState> = mutableState

    private val mutableAutoStopped = MutableSharedFlow<FloatArray>(extraBufferCapacity = 1)
    override val autoStoppedRecordings: SharedFlow<FloatArray> = mutableAutoStopped

    var startCallCount: Int = 0
        private set

    var cancelCallCount: Int = 0
        private set

    var stopCallCount: Int = 0
        private set

    /** Samples returned by [stop]. */
    var stopResult: FloatArray = floatArrayOf(0.1f, 0.2f, 0.3f)

    /** When set, [stop] throws this instead of returning [stopResult]. */
    var stopFailure: Exception? = null

    override fun start() {
        startCallCount++
    }

    override suspend fun stop(): FloatArray {
        stopCallCount++
        stopFailure?.let { throw it }
        return stopResult
    }

    override fun cancel() {
        cancelCallCount++
    }

    fun setState(state: RecordingSessionState) {
        mutableState.value = state
    }

    fun setRecording(elapsedMs: Long = 0L, amplitude: Float = 0f) {
        mutableState.value = RecordingSessionState.Recording(elapsedMs = elapsedMs, amplitude = amplitude)
    }

    /** Emits a hard-cap recording, as the real session does at ~3 minutes. */
    fun emitAutoStopped(samples: FloatArray = stopResult) {
        mutableAutoStopped.tryEmit(samples)
    }
}
