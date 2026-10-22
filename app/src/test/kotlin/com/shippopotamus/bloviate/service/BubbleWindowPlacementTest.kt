package com.shippopotamus.bloviate.service

import android.content.Context
import android.view.View
import android.view.WindowManager
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
 * ENC-154: the single-writer rule, pinned at the placement seam. While a
 * drag is active the drag controller owns the window's layout: width
 * churn must not move the window, and the drag end runs exactly one
 * placement pass. Resting and bar-form drag routing (ENC-123) is
 * preserved verbatim through the extraction.
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
        )
        placement.attach(view, params)
        sizeView(BUBBLE_PX, BUBBLE_PX)
    }

    @Test
    fun `width churn while dragging performs no placement writes`() {
        enterBarFormFromDock()
        assertEquals(BAR_REST_X, params.x)
        pushes.clear()

        dragging = true
        sizeView(BAR_WIDE_PX, BAR_HEIGHT_PX)
        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        placement.onWindowResized(widthChanged = true, heightChanged = false, bounds = BOUNDS)
        placement.onConfigurationChanged(bounds = BOUNDS)

        assertTrue(pushes.isEmpty(), "no placement write may land mid-drag, got $pushes")
    }

    @Test
    fun `drag end runs exactly one placement pass for a dragged bar`() {
        enterBarFormFromDock()
        assertEquals(BAR_REST_X, params.x)
        pushes.clear()

        // The drag controller moved the window; the end pass re-clamps
        // the dragged bar position exactly once and persists nothing.
        dragging = true
        params.x = DRAGGED_BAR_X
        dragging = false
        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)

        assertEquals(1, pushes.size)
        assertEquals(BAR_REST_X, params.x)
        assertTrue(savedPositions.isEmpty())
    }

    @Test
    fun `resting drag end persists the anchor with no corrective write`() {
        val anchor = BubblePosition(
            xFraction = REST_X.toFloat() / (BOUNDS.widthPx - BUBBLE_PX).toFloat(),
            yFraction = REST_Y.toFloat() / (BOUNDS.heightPx - BUBBLE_PX).toFloat(),
        )
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
    fun `an undragged record cycle returns the bubble to its dock`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)

        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)

        assertEquals(DOCK_X, params.x)
        assertEquals(DOCK_X to DOCK_Y, pushes.last())
    }

    @Test
    fun `a dragged bar lands the bubble on the dragged edge at close`() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)
        pushes.clear()

        // The bar was dragged left; closing persists the bar's final edge
        // as the bubble's new anchor, computed with the bubble's size.
        params.x = DRAGGED_REST_X
        placement.onDragEnded(position = BubblePosition(xFraction = 0f, yFraction = 0f), bounds = BOUNDS)
        placement.onBarStateChanged(RecordingBarState.Idle, bounds = BOUNDS)

        val expectedXFraction = DRAGGED_REST_X.toFloat() / (BOUNDS.widthPx - BUBBLE_PX).toFloat()
        assertEquals(1, savedPositions.size)
        assertEquals(expectedXFraction, savedPositions.single().xFraction, 0.001f)
        assertEquals(DRAGGED_REST_X, params.x)
    }

    /** Parks the bubble at the right dock and enters bar form with the bubble-sized view. */
    private fun enterBarFormFromDock() {
        dockRight()
        placement.onBarStateChanged(RecordingBarState.Recording(elapsedMs = 0L), bounds = BOUNDS)
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        placement.onWindowResized(widthChanged = true, heightChanged = true, bounds = BOUNDS)
    }

    private fun dockRight() {
        placement.restingPosition = BubblePosition(xFraction = 1f, yFraction = 0.5f)
        params.x = DOCK_X
        params.y = DOCK_Y
        sizeView(BUBBLE_PX, BUBBLE_PX)
    }

    private fun sizeView(widthPx: Int, heightPx: Int) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, widthPx, heightPx)
    }

    private companion object {
        const val BUBBLE_PX: Int = 168
        const val BAR_PX: Int = 640
        const val BAR_WIDE_PX: Int = 700
        const val BAR_HEIGHT_PX: Int = 128
        const val MARGIN_PX: Int = 24

        /** Right dock for the 168px bubble in 1080x2400 bounds. */
        const val DOCK_X: Int = 912
        const val DOCK_Y: Int = 1116

        /** A dragged bar x that still overflows at bar width: re-clamps to [BAR_REST_X]. */
        const val DRAGGED_BAR_X: Int = 500
        const val BAR_REST_X: Int = 416

        /** A mid-screen resting drag end, consistent with its anchor. */
        const val REST_X: Int = 319
        const val REST_Y: Int = 893

        /** A dragged resting x inside the bubble's own travel range. */
        const val DRAGGED_REST_X: Int = 200

        val BOUNDS = ScreenBounds(widthPx = 1080, heightPx = 2400)
    }
}
