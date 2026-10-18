package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import javax.inject.Inject

/**
 * Pure state machine mapping [FieldFocusState] transitions to bubble window
 * commands.
 *
 * - [FieldFocusState.EditableFocus] shows the bubble unless it is already
 *   visible or was dismissed for the current field session.
 * - Any non-editable state (none, or a password field) hides the bubble and
 *   clears dismissal suppression: dismissal is per field session, so once
 *   focus leaves editable fields the next editable field shows the bubble
 *   again.
 * - Duplicate or equivalent states produce no command, keeping show/hide
 *   idempotent across rapid transitions.
 *
 * Stateful but single-threaded in practice: driven from the bubble service's
 * main-thread collection. Not a singleton — each service instance owns one
 * through the use case.
 */
class BubbleVisibilityReducer @Inject constructor() {

    private var bubbleVisible: Boolean = false
    private var dismissedThisFieldSession: Boolean = false

    /** Feed one focus transition; returns the window command it implies, if any. */
    fun onFocusState(state: FieldFocusState): BubbleVisibilityCommand? =
        when (state) {
            is FieldFocusState.EditableFocus -> showIfNeeded()
            FieldFocusState.NoEditableFocus,
            is FieldFocusState.PasswordFocus,
            -> {
                dismissedThisFieldSession = false
                hideIfVisible()
            }
        }

    /** The user dragged the visible bubble into the dismiss zone. */
    fun onBubbleDismissed(): BubbleVisibilityCommand? {
        dismissedThisFieldSession = true
        return hideIfVisible()
    }

    /** Forget visibility + dismissal; called when the owning service is destroyed. */
    fun reset() {
        bubbleVisible = false
        dismissedThisFieldSession = false
    }

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
