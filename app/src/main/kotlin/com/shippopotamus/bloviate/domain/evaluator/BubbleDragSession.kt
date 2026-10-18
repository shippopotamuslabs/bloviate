package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.PointerSample
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Accumulates one bubble drag from the deltas reported by the Compose drag
 * detector: pointer deltas in, clamped window positions out, and a final
 * [BubbleGesture] when the drag ends.
 *
 * The session exists only because the detector's own slop gate already fired,
 * so a finished session is always a drag. Classification still goes through
 * [BubbleGestureClassifier] — fed with samples synthesized in delta space
 * (down point, peak-excursion point, release point) — so its rules are
 * preserved verbatim, in particular that dismissal requires a drag: if the
 * accumulated deltas under-measure the gesture (the movement that crossed
 * the slop gate is not itself delivered as a drag delta) the classifier can
 * return a still-press verdict, which [finish] maps to [BubbleGesture.Drag]
 * rather than ever dismissing.
 */
class BubbleDragSession(
    private val originTopLeft: BubblePixelPosition,
    private val startTimeMs: Long,
) {

    private val classifier = BubbleGestureClassifier()

    private var totalDeltaXPx: Float = 0f
    private var totalDeltaYPx: Float = 0f
    private var peakDeltaXPx: Float = 0f
    private var peakDeltaYPx: Float = 0f
    private var peakTimeMs: Long = startTimeMs
    private var lastTimeMs: Long = startTimeMs

    /**
     * Accumulate one pointer delta and return the window's new top-left,
     * clamped fully inside [bounds].
     */
    fun dragBy(
        deltaXPx: Float,
        deltaYPx: Float,
        timeMs: Long,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePixelPosition {
        totalDeltaXPx += deltaXPx
        totalDeltaYPx += deltaYPx
        lastTimeMs = timeMs
        if (hypot(totalDeltaXPx, totalDeltaYPx) > hypot(peakDeltaXPx, peakDeltaYPx)) {
            peakDeltaXPx = totalDeltaXPx
            peakDeltaYPx = totalDeltaYPx
            peakTimeMs = timeMs
        }
        return currentTopLeft(
            bubbleWidthPx = bubbleWidthPx,
            bubbleHeightPx = bubbleHeightPx,
            bounds = bounds,
        )
    }

    /** The window's current top-left for the accumulated deltas, clamped into [bounds]. */
    fun currentTopLeft(
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePixelPosition =
        BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(
                xPx = originTopLeft.xPx + totalDeltaXPx.roundToInt(),
                yPx = originTopLeft.yPx + totalDeltaYPx.roundToInt(),
            ),
            bubbleWidthPx = bubbleWidthPx,
            bubbleHeightPx = bubbleHeightPx,
            bounds = bounds,
        )

    /**
     * Classify the completed drag. [endInDismissZone] is the bubble's final
     * position relative to the dismiss zone; only a genuine drag can dismiss.
     */
    fun finish(
        touchSlopPx: Float,
        longPressTimeoutMs: Long,
        endInDismissZone: Boolean,
    ): BubbleGesture {
        val samples = listOf(
            PointerSample(timeMs = startTimeMs, xPx = 0f, yPx = 0f),
            PointerSample(timeMs = peakTimeMs, xPx = peakDeltaXPx, yPx = peakDeltaYPx),
            PointerSample(timeMs = lastTimeMs, xPx = totalDeltaXPx, yPx = totalDeltaYPx),
        )
        val gesture = classifier.classify(
            samples = samples,
            touchSlopPx = touchSlopPx,
            longPressTimeoutMs = longPressTimeoutMs,
            endInDismissZone = endInDismissZone,
        )
        return when (gesture) {
            // The session only exists because the detector's slop gate fired,
            // so a still-press verdict means the deltas under-measured a real
            // drag — it persists like any other drag and never dismisses.
            BubbleGesture.Tap, BubbleGesture.LongPress -> BubbleGesture.Drag
            BubbleGesture.Drag, BubbleGesture.DragDismiss -> gesture
        }
    }
}
