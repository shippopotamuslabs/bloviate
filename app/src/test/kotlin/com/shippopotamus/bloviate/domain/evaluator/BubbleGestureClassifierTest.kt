package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleGesture
import com.shippopotamus.bloviate.domain.model.PointerSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleGestureClassifierTest {

    private val classifier = BubbleGestureClassifier()

    @Test
    fun `short press with minimal movement is a tap`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 100f, y = 100f),
                sample(timeMs = 90, x = 102f, y = 101f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = false,
        )

        assertEquals(BubbleGesture.Tap, gesture)
    }

    @Test
    fun `press held past the timeout with minimal movement is a long press`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 100f, y = 100f),
                sample(timeMs = 300, x = 101f, y = 100f),
                sample(timeMs = 650, x = 100f, y = 102f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = false,
        )

        assertEquals(BubbleGesture.LongPress, gesture)
    }

    @Test
    fun `movement past the slop is a drag`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 100f, y = 100f),
                sample(timeMs = 120, x = 220f, y = 140f),
                sample(timeMs = 240, x = 340f, y = 180f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = false,
        )

        assertEquals(BubbleGesture.Drag, gesture)
    }

    @Test
    fun `drag ending in the dismiss zone is a dismiss`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 500f, y = 600f),
                sample(timeMs = 150, x = 520f, y = 1400f),
                sample(timeMs = 300, x = 540f, y = 2250f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = true,
        )

        assertEquals(BubbleGesture.DragDismiss, gesture)
    }

    @Test
    fun `tap ending in the dismiss zone is still a tap, not a dismiss`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 540f, y = 2250f),
                sample(timeMs = 80, x = 542f, y = 2251f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = true,
        )

        assertEquals(BubbleGesture.Tap, gesture)
    }

    @Test
    fun `long press ending in the dismiss zone is still a long press, not a dismiss`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 540f, y = 2250f),
                sample(timeMs = 700, x = 541f, y = 2250f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = true,
        )

        assertEquals(BubbleGesture.LongPress, gesture)
    }

    @Test
    fun `a drag that wanders out and returns is classified by max excursion`() {
        val gesture = classifier.classify(
            samples = listOf(
                sample(timeMs = 0, x = 100f, y = 100f),
                sample(timeMs = 100, x = 400f, y = 100f),
                sample(timeMs = 200, x = 105f, y = 102f),
            ),
            touchSlopPx = SLOP_PX,
            longPressTimeoutMs = LONG_PRESS_MS,
            endInDismissZone = false,
        )

        assertEquals(BubbleGesture.Drag, gesture)
    }

    @Test
    fun `empty and single-sample sequences are taps`() {
        assertEquals(
            BubbleGesture.Tap,
            classifier.classify(
                samples = emptyList(),
                touchSlopPx = SLOP_PX,
                longPressTimeoutMs = LONG_PRESS_MS,
                endInDismissZone = false,
            ),
        )
        assertEquals(
            BubbleGesture.Tap,
            classifier.classify(
                samples = listOf(sample(timeMs = 42, x = 10f, y = 10f)),
                touchSlopPx = SLOP_PX,
                longPressTimeoutMs = LONG_PRESS_MS,
                endInDismissZone = false,
            ),
        )
    }

    private fun sample(timeMs: Long, x: Float, y: Float): PointerSample =
        PointerSample(timeMs = timeMs, xPx = x, yPx = y)

    private companion object {
        const val SLOP_PX: Float = 24f
        const val LONG_PRESS_MS: Long = 500L
    }
}
