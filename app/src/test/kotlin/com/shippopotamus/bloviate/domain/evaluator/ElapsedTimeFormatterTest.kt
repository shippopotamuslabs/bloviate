package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ElapsedTimeFormatterTest {

    @Test
    fun `zero formats as 0 colon 00`() {
        assertEquals("0:00", ElapsedTimeFormatter.format(0L))
    }

    @Test
    fun `seconds are zero padded and sub-second remainders floor`() {
        assertEquals("0:07", ElapsedTimeFormatter.format(7_500L))
        assertEquals("0:59", ElapsedTimeFormatter.format(59_999L))
    }

    @Test
    fun `minutes accumulate without a display cap`() {
        assertEquals("1:00", ElapsedTimeFormatter.format(60_000L))
        assertEquals("3:00", ElapsedTimeFormatter.format(180_000L))
        assertEquals("12:05", ElapsedTimeFormatter.format(725_000L))
    }

    @Test
    fun `negative input clamps to zero`() {
        assertEquals("0:00", ElapsedTimeFormatter.format(-5_000L))
    }
}
