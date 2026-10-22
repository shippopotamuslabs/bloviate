package com.shippopotamus.bloviate.domain.model

/**
 * Lifecycle of the on-device transcription model file, published by the model
 * store. Recording and transcription are available only in [Ready]; the load
 * gate itself is the store's hash verification (see `ModelStorePort`), so a
 * corrupt file can never reach the engine even if this state was optimistic.
 */
sealed interface ModelState {

    /** No model file is present in app-private storage. */
    data object Absent : ModelState

    /** A download is in flight; [progressFraction] is downloaded / total bytes, 0..1. */
    data class Downloading(val progressFraction: Float) : ModelState

    /** The model file is present (and verified before any load). */
    data object Ready : ModelState

    /**
     * The last ensure/download attempt failed. [reason] is a short
     * human-readable cause (exception kind + message, an HTTP status, or the
     * integrity/free-space outcome) — the model card shows it verbatim so a
     * failure on device is diagnosable without logcat.
     */
    data class Failed(val reason: String) : ModelState
}
