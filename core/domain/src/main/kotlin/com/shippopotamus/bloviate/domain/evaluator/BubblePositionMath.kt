package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BarExpansionSide
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import kotlin.math.roundToInt

/**
 * Pure geometry for the bubble: clamping into current bounds and converting
 * between pixel offsets and the persisted normalized [BubblePosition].
 *
 * The normalized form stores the top-left corner as fractions of the travel
 * range (`screen - bubble`) per axis, so a normalized ↔ pixel round trip is
 * stable and a position saved in one posture lands fully on-screen in another
 * (rotation, fold/unfold) after clamping.
 */
object BubblePositionMath {

    /** Height of the dismiss zone as a fraction of screen height, measured from the bottom. */
    const val DISMISS_ZONE_HEIGHT_FRACTION: Float = 0.12f

    private const val MIN_FRACTION: Float = 0f
    private const val MAX_FRACTION: Float = 1f

    /** Clamp a top-left offset so the bubble stays fully inside [bounds]. */
    fun clamp(
        topLeft: BubblePixelPosition,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePixelPosition =
        BubblePixelPosition(
            xPx = topLeft.xPx.coerceIn(MIN_OFFSET_PX, maxTravelPx(bounds.widthPx, bubbleWidthPx)),
            yPx = topLeft.yPx.coerceIn(MIN_OFFSET_PX, maxTravelPx(bounds.heightPx, bubbleHeightPx)),
        )

    /** Convert a pixel offset to the persisted normalized form (clamped first). */
    fun toNormalized(
        topLeft: BubblePixelPosition,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePosition {
        val clamped = clamp(topLeft, bubbleWidthPx, bubbleHeightPx, bounds)
        return BubblePosition(
            xFraction = fractionOf(clamped.xPx, maxTravelPx(bounds.widthPx, bubbleWidthPx)),
            yFraction = fractionOf(clamped.yPx, maxTravelPx(bounds.heightPx, bubbleHeightPx)),
        )
    }

    /** Convert the persisted normalized form back to pixels for [bounds] (clamped). */
    fun toPixels(
        position: BubblePosition,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePixelPosition =
        clamp(
            topLeft = BubblePixelPosition(
                xPx = (position.xFraction.coerceIn(MIN_FRACTION, MAX_FRACTION) *
                    maxTravelPx(bounds.widthPx, bubbleWidthPx)).roundToInt(),
                yPx = (position.yFraction.coerceIn(MIN_FRACTION, MAX_FRACTION) *
                    maxTravelPx(bounds.heightPx, bubbleHeightPx)).roundToInt(),
            ),
            bubbleWidthPx = bubbleWidthPx,
            bubbleHeightPx = bubbleHeightPx,
            bounds = bounds,
        )

    /**
     * The bubble's resting top-left: [position] (the persisted anchor)
     * re-derived against the bubble's OWN size in [bounds] (ENC-123).
     *
     * This is the only way a resting position may be recomputed when the
     * window's form or size changes around the bubble — the bar morph
     * settling, an ENC-68 size-step change, a posture change. The window's
     * current pixel offset and any transient form width are never inputs:
     * pixel-clamping the resting offset against a wider form's width
     * (the bar's, mid-collapse) pulls a docked bubble toward the interior
     * by the width difference, and clamping never moves it back — the
     * bubble came to rest displaced toward mid-screen after every record
     * cycle. Re-derived from the anchor instead, the docked edge is
     * invariant: expand → collapse returns the identical resting
     * position, and a resize preserves the docked edge exactly.
     */
    fun restingTopLeft(
        position: BubblePosition,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        bounds: ScreenBounds,
    ): BubblePixelPosition = toPixels(position, bubbleWidthPx, bubbleHeightPx, bounds)

    /** True when the bubble's vertical center sits inside the bottom dismiss zone. */
    fun isInDismissZone(centerYPx: Int, bounds: ScreenBounds): Boolean =
        centerYPx.toFloat() >= bounds.heightPx * (MAX_FRACTION - DISMISS_ZONE_HEIGHT_FRACTION)

    /**
     * Fit helper for a bar-form row (ENC-65, fit math reused by ENC-167). The bar
     * is much wider than the bubble and the window is anchored at its
     * top-left, so growing rightward from a right-parked bubble would push
     * the bar off-screen. The expansion is positionally aware instead: the
     * bar grows rightward from the bubble's own x while the whole bar fits
     * (keeping [edgeMarginPx] clear of the right edge); when it would not
     * fit, the bar grows leftward — its right edge lands [edgeMarginPx]
     * inside the screen and its x is pulled back toward the interior. The
     * result always keeps the full bar on-screen (or pins it to the left
     * edge when the bar is wider than the screen allows). This moves only
     * the *window*; the bubble's own anchor is restored by the caller when
     * the bar closes — the bar's x is never the bubble's position.
     */
    fun barWindowX(
        bubbleXPx: Int,
        barWidthPx: Int,
        bounds: ScreenBounds,
        edgeMarginPx: Int,
    ): Int {
        val rightLimitPx = bounds.widthPx - edgeMarginPx
        return if (bubbleXPx + barWidthPx <= rightLimitPx) {
            bubbleXPx.coerceAtLeast(MIN_OFFSET_PX)
        } else {
            (rightLimitPx - barWidthPx).coerceAtLeast(MIN_OFFSET_PX)
        }
    }

    /**
     * The expansion side for an anchor at [anchorXPx] with extent
     * [anchorWidthPx] in [bounds] (ENC-167): the anchor's center is
     * compared against the screen's center. A center exactly on the
     * screen center is NOT left of it, so it expands left — the check
     * sits at the anchor spot and the row unfolds toward the larger
     * (left) half's interior in the tie the same way a right-half
     * anchor does.
     */
    fun expansionSide(
        anchorXPx: Int,
        anchorWidthPx: Int,
        bounds: ScreenBounds,
    ): BarExpansionSide {
        return if (anchorXPx * CENTER_DIVISOR + anchorWidthPx < bounds.widthPx) {
            BarExpansionSide.EXPAND_RIGHT
        } else {
            BarExpansionSide.EXPAND_LEFT
        }
    }

    /**
     * One-shot window x for a bar-form row of [contentWidthPx] unfolding
     * from the anchor at [anchorXPx] / [anchorWidthPx] (ENC-167).
     *
     * Computed ONCE, before the row composes, from the known content
     * width — never from a measured live view width — so no per-frame
     * correction follows it. The invariant is the anchor edge: expanding
     * right, the row's left edge (the X's spot) is the anchor's left
     * edge; expanding left, the row's right edge (the check's spot) is
     * the anchor's right edge. The single exception is the pre-computed
     * fit shift, applied here and only here via [barWindowX]'s fit math:
     * when the unfolded row would overflow the screen (possible near the
     * center, where the side is decided but the full row may not fit),
     * the window shifts once so the whole row stays on-screen with
     * [edgeMarginPx] clear of the far edge.
     */
    fun barWindowXOneShot(
        anchorXPx: Int,
        anchorWidthPx: Int,
        contentWidthPx: Int,
        bounds: ScreenBounds,
        edgeMarginPx: Int,
    ): Int {
        val idealXPx = when (expansionSide(anchorXPx, anchorWidthPx, bounds)) {
            BarExpansionSide.EXPAND_RIGHT -> anchorXPx
            BarExpansionSide.EXPAND_LEFT -> anchorXPx + anchorWidthPx - contentWidthPx
        }
        return barWindowX(
            bubbleXPx = idealXPx,
            barWidthPx = contentWidthPx,
            bounds = bounds,
            edgeMarginPx = edgeMarginPx,
        )
    }

    private fun maxTravelPx(screenPx: Int, bubblePx: Int): Int = (screenPx - bubblePx).coerceAtLeast(MIN_OFFSET_PX)

    private fun fractionOf(offsetPx: Int, travelPx: Int): Float =
        if (travelPx <= MIN_OFFSET_PX) {
            MIN_FRACTION
        } else {
            (offsetPx.toFloat() / travelPx).coerceIn(MIN_FRACTION, MAX_FRACTION)
        }

    private const val MIN_OFFSET_PX: Int = 0
    private const val CENTER_DIVISOR: Int = 2
}
