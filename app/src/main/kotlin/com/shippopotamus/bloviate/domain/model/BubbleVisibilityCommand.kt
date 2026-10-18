package com.shippopotamus.bloviate.domain.model

/**
 * Window commands for the bubble overlay, derived from field-focus
 * transitions by [com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer].
 * Idempotent: the service never adds an already-added view or removes an
 * already-removed one.
 */
sealed interface BubbleVisibilityCommand {

    /** Show the bubble window (no-op if already visible). */
    data object ShowBubble : BubbleVisibilityCommand

    /** Hide and remove the bubble window (no-op if already hidden). */
    data object HideBubble : BubbleVisibilityCommand
}
