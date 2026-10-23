package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.Flow

/**
 * Microphone capture behind a domain seam, so the recording session never
 * sees the capture implementation (the whisper-android library's
 * `AudioRecorder`). Collecting [chunks] requires the `RECORD_AUDIO`
 * permission; the flow ends when its collector is cancelled.
 */
interface AudioCapturePort {

    /** 16 kHz mono PCM samples normalized to floats in [-1, 1], in capture order. */
    fun chunks(): Flow<FloatArray>
}
