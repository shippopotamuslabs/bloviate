package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.BubbleServiceStarterPort
import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin

/** Hand-written fake of [BubbleServiceStarterPort]: records requested origins, scripts the outcome. */
class FakeBubbleServiceStarterPort : BubbleServiceStarterPort {

    val requestedOrigins = mutableListOf<BubbleServiceStartOrigin>()

    var outcome: BubbleServiceStartOutcome = BubbleServiceStartOutcome.Started

    override fun startBubble(origin: BubbleServiceStartOrigin): BubbleServiceStartOutcome {
        requestedOrigins += origin
        return outcome
    }
}
