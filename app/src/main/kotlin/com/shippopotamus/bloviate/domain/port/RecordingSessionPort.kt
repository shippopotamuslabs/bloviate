package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.AutoFinishReason
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.domain.model.StopResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One dictation recording session: capture, auto-finish, and silence trim.
 * The recording bar (ENC-61) drives it; the transcription pipeline (ENC-62)
 * consumes what [stop] returns.
 *
 * Contract (ENC-160):
 * - [start] begins capture only from [RecordingSessionState.Idle]; from
 *   [RecordingSessionState.Unavailable] or while already recording it is a
 *   logged no-op.
 * - [stop] ends capture and returns the buffered samples with leading and
 *   trailing silence trimmed, plus why the recording finished. It also
 *   returns the result of a recording an auto-finish just finished, so a
 *   late stop tap is never an error.
 * - An auto-finish ends the recording exactly like a stop (it is NOT a
 *   cancel): thirty seconds of dead air ([AutoFinishReason.SILENCE]) or the
 *   ten-minute safety backstop ([AutoFinishReason.MAX_LENGTH]). The trimmed
 *   result is emitted on [autoStoppedRecordings] for the pipeline, and a
 *   subsequent [stop] still returns it once.
 * - [cancel] discards the buffered audio; nothing is emitted or returned.
 */
interface RecordingSessionPort {

    /** Session state: idle / recording (with elapsed + amplitude) / unavailable. */
    val state: StateFlow<RecordingSessionState>

    /** Trimmed results of recordings finished by an auto-finish, where no [stop] caller was waiting. */
    val autoStoppedRecordings: SharedFlow<StopResult>

    /** Starts capture. No-op unless the state is [RecordingSessionState.Idle]. */
    fun start()

    /**
     * Stops capture and returns the trimmed samples of the active (or
     * just-auto-finished) recording, with the finish reason attached.
     *
     * @throws IllegalStateException when no recording is active or pending.
     */
    suspend fun stop(): StopResult

    /** Discards the active recording. No-op when nothing is recording. */
    fun cancel()
}
