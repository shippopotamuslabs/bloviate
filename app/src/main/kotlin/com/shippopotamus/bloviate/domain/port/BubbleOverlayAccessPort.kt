package com.shippopotamus.bloviate.domain.port

/** Platform check for the "draw over other apps" permission the bubble window needs. */
interface BubbleOverlayAccessPort {

    fun canDrawOverlays(): Boolean
}
