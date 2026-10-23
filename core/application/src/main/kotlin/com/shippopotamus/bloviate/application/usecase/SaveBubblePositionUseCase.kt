package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.application.port.BubblePositionPort
import javax.inject.Inject

/** Persists the bubble position after a drag ends. */
class SaveBubblePositionUseCase @Inject constructor(
    private val bubblePositionPort: BubblePositionPort,
) {

    suspend operator fun invoke(position: BubblePosition) {
        bubblePositionPort.save(position)
    }
}
