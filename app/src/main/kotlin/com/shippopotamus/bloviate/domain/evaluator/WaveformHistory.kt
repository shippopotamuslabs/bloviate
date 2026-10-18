package com.shippopotamus.bloviate.domain.evaluator

import kotlin.math.sqrt

/**
 * Rolling window of the most recent audio levels behind the recording bar's
 * waveform (~[DEFAULT_BAR_COUNT] bars). Pure and framework-free: the bar's
 * Canvas reads [levels] through a deferred lambda at draw time
 * (`CODE_STYLE_RULES.md` §4.5), so amplitude updates never recompose the
 * overlay tree.
 *
 * [record] takes the session's **raw RMS amplitude** and stores a *display
 * level* in two stages:
 *
 * 1. **Soft scaling.** Raw mic RMS for ordinary speech is tiny (roughly
 *    0.01–0.15 of full scale), and perceived loudness is compressive, so the
 *    level is sqrt-scaled after a modest gain: `sqrt(rms × [DISPLAY_GAIN])`.
 *    Conversational speech lands mid-range (0.4–0.7), loud peaks approach
 *    full scale, and only genuinely full-scale input pins at 1. ENC-65's
 *    earlier linear ×8 gain put everything above RMS 0.125 at full scale,
 *    so the bars sat maximized while talking (ENC-66).
 * 2. **Asymmetric envelope.** The stored level chases the scaled target
 *    with a fast attack ([ATTACK_ALPHA] per frame) and a slower release
 *    ([RELEASE_ALPHA] per frame) — the classic level-meter ballistics, so
 *    the wave jumps with speech and settles smoothly back to the baseline
 *    in silence instead of stepping.
 *
 * Frames arrive at ~30 Hz from the session manager's amplitude ticker
 * (ENC-66), so one bar spans ~33 ms and the full window ~1/3 s of audio.
 *
 * Not thread-safe by design: levels are recorded from the overlay service's
 * main-thread session collector and read on the main thread at draw time.
 */
class WaveformHistory(
    private val barCount: Int = DEFAULT_BAR_COUNT,
) {

    private val recorded = ArrayDeque<Float>(barCount)
    private var envelopedLevel: Float = MIN_LEVEL

    /**
     * Append one raw RMS amplitude; the stored display level is the scaled,
     * enveloped value. The oldest level drops off once the window is full.
     */
    fun record(amplitude: Float) {
        val target = displayLevel(amplitude)
        val alpha = if (target > envelopedLevel) ATTACK_ALPHA else RELEASE_ALPHA
        envelopedLevel += (target - envelopedLevel) * alpha
        recorded.addLast(envelopedLevel)
        while (recorded.size > barCount) {
            recorded.removeFirst()
        }
    }

    /** Current display levels, oldest first, at most [barCount] entries, each in 0..1. */
    fun levels(): List<Float> = recorded.toList()

    /** Forget all levels (recording ended, or memory pressure — TRANSCRIPTION_RULES §4). */
    fun clear() {
        recorded.clear()
        envelopedLevel = MIN_LEVEL
    }

    companion object {
        const val DEFAULT_BAR_COUNT: Int = 10

        private const val MIN_LEVEL: Float = 0f
        private const val MAX_LEVEL: Float = 1f
        private const val MIN_BAR_FRACTION: Float = 0.15f

        /**
         * Pre-scaling gain inside the sqrt curve: full display scale is
         * reached at raw RMS `1 / DISPLAY_GAIN` (0.2 — loud, close speech),
         * while conversational RMS (0.03–0.08) maps to 0.39–0.63.
         */
        private const val DISPLAY_GAIN: Float = 5f

        /** Envelope attack per ~33 ms frame: a speech burst registers within a frame or two. */
        private const val ATTACK_ALPHA: Float = 0.7f

        /** Envelope release per ~33 ms frame: silence settles to baseline in ~200 ms. */
        private const val RELEASE_ALPHA: Float = 0.4f

        /** Soft (sqrt) loudness scaling of a raw RMS amplitude into display range 0..1. */
        fun displayLevel(amplitude: Float): Float =
            sqrt(amplitude.coerceIn(MIN_LEVEL, MAX_LEVEL) * DISPLAY_GAIN)
                .coerceIn(MIN_LEVEL, MAX_LEVEL)

        /** Bar height as a fraction of the waveform area: silence still draws a stub bar. */
        fun heightFraction(level: Float): Float =
            MIN_BAR_FRACTION + (MAX_LEVEL - MIN_BAR_FRACTION) * level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }
}
