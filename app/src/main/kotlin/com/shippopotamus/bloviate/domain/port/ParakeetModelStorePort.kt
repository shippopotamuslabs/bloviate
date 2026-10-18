package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.ParakeetModelFiles
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the Parakeet model **file set** (encoder / decoder / joiner / tokens)
 * in app-private storage: presence, download, and per-file integrity. The
 * multi-file sibling of [ModelStorePort], with the same discipline
 * (`contracts/DATA_LAYER_RULES.md` §4): each file downloads to a `.part`
 * temp, is SHA-256 verified against its pinned digest, and is renamed into
 * place atomically. Readiness is all-or-nothing — a partial set is never
 * [ModelState.Ready] and never reaches the engine.
 */
interface ParakeetModelStorePort {

    /** Current lifecycle state of the whole file set. */
    val state: StateFlow<ModelState>

    /**
     * Makes the full set ready if possible: verifies the on-disk files, or
     * downloads + verifies + finalizes the missing ones. Idempotent and safe
     * to call concurrently; on return the state is [ModelState.Ready] or
     * [ModelState.Failed] carrying the failure reason.
     */
    suspend fun ensureModel()

    /**
     * Absolute paths of the model set **after** every file's SHA-256 has
     * been verified, or null when no verified complete set is available. A
     * file that fails verification is deleted and null returned.
     */
    suspend fun verifiedModelFiles(): ParakeetModelFiles?
}
