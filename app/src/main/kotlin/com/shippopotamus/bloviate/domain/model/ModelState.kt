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

    /** The last ensure/download attempt failed; details are in the data-layer log. */
    data object Failed : ModelState
}
