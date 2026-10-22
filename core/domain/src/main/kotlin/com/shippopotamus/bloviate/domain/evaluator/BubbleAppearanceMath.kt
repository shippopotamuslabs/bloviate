package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleRenderParams
import com.shippopotamus.bloviate.domain.model.BubbleSize

/**
 * Pure mapping from [BubbleAppearance] to rendering geometry (ENC-68).
 *
 * Every size derives from the size step's scale against the medium
 * geometry the bubble shipped with (56dp bubble/bar, 48dp slots, and the
 * glyph sizes in `BubbleView` before this preference existed), so MEDIUM
 * reproduces the pre-ENC-68 layout exactly.
 *
 * The one floor that outranks the scale: the 48dp touch-target law
 * (`contracts/UI_DESIGN_SPEC.md` §5/§6.6). At SMALL the bar's slots would
 * scale to ~41dp, so the slot clamps at 48dp — exactly the bar's SMALL
 * height, making the slots flush end caps rather than sub-law targets.
 * The SMALL bubble itself is 48dp: the smallest lawful target, by
 * design of the step.
 */
object BubbleAppearanceMath {

    /** The medium (shipping) bubble extent; the scale's denominator. */
    const val BASE_BUBBLE_SIZE_DP: Float = 56f

    private const val SMALL_BUBBLE_SIZE_DP: Float = 48f
    private const val LARGE_BUBBLE_SIZE_DP: Float = 64f

    private const val BASE_BUBBLE_ICON_DP: Float = 28f
    private const val BASE_BAR_SLOT_DP: Float = 48f
    private const val BASE_BAR_ICON_DP: Float = 24f
    private const val BASE_SEND_CIRCLE_DP: Float = 36f
    private const val BASE_SEND_ICON_DP: Float = 22f
    private const val BASE_WAVEFORM_HEIGHT_DP: Float = 28f

    /** The 48dp minimum touch target (`UI_DESIGN_SPEC.md` §6.6) — slots never render below it. */
    private const val MIN_TOUCH_TARGET_DP: Float = 48f

    /** The dp extent a size step renders the bubble at. */
    fun bubbleSizeDp(size: BubbleSize): Float =
        when (size) {
            BubbleSize.SMALL -> SMALL_BUBBLE_SIZE_DP
            BubbleSize.MEDIUM -> BASE_BUBBLE_SIZE_DP
            BubbleSize.LARGE -> LARGE_BUBBLE_SIZE_DP
        }

    /** Full rendering geometry for [appearance]; opacity is coerced into its legal range here. */
    fun renderParams(appearance: BubbleAppearance): BubbleRenderParams {
        val bubbleSizeDp = bubbleSizeDp(appearance.size)
        val scale = bubbleSizeDp / BASE_BUBBLE_SIZE_DP
        return BubbleRenderParams(
            opacity = appearance.opacity.coerceIn(BubbleAppearance.MIN_OPACITY, BubbleAppearance.MAX_OPACITY),
            bubbleSizeDp = bubbleSizeDp,
            bubbleIconDp = BASE_BUBBLE_ICON_DP * scale,
            barHeightDp = bubbleSizeDp,
            barSlotDp = (BASE_BAR_SLOT_DP * scale).coerceAtLeast(MIN_TOUCH_TARGET_DP),
            barIconDp = BASE_BAR_ICON_DP * scale,
            sendCircleDp = BASE_SEND_CIRCLE_DP * scale,
            sendIconDp = BASE_SEND_ICON_DP * scale,
            waveformHeightDp = BASE_WAVEFORM_HEIGHT_DP * scale,
        )
    }
}
