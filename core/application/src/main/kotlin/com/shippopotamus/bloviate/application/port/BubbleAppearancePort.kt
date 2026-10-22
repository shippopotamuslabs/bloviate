package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted bubble appearance (ENC-68): opacity, size,
 * silhouette, and tint. Both the overlay service and the Setup screen
 * read this preference live — an edit in Setup restyles the already-
 * showing bubble on the next emission, with no service restart and
 * nothing captured at startup.
 */
interface BubbleAppearancePort {

    /** The current appearance; [BubbleAppearance.DEFAULT] until changed. */
    val appearance: StateFlow<BubbleAppearance>

    /** Persists [appearance] as the bubble's look. */
    suspend fun save(appearance: BubbleAppearance)
}
