package com.shippopotamus.bloviate.service

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.presentation.BloviateTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-167: the recording row's X and check are independent tap targets
 * and no drag gesture is registered in the recording layout. Driven
 * through real touch dispatch on a hosted [BubbleContent] (no looper
 * idle: the waveform frame clock would spin the shadow looper).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class RecordingBarLayoutTest {

    @Test
    fun `X and check are independent taps and a drag fires neither`() {
        var cancels = 0
        var sends = 0
        var drags = 0
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val view = ComposeView(activity)
            val owner = ServiceLifecycleOwner()
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            view.setContent {
                BloviateTheme {
                    BubbleContent(
                        barState = RecordingBarState.Recording(elapsedMs = 1_000L),
                        waveformLevels = { List(10) { 0.5f } },
                        inDismissZone = false,
                        onCancelRecording = { cancels++ },
                        onSendRecording = { sends++ },
                        onDragStart = { drags++ },
                        onDrag = { _, _, _ -> drags++ },
                    )
                }
            }
            activity.setContentView(view)
            owner.create()
            val widthSpec = View.MeasureSpec.makeMeasureSpec(2_000, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val width = view.measuredWidth
            val height = view.measuredHeight
            assertTrue(width > 0 && height > 0, "recording row must lay out, got ${width}x$height")
            val midY = height / 2f

            tap(view, SLOT_CENTER_PX, midY)
            assertEquals(1, cancels, "X tap must fire cancel")
            assertEquals(0, sends)

            tap(view, width - SLOT_CENTER_PX, midY)
            assertEquals(1, sends, "check tap must fire send")
            assertEquals(1, cancels)
            assertEquals(0, drags)

            // A drag across the row: neither tap nor any drag callback.
            val downTime = SystemClock.uptimeMillis()
            view.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, SLOT_CENTER_PX, midY, 0),
            )
            view.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, width / 2f, midY, 0),
            )
            view.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, width - SLOT_CENTER_PX, midY, 0),
            )
            assertEquals(1, cancels, "a drag must not become a cancel tap")
            assertEquals(1, sends, "a drag must not become a send tap")
            assertEquals(0, drags, "no drag gesture is registered in the recording layout")
            owner.destroy()
        } finally {
            controller.destroy()
        }
    }

    private fun tap(view: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0),
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, x, y, 0),
        )
    }

    private companion object {
        // Density is 1 in Robolectric config, slot is 48dp: center at 24.
        const val SLOT_CENTER_PX: Float = 24f
    }
}
