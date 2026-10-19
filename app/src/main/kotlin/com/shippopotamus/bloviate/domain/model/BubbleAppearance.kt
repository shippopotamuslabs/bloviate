package com.shippopotamus.bloviate.domain.model

/** Bubble size steps (ENC-68). The dp each step renders at lives in `BubbleAppearanceMath`. */
enum class BubbleSize {
    SMALL,
    MEDIUM,
    LARGE,
}

/**
 * Bubble silhouettes (ENC-68): the gospel's circle and pill, its rounded
 * square (squircle), and the M3 Expressive morph polygons adopted in
 * ENC-90 (cookie, clover, arch). The Compose shape each name renders as
 * is a presentation mapping; the domain only names the choice.
 */
enum class BubbleShape {
    CIRCLE,
    PILL,
    SQUIRCLE,
    COOKIE,
    CLOVER,
    ARCH,
}

/**
 * Bubble tints (ENC-68): palette-true choices only — the gospel's indigo
 * accent treatment (the default, today's look), a neutral surface tint,
 * and the four semantic tones (§1 of `contracts/UI_DESIGN_SPEC.md`).
 * There is deliberately no freeform color picker: every tint resolves to
 * named `BloviatePalette` tokens in the presentation mapping.
 */
enum class BubbleTint {
    INDIGO,
    NEUTRAL,
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
}

/**
 * The user's bubble appearance (ENC-68): opacity, size, silhouette, and
 * tint, persisted as schema v1 in the shared preferences DataStore (see
 * `contracts/DATA_LAYER_RULES.md` §2). [DEFAULT] is exactly the look the
 * bubble shipped with before this preference existed — full opacity,
 * medium (56dp), circle, indigo — so an absent record changes nothing.
 *
 * Opacity is bounded to [MIN_OPACITY]..[MAX_OPACITY]: below the floor the
 * bubble would be too faint to find against a busy host app, which is a
 * discoverability failure, not a style. The bounds are enforced at the
 * persistence mapper and again in `BubbleAppearanceMath`, so no stored
 * or passed value can render the bubble invisible.
 */
data class BubbleAppearance(
    val opacity: Float = MAX_OPACITY,
    val size: BubbleSize = BubbleSize.MEDIUM,
    val shape: BubbleShape = BubbleShape.CIRCLE,
    val tint: BubbleTint = BubbleTint.INDIGO,
) {

    companion object {

        /** The pre-ENC-68 shipping look; an absent or invalidated record reads as this. */
        val DEFAULT: BubbleAppearance = BubbleAppearance()

        /** Faintest the bubble may render (40%) — below this it is lost against host apps. */
        const val MIN_OPACITY: Float = 0.4f

        /** Fully opaque. */
        const val MAX_OPACITY: Float = 1f
    }
}
