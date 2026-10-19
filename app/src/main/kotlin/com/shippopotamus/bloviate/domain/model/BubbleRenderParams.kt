package com.shippopotamus.bloviate.domain.model

/**
 * Concrete rendering geometry for one [BubbleAppearance], computed by
 * `BubbleAppearanceMath` (ENC-68). Sizes are dp magnitudes as plain
 * floats — the domain stays free of Compose unit types, and the overlay
 * converts at the composition edge. The recording bar inherits the
 * bubble's size step: its height, slots, and glyphs scale from the same
 * factor so the morph between bubble and bar stays one object changing
 * shape, not two objects trading places.
 */
data class BubbleRenderParams(
    /** Rendered opacity, already coerced into the appearance's legal range. */
    val opacity: Float,
    /** The idle/transient bubble's square extent. */
    val bubbleSizeDp: Float,
    /** The mic/check glyph inside the bubble. */
    val bubbleIconDp: Float,
    /** The recording + processing bar height. */
    val barHeightDp: Float,
    /** The bar's cancel/send slot extent — never below the 48dp touch-target law. */
    val barSlotDp: Float,
    /** The cancel/send glyphs inside their slots. */
    val barIconDp: Float,
    /** The accent send circle's extent. */
    val sendCircleDp: Float,
    /** The check glyph inside the send circle. */
    val sendIconDp: Float,
    /** The waveform canvas height. */
    val waveformHeightDp: Float,
)
