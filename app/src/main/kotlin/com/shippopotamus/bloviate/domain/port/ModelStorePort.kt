package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.ModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the transcription model file in app-private storage: presence,
 * download, and integrity. This is the deliberately small placeholder behind
 * which ENC-55's full download stack (WorkManager chain, byte-offset resume)
 * later lands without touching the engine or session code.
 *
 * Integrity rule (`contracts/DATA_LAYER_RULES.md` §4): a model file becomes
 * visible to the engine only after its SHA-256 has been verified against the
 * pinned digest — downloads land in a temp file, are verified, and are then
 * renamed into place atomically.
 */
interface ModelStorePort {

    /** Current model lifecycle state. */
    val state: StateFlow<ModelState>

    /**
     * Makes the model ready if possible: verifies the on-disk file, or
     * downloads + verifies + finalizes it when absent or corrupt. Idempotent
     * and safe to call concurrently; on return the state is [ModelState.Ready]
     * or [ModelState.Failed].
     */
    suspend fun ensureModel()

    /**
     * Absolute path of the model file **after** SHA-256 verification, or null
     * when no verified model is available. This is the only path an engine may
     * load from; a file that fails verification is deleted and null returned.
     */
    suspend fun verifiedModelPath(): String?
}
