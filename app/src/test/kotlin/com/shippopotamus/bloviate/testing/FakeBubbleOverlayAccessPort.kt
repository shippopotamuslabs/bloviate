package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.BubbleOverlayAccessPort

/** Hand-written fake of [BubbleOverlayAccessPort] with a settable permission answer. */
class FakeBubbleOverlayAccessPort : BubbleOverlayAccessPort {

    var canDraw: Boolean = false

    override fun canDrawOverlays(): Boolean = canDraw
}
