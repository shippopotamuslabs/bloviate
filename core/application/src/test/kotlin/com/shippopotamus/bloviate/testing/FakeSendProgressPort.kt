package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.SendProgressPort
import com.shippopotamus.bloviate.application.port.SendStage

/**
 * Hand-written fake of [SendProgressPort]: records every reported stage
 * in order, so pipeline tests can assert exactly when (and whether) the
 * polishing stage was announced.
 */
class FakeSendProgressPort : SendProgressPort {

    private val mutableStages = mutableListOf<SendStage>()

    /** Every stage reported so far, in order. */
    val stages: List<SendStage> get() = mutableStages.toList()

    override fun onStage(stage: SendStage) {
        mutableStages += stage
    }
}
