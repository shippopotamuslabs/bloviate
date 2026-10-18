package com.shippopotamus.bloviate.domain.port

/**
 * Sink for discrete bubble gestures that mean something to the dictation
 * pipeline. In Phase 1 the implementation only logs; Phase 2 (ENC-48) routes
 * these to recording start/stop behind this same port.
 */
interface BubbleActionsPort {

    /** Tap on the bubble. Phase 2: toggle recording. */
    fun onTap()

    /** Long-press on the bubble. Phase 2: push-to-talk. */
    fun onLongPress()
}
