package com.shippopotamus.bloviate.domain.model

/**
 * One touch sample in screen coordinates. A completed gesture is a list of
 * these from down to up, fed to the gesture classifier.
 */
data class PointerSample(
    val timeMs: Long,
    val xPx: Float,
    val yPx: Float,
)
