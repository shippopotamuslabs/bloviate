package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.BubbleServiceStarterPort
import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.application.port.BubbleStartRequest

/** Hand-written fake of [BubbleServiceStarterPort]: records requested starts, scripts the outcome. */
class FakeBubbleServiceStarterPort : BubbleServiceStarterPort {

    val requestedRequests = mutableListOf<BubbleStartRequest>()

    var outcome: BubbleServiceStartOutcome = BubbleServiceStartOutcome.Started

    override fun startBubble(request: BubbleStartRequest): BubbleServiceStartOutcome {
        requestedRequests += request
        return outcome
    }
}
