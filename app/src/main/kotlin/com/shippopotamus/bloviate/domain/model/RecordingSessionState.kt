package com.shippopotamus.bloviate.domain.model

/**
 * State of the dictation recording session, published by `RecordingSessionPort`
 * and consumed by the recording bar (ENC-61).
 *
 * [Unavailable] is published whenever a recording cannot start right now —
 * the model is not [ModelState.Ready] or the microphone permission is not
 * granted — so the UI can show its error state instead of a dead bubble.
 */
sealed interface RecordingSessionState {

    /** No active recording; a recording may be started. */
    data object Idle : RecordingSessionState

    /** A recording cannot start (model missing/downloading/failed, or mic permission missing). */
    data object Unavailable : RecordingSessionState

    /**
     * Capture is running. [elapsedMs] is derived from the captured sample
     * count (the honest audio duration, not wall time). [amplitude] is the
     * RMS level of the most recent audio chunk, 0..1, published at a
     * UI-sane rate (~12 Hz).
     */
    data class Recording(
        val elapsedMs: Long,
        val amplitude: Float,
    ) : RecordingSessionState
}
