package com.shippopotamus.bloviate.domain.evaluator

import kotlin.math.sqrt

/**
 * Leading/trailing silence trim for a stopped dictation, per
 * `contracts/TRANSCRIPTION_RULES.md` §2 step 3. The utterance is scanned in
 * 10 ms frames; the first and last frames whose RMS reaches
 * [SILENCE_RMS_THRESHOLD] bound the speech, and [PADDING_MS] of audio is kept
 * on both sides so word onsets/tails are never clipped. Interior pauses are
 * untouched — dictation is transcribed as one whole pass.
 *
 * An utterance with no frame above the threshold is all silence and trims to
 * an empty array (the pipeline treats it as "nothing dictated").
 */
object SilenceTrimmer {

    /** Frame RMS at or above this counts as speech (Enclave's dictation threshold). */
    const val SILENCE_RMS_THRESHOLD: Float = 0.02f

    /**
     * Whether [samples] (16 kHz mono normalized floats) contain any speech
     * (ENC-160): true when any 10 ms frame reaches [SILENCE_RMS_THRESHOLD].
     * That is the same definition of "speech" the trim uses, so the session's
     * inactivity detector and the trim never disagree about what counts
     * as voice.
     */
    fun containsSpeech(
        samples: FloatArray,
        sampleRateHz: Int = SAMPLE_RATE_HZ,
    ): Boolean {
        if (samples.isEmpty()) return false
        val frameSize = sampleRateHz * FRAME_MS / MS_PER_SECOND
        var frameStart = 0
        while (frameStart < samples.size) {
            if (frameRms(samples, frameStart, frameSize) >= SILENCE_RMS_THRESHOLD) return true
            frameStart += frameSize
        }
        return false
    }

    /** Analysis frame length. */
    const val FRAME_MS: Int = 10

    /** Audio kept before the first and after the last speech frame. */
    const val PADDING_MS: Int = 100

    /** Sample rate of captured dictation audio. */
    const val SAMPLE_RATE_HZ: Int = 16_000

    /** Trims [samples] (16 kHz mono normalized floats) as described above. */
    fun trim(
        samples: FloatArray,
        sampleRateHz: Int = SAMPLE_RATE_HZ,
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val frameSize = sampleRateHz * FRAME_MS / MS_PER_SECOND
        val paddingSamples = sampleRateHz * PADDING_MS / MS_PER_SECOND
        val firstSpeechFrame = firstSpeechFrame(samples, frameSize) ?: return FloatArray(0)
        val lastSpeechFrame = lastSpeechFrame(samples, frameSize)
        val startSample = (firstSpeechFrame * frameSize - paddingSamples).coerceAtLeast(0)
        val endSample = ((lastSpeechFrame + 1) * frameSize + paddingSamples).coerceAtMost(samples.size)
        return samples.copyOfRange(startSample, endSample)
    }

    private fun firstSpeechFrame(
        samples: FloatArray,
        frameSize: Int,
    ): Int? {
        var frameStart = 0
        var frameIndex = 0
        while (frameStart < samples.size) {
            if (frameRms(samples, frameStart, frameSize) >= SILENCE_RMS_THRESHOLD) return frameIndex
            frameStart += frameSize
            frameIndex++
        }
        return null
    }

    private fun lastSpeechFrame(
        samples: FloatArray,
        frameSize: Int,
    ): Int {
        var frameStart = (samples.size - 1) / frameSize * frameSize
        var frameIndex = frameStart / frameSize
        while (frameStart >= 0) {
            if (frameRms(samples, frameStart, frameSize) >= SILENCE_RMS_THRESHOLD) return frameIndex
            frameStart -= frameSize
            frameIndex--
        }
        return 0 // Unreachable: callers guarantee a speech frame exists.
    }

    private fun frameRms(
        samples: FloatArray,
        frameStart: Int,
        frameSize: Int,
    ): Float {
        val frameEnd = (frameStart + frameSize).coerceAtMost(samples.size)
        var sumSquares = 0.0
        for (index in frameStart until frameEnd) {
            sumSquares += samples[index].toDouble() * samples[index].toDouble()
        }
        return sqrt(sumSquares / (frameEnd - frameStart)).toFloat()
    }

    private const val MS_PER_SECOND: Int = 1_000
}
