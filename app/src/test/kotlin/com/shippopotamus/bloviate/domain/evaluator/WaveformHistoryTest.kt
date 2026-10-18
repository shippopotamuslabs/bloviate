package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveformHistoryTest {

    @Test
    fun `levels are empty before anything is recorded`() {
        val history = WaveformHistory()

        assertTrue(history.levels().isEmpty())
    }

    @Test
    fun `recorded levels come back oldest first`() {
        val history = WaveformHistory()

        history.record(0.2f)
        history.record(0.5f)
        history.record(0.9f)

        assertEquals(listOf(0.2f, 0.5f, 0.9f), history.levels())
    }

    @Test
    fun `the window keeps only the most recent bar count levels`() {
        val history = WaveformHistory(barCount = 3)

        history.record(0.1f)
        history.record(0.2f)
        history.record(0.3f)
        history.record(0.4f)
        history.record(0.5f)

        assertEquals(listOf(0.3f, 0.4f, 0.5f), history.levels())
    }

    @Test
    fun `the default window holds ten bars`() {
        val history = WaveformHistory()

        repeat(15) { history.record(0.5f) }

        assertEquals(WaveformHistory.DEFAULT_BAR_COUNT, history.levels().size)
        assertEquals(10, WaveformHistory.DEFAULT_BAR_COUNT)
    }

    @Test
    fun `levels are clamped into the zero to one range`() {
        val history = WaveformHistory()

        history.record(1.7f)
        history.record(-0.4f)

        assertEquals(listOf(1f, 0f), history.levels())
    }

    @Test
    fun `clear forgets every recorded level`() {
        val history = WaveformHistory()
        history.record(0.5f)
        history.record(0.6f)

        history.clear()

        assertTrue(history.levels().isEmpty())
    }

    @Test
    fun `height fraction maps silence to the stub and full level to the whole area`() {
        assertEquals(0.15f, WaveformHistory.heightFraction(0f), FLOAT_DELTA)
        assertEquals(1f, WaveformHistory.heightFraction(1f), FLOAT_DELTA)
        assertEquals(0.575f, WaveformHistory.heightFraction(0.5f), FLOAT_DELTA)
    }

    @Test
    fun `height fraction clamps out of range levels`() {
        assertEquals(1f, WaveformHistory.heightFraction(2f), FLOAT_DELTA)
        assertEquals(0.15f, WaveformHistory.heightFraction(-1f), FLOAT_DELTA)
    }

    private companion object {
        const val FLOAT_DELTA: Float = 0.0001f
    }
}
