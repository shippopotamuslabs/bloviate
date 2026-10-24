package com.shippopotamus.bloviate.service

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-167: bar-form placement is capture-anchor → one-shot place →
 * restore-on-exit. The window x is computed once per bar-form state
 * from the known content width (injected here), never from a measured
 * view width; a resize while a bar form is up moves nothing. Resting
 * drag routing (ENC-123/ENC-154) is preserved.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class BubbleWindowPlacementTest {

    private lateinit var view: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var placement: BubbleWindowPlacement
    private val pushes = mutableListOf<Pair<Int, Int>>()
    private val savedPositions = mutableListOf<BubblePosition>()
    private var dragging: Boolean = false
    private var contentWidths: Map<String, Int> = mapOf(
        "Recording" to BAR_PX,
        "Transcribing" to PROCESSING_PX,
        "Polishing" to PROCESSING_PX,
    )

    @BeforeEach
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        view = View(context)
        params = WindowManager.LayoutParams()
        params.x = 0
        params.y = 0
        placement = BubbleWindowPlacement(
            pushLayout = { _, pushed -> pushes += pushed.x to pushed.y },
            bubbleSizePx = { BUBBLE_PX to BUBBLE_PX },
            barEdgeMarginPx = { MARGIN_PX },
            savePosition = { position -> savedPositions += position },
            isDragging = { dragging },
            barContentWidthPx = { state ->
                contentWidths[state::class.simpleName] ?: 0
            },
        )
        placement.attach(view, params)
    }

    @Test
    fun `entering recording from the right dock places the row in one computation`() {
        dockRight()

        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)

        assertEquals(1, pushes.size)
        assertEquals(BAR_REST_X, params.x)
        assertEquals(DOCK_Y, params.y)
    }

    @Test
    fun `entering recording from the left dock unfolds rightward from the anchor`() {
        placement.restingPosition = BubblePosition(xFraction = 0f, yFraction = 0.5f)
        params.x = 0
        params.y = DOCK_Y

        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)

        // Already at the computed x: one-shot placement needs no push.
        assertTrue(pushes.isEmpty())
        assertEquals(0, params.x)
    }

    @Test
    fun `a resize callback during recording does not move the window`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        assertEquals(BAR_REST_X, params.x)
        pushes.clear()

        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)
        placement.onWindowResized(widthChanged = true, heightChanged = false, bounds = BOUNDS)
        placement.onWindowResized(widthChanged = false, heightChanged = true, bounds = BOUNDS)

        assertTrue(pushes.isEmpty(), "no placement write may land during recording, got $pushes")
        assertEquals(BAR_REST_X, params.x)
    }

    @Test
    fun `recording to processing re-places once keeping the anchor edge invariant`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        pushes.clear()

        placement.onBarStateChanged(RecordingBarState.Transcribing, bounds = BOUNDS)

        // Right side: ideal right edge is the anchor's right edge (1080),
        // fit-shifted to the margin limit (1056) for the 300px pill.
        assertEquals(1, pushes.size)
        assertEquals(BOUNDS.widthPx - MARGIN_PX - PROCESSING_PX, params.x)
        assertEquals(DOCK_Y, params.y)
    }

    @Test
    fun `processing to done returns the single bubble to the anchor spot`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        placement.onBarStateChanged(RecordingBarState.Transcribing, bounds = BOUNDS)
        pushes.clear()

        placement.onBarStateChanged(RecordingBarState.Done(), bounds = BOUNDS)

        assertEquals(1, pushes.size)
        assertEquals(DOCK_X, params.x)
        assertEquals(DOCK_Y, params.y)

        pushes.clear()
        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)
        assertTrue(pushes.isEmpty(), "Done is already at the anchor; Idle must not move it")
        assertEquals(DOCK_X, params.x)
    }

    @Test
    fun `an undragged record cycle returns the bubble to its dock on both sides`() {
        // Right dock.
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)
        assertEquals(DOCK_X, params.x)
        assertEquals(DOCK_X to DOCK_Y, pushes.last())

        // Left dock.
        pushes.clear()
        placement.restingPosition = BubblePosition(xFraction = 0f, yFraction = 0.5f)
        params.x = 0
        params.y = DOCK_Y
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)
        assertEquals(0, params.x)
        assertEquals(0 to DOCK_Y, pushes.last())
    }

    @Test
    fun `a bar-form drag end persists the shifted anchor and pushes no layout`() {
        // BLO-152: the recording row drags again. The drag controller
        // already moved the window (left 100, down 100 from the placed
        // spot); the anchor follows by that same displacement.
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        assertEquals(BAR_REST_X, params.x)
        pushes.clear()
        params.x = BAR_REST_X - DRAG_DX_PX
        params.y = DOCK_Y + DRAG_DY_PX

        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)

        val expectedAnchor = BubblePixelPosition(xPx = DOCK_X - DRAG_DX_PX, yPx = DOCK_Y + DRAG_DY_PX)
        val expected = BubblePositionMath.toNormalized(
            topLeft = expectedAnchor,
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = BOUNDS,
        )
        assertEquals(listOf(expected), savedPositions)
        assertEquals(expected, placement.restingPosition)
        assertTrue(pushes.isEmpty(), "a bar-form drag end must not push layout, got $pushes")
        assertEquals(BAR_REST_X - DRAG_DX_PX, params.x)
        assertEquals(DOCK_Y + DRAG_DY_PX, params.y)
    }

    @Test
    fun `exiting bar form after a drag restores the dragged spot`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        pushes.clear()
        params.x = BAR_REST_X - DRAG_DX_PX
        params.y = DOCK_Y + DRAG_DY_PX
        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)
        pushes.clear()

        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)

        assertEquals(DOCK_X - DRAG_DX_PX, params.x)
        assertEquals(DOCK_Y + DRAG_DY_PX, params.y)
        assertEquals(DOCK_X - DRAG_DX_PX to DOCK_Y + DRAG_DY_PX, pushes.last())
    }

    @Test
    fun `a left-side bar-form drag persists the shifted anchor and restores it on exit`() {
        placement.restingPosition = BubblePosition(xFraction = 0f, yFraction = 0.5f)
        params.x = 0
        params.y = DOCK_Y
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        assertTrue(pushes.isEmpty(), "left dock unfolds rightward from the anchor with no push")
        params.x = DRAG_DX_PX
        params.y = DOCK_Y - DRAG_DY_PX

        placement.onDragEnded(position = BubblePosition(xFraction = 1f, yFraction = 1f), bounds = BOUNDS)

        val expectedAnchor = BubblePixelPosition(xPx = DRAG_DX_PX, yPx = DOCK_Y - DRAG_DY_PX)
        val expected = BubblePositionMath.toNormalized(
            topLeft = expectedAnchor,
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = BOUNDS,
        )
        assertEquals(listOf(expected), savedPositions)
        assertEquals(expected, placement.restingPosition)

        pushes.clear()
        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)

        assertEquals(DRAG_DX_PX, params.x)
        assertEquals(DOCK_Y - DRAG_DY_PX, params.y)
    }

    @Test
    fun `a bar-form drag end with zero displacement persists nothing`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        pushes.clear()

        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)

        assertTrue(savedPositions.isEmpty())
        assertTrue(pushes.isEmpty())
        assertEquals(BAR_REST_X, params.x)
    }

    @Test
    fun `recording to processing after a drag re-places from the dragged anchor without yanking`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        params.x = BAR_REST_X - DRAG_DX_PX
        params.y = DOCK_Y + DRAG_DY_PX
        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)
        pushes.clear()

        placement.onBarStateChanged(RecordingBarState.Transcribing, bounds = BOUNDS)

        // From the dragged anchor (812, 1216): expand-left ideal is
        // 812 + 168 - 300 = 680, which fits, so the row lands at 680 —
        // not back at the undisplaced 756.
        assertEquals(1, pushes.size)
        assertEquals(DRAGGED_PROCESSING_X, params.x)
        assertEquals(DOCK_Y + DRAG_DY_PX, params.y)
    }

    @Test
    fun `resting drag end persists the anchor with no corrective write`() {
        val anchor = BubblePosition(
            xFraction = REST_X.toFloat() / (BOUNDS.widthPx - BUBBLE_PX).toFloat(),
            yFraction = REST_Y.toFloat() / (BOUNDS.heightPx - BUBBLE_PX).toFloat(),
        )
        placement.restingPosition = anchor
        params.x = REST_X
        params.y = REST_Y

        placement.onDragEnded(position = anchor, bounds = BOUNDS)

        assertEquals(listOf(anchor), savedPositions)
        assertEquals(anchor, placement.restingPosition)
        assertTrue(pushes.isEmpty(), "the dragged position already satisfies the anchor, got $pushes")
        assertEquals(REST_X, params.x)
        assertEquals(REST_Y, params.y)
    }

    @Test
    fun `width churn while dragging performs no placement writes`() {
        params.x = REST_X
        params.y = REST_Y
        dragging = true

        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)
        placement.onConfigurationChanged(bounds = BOUNDS)

        assertTrue(pushes.isEmpty(), "no placement write may land mid-drag, got $pushes")
    }

    private fun dockRight() {
        placement.restingPosition = BubblePosition(xFraction = 1f, yFraction = 0.5f)
        params.x = DOCK_X
        params.y = DOCK_Y
    }

    private companion object {
        const val BUBBLE_PX: Int = 168
        const val BAR_PX: Int = 640
        const val PROCESSING_PX: Int = 300
        const val MARGIN_PX: Int = 24
        const val DOCK_X: Int = 912
        const val DOCK_Y: Int = 1116
        const val BAR_REST_X: Int = 416
        const val REST_X: Int = 319
        const val REST_Y: Int = 893
        const val DRAG_DX_PX: Int = 100
        const val DRAG_DY_PX: Int = 100
        const val DRAGGED_PROCESSING_X: Int = 680
        val BOUNDS = ScreenBounds(widthPx = 1080, heightPx = 2400)
    }
}
