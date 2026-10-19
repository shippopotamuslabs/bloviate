package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Semantic tone for status surfaces across the dashboard screens
 * (ENC-75): a status is a word on a colored chip, never a sentence.
 * Tones map onto theme roles so screens never pick colors themselves.
 */
enum class StatusTone {
    /** Working / granted / ready — the green. */
    POSITIVE,

    /** Informational, nothing to do — the gray. */
    NEUTRAL,

    /** Blocked or failed; the user's next action lives here. */
    ATTENTION,

    /** Brand-tinted informational (e.g. "applied everywhere"). */
    ACCENT,
}

/** Background/foreground pair a [StatusTone] resolves to in the current theme. */
internal data class StatusColors(val container: Color, val content: Color)

@Composable
internal fun StatusTone.colors(): StatusColors =
    when (this) {
        StatusTone.POSITIVE -> StatusColors(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        StatusTone.NEUTRAL -> StatusColors(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusTone.ATTENTION -> StatusColors(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        StatusTone.ACCENT -> StatusColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

/** A compact status word in a rounded chip — the dashboard's unit of state (ENC-75). */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Chips - Dark", showBackground = true)
@Composable
private fun StatusChipPreview() {
    BloviateTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(text = "On", tone = StatusTone.POSITIVE)
            StatusChip(text = "Soon", tone = StatusTone.NEUTRAL)
            StatusChip(text = "Off", tone = StatusTone.ATTENTION)
            StatusChip(text = "AI", tone = StatusTone.ACCENT)
        }
    }
}
