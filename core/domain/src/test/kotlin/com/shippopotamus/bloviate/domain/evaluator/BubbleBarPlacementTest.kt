package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BarExpansionSide
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ENC-167: the side function and one-shot placement that replace the
 * morphing pill's per-frame size animation (the deleted
 * BarSizeAnimationSpecTest's successor). The side is a pure function of
 * the anchor; the window x is computed once from a known content width
 * and keeps the anchor edge fixed.
 */
class BubbleBarPlacementTest {

    @Test
    fun `anchor centers swept across the width map to the specified side`() {
        for (bounds in listOf(PORTRAIT, LANDSCAPE, FOLDED, UNFOLDED)) {
            var anchorX = 0
            val maxX = bounds.widthPx - BUBBLE_PX
            while (anchorX <= maxX) {
                val side = BubblePositionMath.expansionSide(anchorX, BUBBLE_PX, bounds)
                val centerTimesTwo = anchorX * 2 + BUBBLE_PX
                val expected = if (centerTimesTwo < bounds.widthPx) {
                    BarExpansionSide.EXPAND_RIGHT
                } else {
                    BarExpansionSide.EXPAND_LEFT
                }
                assertEquals(expected, side, "anchorX=$anchorX in $bounds")
                anchorX += STEP_PX
            }
        }
    }

    @Test
    fun `an anchor centered exactly on the screen center expands left`() {
        // 456 + 168/2 = 540, exactly the 1080-wide screen's center: not
        // left of center, so the check sits at the anchor spot.
        assertEquals(
            BarExpansionSide.EXPAND_LEFT,
            BubblePositionMath.expansionSide(456, BUBBLE_PX, PORTRAIT),
        )
        assertEquals(
            BarExpansionSide.EXPAND_RIGHT,
            BubblePositionMath.expansionSide(455, BUBBLE_PX, PORTRAIT),
        )
    }

    @Test
    fun `one-shot placement keeps the anchor edge across row and screen widths`() {
        val screens = listOf(PORTRAIT, LANDSCAPE, FOLDED, UNFOLDED)
        val rowWidths = listOf(240, 420, 640, 900)
        for (bounds in screens) {
            // Left-half and right-half anchors that fit without a shift.
            val leftAnchor = 24
            val rightAnchor = bounds.widthPx - BUBBLE_PX - 24
            for (rowWidth in rowWidths) {
                val leftX = BubblePositionMath.barWindowXOneShot(
                    anchorXPx = leftAnchor,
                    anchorWidthPx = BUBBLE_PX,
                    contentWidthPx = rowWidth,
                    bounds = bounds,
                    edgeMarginPx = MARGIN_PX,
                )
                if (leftAnchor + rowWidth <= bounds.widthPx - MARGIN_PX) {
                    assertEquals(leftAnchor, leftX, "left anchor row $rowWidth in $bounds")
                }
                assertTrue(leftX >= 0 && leftX + rowWidth <= bounds.widthPx, "left row on screen $bounds/$rowWidth")

                val rightX = BubblePositionMath.barWindowXOneShot(
                    anchorXPx = rightAnchor,
                    anchorWidthPx = BUBBLE_PX,
                    contentWidthPx = rowWidth,
                    bounds = bounds,
                    edgeMarginPx = MARGIN_PX,
                )
                assertTrue(rightX >= 0 && rightX + rowWidth <= bounds.widthPx, "right row on screen $bounds/$rowWidth")
            }
        }
    }

    @Test
    fun `one-shot placement unfolds right from a left anchor and left from a right anchor in one computation`() {
        assertEquals(
            24,
            BubblePositionMath.barWindowXOneShot(24, BUBBLE_PX, 420, PORTRAIT, MARGIN_PX),
        )
        // Right dock: check's right edge is the anchor's right edge (1080),
        // fit-shifted by the margin to 1056.
        assertEquals(
            PORTRAIT.widthPx - MARGIN_PX - 640,
            BubblePositionMath.barWindowXOneShot(912, BUBBLE_PX, 640, PORTRAIT, MARGIN_PX),
        )
        // Without margin pressure the right edge is exact: use a row that
        // ends inside the margin limit.
        val x = BubblePositionMath.barWindowXOneShot(800, BUBBLE_PX, 240, PORTRAIT, MARGIN_PX)
        assertEquals(800 + BUBBLE_PX, x + 240)
    }

    @Test
    fun `the fit shift applies once near the center when the row would overflow`() {
        // Anchor just left of center expands right, but a 640 row from
        // x=450 overflows the right margin limit (1056): one shift back.
        val x = BubblePositionMath.barWindowXOneShot(450, BUBBLE_PX, 640, PORTRAIT, MARGIN_PX)
        assertEquals(PORTRAIT.widthPx - MARGIN_PX - 640, x)
        assertTrue(x + 640 <= PORTRAIT.widthPx - MARGIN_PX)
    }

    @Test
    fun `expand then collapse returns the identical resting position on both sides`() {
        for (anchor in listOf(
            BubblePosition(xFraction = 1f, yFraction = 0.68f),
            BubblePosition(xFraction = 0f, yFraction = 0.68f),
            BubblePosition(xFraction = 0.5f, yFraction = 0.5f),
        )) {
            val resting = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
            for (rowWidth in listOf(240, 420, 640, 900)) {
                BubblePositionMath.barWindowXOneShot(resting.xPx, BUBBLE_PX, rowWidth, PORTRAIT, MARGIN_PX)
                val collapsed = BubblePositionMath.restingTopLeft(anchor, BUBBLE_PX, BUBBLE_PX, PORTRAIT)
                assertEquals(resting, collapsed, "anchor $anchor after row $rowWidth")
            }
        }
    }

    private companion object {
        const val BUBBLE_PX: Int = 168
        const val MARGIN_PX: Int = 24
        const val STEP_PX: Int = 24
        val PORTRAIT = ScreenBounds(widthPx = 1080, heightPx = 2400)
        val LANDSCAPE = ScreenBounds(widthPx = 2400, heightPx = 1080)
        val FOLDED = ScreenBounds(widthPx = 1080, heightPx = 2640)
        val UNFOLDED = ScreenBounds(widthPx = 1812, heightPx = 2176)
    }
}
