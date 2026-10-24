package com.shippopotamus.bloviate.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * BLO-152: one-stream arbitration for the recording row. A release
 * within touch slop is a tap hit-tested against the X slot (left) and
 * the check slot (right) — the center is a no-op — while a release
 * after slop-crossing is a drag end that never fires the slots, and a
 * cancelled stream only reports a drag cancel when it was dragging.
 */
class BarGestureArbitrationTest {

    private var cancels: Int = 0
    private var sends: Int = 0
    private var dragEnds: Int = 0
    private var dragCancels: Int = 0

    @BeforeEach
    fun setup() {
        cancels = 0
        sends = 0
        dragEnds = 0
        dragCancels = 0
    }

    @Test
    fun `a tap in the left slot cancels`() {
        finishBarGesture(
            wasCancelled = false,
            wasDragging = false,
            downX = SLOT_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(1, cancels)
        assertEquals(0, sends)
        assertEquals(0, dragEnds)
        assertEquals(0, dragCancels)
    }

    @Test
    fun `a tap in the right slot sends`() {
        finishBarGesture(
            wasCancelled = false,
            wasDragging = false,
            downX = ROW_WIDTH_PX - SLOT_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(0, cancels)
        assertEquals(1, sends)
        assertEquals(0, dragEnds)
        assertEquals(0, dragCancels)
    }

    @Test
    fun `a tap in the center does nothing`() {
        finishBarGesture(
            wasCancelled = false,
            wasDragging = false,
            downX = ROW_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(0, cancels)
        assertEquals(0, sends)
        assertEquals(0, dragEnds)
        assertEquals(0, dragCancels)
    }

    @Test
    fun `a drag release ends the drag even when it started in a slot`() {
        finishBarGesture(
            wasCancelled = false,
            wasDragging = true,
            downX = SLOT_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(0, cancels)
        assertEquals(0, sends)
        assertEquals(1, dragEnds)
        assertEquals(0, dragCancels)
    }

    @Test
    fun `a cancelled drag reports drag cancel only`() {
        finishBarGesture(
            wasCancelled = true,
            wasDragging = true,
            downX = SLOT_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(0, cancels)
        assertEquals(0, sends)
        assertEquals(0, dragEnds)
        assertEquals(1, dragCancels)
    }

    @Test
    fun `a cancelled tap reports nothing`() {
        finishBarGesture(
            wasCancelled = true,
            wasDragging = false,
            downX = SLOT_WIDTH_PX / 2f,
            widthPx = ROW_WIDTH_PX,
            slotWidthPx = SLOT_WIDTH_PX,
            onCancelTap = { cancels++ },
            onSendTap = { sends++ },
            onDragEnd = { dragEnds++ },
            onDragCancel = { dragCancels++ },
        )

        assertEquals(0, cancels)
        assertEquals(0, sends)
        assertEquals(0, dragEnds)
        assertEquals(0, dragCancels)
    }

    private companion object {
        const val ROW_WIDTH_PX: Float = 640f
        const val SLOT_WIDTH_PX: Float = 48f
    }
}
