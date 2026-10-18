package com.shippopotamus.bloviate.domain.evaluator

/**
 * Rolling window of the most recent audio levels behind the recording bar's
 * waveform (~[DEFAULT_BAR_COUNT] bars). Pure and framework-free: the bar's
 * Canvas reads [levels] through a deferred lambda at draw time
 * (`CODE_STYLE_RULES.md` §4.5), so amplitude updates never recompose the
 * overlay tree.
 *
 * Not thread-safe by design: levels are recorded from the overlay service's
 * main-thread session collector and read on the main thread at draw time.
 */
class WaveformHistory(
    private val barCount: Int = DEFAULT_BAR_COUNT,
) {

    private val recorded = ArrayDeque<Float>(barCount)

    /** Append one level (clamped to 0..1); the oldest level drops off once the window is full. */
    fun record(amplitude: Float) {
        recorded.addLast(amplitude.coerceIn(MIN_LEVEL, MAX_LEVEL))
        while (recorded.size > barCount) {
            recorded.removeFirst()
        }
    }

    /** Current levels, oldest first, at most [barCount] entries. */
    fun levels(): List<Float> = recorded.toList()

    /** Forget all levels (recording ended, or memory pressure — TRANSCRIPTION_RULES §4). */
    fun clear() {
        recorded.clear()
    }

    companion object {
        const val DEFAULT_BAR_COUNT: Int = 10

        private const val MIN_LEVEL: Float = 0f
        private const val MAX_LEVEL: Float = 1f
        private const val MIN_BAR_FRACTION: Float = 0.15f

        /** Bar height as a fraction of the waveform area: silence still draws a stub bar. */
        fun heightFraction(level: Float): Float =
            MIN_BAR_FRACTION + (MAX_LEVEL - MIN_BAR_FRACTION) * level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }
}
