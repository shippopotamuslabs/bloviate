package com.shippopotamus.bloviate.domain.evaluator

/**
 * Rolling window of the most recent audio levels behind the recording bar's
 * waveform (~[DEFAULT_BAR_COUNT] bars). Pure and framework-free: the bar's
 * Canvas reads [levels] through a deferred lambda at draw time
 * (`CODE_STYLE_RULES.md` §4.5), so amplitude updates never recompose the
 * overlay tree.
 *
 * [record] takes the session's **raw RMS amplitude** and stores a *display
 * level*: raw mic RMS for ordinary speech is tiny (roughly 0.01–0.15 of
 * full scale), so the raw value is gained up and smoothed before it is
 * stored — the same treatment Enclave's live-voice waveform applies
 * (gain ×8, exponential smoothing). Without the gain stage, speech moved
 * the bars by a fraction of a dp and the waveform looked frozen (ENC-65).
 *
 * Not thread-safe by design: levels are recorded from the overlay service's
 * main-thread session collector and read on the main thread at draw time.
 */
class WaveformHistory(
    private val barCount: Int = DEFAULT_BAR_COUNT,
) {

    private val recorded = ArrayDeque<Float>(barCount)
    private var smoothedLevel: Float = MIN_LEVEL

    /**
     * Append one raw RMS amplitude; the stored display level is the gained,
     * smoothed value. The oldest level drops off once the window is full.
     */
    fun record(amplitude: Float) {
        val target = (amplitude.coerceIn(MIN_LEVEL, MAX_LEVEL) * LEVEL_GAIN)
            .coerceIn(MIN_LEVEL, MAX_LEVEL)
        smoothedLevel += (target - smoothedLevel) * SMOOTHING_ALPHA
        recorded.addLast(smoothedLevel)
        while (recorded.size > barCount) {
            recorded.removeFirst()
        }
    }

    /** Current display levels, oldest first, at most [barCount] entries, each in 0..1. */
    fun levels(): List<Float> = recorded.toList()

    /** Forget all levels (recording ended, or memory pressure — TRANSCRIPTION_RULES §4). */
    fun clear() {
        recorded.clear()
        smoothedLevel = MIN_LEVEL
    }

    companion object {
        const val DEFAULT_BAR_COUNT: Int = 10

        private const val MIN_LEVEL: Float = 0f
        private const val MAX_LEVEL: Float = 1f
        private const val MIN_BAR_FRACTION: Float = 0.15f

        /** Raw-RMS → display gain; Enclave's live-voice waveform uses the same ×8. */
        private const val LEVEL_GAIN: Float = 8f

        /** Exponential smoothing toward the gained target, per recorded frame. */
        private const val SMOOTHING_ALPHA: Float = 0.55f

        /** Bar height as a fraction of the waveform area: silence still draws a stub bar. */
        fun heightFraction(level: Float): Float =
            MIN_BAR_FRACTION + (MAX_LEVEL - MIN_BAR_FRACTION) * level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }
}
