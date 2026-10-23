package com.shippopotamus.bloviate.application.port

/**
 * The published snapshot of the account-owned custom-styles cache
 * (ENC-199). [isLoaded] is false until the backing store has emitted its
 * persisted snapshot — consumers must not mistake an empty pre-load
 * cache for "the user has no custom styles".
 */
data class CustomStylesState(
    val styles: List<CustomStyleRecord>,
    val isLoaded: Boolean,
)
