package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FieldFocusState

/**
 * Pure policy for the keyboard-derivation watchdog (ENC-128).
 *
 * Keyboard visibility is derived from the accessibility window list when
 * events fire. On some entry paths — entering an app by tapping a
 * notification is the observed one — the input-method window can join
 * the window list *after* the last accessibility event of the entry
 * sequence: the composer field is restored already-focused, and if every
 * derivation during the tap sequence still enumerates a list without
 * the IME window, no further event ever fires and the state machine
 * sits at keyboard = false forever, so the bubble never appears.
 *
 * While the state is *starved* — an editable, non-password field is
 * focused and the keyboard reads hidden, exactly the state where the
 * bubble is desired but cannot show — the watcher re-derives on a
 * bounded timer instead of relying purely on events. The watchdog arms
 * only in that state and stops the moment the state converges:
 *
 * - [StopReason.KEYBOARD_VISIBLE] — the re-derivation (or an event)
 *   found the keyboard; the pipeline takes it from there.
 * - [StopReason.FOCUS_LOST] — focus left editable, non-password fields
 *   (including a password field taking focus: nothing about a password
 *   field may ever arm or sustain the watchdog).
 * - [StopReason.EXPIRED] — the arm outlived [MAX_ARM_DURATION_MS]
 *   (e.g. a field focused with a hardware keyboard, where no IME window
 *   ever joins); polling stops so the watchdog can never spin forever.
 *
 * This complements, and does not replace, the ENC-127 hysteresis: that
 * filter absorbs keyboard readings that *flap* between events; the
 * watchdog heals readings that are stuck because no event arrives.
 *
 * The mirror failure (ENC-186): the ENC-128 arm heals the keyboard
 * reading only, but on the same notification-entry path the *focus*
 * derivation can be the stale one — read from a mid-transition window
 * tree during the entry sequence (or never re-read because the field
 * was restored already-focused and no subscribed event follows), it
 * sits at [FieldFocusState.NoEditableFocus] while the keyboard is up.
 * That pair is inconsistent — a visible IME means an editable field
 * almost certainly holds input focus — so the watchdog also arms in
 * the mirrored state ([isMirrorStarved]) and re-derives *focus* from
 * the live tree until the pair converges, the keyboard leaves, or the
 * arm expires. A password field is never mirror-starved: its keyboard
 * is legitimately visible, and nothing about it may sustain polling.
 */
object KeyboardWatchdogPolicy {

    /** Why an armed watchdog stopped polling. */
    enum class StopReason {
        KEYBOARD_VISIBLE,
        FOCUS_LOST,
        FOCUS_REGAINED,
        EXPIRED,
    }

    /** Delay between watchdog re-derivations while starved. */
    const val POLL_INTERVAL_MS: Long = 400L

    /** Hard cap on one arm, so a permanently keyboard-less focus never polls forever. */
    const val MAX_ARM_DURATION_MS: Long = 30_000L

    /**
     * The starved state: an editable, non-password field is focused and
     * the keyboard reads hidden — the bubble is desired but the
     * derivation may simply be event-starved.
     */
    fun isStarved(focusState: FieldFocusState, keyboardVisible: Boolean): Boolean =
        focusState is FieldFocusState.EditableFocus && !keyboardVisible

    /**
     * The mirrored starved state (ENC-186): the keyboard reads visible
     * while focus reads none — the focus derivation is the suspect one,
     * so the watchdog arms and re-derives focus from the live tree.
     * Password focus is excluded: its keyboard is legitimately up.
     */
    fun isMirrorStarved(focusState: FieldFocusState, keyboardVisible: Boolean): Boolean =
        focusState is FieldFocusState.NoEditableFocus && keyboardVisible

    /**
     * Why the watchdog should stop, given the current signals and how
     * long this arm has been running — or null to keep polling.
     * [mirrored] marks an arm taken under [isMirrorStarved]: its
     * convergence is focus becoming editable ([StopReason.FOCUS_REGAINED]),
     * and it stops as focus-lost when the keyboard leaves (the pair is
     * consistent at hidden and there is nothing left to heal) or when a
     * password field takes focus. Convergence outranks expiry in both
     * modes, as in the original policy.
     */
    fun stopReason(
        focusState: FieldFocusState,
        keyboardVisible: Boolean,
        armedForMs: Long,
        mirrored: Boolean = false,
    ): StopReason? = when {
        mirrored && focusState is FieldFocusState.EditableFocus -> StopReason.FOCUS_REGAINED
        mirrored && focusState !is FieldFocusState.NoEditableFocus -> StopReason.FOCUS_LOST
        mirrored && !keyboardVisible -> StopReason.FOCUS_LOST
        mirrored && armedForMs >= MAX_ARM_DURATION_MS -> StopReason.EXPIRED
        mirrored -> null
        keyboardVisible -> StopReason.KEYBOARD_VISIBLE
        focusState !is FieldFocusState.EditableFocus -> StopReason.FOCUS_LOST
        armedForMs >= MAX_ARM_DURATION_MS -> StopReason.EXPIRED
        else -> null
    }
}
