package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One dictation recording session: capture, hard cap, and silence trim. The
 * recording bar (ENC-61) drives it; the transcription pipeline (ENC-62)
 * consumes what [stop] returns.
 *
 * Contract:
 * - [start] begins capture only from [RecordingSessionState.Idle]; from
 *   [RecordingSessionState.Unavailable] or while already recording it is a
 *   logged no-op.
 * - [stop] ends capture and returns the buffered samples with leading and
 *   trailing silence trimmed. It also returns the samples of a recording the
 *   hard cap just finished, so a late stop tap is never an error.
 * - The ~3 minute hard cap finishes the recording exactly like a stop (it is
 *   NOT a cancel): the trimmed samples are emitted on [autoStoppedRecordings]
 *   for the pipeline, and a subsequent [stop] still returns them once.
 * - [cancel] discards the buffered audio; nothing is emitted or returned.
 */
interface RecordingSessionPort {

    /** Session state: idle / recording (with elapsed + amplitude) / unavailable. */
    val state: StateFlow<RecordingSessionState>

    /** Trimmed samples of recordings finished by the hard cap, where no [stop] caller was waiting. */
    val autoStoppedRecordings: SharedFlow<FloatArray>

    /** Starts capture. No-op unless the state is [RecordingSessionState.Idle]. */
    fun start()

    /**
     * Stops capture and returns the trimmed samples of the active (or
     * just-capped) recording.
     *
     * @throws IllegalStateException when no recording is active or pending.
     */
    suspend fun stop(): FloatArray

    /** Discards the active recording. No-op when nothing is recording. */
    fun cancel()
}
