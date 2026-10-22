package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmplitudeCalculatorTest {

    @Test
    fun `empty input is silence`() {
        assertEquals(0f, AmplitudeCalculator.rms(FloatArray(0)), EPSILON)
    }

    @Test
    fun `all-zero samples are silence`() {
        assertEquals(0f, AmplitudeCalculator.rms(FloatArray(1_600) { 0f }), EPSILON)
    }

    @Test
    fun `constant level returns that level`() {
        assertEquals(0.5f, AmplitudeCalculator.rms(FloatArray(1_600) { 0.5f }), EPSILON)
    }

    @Test
    fun `full-scale alternating samples are unity`() {
        val samples = FloatArray(1_600) { index -> if (index % 2 == 0) 1f else -1f }
        assertEquals(1f, AmplitudeCalculator.rms(samples), EPSILON)
    }

    @Test
    fun `rms averages energy rather than peak`() {
        // Half silence, half full-scale: RMS = sqrt(0.5) ≈ 0.7071, not 0.5 or 1.
        val samples = FloatArray(1_600) { index -> if (index < 800) 0f else 1f }
        assertEquals(0.7071f, AmplitudeCalculator.rms(samples), EPSILON)
    }

    @Test
    fun `out-of-range input is clamped to unity`() {
        assertEquals(1f, AmplitudeCalculator.rms(FloatArray(100) { 2f }), EPSILON)
    }

    private companion object {
        const val EPSILON: Float = 0.0001f
    }
}
