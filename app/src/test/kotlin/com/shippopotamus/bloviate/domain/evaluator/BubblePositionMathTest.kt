package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubblePositionMathTest {

    @Test
    fun `clamp keeps an on-screen position unchanged`() {
        val clamped = BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(xPx = 400, yPx = 900),
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = PORTRAIT,
        )

        assertEquals(BubblePixelPosition(xPx = 400, yPx = 900), clamped)
    }

    @Test
    fun `clamp pulls negative and overflowing offsets back on screen`() {
        val clamped = BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(xPx = -50, yPx = 9999),
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = PORTRAIT,
        )

        assertEquals(
            BubblePixelPosition(
                xPx = 0,
                yPx = PORTRAIT.heightPx - BUBBLE_PX,
            ),
            clamped,
        )
    }

    @Test
    fun `clamp pins to origin when the bubble is larger than the screen`() {
        val clamped = BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(xPx = 300, yPx = 300),
            bubbleWidthPx = 2000,
            bubbleHeightPx = 3000,
            bounds = PORTRAIT,
        )

        assertEquals(BubblePixelPosition(xPx = 0, yPx = 0), clamped)
    }

    @Test
    fun `normalized round trip is stable in portrait, landscape, folded, and unfolded bounds`() {
        val boundsCases = listOf(PORTRAIT, LANDSCAPE, FOLDED, UNFOLDED)
        val positions = listOf(
            BubblePosition(xFraction = 0f, yFraction = 0f),
            BubblePosition(xFraction = 0.5f, yFraction = 0.5f),
            BubblePosition(xFraction = 1f, yFraction = 1f),
            BubblePosition(xFraction = 0.86f, yFraction = 0.68f),
        )

        for (bounds in boundsCases) {
            for (position in positions) {
                val pixels = BubblePositionMath.toPixels(position, BUBBLE_PX, BUBBLE_PX, bounds)
                val roundTripped = BubblePositionMath.toNormalized(pixels, BUBBLE_PX, BUBBLE_PX, bounds)
                assertEquals(position.xFraction, roundTripped.xFraction, FRACTION_TOLERANCE, "x in $bounds")
                assertEquals(position.yFraction, roundTripped.yFraction, FRACTION_TOLERANCE, "y in $bounds")
            }
        }
    }

    @Test
    fun `a right-edge portrait position lands fully on screen after rotating to landscape`() {
        val pixels = BubblePositionMath.toPixels(
            position = BubblePosition(xFraction = 1f, yFraction = 0.5f),
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = LANDSCAPE,
        )

        assertEquals(LANDSCAPE.widthPx - BUBBLE_PX, pixels.xPx)
        assertTrue(pixels.xPx + BUBBLE_PX <= LANDSCAPE.widthPx)
        assertTrue(pixels.yPx + BUBBLE_PX <= LANDSCAPE.heightPx)
    }

    @Test
    fun `toPixels coerces out-of-range fractions into bounds`() {
        val pixels = BubblePositionMath.toPixels(
            position = BubblePosition(xFraction = 1.5f, yFraction = -0.25f),
            bubbleWidthPx = BUBBLE_PX,
            bubbleHeightPx = BUBBLE_PX,
            bounds = PORTRAIT,
        )

        assertEquals(
            BubblePixelPosition(xPx = PORTRAIT.widthPx - BUBBLE_PX, yPx = 0),
            pixels,
        )
    }

    @Test
    fun `dismiss zone is the bottom twelve percent of the screen`() {
        // 2400 * (1 - 0.12) = 2112: the center must reach it to count as inside.
        assertFalse(BubblePositionMath.isInDismissZone(centerYPx = 2111, bounds = PORTRAIT))
        assertTrue(BubblePositionMath.isInDismissZone(centerYPx = 2112, bounds = PORTRAIT))
        assertTrue(BubblePositionMath.isInDismissZone(centerYPx = 2400, bounds = PORTRAIT))
        assertFalse(BubblePositionMath.isInDismissZone(centerYPx = 1200, bounds = PORTRAIT))
    }

    private companion object {
        const val BUBBLE_PX: Int = 168
        const val FRACTION_TOLERANCE: Float = 0.001f

        /** Pixel-class phone portrait. */
        val PORTRAIT = ScreenBounds(widthPx = 1080, heightPx = 2400)

        /** Same device rotated. */
        val LANDSCAPE = ScreenBounds(widthPx = 2400, heightPx = 1080)

        /** Razr-fold-class cover display. */
        val FOLDED = ScreenBounds(widthPx = 1080, heightPx = 2640)

        /** Razr-fold-class unfolded inner display. */
        val UNFOLDED = ScreenBounds(widthPx = 1812, heightPx = 2176)
    }
}
