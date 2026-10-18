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

        history.record(0.02f)
        history.record(0.05f)
        history.record(0.1f)

        val levels = history.levels()
        assertEquals(3, levels.size)
        assertTrue(levels[0] < levels[1] && levels[1] < levels[2])
    }

    @Test
    fun `the window keeps only the most recent bar count levels`() {
        val history = WaveformHistory(barCount = 3)

        history.record(0.02f)
        history.record(0.04f)
        history.record(0.06f)
        history.record(0.08f)
        history.record(0.1f)

        val levels = history.levels()
        assertEquals(3, levels.size)
        assertTrue(levels[0] < levels[1] && levels[1] < levels[2])
    }

    @Test
    fun `the default window holds ten bars`() {
        val history = WaveformHistory()

        repeat(15) { history.record(0.05f) }

        assertEquals(WaveformHistory.DEFAULT_BAR_COUNT, history.levels().size)
        assertEquals(10, WaveformHistory.DEFAULT_BAR_COUNT)
    }

    @Test
    fun `levels are clamped into the zero to one range`() {
        val history = WaveformHistory()

        history.record(1.7f)
        history.record(-0.4f)

        val levels = history.levels()
        assertTrue(levels.all { it in 0f..1f })
        // An over-range input behaves exactly like the full-scale input it clamps to.
        val fullScale = WaveformHistory()
        fullScale.record(1f)
        assertEquals(fullScale.levels().single(), levels.first(), FLOAT_DELTA)
        // The under-range input clamps to silence: one decay step down from the loud level.
        assertTrue(levels.last() < levels.first())
    }

    @Test
    fun `clear forgets every recorded level and resets the smoothing`() {
        val history = WaveformHistory()
        history.record(0.1f)
        history.record(0.1f)

        history.clear()

        assertTrue(history.levels().isEmpty())
        // After a clear, the next level starts from silence again, not from the old level.
        history.record(0.1f)
        val fresh = WaveformHistory()
        fresh.record(0.1f)
        assertEquals(fresh.levels(), history.levels())
    }

    @Test
    fun `ordinary speech amplitudes produce visibly moving bars`() {
        // Raw mic RMS for conversational speech is roughly 0.01-0.15 of full
        // scale. Before the gain stage existed (ENC-65), these values moved
        // the bars by well under a dp and the waveform looked frozen.
        val history = WaveformHistory()
        val speechRms = listOf(0.02f, 0.04f, 0.09f, 0.13f, 0.07f, 0.03f)

        speechRms.forEach { history.record(it) }

        val levels = history.levels()
        val loudest = WaveformHistory.heightFraction(levels.max())
        val quietest = WaveformHistory.heightFraction(levels.min())
        assertTrue(levels.max() > 0.5f, "loud speech should reach the upper half: $levels")
        assertTrue(
            loudest >= 2f * quietest,
            "bar heights must vary visibly across a phrase: $levels",
        )
        assertTrue(levels.zipWithNext().any { (a, b) -> b - a > 0.1f })
    }

    @Test
    fun `silence decays toward zero instead of sticking at the last level`() {
        val history = WaveformHistory()
        repeat(5) { history.record(0.1f) }
        val loudLevel = history.levels().last()

        repeat(5) { history.record(0f) }

        val settled = history.levels().last()
        assertTrue(settled < loudLevel * 0.1f, "expected decay from $loudLevel, got $settled")
    }

    @Test
    fun `a single loud frame is smoothed rather than slamming to full scale`() {
        val history = WaveformHistory()

        history.record(0.125f) // Gained target is full scale.

        val level = history.levels().single()
        assertTrue(level in 0.4f..0.7f, "one frame should land mid-scale, got $level")
    }

    @Test
    fun `sustained loud input converges toward full scale`() {
        val history = WaveformHistory()

        repeat(10) { history.record(0.2f) }

        assertTrue(history.levels().last() > 0.99f)
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
