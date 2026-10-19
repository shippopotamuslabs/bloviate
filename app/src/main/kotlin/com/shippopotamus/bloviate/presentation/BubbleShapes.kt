package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.presentation.BloviateMorphShapes.asShape

/**
 * The Compose face of a bubble silhouette (ENC-68): the one mapping from
 * the domain's shape names to Compose shapes, shared by the overlay and
 * the Setup preview. The tint mapping lives in `BubbleTintColors.kt`.
 */
/**
 * The bubble silhouette for this shape: the gospel's circle and pill,
 * its large rounded square (squircle), and the ENC-90 morph polygons.
 * The polygons are for the square bubble — see [barShape] for the wide
 * bar's reduced vocabulary.
 */
@Composable
fun BubbleShape.bubbleShape(): Shape =
    when (this) {
        BubbleShape.CIRCLE -> CircleShape
        BubbleShape.PILL -> BloviateShapes.Pill
        BubbleShape.SQUIRCLE -> BloviateShapes.Scale.large
        BubbleShape.COOKIE -> with(BloviateMorphShapes) { Cookie4Sided.asShape() }
        BubbleShape.CLOVER -> with(BloviateMorphShapes) { Clover4Leaf.asShape() }
        BubbleShape.ARCH -> with(BloviateMorphShapes) { Arch.asShape() }
    }

/**
 * The recording/processing bar's corner treatment for this shape
 * (ENC-68): the bar is a wide pill-family form, so the choice lands as
 * corner language, not silhouette — PILL is the true capsule, SQUIRCLE
 * lends its 16dp radius, and the polygon silhouettes (built for the
 * square bubble) fall back to the bar's shipping extraLarge radius.
 */
@Composable
@ReadOnlyComposable
fun BubbleShape.barShape(): Shape =
    when (this) {
        BubbleShape.PILL -> BloviateShapes.Pill
        BubbleShape.SQUIRCLE -> BloviateShapes.Scale.large
        BubbleShape.CIRCLE,
        BubbleShape.COOKIE,
        BubbleShape.CLOVER,
        BubbleShape.ARCH,
        -> MaterialTheme.shapes.extraLarge
    }
