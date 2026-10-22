package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.PointerSample
import javax.inject.Inject
import kotlin.math.hypot

/**
 * Pure classification of a completed touch sequence on the bubble.
 *
 * Movement is judged by the *maximum* excursion from the down point, not the
 * end point: a drag that wanders out and returns is still a drag. Dismissal
 * additionally requires a drag — a tap or press ending in the dismiss zone
 * does not dismiss.
 */
class BubbleGestureClassifier @Inject constructor() {

    /**
     * @param samples down-to-up samples in screen coordinates; may be empty.
     * @param touchSlopPx movement (px) beyond which the gesture is a drag.
     * @param longPressTimeoutMs hold duration (ms) at/after which a still press is a long-press.
     * @param endInDismissZone whether the final bubble position is inside the dismiss zone.
     */
    fun classify(
        samples: List<PointerSample>,
        touchSlopPx: Float,
        longPressTimeoutMs: Long,
        endInDismissZone: Boolean,
    ): BubbleGesture {
        val first = samples.firstOrNull() ?: return BubbleGesture.Tap
        val maxDisplacementPx = samples.maxOf { sample ->
            hypot(sample.xPx - first.xPx, sample.yPx - first.yPx)
        }
        val durationMs = samples.last().timeMs - first.timeMs
        return when {
            maxDisplacementPx > touchSlopPx ->
                if (endInDismissZone) BubbleGesture.DragDismiss else BubbleGesture.Drag
            durationMs >= longPressTimeoutMs -> BubbleGesture.LongPress
            else -> BubbleGesture.Tap
        }
    }
}
