package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/**
 * Semantic tone for status surfaces across the dashboard screens
 * (ENC-75): a status is a word on a colored chip, never a sentence.
 * Tones resolve to the canonical palette's semantic colors (ENC-85) so
 * screens never pick colors themselves.
 */
enum class StatusTone {
    /** Working / granted / ready — the success green. */
    POSITIVE,

    /** Informational, nothing to do — the gray. */
    NEUTRAL,

    /** Blocked until the user acts; the next action lives here — warning amber. */
    ATTENTION,

    /** Informational brand note (e.g. "applied everywhere") — info sky. */
    ACCENT,
}

/** Background/foreground pair a [StatusTone] resolves to in the current theme. */
internal data class StatusColors(val container: Color, val content: Color)

@Composable
internal fun StatusTone.colors(): StatusColors {
    val semantic = BloviateColors.extended.semantic
    return when (this) {
        StatusTone.POSITIVE -> StatusColors(
            container = semantic.successContainer,
            content = semantic.success,
        )
        StatusTone.NEUTRAL -> StatusColors(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusTone.ATTENTION -> StatusColors(
            container = semantic.warningContainer,
            content = semantic.warning,
        )
        StatusTone.ACCENT -> StatusColors(
            container = semantic.infoContainer,
            content = semantic.info,
        )
    }
}

/**
 * A compact status word in a chip — the dashboard's unit of state
 * (ENC-75). Geometry is the Compose Gospel's chip spec (ENC-86): 32dp
 * height, the small (8dp) shape, labelLarge text, s16 horizontal
 * padding.
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .height(BloviateSpacing.s32)
                .padding(horizontal = BloviateSpacing.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Chips - Dark", showBackground = true)
@Composable
private fun StatusChipPreview() {
    BloviateTheme {
        Row(
            modifier = Modifier.padding(BloviateSpacing.s16),
            horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        ) {
            StatusChip(text = "On", tone = StatusTone.POSITIVE)
            StatusChip(text = "Soon", tone = StatusTone.NEUTRAL)
            StatusChip(text = "Off", tone = StatusTone.ATTENTION)
            StatusChip(text = "AI", tone = StatusTone.ACCENT)
        }
    }
}
