package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Synthetic-array tests for the silence trim: 16 kHz audio, speech is a
 * constant 0.5 block (frame RMS 0.5, well above the 0.02 threshold) and
 * silence is zeros. Frame = 160 samples, padding = 1_600 samples.
 */
class SilenceTrimmerTest {

    @Test
    fun `empty input stays empty`() {
        assertEquals(0, SilenceTrimmer.trim(FloatArray(0)).size)
    }

    @Test
    fun `all silence trims to empty`() {
        assertEquals(0, SilenceTrimmer.trim(FloatArray(19_200) { 0f }).size)
    }

    @Test
    fun `below-threshold noise counts as silence`() {
        assertEquals(0, SilenceTrimmer.trim(FloatArray(19_200) { 0.01f }).size)
    }

    @Test
    fun `speech with no silence is unchanged`() {
        val samples = FloatArray(4_800) { 0.5f }
        val trimmed = SilenceTrimmer.trim(samples)
        assertEquals(samples.size, trimmed.size)
        assertTrue(trimmed.contentEquals(samples))
    }

    @Test
    fun `leading and trailing silence are cut with padding kept`() {
        // 500 ms silence + 200 ms speech + 500 ms silence.
        val samples = FloatArray(19_200) { index -> if (index in 8_000 until 11_200) 0.5f else 0f }
        val trimmed = SilenceTrimmer.trim(samples)
        // Speech bounds 8_000..11_200, padding 1_600 each side: 6_400..12_800.
        assertEquals(6_400, trimmed.size)
        assertEquals(0f, trimmed[0]) // still padding silence
        assertEquals(0f, trimmed[1_599])
        assertEquals(0.5f, trimmed[1_600]) // first speech sample
        assertEquals(0.5f, trimmed[4_799]) // last speech sample
        assertEquals(0f, trimmed[4_800])
        assertEquals(0f, trimmed[6_399])
    }

    @Test
    fun `speech at the very start clamps padding to the array start`() {
        val samples = FloatArray(11_200) { index -> if (index < 3_200) 0.5f else 0f }
        val trimmed = SilenceTrimmer.trim(samples)
        // No leading cut possible; trailing: speech ends at 3_200, + 1_600 padding.
        assertEquals(4_800, trimmed.size)
        assertEquals(0.5f, trimmed[0])
        assertEquals(0.5f, trimmed[3_199])
        assertEquals(0f, trimmed[3_200])
    }

    @Test
    fun `speech at the very end clamps padding to the array end`() {
        val samples = FloatArray(11_200) { index -> if (index >= 8_000) 0.5f else 0f }
        val trimmed = SilenceTrimmer.trim(samples)
        // Leading: speech starts at 8_000, - 1_600 padding = 6_400; nothing cut at the end.
        assertEquals(11_200 - 6_400, trimmed.size)
        assertEquals(0f, trimmed[0])
        assertEquals(0.5f, trimmed[1_600])
        assertEquals(0.5f, trimmed[trimmed.size - 1])
    }

    @Test
    fun `interior pauses are preserved`() {
        // 100 ms speech, 200 ms pause, 100 ms speech — one utterance.
        val samples = FloatArray(6_400) { index ->
            if (index < 1_600 || index >= 4_800) 0.5f else 0f
        }
        val trimmed = SilenceTrimmer.trim(samples)
        assertEquals(6_400, trimmed.size)
        assertTrue(trimmed.contentEquals(samples))
    }
}
