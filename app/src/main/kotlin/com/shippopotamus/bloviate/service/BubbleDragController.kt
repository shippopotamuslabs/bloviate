package com.shippopotamus.bloviate.service

import android.os.SystemClock
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import com.shippopotamus.bloviate.domain.evaluator.BubbleDragSession
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.ScreenBounds

/**
 * Gesture handling for the bubble window, driven by the gesture callbacks of
 * the bubble's own composition (`BubbleContent`'s `pointerInput` detectors).
 *
 * Gestures are deliberately NOT handled with a [View.OnTouchListener] on the
 * ComposeView: the composition's internal child view becomes the touch target
 * on `ACTION_DOWN` and consumes the stream, so a view-level listener never
 * receives a usable move sequence and the window cannot follow the finger.
 * Detecting inside the composition and reporting high-level events here keeps
 * the window mutation and classification in one place.
 *
 * A tap routes to [onTapGesture] (the recording controller: tap starts a
 * recording — there is no hold-to-talk). A drag accumulates in a
 * [BubbleDragSession]: the window follows the finger (clamped on-screen),
 * [inDismissZone] tracks whether the bubble's center is inside the bottom
 * dismiss zone (driving the shrink affordance), and on release a drag
 * persists its end position via [onDragEnded] while a drag released in the
 * dismiss zone requests dismissal via [onDismissRequested].
 */
class BubbleDragController(
    private val windowManager: WindowManager,
    private val bubbleView: View,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTapGesture: () -> Unit,
    private val onDismissRequested: () -> Unit,
    private val onDragEnded: (BubblePosition) -> Unit,
    private val inDismissZone: MutableState<Boolean>,
) {

    private val touchSlopPx: Float = ViewConfiguration.get(bubbleView.context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs: Long = ViewConfiguration.getLongPressTimeout().toLong()

    private var session: BubbleDragSession? = null

    fun onTap() {
        onTapGesture()
    }

    /** The drag detector crossed its slop gate; anchor a session at the current window position. */
    fun onDragStart() {
        session = BubbleDragSession(
            originTopLeft = BubblePixelPosition(xPx = layoutParams.x, yPx = layoutParams.y),
            startTimeMs = SystemClock.uptimeMillis(),
        )
    }

    fun onDrag(deltaXPx: Float, deltaYPx: Float, timeMs: Long) {
        val activeSession = session ?: return
        val bounds = currentBounds()
        val moved = activeSession.dragBy(
            deltaXPx = deltaXPx,
            deltaYPx = deltaYPx,
            timeMs = timeMs,
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

    fun onDragEnd() {
        val activeSession = session ?: return
        session = null
        val gesture = activeSession.finish(
            touchSlopPx = touchSlopPx,
            longPressTimeoutMs = longPressTimeoutMs,
            endInDismissZone = inDismissZone.value,
        )
        inDismissZone.value = false
        // The session guarantees a drag verdict (see BubbleDragSession.finish):
        // a dismiss ends the field session, any other drag persists its position.
        if (gesture == BubbleGesture.DragDismiss) {
            onDismissRequested()
        } else {
            onDragEnded(currentNormalizedPosition())
        }
    }

    fun onDragCancel() {
        session = null
        inDismissZone.value = false
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

    private companion object {
        const val CENTER_DIVISOR: Int = 2
    }
}
