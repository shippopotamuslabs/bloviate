package com.shippopotamus.bloviate.service

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import com.shippopotamus.bloviate.domain.evaluator.BubbleGestureClassifier
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.PointerSample
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import com.shippopotamus.bloviate.domain.port.BubbleActionsPort
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Touch handling for the bubble window. Attached as the [View.OnTouchListener]
 * of the bubble's ComposeView, so every touch on the bubble lands here and
 * Compose stays purely visual.
 *
 * Live behavior: once movement passes the touch slop, the window follows the
 * finger (clamped on-screen) and [inDismissZone] tracks whether the bubble's
 * center is inside the bottom dismiss zone, driving the shrink affordance.
 * On release, the completed sample sequence goes through
 * [BubbleGestureClassifier]: tap/long-press are routed to [BubbleActionsPort],
 * a drag persists its end position via [onDragEnded], and a drag ending in
 * the dismiss zone requests dismissal via [onDismissRequested].
 */
class BubbleDragController(
    private val windowManager: WindowManager,
    private val bubbleView: View,
    private val layoutParams: WindowManager.LayoutParams,
    private val actions: BubbleActionsPort,
    private val onDismissRequested: () -> Unit,
    private val onDragEnded: (BubblePosition) -> Unit,
    private val inDismissZone: MutableState<Boolean>,
) {

    private val classifier = BubbleGestureClassifier()
    private val touchSlopPx: Float = ViewConfiguration.get(bubbleView.context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs: Long = ViewConfiguration.getLongPressTimeout().toLong()

    private val samples = mutableListOf<PointerSample>()
    private var dragOrigin = BubblePixelPosition(xPx = 0, yPx = 0)
    private var dragging: Boolean = false

    /** Attach to the bubble view. Consumes every event on the bubble. */
    val touchListener: View.OnTouchListener = View.OnTouchListener { _, event -> handleTouchEvent(event) }

    private fun handleTouchEvent(event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onDown(event)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                onMove(event)
                true
            }
            MotionEvent.ACTION_UP -> {
                onUp(event)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetGesture()
                true
            }
            else -> false
        }

    private fun onDown(event: MotionEvent) {
        samples.clear()
        samples += event.toSample()
        dragOrigin = BubblePixelPosition(xPx = layoutParams.x, yPx = layoutParams.y)
        dragging = false
    }

    private fun onMove(event: MotionEvent) {
        if (samples.isEmpty()) return
        val sample = event.toSample()
        samples += sample
        if (!dragging && !exceedsSlop(sample)) return
        dragging = true
        val down = samples.first()
        val bounds = currentBounds()
        val moved = BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(
                xPx = dragOrigin.xPx + (sample.xPx - down.xPx).roundToInt(),
                yPx = dragOrigin.yPx + (sample.yPx - down.yPx).roundToInt(),
            ),
            bubbleWidthPx = bubbleView.width,
            bubbleHeightPx = bubbleView.height,
            bounds = bounds,
        )
        layoutParams.x = moved.xPx
        layoutParams.y = moved.yPx
        windowManager.updateViewLayout(bubbleView, layoutParams)
        inDismissZone.value = BubblePositionMath.isInDismissZone(
            centerYPx = moved.yPx + bubbleView.height / CENTER_DIVISOR,
            bounds = bounds,
        )
    }

    private fun onUp(event: MotionEvent) {
        if (samples.isEmpty()) {
            resetGesture()
            return
        }
        samples += event.toSample()
        val gesture = classifier.classify(
            samples = samples.toList(),
            touchSlopPx = touchSlopPx,
            longPressTimeoutMs = longPressTimeoutMs,
            endInDismissZone = dragging && inDismissZone.value,
        )
        when (gesture) {
            BubbleGesture.Tap -> actions.onTap()
            BubbleGesture.LongPress -> actions.onLongPress()
            BubbleGesture.Drag -> onDragEnded(currentNormalizedPosition())
            BubbleGesture.DragDismiss -> onDismissRequested()
        }
        resetGesture()
    }

    private fun exceedsSlop(sample: PointerSample): Boolean {
        val down = samples.first()
        return hypot(sample.xPx - down.xPx, sample.yPx - down.yPx) > touchSlopPx
    }

    private fun currentNormalizedPosition(): BubblePosition =
        BubblePositionMath.toNormalized(
            topLeft = BubblePixelPosition(xPx = layoutParams.x, yPx = layoutParams.y),
            bubbleWidthPx = bubbleView.width,
            bubbleHeightPx = bubbleView.height,
            bounds = currentBounds(),
        )

    private fun currentBounds(): ScreenBounds {
        val rect = windowManager.currentWindowMetrics.bounds
        return ScreenBounds(widthPx = rect.width(), heightPx = rect.height())
    }

    private fun resetGesture() {
        samples.clear()
        dragging = false
        inDismissZone.value = false
    }

    private fun MotionEvent.toSample(): PointerSample =
        PointerSample(timeMs = eventTime, xPx = rawX, yPx = rawY)

    private companion object {
        const val CENTER_DIVISOR: Int = 2
    }
}
