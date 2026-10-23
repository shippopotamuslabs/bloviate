package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.application.port.BubblePositionPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hand-written fake of [BubblePositionPort]: in-memory value plus a record of every save. */
class FakeBubblePositionPort : BubblePositionPort {

    private val mutablePosition = MutableStateFlow<BubblePosition?>(null)

    override val position: Flow<BubblePosition?> = mutablePosition.asStateFlow()

    val savedPositions = mutableListOf<BubblePosition>()

    override suspend fun save(position: BubblePosition) {
        savedPositions += position
        mutablePosition.update { position }
    }
}
