package com.shippopotamus.bloviate.domain.model

/** Classified end-of-gesture result for a completed touch sequence on the bubble. */
sealed interface BubbleGesture {

    /** Short press without meaningful movement. Phase 2: start/stop recording. */
    data object Tap : BubbleGesture

    /** Held in place past the long-press timeout. Phase 2: push-to-talk. */
    data object LongPress : BubbleGesture

    /** Moved past the touch slop. The new position is persisted. */
    data object Drag : BubbleGesture

    /** A drag released inside the dismiss zone. Hides the bubble for this field session. */
    data object DragDismiss : BubbleGesture
}
