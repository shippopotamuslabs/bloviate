package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleAppearanceTest {

    @Test
    fun `the default is the pre-ENC-68 shipping look`() {
        val appearance = BubbleAppearance.DEFAULT

        assertEquals(1f, appearance.opacity)
        assertEquals(BubbleSize.MEDIUM, appearance.size)
        assertEquals(BubbleShape.PILL, appearance.shape)
        assertEquals(BubbleTint.INDIGO, appearance.tint)
    }

    @Test
    fun `the no-arg appearance equals the default`() {
        assertEquals(BubbleAppearance.DEFAULT, BubbleAppearance())
    }

    @Test
    fun `the opacity range is forty percent to fully opaque`() {
        assertEquals(0.4f, BubbleAppearance.MIN_OPACITY)
        assertEquals(1f, BubbleAppearance.MAX_OPACITY)
    }
}
