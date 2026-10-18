package com.shippopotamus.bloviate.domain.model

/** Bubble top-left corner in physical pixels. Pure display math, no framework types. */
data class BubblePixelPosition(
    val xPx: Int,
    val yPx: Int,
)
