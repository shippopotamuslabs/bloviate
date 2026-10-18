package com.shippopotamus.bloviate.domain.model

/**
 * Persisted bubble anchor: the bubble's top-left corner as fractions of its
 * travel range on each axis (0 = min edge, 1 = max edge).
 *
 * Storing fractions of the travel range — rather than pixels or fractions of
 * the screen — is what makes the value survive rotation and fold/unfold:
 * denormalizing into the new bounds always lands fully on-screen after
 * clamping. See `contracts/DATA_LAYER_RULES.md` §1 — schema v1.
 */
data class BubblePosition(
    val xFraction: Float,
    val yFraction: Float,
) {

    companion object {

        /** First-run anchor: right edge, just above where the keyboard rests. */
        val DEFAULT: BubblePosition = BubblePosition(xFraction = 0.86f, yFraction = 0.68f)
    }
}
