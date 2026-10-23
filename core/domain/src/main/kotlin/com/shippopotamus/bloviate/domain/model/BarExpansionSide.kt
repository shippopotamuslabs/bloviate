package com.shippopotamus.bloviate.domain.model

/**
 * Which way a bar-form row unfolds from the bubble's anchor spot
 * (ENC-167). The side is a pure function of the anchor: an anchor whose
 * center sits left of the screen center becomes the row's cancel (X)
 * end and the row unfolds rightward; any other anchor becomes the
 * row's send (check) end and the row unfolds leftward. There is no
 * snap-to-edge or dock model — the anchor stays where the user left it.
 */
enum class BarExpansionSide {
    /** Anchor spot becomes the X; the row extends to the right of it. */
    EXPAND_RIGHT,

    /** Anchor spot becomes the check; the row extends to the left of it. */
    EXPAND_LEFT,
}
