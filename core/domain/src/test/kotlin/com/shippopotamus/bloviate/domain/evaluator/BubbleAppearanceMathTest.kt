package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubbleAppearanceMathTest {

    @Test
    fun `medium reproduces the pre-ENC-68 geometry exactly`() {
        val params = BubbleAppearanceMath.renderParams(BubbleAppearance.DEFAULT)

        assertEquals(56f, params.bubbleSizeDp)
        assertEquals(28f, params.bubbleIconDp)
        assertEquals(56f, params.barHeightDp)
        assertEquals(48f, params.barSlotDp)
        assertEquals(24f, params.barIconDp)
        assertEquals(36f, params.sendCircleDp)
        assertEquals(22f, params.sendIconDp)
        assertEquals(28f, params.waveformHeightDp)
        assertEquals(1f, params.opacity)
    }

    @Test
    fun `size steps render at 48 56 and 64 dp`() {
        assertEquals(48f, BubbleAppearanceMath.bubbleSizeDp(BubbleSize.SMALL))
        assertEquals(56f, BubbleAppearanceMath.bubbleSizeDp(BubbleSize.MEDIUM))
        assertEquals(64f, BubbleAppearanceMath.bubbleSizeDp(BubbleSize.LARGE))
    }

    @Test
    fun `small scales every glyph with the bubble`() {
        val params = BubbleAppearanceMath.renderParams(
            BubbleAppearance(size = BubbleSize.SMALL),
        )
        val scale = 48f / 56f

        assertEquals(48f, params.bubbleSizeDp)
        assertEquals(48f, params.barHeightDp)
        assertEquals(28f * scale, params.bubbleIconDp)
        assertEquals(24f * scale, params.barIconDp)
        assertEquals(36f * scale, params.sendCircleDp)
        assertEquals(22f * scale, params.sendIconDp)
        assertEquals(28f * scale, params.waveformHeightDp)
    }

    @Test
    fun `the bar slots never fall below the 48dp touch target`() {
        // At SMALL the raw scale would put the slots at ~41dp; the law
        // clamps them to 48dp — flush with the 48dp bar, never under it.
        val small = BubbleAppearanceMath.renderParams(BubbleAppearance(size = BubbleSize.SMALL))
        assertEquals(48f, small.barSlotDp)

        BubbleSize.entries.forEach { size ->
            val params = BubbleAppearanceMath.renderParams(BubbleAppearance(size = size))
            assertTrue(params.barSlotDp >= 48f, "slot below target at $size: ${params.barSlotDp}")
            assertTrue(params.barSlotDp <= params.barHeightDp, "slot overflows bar at $size")
            assertTrue(params.bubbleSizeDp >= 48f, "bubble below target at $size")
        }
    }

    @Test
    fun `large scales every glyph up with the bubble`() {
        val params = BubbleAppearanceMath.renderParams(
            BubbleAppearance(size = BubbleSize.LARGE),
        )
        val scale = 64f / 56f

        assertEquals(64f, params.bubbleSizeDp)
        assertEquals(64f, params.barHeightDp)
        assertEquals(48f * scale, params.barSlotDp)
        assertEquals(28f * scale, params.bubbleIconDp)
        assertEquals(36f * scale, params.sendCircleDp)
    }

    @Test
    fun `opacity passes through inside the legal range and coerces outside it`() {
        fun opacityOf(opacity: Float): Float =
            BubbleAppearanceMath.renderParams(BubbleAppearance(opacity = opacity)).opacity

        assertEquals(0.7f, opacityOf(0.7f))
        assertEquals(BubbleAppearance.MIN_OPACITY, opacityOf(0.1f))
        assertEquals(BubbleAppearance.MIN_OPACITY, opacityOf(0f))
        assertEquals(BubbleAppearance.MAX_OPACITY, opacityOf(1.5f))
    }
}
