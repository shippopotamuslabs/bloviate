package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleDragSessionTest {

    @Test
    fun `dragBy accumulates deltas into the window position`() {
        val session = newSession(originX = 500, originY = 800)

        val first = session.dragBy(deltaXPx = 30f, deltaYPx = 40f, timeMs = 100)
        val second = session.dragBy(deltaXPx = 10f, deltaYPx = -15f, timeMs = 200)

        assertEquals(BubblePixelPosition(xPx = 530, yPx = 840), first)
        assertEquals(BubblePixelPosition(xPx = 540, yPx = 825), second)
        assertEquals(second, session.currentTopLeft())
    }

    @Test
    fun `dragBy clamps the window fully on screen`() {
        val session = newSession(originX = 900, originY = 2200)

        val moved = session.dragBy(deltaXPx = 500f, deltaYPx = 500f, timeMs = 100)

        assertEquals(BubblePixelPosition(xPx = MAX_X_PX, yPx = MAX_Y_PX), moved)
    }

    @Test
    fun `a drag that wanders out and returns still finishes as a drag`() {
        val session = newSession(originX = 100, originY = 100)
        session.dragBy(deltaXPx = 300f, deltaYPx = 0f, timeMs = 100)
        session.dragBy(deltaXPx = -290f, deltaYPx = 5f, timeMs = 200)

        val gesture = session.finish(endInDismissZone = false)

        assertEquals(BubbleGesture.Drag, gesture)
    }

    @Test
    fun `drag ending in the dismiss zone finishes as drag dismiss`() {
        val session = newSession(originX = 400, originY = 400)
        session.dragBy(deltaXPx = 0f, deltaYPx = 1200f, timeMs = 150)

        val gesture = session.finish(endInDismissZone = true)

        assertEquals(BubbleGesture.DragDismiss, gesture)
    }

    @Test
    fun `drag that wanders out and returns still dismisses when released in the zone`() {
        // Classification keys on max excursion, not the release point: the
        // gesture was a real drag even though the net displacement is tiny.
        val session = newSession(originX = 400, originY = 400)
        session.dragBy(deltaXPx = 0f, deltaYPx = 1500f, timeMs = 150)
        session.dragBy(deltaXPx = 0f, deltaYPx = -1490f, timeMs = 300)

        val gesture = session.finish(endInDismissZone = true)

        assertEquals(BubbleGesture.DragDismiss, gesture)
    }

    @Test
    fun `sub-slop movement ending in the dismiss zone does not dismiss`() {
        // The detector's slop gate fired but the delivered deltas under-measure
        // the gesture; like a tap ending in the zone, this must not dismiss.
        val session = newSession(originX = 400, originY = 2100)
        session.dragBy(deltaXPx = 3f, deltaYPx = 2f, timeMs = 80)

        val gesture = session.finish(endInDismissZone = true)

        assertEquals(BubbleGesture.Drag, gesture)
    }

    @Test
    fun `long hold with sub-slop movement ending in the dismiss zone does not dismiss`() {
        val session = newSession(originX = 400, originY = 2100)
        session.dragBy(deltaXPx = 2f, deltaYPx = 1f, timeMs = LONG_PRESS_MS + 400)

        val gesture = session.finish(endInDismissZone = true)

        assertEquals(BubbleGesture.Drag, gesture)
    }

    @Test
    fun `finish without any movement is a drag that persists the origin`() {
        val session = newSession(originX = 300, originY = 300)

        val gesture = session.finish(endInDismissZone = false)

        assertEquals(BubbleGesture.Drag, gesture)
        assertEquals(BubblePixelPosition(xPx = 300, yPx = 300), session.currentTopLeft())
    }

    private fun newSession(originX: Int, originY: Int): BubbleDragSession =
        BubbleDragSession(
            originTopLeft = BubblePixelPosition(xPx = originX, yPx = originY),
            startTimeMs = 0,
        )

    private fun BubbleDragSession.dragBy(deltaXPx: Float, deltaYPx: Float, timeMs: Long): BubblePixelPosition =
        dragBy(
            deltaXPx = deltaXPx,
            deltaYPx = deltaYPx,
            timeMs = timeMs,
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = BOUNDS,
        )

    private fun BubbleDragSession.currentTopLeft(): BubblePixelPosition =
        currentTopLeft(
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = BOUNDS,
        )

    private fun BubbleDragSession.finish(endInDismissZone: Boolean): BubbleGesture =
        finish(
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = endInDismissZone,
        )

    private companion object {
        val BOUNDS: ScreenBounds = ScreenBounds(widthPx = 1080, heightPx = 2400)
        const val BUBBLE_PX: Int = 168
        const val MAX_X_PX: Int = 1080 - BUBBLE_PX
        const val MAX_Y_PX: Int = 2400 - BUBBLE_PX
        const val SLOP_PX: Float = 24f
        const val LONG_PRESS_MS: Long = 500L
    }
}
