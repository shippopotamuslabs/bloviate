package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * A grouped dashboard card (ENC-75): one card per section, rows inside
 * separated by [DashboardRow]'s dividers instead of a card per control
 * with its own paragraph. Container color is the theme's low surface so
 * cards sit one step above the screen background. Shape is the theme's
 * medium radius — 12dp per the Compose Gospel (ENC-86) — via the M3
 * Card default, so the gospel scale reaches the card from the theme.
 */
@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        content = content,
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Card - Dark", showBackground = true)
@Composable
private fun DashboardCardPreview() {
    BloviateTheme {
        DashboardCard(modifier = Modifier.padding(BloviateSpacing.s16)) {
            Column(modifier = Modifier.padding(BloviateSpacing.s16)) {
                Text(text = "Grouped rows live here.")
            }
        }
    }
}
