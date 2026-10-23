package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.AutoFinishReason
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.domain.model.StopResult
import com.shippopotamus.bloviate.application.port.RecordingSessionPort
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

    private val mutableAutoStopped = MutableSharedFlow<StopResult>(extraBufferCapacity = 1)
    override val autoStoppedRecordings: SharedFlow<StopResult> = mutableAutoStopped

    var startCallCount: Int = 0
        private set

    var cancelCallCount: Int = 0
        private set

    var stopCallCount: Int = 0
        private set

    /** Result returned by [stop]: samples plus the finish reason (null for a manual stop). */
    var stopResult: StopResult = StopResult(floatArrayOf(0.1f, 0.2f, 0.3f))

    /** When set, [stop] throws this instead of returning [stopResult]. */
    var stopFailure: Exception? = null

    override fun start() {
        startCallCount++
    }

    override suspend fun stop(): StopResult {
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

    /** Emits an auto-finished recording, as the real session does on dead air or the safety backstop. */
    fun emitAutoStopped(
        samples: FloatArray = stopResult.samples,
        reason: AutoFinishReason = stopResult.autoFinish ?: AutoFinishReason.MAX_LENGTH,
    ) {
        mutableAutoStopped.tryEmit(StopResult(samples, reason))
    }
}
