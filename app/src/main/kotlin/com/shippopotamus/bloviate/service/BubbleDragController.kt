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
 *
 * Single-writer rule (ENC-154): while [isDragging] is true this controller
 * owns the window's layout exclusively — every window write flows through
 * [onDrag], and the overlay's placement paths ([BubbleWindowPlacement])
 * no-op until the drag ends, then run exactly once. Clamping reads the
 * size captured at drag start, never the live view: while recording, the
 * bar republishes at ~30 Hz and its width churns mid-drag, and clamping
 * against the live width is what shook the window between two x values.
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

    /**
     * Single-writer flag (ENC-154): true from [onDragStart] until
     * [onDragEnd]/[onDragCancel]. While true, the overlay's placement
     * paths must not touch the window's layout.
     */
    var isDragging: Boolean = false
        private set

    /**
     * The window size the active drag clamps against, captured at drag
     * start (ENC-154). The bar's width churns at ~30 Hz mid-drag; reading
     * the live view here moved the clamp target under the finger.
     */
    private var dragWidthPx: Int = 0
    private var dragHeightPx: Int = 0

    /**
     * The screen bounds the active drag clamps against, captured at drag
     * start alongside the size pin (ENC-162): no per-event
     * `currentWindowMetrics` calls while dragging.
     */
    private var dragBounds: ScreenBounds? = null

    fun onTap() {
        onTapGesture()
    }

    /** The drag detector crossed its slop gate; anchor a session at the current window position. */
    fun onDragStart() {
        session = BubbleDragSession(
            originTopLeft = BubblePixelPosition(xPx = layoutParams.x, yPx = layoutParams.y),
            startTimeMs = SystemClock.uptimeMillis(),
        )
        dragWidthPx = bubbleView.width
        dragHeightPx = bubbleView.height
        dragBounds = currentBounds()
        isDragging = true
    }

    fun onDrag(deltaXPx: Float, deltaYPx: Float, timeMs: Long) {
        val activeSession = session ?: return
        val bounds = dragBounds ?: currentBounds()
        val moved = activeSession.dragBy(
            deltaXPx = deltaXPx,
            deltaYPx = deltaYPx,
            timeMs = timeMs,
            bubbleWidthPx = dragWidthPx,
            bubbleHeightPx = dragHeightPx,
            bounds = bounds,
        )
        layoutParams.x = moved.xPx
        layoutParams.y = moved.yPx
        windowManager.updateViewLayout(bubbleView, layoutParams)
        inDismissZone.value = BubblePositionMath.isInDismissZone(
            centerYPx = moved.yPx + dragHeightPx / CENTER_DIVISOR,
            bounds = bounds,
        )
    }

    fun onDragEnd() {
        isDragging = false
        val activeSession = session ?: return
        session = null
        val gesture = activeSession.finish(
            touchSlopPx = touchSlopPx,
            longPressTimeoutMs = longPressTimeoutMs,
            endInDismissZone = inDismissZone.value,
        )
        val endPosition = currentNormalizedPosition()
        dragBounds = null
        inDismissZone.value = false
        // The session guarantees a drag verdict (see BubbleDragSession.finish):
        // a dismiss ends the field session, any other drag persists its position.
        if (gesture == BubbleGesture.DragDismiss) {
            onDismissRequested()
        } else {
            onDragEnded(endPosition)
        }
    }

    fun onDragCancel() {
        isDragging = false
        session = null
        dragBounds = null
        inDismissZone.value = false
    }

    private fun currentNormalizedPosition(): BubblePosition =
        BubblePositionMath.toNormalized(
            topLeft = BubblePixelPosition(xPx = layoutParams.x, yPx = layoutParams.y),
            bubbleWidthPx = dragWidthPx,
            bubbleHeightPx = dragHeightPx,
            bounds = dragBounds ?: currentBounds(),
        )

    private fun currentBounds(): ScreenBounds {
        val rect = windowManager.currentWindowMetrics.bounds
        return ScreenBounds(widthPx = rect.width(), heightPx = rect.height())
    }

    private companion object {
        const val CENTER_DIVISOR: Int = 2
    }
}
