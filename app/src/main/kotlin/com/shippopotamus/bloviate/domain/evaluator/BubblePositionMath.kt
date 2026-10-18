package com.shippopotamus.bloviate.domain.evaluator

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

    /** True when the bubble's vertical center sits inside the bottom dismiss zone. */
    fun isInDismissZone(centerYPx: Int, bounds: ScreenBounds): Boolean =
        centerYPx.toFloat() >= bounds.heightPx * (MAX_FRACTION - DISMISS_ZONE_HEIGHT_FRACTION)

    private fun maxTravelPx(screenPx: Int, bubblePx: Int): Int = (screenPx - bubblePx).coerceAtLeast(MIN_OFFSET_PX)

    private fun fractionOf(offsetPx: Int, travelPx: Int): Float =
        if (travelPx <= MIN_OFFSET_PX) {
            MIN_FRACTION
        } else {
            (offsetPx.toFloat() / travelPx).coerceIn(MIN_FRACTION, MAX_FRACTION)
        }

    private const val MIN_OFFSET_PX: Int = 0
}
