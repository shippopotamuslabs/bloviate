package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.BubblePosition
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the user's chosen bubble position (normalized fractions —
 * see [BubblePosition]). Emits `null` when no position has been saved yet;
 * consumers fall back to [BubblePosition.DEFAULT].
 */
interface BubblePositionPort {

    val position: Flow<BubblePosition?>

    suspend fun save(position: BubblePosition)
}
