package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The header pill (ENC-75): a screen's whole verdict in one glance —
 * a colored dot + a short label on a tinted, outlined pill. Setup uses
 * it for "Ready to dictate" vs the first blocker; Style for the AI
 * connection state.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.container.copy(alpha = PILL_CONTAINER_ALPHA),
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.content.copy(alpha = PILL_BORDER_ALPHA)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color = colors.content, shape = CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private const val PILL_CONTAINER_ALPHA = 0.55f
private const val PILL_BORDER_ALPHA = 0.35f

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Pills - Dark", showBackground = true)
@Composable
private fun StatusPillPreview() {
    BloviateTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(text = "Ready to dictate", tone = StatusTone.POSITIVE)
            StatusPill(text = "Allow microphone", tone = StatusTone.ATTENTION)
        }
    }
}
