package com.shippopotamus.bloviate.service

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.domain.evaluator.WaveformHistory
import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.domain.port.RecordingSessionPort
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import com.shippopotamus.bloviate.domain.port.TranscriptSinkPort
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine behind the recording bar (ENC-61): translates the recording
 * session's state (ENC-60) and the bar's button taps into [RecordingBarState]
 * for the overlay composition, and drives the send pipeline — stop →
 * transcribe (whole utterance, one pass) → [TextPostProcessor] cleanup →
 * [TranscriptSinkPort] insertion (ENC-62). The bar shows Done only when the
 * sink reports the text actually inserted; a refused/failed insertion
 * surfaces Error.
 *
 * Interaction model (Sean's design, ENC-48): tap the mic bubble to start,
 * tap the bar's X to cancel, tap the bar's check to stop + transcribe.
 * There is no hold-to-talk. Recordings the session's ~3 minute hard cap
 * finishes arrive on [RecordingSessionPort.autoStoppedRecordings] and take
 * the same send path as a check tap.
 *
 * Failure keeps the stopped samples for one retry (`ARCHITECTURE_RULES.md`
 * §5.3): while the Error state is up, tapping the bubble re-runs the send
 * pipeline from the retained samples — no re-recording. The buffer clears
 * on success, when the Error window elapses, when the host field is left,
 * and after the single retry is spent.
 *
 * Done and Error are transient: the controller returns them to Idle on a
 * timer ([DONE_DISPLAY_MS] / [ERROR_DISPLAY_MS]), and the overlay
 * composition reports its own display-window elapse through
 * [onTransientStateElapsed] as a backstop — whichever fires first wins.
 *
 * The host window hiding (focus left the field — above all: a password
 * field gained focus) cancels any active recording and discards any
 * in-flight transcription: a transcript is never produced for a field the
 * bubble has left.
 *
 * Owned by [BubbleOverlayService] for its lifetime; all collector and
 * pipeline jobs run on the service's scope, blocking stop/transcribe work
 * on [workDispatcher].
 */
class BubbleRecordingController(
    private val session: RecordingSessionPort,
    private val transcriptionEngine: TranscriptionEngine,
    private val textPostProcessor: TextPostProcessor,
    private val transcriptSink: TranscriptSinkPort,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val doneDisplayMs: Long = DONE_DISPLAY_MS,
    private val errorDisplayMs: Long = ERROR_DISPLAY_MS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /** Rolling amplitude window rendered by the bar's waveform. */
    val waveform: WaveformHistory = WaveformHistory()

    private val mutableBarState = MutableStateFlow<RecordingBarState>(RecordingBarState.Idle)

    /** The bar's current visual state; the overlay composition renders this. */
    val barState: StateFlow<RecordingBarState> = mutableBarState.asStateFlow()

    private var sessionCollectJob: Job? = null
    private var autoStopCollectJob: Job? = null
    private var pipelineJob: Job? = null
    private var resetJob: Job? = null

    /**
     * The stopped samples of a failed send, kept for the one retry tap
     * (§5.3). Null when there is nothing to retry.
     */
    private var retainedSamples: FloatArray? = null

    init {
        sessionCollectJob = scope.launch { collectSessionState() }
        autoStopCollectJob = scope.launch { collectAutoStopped() }
    }

    /**
     * Mic bubble tapped: start a recording, or flash the error state when
     * the session can't. While the Error state is up and a failed send's
     * samples are retained, the tap is the §5.3 retry instead — it re-runs
     * the pipeline from the retained samples (one retry only).
     */
    fun onBubbleTap() {
        val retained = retainedSamples
        if (mutableBarState.value == RecordingBarState.Error && retained != null) {
            Log.d(TAG, "Retrying the failed send from the retained recording")
            cancelResetJob()
            mutableBarState.update { RecordingBarState.Transcribing }
            pipelineJob = scope.launch {
                transcribeAndFinish(retained, stoppedAtMs = nowMs(), isRetry = true)
            }
            return
        }
        if (mutableBarState.value != RecordingBarState.Idle) return
        when (val sessionState = session.state.value) {
            is RecordingSessionState.Recording -> {
                // The bar fell behind the session (e.g. window re-shown mid-recording): resync.
                mutableBarState.update { RecordingBarState.Recording(sessionState.elapsedMs) }
            }
            RecordingSessionState.Idle -> {
                waveform.clear()
                session.start()
            }
            RecordingSessionState.Unavailable -> {
                Log.w(TAG, "Bubble tapped while the session is unavailable (model or microphone)")
                showError()
            }
        }
    }

    /** The bar's X tapped: discard the recording; nothing is transcribed or inserted. */
    fun onCancelTap() {
        if (mutableBarState.value !is RecordingBarState.Recording) return
        session.cancel()
        waveform.clear()
        mutableBarState.update { RecordingBarState.Idle }
        Log.d(TAG, "Recording cancelled from the bar")
    }

    /** The bar's check tapped: stop the recording and run the send pipeline. */
    fun onSendTap() {
        if (mutableBarState.value !is RecordingBarState.Recording) return
        cancelResetJob()
        mutableBarState.update { RecordingBarState.Transcribing }
        pipelineJob = scope.launch {
            try {
                val samples = withContext(workDispatcher) { session.stop() }
                transcribeAndFinish(samples, stoppedAtMs = nowMs())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Could not stop the recording for transcription", exception)
                showError()
            }
        }
    }

    /**
     * The bubble window is going away (field focus lost — critically, a
     * password field may have gained it). An active recording is cancelled
     * and an in-flight transcription is discarded, never delivered.
     */
    fun onHostHidden() {
        cancelResetJob()
        // A retained retry belongs to the field that was just left; it must
        // never fire into whatever field comes next.
        retainedSamples = null
        when (mutableBarState.value) {
            is RecordingBarState.Recording -> {
                session.cancel()
                waveform.clear()
                Log.d(TAG, "Host hidden while recording; recording cancelled")
            }
            RecordingBarState.Transcribing -> {
                pipelineJob?.cancel()
                Log.d(TAG, "Host hidden while transcribing; transcript discarded")
            }
            RecordingBarState.Idle,
            RecordingBarState.Done,
            RecordingBarState.Error,
            -> Unit
        }
        mutableBarState.update { RecordingBarState.Idle }
    }

    /** Memory pressure (TRANSCRIPTION_RULES §4): the waveform history is a releasable cache. */
    fun onLowMemory() {
        waveform.clear()
    }

    /**
     * The Done/Error display window has elapsed, reported by the overlay
     * composition's own effect (ENC-65). The controller's timer
     * ([scheduleReset]) is the primary return path; the composition calls
     * this as a backstop so a transient state can never be stranded on the
     * bubble if the controller's timer is ever swallowed by the overlay's
     * lifecycle. Whichever fires first wins — the transition is guarded,
     * so the second call is a no-op.
     */
    fun onTransientStateElapsed() {
        cancelResetJob()
        returnToIdleIfTransient()
    }

    /** Cancels all collectors and in-flight work; the owning service is going away. */
    fun shutdown() {
        sessionCollectJob?.cancel()
        autoStopCollectJob?.cancel()
        pipelineJob?.cancel()
        cancelResetJob()
    }

    private suspend fun collectSessionState() {
        session.state.collect { state ->
            when (state) {
                is RecordingSessionState.Recording -> {
                    waveform.record(state.amplitude)
                    val bar = mutableBarState.value
                    if (bar == RecordingBarState.Idle || bar is RecordingBarState.Recording) {
                        mutableBarState.update { RecordingBarState.Recording(state.elapsedMs) }
                    }
                }
                RecordingSessionState.Idle,
                RecordingSessionState.Unavailable,
                -> {
                    // The session ended without the bar's buttons (external
                    // cancel, capture failure): fall back to the mic bubble.
                    if (mutableBarState.value is RecordingBarState.Recording) {
                        waveform.clear()
                        mutableBarState.update { RecordingBarState.Idle }
                    }
                }
            }
        }
    }

    private suspend fun collectAutoStopped() {
        session.autoStoppedRecordings.collect { samples ->
            if (mutableBarState.value is RecordingBarState.Recording) {
                Log.d(TAG, "Hard cap reached; sending the auto-stopped recording")
                cancelResetJob()
                mutableBarState.update { RecordingBarState.Transcribing }
                pipelineJob = scope.launch { transcribeAndFinish(samples, stoppedAtMs = nowMs()) }
            }
        }
    }

    /**
     * The send pipeline: transcribe → cleanup → insert. Done is shown only
     * when the sink reports the text inserted (or nothing insertable
     * remained after cleanup). Any failure keeps [samples] for the one
     * retry tap — unless this run already is the retry — and shows Error.
     *
     * Stop→insert latency (the Phase 2 exit metric) is logged from
     * [stoppedAtMs], captured the moment `session.stop()` returned.
     */
    private suspend fun transcribeAndFinish(
        samples: FloatArray,
        stoppedAtMs: Long,
        isRetry: Boolean = false,
    ) {
        try {
            val cleaned = withContext(workDispatcher) {
                textPostProcessor.process(transcriptionEngine.transcribe(samples))
            }
            val transcribedAtMs = nowMs()
            if (cleaned.isBlank()) {
                Log.d(TAG, "Transcript was empty after cleanup; nothing to insert")
                retainedSamples = null
                showDone()
                return
            }
            val result = transcriptSink.onTranscript(cleaned)
            val insertedAtMs = nowMs()
            Log.d(
                TAG,
                "stop->insert latencyMs=${insertedAtMs - stoppedAtMs} " +
                    "(transcribeMs=${transcribedAtMs - stoppedAtMs}, insertMs=${insertedAtMs - transcribedAtMs})",
            )
            when (result) {
                is InsertionResult.Inserted -> {
                    retainedSamples = null
                    showDone()
                }
                is InsertionResult.Refused -> {
                    Log.w(TAG, "Insertion refused: ${result.reason}")
                    retainForRetry(samples, isRetry)
                    showError()
                }
                is InsertionResult.Failed -> {
                    Log.w(TAG, "Insertion failed: ${result.detail}")
                    retainForRetry(samples, isRetry)
                    showError()
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Transcription failed; the recording is kept for one retry tap", exception)
            retainForRetry(samples, isRetry)
            showError()
        }
    }

    /** Keeps [samples] for the retry tap, unless this run already spent the one retry. */
    private fun retainForRetry(samples: FloatArray, isRetry: Boolean) {
        retainedSamples = if (isRetry) null else samples
    }

    private fun showDone() {
        mutableBarState.update { RecordingBarState.Done }
        scheduleReset(doneDisplayMs)
    }

    private fun showError() {
        mutableBarState.update { RecordingBarState.Error }
        scheduleReset(errorDisplayMs)
    }

    /** Done/Error are transient: fall back to the mic bubble after their display window. */
    private fun scheduleReset(delayMs: Long) {
        cancelResetJob()
        resetJob = scope.launch {
            delay(delayMs)
            returnToIdleIfTransient()
        }
    }

    /** Transient states (Done/Error) fall back to the mic bubble; anything else is left alone. */
    private fun returnToIdleIfTransient() {
        val bar = mutableBarState.value
        if (bar == RecordingBarState.Done || bar == RecordingBarState.Error) {
            if (bar == RecordingBarState.Error) {
                // The retry window has closed; the retained buffer's
                // lifetime (§5.3: "long enough for one retry tap") is over.
                retainedSamples = null
            }
            mutableBarState.update { RecordingBarState.Idle }
        }
    }

    private fun cancelResetJob() {
        resetJob?.cancel()
        resetJob = null
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
        const val DONE_DISPLAY_MS: Long = 1_200L
        const val ERROR_DISPLAY_MS: Long = 1_500L
    }
}
