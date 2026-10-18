package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.port.BubblePositionPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the persisted bubble position (`null` until the user first drags the bubble). */
class ObserveBubblePositionUseCase @Inject constructor(
    private val bubblePositionPort: BubblePositionPort,
) {

    operator fun invoke(): Flow<BubblePosition?> = bubblePositionPort.position
}
