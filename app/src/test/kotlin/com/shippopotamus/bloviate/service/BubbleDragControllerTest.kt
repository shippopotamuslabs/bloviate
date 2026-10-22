package com.shippopotamus.bloviate.service

import android.content.Context
import android.graphics.Rect
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.runtime.mutableStateOf
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-154: the drag owns the window exclusively mid-drag. The bar's
 * width churns at ~30 Hz while recording (waveform + elapsed republish),
 * and the old code clamped every move against the live view width — a
 * smooth finger shook the window between two x values. These pins drive
 * the real controller: clamping must read the drag-start size even when
 * the view widens mid-drag, every window write must flow through
 * [BubbleDragController.onDrag], and the end anchor must normalize with
 * the start size.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class BubbleDragControllerTest {

    private lateinit var context: Context
    private lateinit var view: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var fakeWindowManager: FakeOverlayWindowManager
    private lateinit var controller: BubbleDragController
    private val endedPositions = mutableListOf<com.shippopotamus.bloviate.domain.model.BubblePosition>()
    private var dismissRequests: Int = 0

    @BeforeEach
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        view = View(context)
        layoutParams = WindowManager.LayoutParams()
        layoutParams.x = START_X
        layoutParams.y = START_Y
        fakeWindowManager = FakeOverlayWindowManager()
        controller = BubbleDragController(
            windowManager = fakeWindowManager,
            bubbleView = view,
            layoutParams = layoutParams,
            onTapGesture = {},
            onDismissRequested = { dismissRequests++ },
            onDragEnded = { position -> endedPositions += position },
            inDismissZone = mutableStateOf(false),
        )
        sizeView(BUBBLE_PX, BUBBLE_PX)
    }

    @Test
    fun `a drag clamps against the drag-start width when the view widens mid-drag`() {
        controller.onDragStart()
        assertTrue(controller.isDragging)

        controller.onDrag(deltaXPx = 50f, deltaYPx = 0f, timeMs = 1_000L)
        assertEquals(START_X + 50, layoutParams.x)

        // The bar morphs mid-drag: the live view is suddenly bar-wide.
        // The clamp target must not move under the finger.
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        controller.onDrag(deltaXPx = 0f, deltaYPx = 0f, timeMs = 1_050L)

        assertEquals(START_X + 50, layoutParams.x)
        assertEquals(0, dismissRequests)
    }

    @Test
    fun `a right-edge drag stays docked when the view widens mid-drag`() {
        // Start parked at the right edge, then drag further right: the
        // clamp pins to the start size's travel range, not the bar's.
        layoutParams.x = BOUNDS.widthPx - BUBBLE_PX
        controller.onDragStart()

        controller.onDrag(deltaXPx = 200f, deltaYPx = 0f, timeMs = 1_000L)
        assertEquals(BOUNDS.widthPx - BUBBLE_PX, layoutParams.x)

        sizeView(BAR_PX, BAR_HEIGHT_PX)
        controller.onDrag(deltaXPx = 0f, deltaYPx = 0f, timeMs = 1_050L)

        assertEquals(BOUNDS.widthPx - BUBBLE_PX, layoutParams.x)
    }

    @Test
    fun `every window write during a drag flows through onDrag`() {
        controller.onDragStart()
        assertTrue(fakeWindowManager.updateCalls.isEmpty())

        controller.onDrag(deltaXPx = 10f, deltaYPx = 0f, timeMs = 1_000L)
        controller.onDrag(deltaXPx = 10f, deltaYPx = 5f, timeMs = 1_050L)
        controller.onDrag(deltaXPx = 10f, deltaYPx = 5f, timeMs = 1_100L)

        assertEquals(3, fakeWindowManager.updateCalls.size)

        sizeView(BAR_PX, BAR_HEIGHT_PX)
        controller.onDragEnd()

        // Drag start/end themselves write nothing; the end routes to the
        // placement callback, never to the window.
        assertEquals(3, fakeWindowManager.updateCalls.size)
        assertEquals(1, endedPositions.size)
    }

    @Test
    fun `the drag-end anchor normalizes with the drag-start size`() {
        controller.onDragStart()
        controller.onDrag(deltaXPx = 112f, deltaYPx = 50f, timeMs = 1_000L)
        sizeView(BAR_PX, BAR_HEIGHT_PX)
        controller.onDragEnd()

        assertFalse(controller.isDragging)
        assertEquals(1, endedPositions.size)
        val draggedX = (START_X + 112).coerceAtMost(BOUNDS.widthPx - BUBBLE_PX)
        val expectedX = draggedX.toFloat() / (BOUNDS.widthPx - BUBBLE_PX).toFloat()
        val expectedY = (START_Y + 50).toFloat() / (BOUNDS.heightPx - BUBBLE_PX).toFloat()
        assertEquals(expectedX, endedPositions.single().xFraction, 0.001f)
        assertEquals(expectedY, endedPositions.single().yFraction, 0.001f)
        assertEquals(draggedX, layoutParams.x)
    }

    @Test
    fun `isDragging tracks the session and a drag cancel clears it`() {
        assertFalse(controller.isDragging)

        controller.onDragStart()
        assertTrue(controller.isDragging)

        controller.onDragCancel()
        assertFalse(controller.isDragging)
        assertTrue(fakeWindowManager.updateCalls.isEmpty())
        assertTrue(endedPositions.isEmpty())
    }

    @Test
    fun `moves without a drag session write nothing`() {
        controller.onDrag(deltaXPx = 100f, deltaYPx = 100f, timeMs = 1_000L)

        assertEquals(START_X, layoutParams.x)
        assertEquals(START_Y, layoutParams.y)
        assertTrue(fakeWindowManager.updateCalls.isEmpty())
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
        const val BAR_HEIGHT_PX: Int = 128
        const val START_X: Int = 800
        const val START_Y: Int = 100
        val BOUNDS = ScreenBounds(widthPx = 1080, heightPx = 2400)
    }
}

/** Records every window write; the drag controller must be the only writer mid-drag. */
private class FakeOverlayWindowManager(
    var bounds: ScreenBounds = ScreenBounds(widthPx = 1080, heightPx = 2400),
) : WindowManager {

    val updateCalls = mutableListOf<WindowManager.LayoutParams>()

    override fun addView(view: View?, params: ViewGroup.LayoutParams?) = Unit

    override fun updateViewLayout(view: View?, params: ViewGroup.LayoutParams?) {
        updateCalls += params as WindowManager.LayoutParams
    }

    override fun removeView(view: View?) = Unit

    override fun removeViewImmediate(view: View?) = Unit

    override fun getDefaultDisplay(): Display = throw UnsupportedOperationException("not used")

    override fun getCurrentWindowMetrics(): WindowMetrics =
        WindowMetrics(
            Rect(0, 0, bounds.widthPx, bounds.heightPx),
            WindowInsets.CONSUMED,
        )
}
