package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.application.port.TranscriptionEngine
import kotlinx.coroutines.CompletableDeferred

/**
 * Hand-written fake of [TranscriptionEngine]. [transcribe] returns
 * [nextTranscript] unless [failure] is set; every call's samples are kept
 * in [transcribedSamples] for inspection.
 */
class FakeTranscriptionEngine : TranscriptionEngine {

    override val mode: ProcessingMode = ProcessingMode.ON_DEVICE

    var nextTranscript: String = "hello world"

    /** When set, [transcribe] throws this instead of returning [nextTranscript]. */
    var failure: Exception? = null

    /** When set, [transcribe] suspends on this gate before returning — for in-flight cancellation tests. */
    var gate: CompletableDeferred<Unit>? = null

    var transcribedSamples: FloatArray? = null
        private set

    var transcribeCallCount: Int = 0
        private set

    private var warm: Boolean = false

    override suspend fun warmUp() {
        warm = true
    }

    override suspend fun transcribe(samples: FloatArray): String {
        transcribeCallCount++
        transcribedSamples = samples
        gate?.await()
        failure?.let { throw it }
        warm = true
        return nextTranscript
    }

    override suspend fun release() {
        warm = false
    }

    override fun isWarm(): Boolean = warm
}
