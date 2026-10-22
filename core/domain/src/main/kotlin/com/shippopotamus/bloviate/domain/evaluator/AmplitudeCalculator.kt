package com.shippopotamus.bloviate.domain.evaluator

import kotlin.math.sqrt

/** Pure amplitude math for captured PCM samples (normalized floats, nominally [-1, 1]). */
object AmplitudeCalculator {

    /** Root-mean-square level of [samples], clamped to 0..1. Empty input is silence (0). */
    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sumSquares / samples.size).toFloat().coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    private const val MIN_LEVEL: Float = 0f
    private const val MAX_LEVEL: Float = 1f
}
