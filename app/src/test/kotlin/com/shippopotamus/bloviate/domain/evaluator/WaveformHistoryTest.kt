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
    fun `sustained conversational speech lives mid-range and never pins at max`() {
        // ENC-66: the old linear gain saturated at RMS 0.125, so steady
        // talking parked every bar at full scale. With sqrt scaling, a
        // steady conversational RMS (0.08) settles around sqrt(0.08 * 5).
        val history = WaveformHistory()

        repeat(30) { history.record(0.08f) }

        val settled = history.levels().last()
        assertTrue(settled in 0.5f..0.75f, "mid speech should settle mid-range, got $settled")
        assertTrue(history.levels().none { it >= 0.99f }, "no bar may pin at max: ${history.levels()}")
    }

    @Test
    fun `attack rises most of a burst within two frames`() {
        val history = WaveformHistory()
        repeat(10) { history.record(0f) }

        history.record(0.2f)
        history.record(0.2f)

        // Full-scale target (sqrt(0.2 * 5) = 1): two attack frames at 0.7
        // cover 1 - 0.3^2 = 91% of the step.
        assertTrue(history.levels().last() > 0.85f, "burst should register fast: ${history.levels()}")
    }

    @Test
    fun `release falls slower than attack rises`() {
        val history = WaveformHistory()
        repeat(20) { history.record(0.2f) }
        val peak = history.levels().last()

        history.record(0f)

        // One release frame sheds only the release fraction (0.4) of the
        // level — visibly above where one attack frame would have put the
        // mirror-image rise from silence (0.7 of the way would be a fall
        // to 0.3 of peak; release leaves 0.6 of peak).
        val afterOneSilentFrame = history.levels().last()
        assertTrue(
            afterOneSilentFrame > peak * 0.5f,
            "release must glide, not step: peak $peak -> $afterOneSilentFrame",
        )
    }

    @Test
    fun `silence settles back to the quiet baseline`() {
        val history = WaveformHistory()
        repeat(10) { history.record(0.15f) }

        repeat(30) { history.record(0f) }

        assertTrue(
            history.levels().last() < 0.02f,
            "silence must settle to baseline: ${history.levels().last()}",
        )
    }

    @Test
    fun `display level maps raw rms through the soft curve`() {
        assertEquals(0f, WaveformHistory.displayLevel(0f), FLOAT_DELTA)
        // Conversational middle: sqrt(0.05 * 5) = 0.5.
        assertEquals(0.5f, WaveformHistory.displayLevel(0.05f), FLOAT_DELTA)
        // Loud close speech reaches full scale; beyond that clamps.
        assertEquals(1f, WaveformHistory.displayLevel(0.2f), FLOAT_DELTA)
        assertEquals(1f, WaveformHistory.displayLevel(0.9f), FLOAT_DELTA)
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
