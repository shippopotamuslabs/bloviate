package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The one app theme (ENC-75 redesign): a fixed dark Material 3 scheme —
 * dark-only by Sean's direction, no light variant and no dynamic color,
 * so every screen reads the same on every device. Hex values live here
 * and nowhere else (contracts/UI_DESIGN_SPEC.md §1). The palette is the
 * mockup's: near-black background, one-step-lighter cards, violet
 * primary, green reserved for positive status (via the tertiary roles).
 */
private val BloviateDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C6CF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A2456),
    onPrimaryContainer = Color(0xFFCFC7FF),
    secondary = Color(0xFFA79BFF),
    onSecondary = Color(0xFF1D1838),
    secondaryContainer = Color(0xFF23232E),
    onSecondaryContainer = Color(0xFFC9C9D2),
    tertiary = Color(0xFF3ED598),
    onTertiary = Color(0xFF06281A),
    tertiaryContainer = Color(0xFF123B2A),
    onTertiaryContainer = Color(0xFF3ED598),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5A2326),
    onErrorContainer = Color(0xFFF5C6C2),
    background = Color(0xFF101017),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF23232E),
    onSurfaceVariant = Color(0xFF9B9BA6),
    surfaceContainerLowest = Color(0xFF0B0B10),
    surfaceContainerLow = Color(0xFF17171F),
    surfaceContainer = Color(0xFF1B1B25),
    surfaceContainerHigh = Color(0xFF21212C),
    surfaceContainerHighest = Color(0xFF2A2A35),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF23232E),
)

@Composable
fun BloviateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BloviateDarkColorScheme,
        content = content,
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Theme - Dark", showBackground = true)
@Composable
private fun BloviateThemePreview() {
    BloviateTheme {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Bloviate", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Dark-only dashboard theme.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
