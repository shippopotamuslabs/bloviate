package com.shippopotamus.bloviate.application.port

/** Platform check for the "draw over other apps" permission the bubble window needs. */
interface BubbleOverlayAccessPort {

    fun canDrawOverlays(): Boolean
}
