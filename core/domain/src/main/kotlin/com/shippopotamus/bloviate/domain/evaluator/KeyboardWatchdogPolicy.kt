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
 */
object KeyboardWatchdogPolicy {

    /** Why an armed watchdog stopped polling. */
    enum class StopReason {
        KEYBOARD_VISIBLE,
        FOCUS_LOST,
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
     * Why the watchdog should stop, given the current signals and how
     * long this arm has been running — or null to keep polling.
     */
    fun stopReason(
        focusState: FieldFocusState,
        keyboardVisible: Boolean,
        armedForMs: Long,
    ): StopReason? = when {
        keyboardVisible -> StopReason.KEYBOARD_VISIBLE
        focusState !is FieldFocusState.EditableFocus -> StopReason.FOCUS_LOST
        armedForMs >= MAX_ARM_DURATION_MS -> StopReason.EXPIRED
        else -> null
    }
}
