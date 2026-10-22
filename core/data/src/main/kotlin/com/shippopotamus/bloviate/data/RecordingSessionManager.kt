package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.domain.diagnostics.CadenceCounters
import com.shippopotamus.bloviate.domain.evaluator.AmplitudeCalculator
import com.shippopotamus.bloviate.domain.evaluator.SilenceTrimmer
import com.shippopotamus.bloviate.domain.model.AutoFinishReason
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.StopResult
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.application.port.ActiveModelStorePort
import com.shippopotamus.bloviate.application.port.AudioCapturePort
import com.shippopotamus.bloviate.application.port.MicPermissionPort
import com.shippopotamus.bloviate.application.port.RecordingSessionPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingSessionPort] over [AudioCapturePort]. Buffers the whole utterance
 * (dictation is transcribed in one pass — never chunked), auto-finishes
 * thirty seconds of dead air or ten minutes of continuous speech as a stop
 * (not a cancel), and applies [SilenceTrimmer] when a recording finishes.
 *
 * Elapsed time is derived from the captured sample count, not the wall
 * clock, so it always matches the audio that will be transcribed. Mutable
 * session fields are guarded by [stateLock]; finishing is idempotent and
 * funnels through [finishRecording] exactly once per recording, whether the
 * trigger was [stop], [cancel], the cap, or the capture flow ending.
 *
 * Amplitude is **display-only** and deliberately decoupled from the capture
 * chunk cadence (ENC-66): the library recorder hands over device-sized
 * chunks (hundreds of ms to ~1 s apart on some phones), so each chunk is
 * sliced into ~33 ms windows as it lands and a ticker publishes one window
 * level every ~33 ms — a ~30 Hz level stream for the waveform, whatever
 * the chunk size. The transcription buffer is untouched by the slicing:
 * chunks are stored whole, exactly as captured.
 *
 * Availability: the published state is [RecordingSessionState.Unavailable]
 * unless the **effective engine's** model is [ModelState.Ready] (via
 * [ActiveModelStorePort], ENC-54) and the mic permission is granted.
 */
@Singleton
class RecordingSessionManager @Inject constructor(
    private val audioCapture: AudioCapturePort,
    private val modelStore: ActiveModelStorePort,
    private val micPermission: MicPermissionPort,
    private val sessionScope: CoroutineScope,
) : RecordingSessionPort {

    private val stateLock = Any()
    private var inputsAvailable: Boolean = false
    private var recording: Boolean = false
    private var discardOnFinish: Boolean = false
    private var chunks: MutableList<FloatArray> = mutableListOf()
    private var totalSamples: Int = 0
    private var silentSamples: Long = 0L
    private var latestElapsedMs: Long = 0L
    private var latestAmplitude: Float = 0f
    private var captureJob: Job? = null
    private var levelJob: Job? = null

    /** Sliced-but-unpublished amplitude windows, oldest first. Guarded by [stateLock]. */
    private val pendingLevels = ArrayDeque<Float>()

    /** Leftover samples not yet filling a whole amplitude window. Guarded by [stateLock]. */
    private var windowCarry: FloatArray = FloatArray(0)
    private var completion: CompletableDeferred<StopResult>? = null
    private var pendingResult: StopResult? = null

    private val mutableState =
        MutableStateFlow<RecordingSessionState>(RecordingSessionState.Unavailable)
    override val state: StateFlow<RecordingSessionState> = mutableState.asStateFlow()

    private val mutableAutoStopped = MutableSharedFlow<StopResult>(extraBufferCapacity = 1)
    override val autoStoppedRecordings: SharedFlow<StopResult> = mutableAutoStopped.asSharedFlow()

    init {
        sessionScope.launch {
            combine(modelStore.state, micPermission.isGranted) { modelState, micGranted ->
                modelState is ModelState.Ready && micGranted
            }.collect { available -> onAvailabilityChanged(available) }
        }
    }

    override fun start() {
        micPermission.refresh()
        synchronized(stateLock) {
            if (recording) {
                Log.d(TAG, "start() ignored: already recording")
                return
            }
            if (!inputsAvailable) {
                Log.w(TAG, "start() ignored: session unavailable (model not ready or mic not granted)")
                publishIdleOrUnavailableLocked()
                return
            }
            recording = true
            discardOnFinish = false
            chunks = mutableListOf()
            totalSamples = 0
            silentSamples = 0L
            latestElapsedMs = 0L
            latestAmplitude = 0f
            pendingLevels.clear()
            windowCarry = FloatArray(0)
            pendingResult = null
            completion = CompletableDeferred()
            publishRecordingLocked()
            captureJob = sessionScope.launch { collectCapture() }
            levelJob = sessionScope.launch { runLevelTicker() }
        }
        Log.d(TAG, "Recording started")
    }

    override suspend fun stop(): StopResult {
        var pending: StopResult? = null
        var activeCompletion: CompletableDeferred<StopResult>? = null
        var jobToCancel: Job? = null
        synchronized(stateLock) {
            if (!recording && pendingResult != null) {
                pending = pendingResult
                pendingResult = null
                completion = null
            } else if (recording) {
                activeCompletion = completion
                jobToCancel = captureJob
            }
        }
        pending?.let {
            Log.d(TAG, "stop() returning the recording finished by an auto-finish (${it.autoFinish})")
            return it
        }
        val deferred = activeCompletion
            ?: throw IllegalStateException("stop() called with no active recording")
        jobToCancel?.cancel()
        jobToCancel?.join()
        // If the capture job was cancelled before its body ever ran, its
        // finally never fires; finish here instead (idempotent).
        finishRecording(autoFinish = null)
        val result = deferred.await()
        synchronized(stateLock) {
            if (completion === deferred) {
                completion = null
                pendingResult = null
            }
        }
        return result
    }

    override fun cancel() {
        val jobToCancel: Job?
        synchronized(stateLock) {
            if (!recording) return
            discardOnFinish = true
            jobToCancel = captureJob
        }
        jobToCancel?.cancel()
        // Covers a capture job cancelled before its body ran (see stop()).
        finishRecording(autoFinish = null)
    }

    private fun onAvailabilityChanged(available: Boolean) {
        synchronized(stateLock) {
            inputsAvailable = available
            if (!recording) {
                publishIdleOrUnavailableLocked()
            }
        }
    }

    private suspend fun collectCapture() {
        try {
            audioCapture.chunks().collect { chunk ->
                val autoFinish = synchronized(stateLock) { appendChunkLocked(chunk) }
                if (autoFinish != null) {
                    Log.d(TAG, "Auto-finish ($autoFinish); finishing as a stop")
                    finishRecording(autoFinish)
                    currentCoroutineContext().cancel()
                }
            }
        } finally {
            finishRecording(autoFinish = null)
        }
    }

    /**
     * Appends [chunk] to the transcription buffer and slices it into
     * amplitude windows for the level ticker. Tracks consecutive
     * sub-threshold audio for the inactivity auto-finish (ENC-160): any
     * 10 ms frame at speech level resets the dead-air count. Returns the
     * finish reason when a bound is hit, null to keep recording. Caller
     * holds [stateLock].
     */
    private fun appendChunkLocked(chunk: FloatArray): AutoFinishReason? {
        chunks += chunk
        totalSamples += chunk.size
        silentSamples =
            if (SilenceTrimmer.containsSpeech(chunk)) 0L else silentSamples + chunk.size
        latestElapsedMs = totalSamples.toLong() * MS_PER_SECOND / SilenceTrimmer.SAMPLE_RATE_HZ
        sliceLevelsLocked(chunk)
        return when {
            totalSamples >= MAX_RECORDING_SAMPLES -> AutoFinishReason.MAX_LENGTH
            silentSamples >= INACTIVITY_SILENCE_SAMPLES -> AutoFinishReason.SILENCE
            else -> null
        }
    }

    /**
     * Slices [chunk] into [AMPLITUDE_WINDOW_SAMPLES]-sized windows (carrying
     * a partial window across chunk boundaries) and queues each window's RMS
     * for the ticker. Display-only: the buffered chunk above is untouched.
     * The queue is bounded — a stalled ticker drops the oldest levels rather
     * than growing memory. Caller holds [stateLock].
     */
    private fun sliceLevelsLocked(chunk: FloatArray) {
        val source = if (windowCarry.isEmpty()) chunk else windowCarry + chunk
        var offset = 0
        while (source.size - offset >= AMPLITUDE_WINDOW_SAMPLES) {
            val window = source.copyOfRange(offset, offset + AMPLITUDE_WINDOW_SAMPLES)
            pendingLevels.addLast(AmplitudeCalculator.rms(window))
            while (pendingLevels.size > MAX_PENDING_LEVELS) {
                pendingLevels.removeFirst()
            }
            offset += AMPLITUDE_WINDOW_SAMPLES
        }
        windowCarry =
            if (offset < source.size) source.copyOfRange(offset, source.size) else FloatArray(0)
    }

    /**
     * Publishes one queued amplitude window every [AMPLITUDE_TICK_MS] —
     * ~30 Hz, paced in wall time so the waveform flows at the rate the
     * windows were recorded, whatever the capture chunk cadence. When the
     * queue runs dry (capture momentarily behind), nothing publishes and
     * the displayed level simply holds until the next chunk lands.
     */
    private suspend fun runLevelTicker() {
        while (currentCoroutineContext().isActive) {
            delay(AMPLITUDE_TICK_MS)
            synchronized(stateLock) {
                if (!recording) return@synchronized
                val level = pendingLevels.removeFirstOrNull() ?: return@synchronized
                latestAmplitude = level
                publishRecordingLocked()
                CadenceCounters.tickerPublish() // ENC-69 cadence counters (L1)
            }
        }
    }

    /**
     * Finalizes the active recording exactly once: trims and completes the
     * pending [stop] (and emits on [autoStoppedRecordings] when
     * [autoFinish] is set), or discards everything after a [cancel]. Safe
     * to call from any thread and any number of times.
     */
    private fun finishRecording(autoFinish: AutoFinishReason?) {
        val completionToResolve: CompletableDeferred<StopResult>?
        val trimmed: FloatArray
        val discarded: Boolean
        synchronized(stateLock) {
            if (!recording) return
            recording = false
            captureJob = null
            levelJob?.cancel()
            levelJob = null
            pendingLevels.clear()
            windowCarry = FloatArray(0)
            discarded = discardOnFinish
            discardOnFinish = false
            val buffered = assembleLocked()
            chunks = mutableListOf()
            totalSamples = 0
            silentSamples = 0L
            trimmed = if (discarded) FloatArray(0) else SilenceTrimmer.trim(buffered)
            if (!discarded) {
                pendingResult = StopResult(trimmed, autoFinish)
            }
            completionToResolve = completion
            publishIdleOrUnavailableLocked()
        }
        if (discarded) {
            completionToResolve?.cancel()
            Log.d(TAG, "Recording cancelled; buffered audio discarded")
            return
        }
        completionToResolve?.complete(StopResult(trimmed, autoFinish))
        if (autoFinish != null) {
            if (!mutableAutoStopped.tryEmit(StopResult(trimmed, autoFinish))) {
                Log.w(TAG, "Auto-finished recording had no collector; result remains available via stop()")
            }
            Log.d(TAG, "Recording auto-finished ($autoFinish); ${trimmed.size} samples after trim")
        } else {
            Log.d(TAG, "Recording finished; ${trimmed.size} samples after trim")
        }
    }

    /** Concatenates buffered chunks, truncated at the safety backstop. Caller holds [stateLock]. */
    private fun assembleLocked(): FloatArray {
        val targetSize = minOf(totalSamples, MAX_RECORDING_SAMPLES)
        val assembled = FloatArray(targetSize)
        var offset = 0
        for (chunk in chunks) {
            val copyCount = minOf(chunk.size, targetSize - offset)
            if (copyCount <= 0) break
            chunk.copyInto(assembled, destinationOffset = offset, startIndex = 0, endIndex = copyCount)
            offset += copyCount
        }
        return assembled
    }

    private fun publishRecordingLocked() {
        mutableState.update {
            RecordingSessionState.Recording(elapsedMs = latestElapsedMs, amplitude = latestAmplitude)
        }
    }

    private fun publishIdleOrUnavailableLocked() {
        mutableState.update {
            if (inputsAvailable) RecordingSessionState.Idle else RecordingSessionState.Unavailable
        }
    }

    private companion object {
        const val TAG: String = "RecordingSession"

        /**
         * Absolute safety backstop on one dictation (10 minutes, ENC-160),
         * per `contracts/TRANSCRIPTION_RULES.md` §2. Almost unreachable:
         * dead air auto-finishes first; this bounds only continuous speech.
         */
        const val MAX_RECORDING_SECONDS: Int = 600
        const val MAX_RECORDING_SAMPLES: Int = MAX_RECORDING_SECONDS * SilenceTrimmer.SAMPLE_RATE_HZ

        /**
         * Dead-air auto-finish (ENC-160): this much consecutive
         * sub-threshold audio finishes the recording as a stop. Counted in
         * samples, not wall time, so chunk cadence cannot skew it.
         */
        const val INACTIVITY_SILENCE_MS: Long = 30_000L
        const val INACTIVITY_SILENCE_SAMPLES: Long =
            INACTIVITY_SILENCE_MS * SilenceTrimmer.SAMPLE_RATE_HZ / 1_000L

        /** Wall-time interval between amplitude publishes (~30 Hz level stream). */
        const val AMPLITUDE_TICK_MS: Long = 33L

        /** Samples per amplitude window: ~33 ms at 16 kHz — matches [AMPLITUDE_TICK_MS]. */
        const val AMPLITUDE_WINDOW_SAMPLES: Int = 528

        /** Bounded level queue (~3 s); overflow drops the oldest levels. */
        const val MAX_PENDING_LEVELS: Int = 90

        const val MS_PER_SECOND: Long = 1_000L
    }
}
