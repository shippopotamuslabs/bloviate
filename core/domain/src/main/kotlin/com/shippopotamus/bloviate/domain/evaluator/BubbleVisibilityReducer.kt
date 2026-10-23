package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import javax.inject.Inject

/**
 * Pure state machine mapping field-focus transitions, keyboard-visibility
 * transitions, and recording-bar activity to bubble window commands.
 *
 * Composition rule (ENC-125, Sean's Wispr-Flow decision): the bubble is
 * visible exactly while the on-screen keyboard is up **and** an editable,
 * non-password field is focused —
 *
 * - [FieldFocusState.EditableFocus] shows the bubble only while the
 *   keyboard is visible, unless it is already visible or was dismissed
 *   for the current field session.
 * - The keyboard appearing over an already-focused editable field shows
 *   the bubble; the keyboard hiding hides it, even though the field
 *   keeps focus.
 * - Any non-editable focus state (none, or a password field) hides the
 *   bubble and clears dismissal suppression: dismissal is per field
 *   session, so once focus leaves editable fields the next editable
 *   field shows the bubble again. A plain focus loss hides immediately
 *   while the bar is idle, and defers while the bar is active (see
 *   below); a password-field focus always hides immediately.
 *
 * Recording-bar exemption: while the recording/processing bar is active
 * (fed via [onBarActivityChanged]), a keyboard hide never hides the
 * window, and neither does a non-password focus loss (e.g. a
 * notification-shade peek) — the send flow completes first, and the
 * deferred hide lands when the bar returns to idle. The ONLY
 * mid-recording instant hide is a password field taking focus (a
 * security cancel): it hides, and thereby cancels, exactly as before.
 *
 * Duplicate or equivalent inputs produce no command, keeping show/hide
 * idempotent across rapid transitions.
 *
 * Reconciliation (ENC-127, resident window since ENC-163): commands
 * are one-shot, so if a command's effect does not materialize — a
 * show swallowed because overlay permission was missing at apply
 * time, say — the belief in [bubbleVisible] would otherwise stick and
 * swallow every later show. The overlay service reports the resident
 * window's actual visibility back through [onWindowVisibilityChanged]
 * after applying each command, and the belief resyncs to reality;
 * re-driving a show is left to the next transition so a persistently
 * unshowable window cannot spin a command loop.
 *
 * Stateful but single-threaded in practice: driven from the bubble
 * service's main-thread collection. Not a singleton — each service
 * instance owns one through the use case.
 */
class BubbleVisibilityReducer @Inject constructor() {

    private var bubbleVisible: Boolean = false
    private var dismissedThisFieldSession: Boolean = false
    private var lastFocusState: FieldFocusState = FieldFocusState.NoEditableFocus
    private var keyboardVisible: Boolean = false
    private var barActive: Boolean = false

    /** Feed one focus transition; returns the window command it implies, if any. */
    fun onFocusState(state: FieldFocusState): BubbleVisibilityCommand? {
        lastFocusState = state
        return when (state) {
            is FieldFocusState.EditableFocus ->
                if (keyboardVisible) showIfNeeded() else null
            is FieldFocusState.PasswordFocus -> {
                dismissedThisFieldSession = false
                hideIfVisible()
            }
            FieldFocusState.NoEditableFocus -> {
                dismissedThisFieldSession = false
                // A shade peek, not a security cancel: defer like a
                // keyboard hide while the bar is mid-flow; the landing
                // in onBarActivityChanged covers the settle.
                if (barActive) null else hideIfVisible()
            }
        }
    }

    /** Feed one keyboard show/hide transition; returns the window command it implies, if any. */
    fun onKeyboardVisibilityChanged(visible: Boolean): BubbleVisibilityCommand? {
        if (visible == keyboardVisible) return null
        keyboardVisible = visible
        return when {
            visible ->
                if (lastFocusState is FieldFocusState.EditableFocus) showIfNeeded() else null
            // Keyboard gone: hide — unless the bar is mid-flow, in which
            // case the hide defers to onBarActivityChanged.
            barActive -> null
            else -> hideIfVisible()
        }
    }

    /**
     * Feed recording/processing bar activity (any non-idle bar state).
     * When activity ends, a keyboard hide or non-password focus loss
     * that was deferred while the bar was up lands here.
     */
    fun onBarActivityChanged(active: Boolean): BubbleVisibilityCommand? {
        if (active == barActive) return null
        barActive = active
        return if (!active && bubbleVisible && !shouldBeVisible()) {
            hideIfVisible()
        } else {
            null
        }
    }

    /** The user dragged the visible bubble into the dismiss zone. */
    fun onBubbleDismissed(): BubbleVisibilityCommand? {
        dismissedThisFieldSession = true
        return hideIfVisible()
    }

    /**
     * Whether the hide the overlay is about to apply comes from a
     * genuine password-field focus (ENC-156). Only that cause may
     * cancel a recording or discard an in-flight send — every other
     * host-hide must let the session finish and deliver the transcript
     * or retain it for retry. A notification-shade peek is not a
     * password field.
     */
    fun isPasswordFocus(): Boolean = lastFocusState is FieldFocusState.PasswordFocus

    /**
     * The overlay service reports the resident window's actual
     * visibility after applying a command (ENC-127 reconciliation). A
     * mismatch resyncs the visibility belief to reality:
     *
     * - Window hidden but believed visible: a show was swallowed. The
     *   belief silently forgets "visible" so the next show-worthy
     *   transition commands a fresh show; no command is returned here —
     *   re-driving immediately could loop against a window that cannot
     *   be shown at all (e.g. overlay permission still missing).
     * - Window shown but believed hidden and not desired: the one
     *   divergence worth an active correction — [BubbleVisibilityCommand.HideBubble]
     *   is returned for the service to apply.
     */
    fun onWindowVisibilityChanged(visible: Boolean): BubbleVisibilityCommand? {
        if (visible == bubbleVisible) return null
        bubbleVisible = visible
        return if (visible && !shouldBeVisible()) {
            bubbleVisible = false
            BubbleVisibilityCommand.HideBubble
        } else {
            null
        }
    }

    /**
     * One-line snapshot of the machine's inputs and belief for decision
     * logging (ENC-145): a device repro that shows the bubble never
     * appearing (or never hiding) resolves to the exact stuck signal
     * from this line alone.
     */
    fun describeStateForLog(): String {
        val focus = when (val focusState = lastFocusState) {
            FieldFocusState.NoEditableFocus -> "NoEditableFocus"
            is FieldFocusState.EditableFocus -> "EditableFocus(${focusState.packageName})"
            is FieldFocusState.PasswordFocus -> "PasswordFocus(${focusState.packageName})"
        }
        return "focus=$focus keyboardVisible=$keyboardVisible barActive=$barActive " +
            "dismissedThisFieldSession=$dismissedThisFieldSession bubbleVisible=$bubbleVisible"
    }

    /** Forget visibility, dismissal, and both signal states; called when the owning service is destroyed. */
    fun reset() {
        bubbleVisible = false
        dismissedThisFieldSession = false
        lastFocusState = FieldFocusState.NoEditableFocus
        keyboardVisible = false
        barActive = false
    }

    private fun shouldBeVisible(): Boolean =
        lastFocusState is FieldFocusState.EditableFocus &&
            keyboardVisible &&
            !dismissedThisFieldSession

    private fun showIfNeeded(): BubbleVisibilityCommand? =
        if (!bubbleVisible && !dismissedThisFieldSession) {
            bubbleVisible = true
            BubbleVisibilityCommand.ShowBubble
        } else {
            null
        }

    private fun hideIfVisible(): BubbleVisibilityCommand? =
        if (bubbleVisible) {
            bubbleVisible = false
            BubbleVisibilityCommand.HideBubble
        } else {
            null
        }
}
