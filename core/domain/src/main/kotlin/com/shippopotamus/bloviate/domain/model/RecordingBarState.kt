package com.shippopotamus.bloviate.domain.model

/**
 * Visual state of the bubble window while dictation is possible, mirroring
 * the bubble states in `contracts/UI_DESIGN_SPEC.md` §3. The mic bubble and
 * the recording row are one window with instant content swaps (ENC-167):
 * [Idle] renders the mic bubble, the other states render their own elements.
 */
sealed interface RecordingBarState {

    /** Mic bubble, resting. A tap starts a recording. */
    data object Idle : RecordingBarState

    /**
     * Recording in progress: cancel (X), live waveform, elapsed time, and
     * the check (send) affordance. [elapsedMs] is the session's
     * sample-count-derived duration — but the republish trigger is a
     * change in the displayed second ([elapsedMs] / 1000), not any
     * elapsed change: the session emits per ~33ms amplitude window
     * (~30 Hz), and republishing at that cadence recomposed the overlay
     * mid-drag and corrupted pointer deltas (ENC-162). Entry from Idle
     * always publishes. [frame] is a monotonic tick bumped on
     * each publish; elapsed-only publishes always differ in [elapsedMs]
     * already, so [frame] is belt-and-braces distinctness rather than
     * load-bearing against StateFlow conflation — it is kept so successive
     * publishes stay distinguishable even if two ever carried the same
     * elapsed value. The levels themselves stay out of this state — the
     * Canvas re-reads them through its deferred lambda at display rate via
     * its own frame clock (`CODE_STYLE_RULES.md` §4.5), with no republish.
     */
    data class Recording(val elapsedMs: Long, val frame: Long = 0L) : RecordingBarState

    /**
     * Stage 1 of the send pipeline: the utterance is being transcribed
     * on-device. The bar shows the stage's name as small shimmering text
     * (ENC-76) — no spinner, no fake percentages.
     */
    data object Transcribing : RecordingBarState

    /**
     * Stage 2 of the send pipeline: the LLM style pass (ENC-73) is
     * running. Entered only when the pass actually runs — an LLM style
     * selected and a key stored; local-only sends go from [Transcribing]
     * straight to [Done]/[Error]. Same shimmering stage text as
     * [Transcribing], different word (ENC-76).
     */
    data object Polishing : RecordingBarState

    /**
     * Transcription succeeded; the brief check affordance before returning to [Idle].
     * [autoFinish] marks a recording the session finished on its own
     * (ENC-155, ENC-160): the send path is identical, but the bubble names
     * the reason so an auto-send never reads as a crash, and a manual stop
     * stays a plain check.
     */
    data class Done(val autoFinish: AutoFinishReason? = null) : RecordingBarState

    /** Something failed (or a tap landed while the session is unavailable); brief error flash. */
    data object Error : RecordingBarState

    /**
     * Why the bubble names an unstyled insert instead of showing the
     * plain [Done] check (BLO-147): cloud styling fell back for a reason
     * worth telling the user about. Transient transport trouble stays
     * silent — only entitlement and sign-in causes surface.
     */
    enum class StyleFallbackNotice {
        /** The style call was refused: no entitlement for cloud styling. */
        SUBSCRIPTION_REQUIRED,

        /** No signed-in user: the style call never ran. */
        SIGN_IN_REQUIRED,
    }

    /**
     * The text was inserted, but the cloud style pass fell back for a
     * [notice] cause — so instead of the plain [Done] check, the bubble
     * shows a brief labeled pill naming what happened. Dictation is
     * never blocked: the raw text is already in the field. Transient
     * failures (no network, timed out) land on [Done] as before.
     */
    data class NotStyled(val notice: StyleFallbackNotice) : RecordingBarState
}
