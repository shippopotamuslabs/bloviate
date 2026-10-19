package com.shippopotamus.bloviate.presentation

import androidx.compose.runtime.Composable

/**
 * Theme wrapper for `@Preview` composables across the presentation
 * layer: previews render the real app theme ([BloviateTheme] — dark
 * only since ENC-75), so a preview is a truthful picture of the shipped
 * screen. Previews never reference Hilt or real ViewModels
 * (contracts/UI_DESIGN_SPEC.md §4).
 */
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    BloviateTheme(content = content)
}
