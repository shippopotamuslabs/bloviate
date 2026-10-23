package com.shippopotamus.bloviate.domain.model

/** Physical screen/window bounds in pixels, used for clamping the bubble. */
data class ScreenBounds(
    val widthPx: Int,
    val heightPx: Int,
)
