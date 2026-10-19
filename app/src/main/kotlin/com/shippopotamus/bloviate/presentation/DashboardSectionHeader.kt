package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * A dashboard section label (ENC-75): small-caps, letter-spaced, muted —
 * with an optional trailing slot (a count, in the mockup) so the header
 * carries the section's size without another line of prose.
 */
@Composable
fun DashboardSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = BloviateSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailingText != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Section header - Dark", showBackground = true)
@Composable
private fun DashboardSectionHeaderPreview() {
    BloviateTheme {
        DashboardSectionHeader(
            text = "Words",
            trailingText = "3",
            modifier = Modifier.padding(BloviateSpacing.s16),
        )
    }
}
