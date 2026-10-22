package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.ProcessingMode

/**
 * The transcription seam (see `contracts/TRANSCRIPTION_RULES.md`). Every
 * engine — the on-device whisper implementation today, Parakeet (ENC-54) and
 * a future opt-in cloud engine behind it — is consumed through this
 * interface; call sites never branch on the implementation.
 *
 * Lifecycle contract: the engine keeps its model context **warm** across
 * dictations. [warmUp] loads lazily and is idempotent; [transcribe] warms
 * up first if needed; [release] frees the context under memory pressure and
 * the next call re-loads lazily. All calls are serialized by the
 * implementation — the native context is not thread-safe.
 */
interface TranscriptionEngine {

    /** Where this engine processes audio. */
    val mode: ProcessingMode

    /** Loads the model context if it is not already warm. No-op when warm or when no verified model exists. */
    suspend fun warmUp()

    /**
     * Transcribes one whole utterance of 16 kHz mono PCM samples (normalized
     * floats, already silence-trimmed by the session layer) in a single pass.
     *
     * @throws IllegalStateException when no verified model is available.
     */
    suspend fun transcribe(samples: FloatArray): String

    /** Frees the model context (memory pressure / shutdown). Safe when not warm. */
    suspend fun release()

    /** True while the model context is loaded. */
    fun isWarm(): Boolean
}
