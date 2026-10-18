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
     * sample-count-derived duration — it only advances when a recorder
     * chunk lands (~1 Hz). [frame] is a redraw tick bumped whenever the
     * waveform window materially changes (~30 Hz while speaking, ENC-69):
     * without it, successive Recording values between chunks would be
     * equal, StateFlow conflation would swallow them, and the overlay
     * composition would never redraw the waveform between chunk arrivals.
     * The levels themselves stay out of this state — the Canvas reads them
     * through its deferred lambda (`CODE_STYLE_RULES.md` §4.5); [frame]
     * only paces the redraw.
     */
    data class Recording(val elapsedMs: Long, val frame: Long = 0L) : RecordingBarState

    /** The utterance is being transcribed; indeterminate progress, no fake percentages. */
    data object Transcribing : RecordingBarState

    /** Transcription succeeded; the brief check affordance before returning to [Idle]. */
    data object Done : RecordingBarState

    /** Something failed (or a tap landed while the session is unavailable); brief error flash. */
    data object Error : RecordingBarState
}
