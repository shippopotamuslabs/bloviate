package com.shippopotamus.bloviate.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.shippopotamus.bloviate.domain.model.BubbleTint

/**
 * The Compose face of a bubble tint (ENC-68): the one mapping from the
 * domain's tint names to colors, shared by the overlay (which renders
 * the real bubble) and the Setup screen (which renders the live preview)
 * so the preview can never promise a look the bubble doesn't wear. Every
 * color resolves to `BloviatePalette` tokens through the theme and the
 * gospel's extended colors — no hex lives here. The silhouette mapping
 * lives in `BubbleShapes.kt`.
 */

/** A tint's container/content pairing, mirroring the theme's container relationships. */
data class BubbleTintColors(
    val container: Color,
    val content: Color,
)

/**
 * This tint's colors. INDIGO is the shipping look (the M3 container
 * pairing); NEUTRAL is a surface tint with primary text; the semantic
 * tints pair each tone's §1 container with the tone itself, exactly as
 * the theme pairs `tertiaryContainer` with `tertiary`.
 */
@Composable
@ReadOnlyComposable
fun BubbleTint.tintColors(): BubbleTintColors {
    val semantic = BloviateColors.extended.semantic
    return when (this) {
        BubbleTint.INDIGO -> BubbleTintColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        BubbleTint.NEUTRAL -> BubbleTintColors(
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurface,
        )
        BubbleTint.SUCCESS -> BubbleTintColors(
            container = semantic.successContainer,
            content = semantic.success,
        )
        BubbleTint.WARNING -> BubbleTintColors(
            container = semantic.warningContainer,
            content = semantic.warning,
        )
        BubbleTint.DANGER -> BubbleTintColors(
            container = semantic.dangerContainer,
            content = semantic.danger,
        )
        BubbleTint.INFO -> BubbleTintColors(
            container = semantic.infoContainer,
            content = semantic.info,
        )
    }
}
