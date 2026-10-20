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

    @Test
    fun `bar window x grows rightward from a left parked bubble`() {
        val barX = BubblePositionMath.barWindowX(
            bubbleXPx = 24,
            barWidthPx = BAR_PX,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )

        assertEquals(24, barX)
        assertTrue(barX + BAR_PX <= PORTRAIT.widthPx - MARGIN_PX)
    }

    @Test
    fun `bar window x grows leftward from a right parked bubble`() {
        // Bubble parked against the right edge, as Sean keeps it.
        val bubbleX = PORTRAIT.widthPx - BUBBLE_PX
        val barX = BubblePositionMath.barWindowX(
            bubbleXPx = bubbleX,
            barWidthPx = BAR_PX,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )

        assertEquals(PORTRAIT.widthPx - MARGIN_PX - BAR_PX, barX)
        assertTrue(barX < bubbleX, "the bar must expand toward the interior")
        assertTrue(barX >= 0 && barX + BAR_PX <= PORTRAIT.widthPx)
    }

    @Test
    fun `bar window x in the center grows rightward while the bar fits and leftward once it would overflow`() {
        val centeredX = 200
        val fitted = BubblePositionMath.barWindowX(
            bubbleXPx = centeredX,
            barWidthPx = BAR_PX,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )
        assertEquals(centeredX, fitted)

        val nearRightX = PORTRAIT.widthPx - MARGIN_PX - BAR_PX + 1
        val pulledBack = BubblePositionMath.barWindowX(
            bubbleXPx = nearRightX,
            barWidthPx = BAR_PX,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )
        assertEquals(PORTRAIT.widthPx - MARGIN_PX - BAR_PX, pulledBack)
    }

    @Test
    fun `bar window x keeps the full bar on screen on the unfolded fold display`() {
        val bubbleX = UNFOLDED.widthPx - BUBBLE_PX
        val barX = BubblePositionMath.barWindowX(
            bubbleXPx = bubbleX,
            barWidthPx = BAR_PX,
            bounds = UNFOLDED,
            edgeMarginPx = MARGIN_PX,
        )

        assertEquals(UNFOLDED.widthPx - MARGIN_PX - BAR_PX, barX)
        assertTrue(barX + BAR_PX <= UNFOLDED.widthPx - MARGIN_PX)
    }

    @Test
    fun `bar window x pins to the left edge when the bar is wider than the screen allows`() {
        val barX = BubblePositionMath.barWindowX(
            bubbleXPx = 500,
            barWidthPx = PORTRAIT.widthPx,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )

        assertEquals(0, barX)
    }

    // ENC-123: the docked edge is invariant across bubble <-> bar morphs.
    // The resting position is a function of the persisted anchor and the
    // bubble's own size ONLY — a collapse that re-derives it lands exactly
    // where the bubble rested before the expansion, whatever the bar's
    // width was and whatever widths the morph passed through.

    @Test
    fun `expand then collapse returns the identical resting position for a right docked bubble`() {
        val anchor = BubblePosition(xFraction = 1f, yFraction = 0.68f)
        val resting = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
        assertEquals(PORTRAIT.widthPx - BUBBLE_PX, resting.xPx)

        for (barWidthPx in listOf(320, 480, BAR_PX, 900)) {
            val barX = BubblePositionMath.barWindowX(
                bubbleXPx = resting.xPx,
                barWidthPx = barWidthPx,
                bounds = PORTRAIT,
                edgeMarginPx = MARGIN_PX,
            )
            assertTrue(barX + barWidthPx <= PORTRAIT.widthPx, "bar of $barWidthPx stays on screen")
            val collapsed = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
            assertEquals(resting, collapsed, "collapse after a $barWidthPx bar restores the resting position")
        }
    }

    @Test
    fun `expand then collapse returns the identical resting position for a left docked bubble`() {
        val anchor = BubblePosition(xFraction = 0f, yFraction = 0.68f)
        val resting = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
        assertEquals(0, resting.xPx)

        val barX = BubblePositionMath.barWindowX(
            bubbleXPx = resting.xPx,
            barWidthPx = BAR_PX,
            bounds = PORTRAIT,
            edgeMarginPx = MARGIN_PX,
        )
        assertEquals(resting.xPx, barX, "a left docked bubble's bar grows rightward from the resting x")
        val collapsed = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
        assertEquals(resting, collapsed)
    }

    @Test
    fun `clamping a docked position against a wider form's width displaces it - the trap restingTopLeft avoids`() {
        // Characterization of the ENC-123 bug: pixel-clamping the resting x
        // against a transient width (the bar's, mid-collapse) pulls a right
        // docked bubble left by the width difference, and clamping never
        // moves it back. Resting positions must be re-derived from the
        // anchor (restingTopLeft), never pixel-clamped against a form width.
        val anchor = BubblePosition(xFraction = 1f, yFraction = 0.68f)
        val resting = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)

        val displaced = BubblePositionMath.clamp(resting, BAR_PX, BUBBLE_PX, PORTRAIT)

        assertEquals(PORTRAIT.widthPx - BAR_PX, displaced.xPx)
        assertTrue(displaced.xPx < resting.xPx, "the clamp displaced the bubble toward the interior")
    }

    @Test
    fun `a size change while docked preserves the docked edge`() {
        val rightDock = BubblePosition(xFraction = 1f, yFraction = 0.5f)
        val leftDock = BubblePosition(xFraction = 0f, yFraction = 0.5f)

        for (sizePx in listOf(BUBBLE_SMALL_PX, BUBBLE_PX, BUBBLE_LARGE_PX)) {
            val right = BubblePositionMath.restingTopLeft(rightDock, sizePx, sizePx, PORTRAIT)
            assertEquals(PORTRAIT.widthPx - sizePx, right.xPx, "right edge at size $sizePx")
            assertEquals(0, BubblePositionMath.restingTopLeft(leftDock, sizePx, sizePx, PORTRAIT).xPx)
        }
    }

    @Test
    fun `a size change moves a mid-screen position only as the anchor model prescribes`() {
        val anchor = BubblePosition(xFraction = 0.5f, yFraction = 0.25f)

        for (sizePx in listOf(BUBBLE_SMALL_PX, BUBBLE_PX, BUBBLE_LARGE_PX)) {
            val resting = BubblePositionMath.restingTopLeft(anchor, sizePx, sizePx, PORTRAIT)
            val roundTripped = BubblePositionMath.toNormalized(resting, sizePx, sizePx, PORTRAIT)
            assertEquals(anchor.xFraction, roundTripped.xFraction, FRACTION_TOLERANCE, "x at size $sizePx")
            assertEquals(anchor.yFraction, roundTripped.yFraction, FRACTION_TOLERANCE, "y at size $sizePx")
        }

        // The pixel position scales with the travel range — it never jumps
        // to an edge or keeps a stale pixel from the previous size.
        val small = BubblePositionMath.restingTopLeft(anchor, BUBBLE_SMALL_PX, BUBBLE_SMALL_PX, PORTRAIT)
        val large = BubblePositionMath.restingTopLeft(anchor, BUBBLE_LARGE_PX, BUBBLE_LARGE_PX, PORTRAIT)
        assertEquals((PORTRAIT.widthPx - BUBBLE_SMALL_PX) / 2, small.xPx)
        assertEquals((PORTRAIT.widthPx - BUBBLE_LARGE_PX) / 2, large.xPx)
    }

    private companion object {
        const val BUBBLE_PX: Int = 168
        const val BUBBLE_SMALL_PX: Int = 144
        const val BUBBLE_LARGE_PX: Int = 192
        const val BAR_PX: Int = 640
        const val MARGIN_PX: Int = 24
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
