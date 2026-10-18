package com.shippopotamus.bloviate.domain.model

/**
 * Visual state of the bubble window while dictation is possible, mirroring
 * the bubble states in `contracts/UI_DESIGN_SPEC.md` §3. The mic bubble and
 * the recording bar are one window: [Idle] renders the mic bubble, every
 * other state renders the recording bar that the bubble transforms into
 * (ENC-48's binding interaction design).
 */
sealed interface RecordingBarState {

    /** Mic bubble, resting. A tap starts a recording. */
    data object Idle : RecordingBarState

    /**
     * Recording in progress: cancel (X), live waveform, elapsed time, and
     * the check (send) affordance. [elapsedMs] is the session's
     * sample-count-derived duration.
     */
    data class Recording(val elapsedMs: Long) : RecordingBarState

    /** The utterance is being transcribed; indeterminate progress, no fake percentages. */
    data object Transcribing : RecordingBarState

    /** Transcription succeeded; the brief check affordance before returning to [Idle]. */
    data object Done : RecordingBarState

    /** Something failed (or a tap landed while the session is unavailable); brief error flash. */
    data object Error : RecordingBarState
}
