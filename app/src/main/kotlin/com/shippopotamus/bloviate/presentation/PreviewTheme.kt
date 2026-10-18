package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Theme wrapper for `@Preview` composables across the presentation layer:
 * follows the system dark-mode setting so one preview declaration pair
 * covers Light + Dark. Previews never reference Hilt or real ViewModels
 * (contracts/UI_DESIGN_SPEC.md §4).
 */
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
