package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The Shippopotamus canonical palette — the "Palette Gospel", declared
 * canonical for all Shippopotamus apps by Sean on 2026-10-19 and adopted
 * here in ENC-85. Every hex the app screens and the bubble overlay are
 * allowed to wear lives in this file as a named token
 * (contracts/UI_DESIGN_SPEC.md §1); where any mockup or generated image
 * disagrees with these values, these values win.
 *
 * The gospel's rules, encoded: no pure black/white (halation) — the
 * canvas is #0D0E11 and primary text #EDEEF0 (15.8:1); elevation is
 * lightness steps, never shadows; accents are desaturated so they don't
 * vibrate on dark. Container tints are exact alpha composites of one
 * token over another (derivation noted per token) so overlay surfaces
 * stay opaque over other apps.
 */
object BloviatePalette {
    // Background layers.
    val BgBase = Color(0xFF0D0E11)
    val BgSubtle = Color(0xFF111216)
    val Surface1 = Color(0xFF181A20)
    val Surface2 = Color(0xFF1F222A)
    val Surface3 = Color(0xFF272A33)

    // Borders / dividers.
    val BorderDefault = Color(0xFF2A2E39)
    val BorderStrong = Color(0xFF343A48)
    val BorderSubtle = Color(0xFF20222E)

    // Text layers.
    val TextPrimary = Color(0xFFEDEEF0)
    val TextHigh = Color(0xFFC5C7DD)
    val TextSecondary = Color(0xFFA1A5B0)
    val TextTertiary = Color(0xFF858994)
    val TextFaint = Color(0xFF6E7280)

    // Indigo accent family (primary action).
    val Accent = Color(0xFF7A7CFF)
    val AccentHover = Color(0xFF8D8FFF)
    val AccentActive = Color(0xFF5F62B9)
    val AccentBorderSubtle = Color(0xFF4B4EBF)
    val AccentDeep = Color(0xFF3B3BAA)
    val AccentMutedBg = Color(0x267A7CFF) // Accent at 15% alpha.
    val FocusRing = Color(0x667A7CFF) // Accent at 40% alpha.

    // Semantic colors (desaturated, per the gospel).
    val Success = Color(0xFF7DBE8C)
    val Warning = Color(0xFFD9B07A)
    val Danger = Color(0xFFE07A7A)
    val Info = Color(0xFF7BB5D6)

    // Mobile extras.
    val Scrim = Color(0xCC0D0E11) // BgBase at 80% alpha.
    val SurfaceOverlay = Color(0xF21F222A) // Surface2 at ~95% alpha.

    // Derived containers: exact alpha composites, kept opaque so the
    // bubble overlay never turns translucent over another app.
    val PrimaryContainer = Color(0xFF2E2F58) // Accent at 30% over BgBase.
    val SuccessContainer = Color(0xFF283431) // Success at 16% over Surface1.
    val WarningContainer = Color(0xFF37322E) // Warning at 16% over Surface1.
    val DangerContainer = Color(0xFF38292E) // Danger at 16% over Surface1.
    val InfoContainer = Color(0xFF28333D) // Info at 16% over Surface1.
}

/**
 * The gospel's semantic colors as first-class theme colors (ENC-85):
 * M3's roles carry success on tertiary and danger on error, but warning
 * and info have no M3 home, so all four live here with their containers
 * and foregrounds. Foregrounds are [BloviatePalette.BgBase] throughout —
 * computed contrast against every semantic beats white by 2–4×
 * (success 8.84:1, warning 9.60:1, danger 6.65:1, info 8.66:1).
 */
data class BloviateSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDanger: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfo: Color,
)

/**
 * Gospel tokens with no Material 3 role (text tiers beyond secondary,
 * the strong border, accent states, scrim/overlay, the tab-bar tint) —
 * exposed next to the scheme so components never reach for a hex.
 */
data class BloviateExtendedColors(
    val bgSubtle: Color,
    val textHigh: Color,
    val textTertiary: Color,
    val textFaint: Color,
    val borderStrong: Color,
    val accentHover: Color,
    val accentActive: Color,
    val accentBorderSubtle: Color,
    val accentDeep: Color,
    val accentMutedBg: Color,
    val focusRing: Color,
    val scrim: Color,
    val surfaceOverlay: Color,
    val semantic: BloviateSemanticColors,
)

internal val GospelSemanticColors = BloviateSemanticColors(
    success = BloviatePalette.Success,
    successContainer = BloviatePalette.SuccessContainer,
    onSuccess = BloviatePalette.BgBase,
    warning = BloviatePalette.Warning,
    warningContainer = BloviatePalette.WarningContainer,
    onWarning = BloviatePalette.BgBase,
    danger = BloviatePalette.Danger,
    dangerContainer = BloviatePalette.DangerContainer,
    onDanger = BloviatePalette.BgBase,
    info = BloviatePalette.Info,
    infoContainer = BloviatePalette.InfoContainer,
    onInfo = BloviatePalette.BgBase,
)

internal val GospelExtendedColors = BloviateExtendedColors(
    bgSubtle = BloviatePalette.BgSubtle,
    textHigh = BloviatePalette.TextHigh,
    textTertiary = BloviatePalette.TextTertiary,
    textFaint = BloviatePalette.TextFaint,
    borderStrong = BloviatePalette.BorderStrong,
    accentHover = BloviatePalette.AccentHover,
    accentActive = BloviatePalette.AccentActive,
    accentBorderSubtle = BloviatePalette.AccentBorderSubtle,
    accentDeep = BloviatePalette.AccentDeep,
    accentMutedBg = BloviatePalette.AccentMutedBg,
    focusRing = BloviatePalette.FocusRing,
    scrim = BloviatePalette.Scrim,
    surfaceOverlay = BloviatePalette.SurfaceOverlay,
    semantic = GospelSemanticColors,
)

val LocalBloviateExtendedColors = compositionLocalOf { GospelExtendedColors }

/** Accessor for the gospel's non-M3 tokens: `BloviateColors.extended`. */
object BloviateColors {
    val extended: BloviateExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBloviateExtendedColors.current
}

/**
 * Material 3 roles mapped deliberately onto the gospel (ENC-85):
 * background = bg-base (the canvas), surface = surface-1 (cards and
 * sheets), the surfaceContainer ladder climbs bg-subtle → surface-1 →
 * surface-2 → surface-3 in gospel order, borders come from the border
 * tokens, and primary is the indigo accent. The on-accent foreground is
 * bg-base: on #7A7CFF it computes to 5.66:1, against white's 3.41:1 and
 * text-primary's 2.94:1 (the same dark foreground wins on every semantic
 * color; only accent-active flips, and M3 has no separate pressed-state
 * foreground, so the base accent governs).
 */
internal val BloviateDarkColorScheme = darkColorScheme(
    primary = BloviatePalette.Accent,
    onPrimary = BloviatePalette.BgBase,
    primaryContainer = BloviatePalette.PrimaryContainer,
    onPrimaryContainer = BloviatePalette.TextHigh,
    secondary = BloviatePalette.AccentHover,
    onSecondary = BloviatePalette.BgBase,
    secondaryContainer = BloviatePalette.PrimaryContainer,
    onSecondaryContainer = BloviatePalette.TextHigh,
    tertiary = BloviatePalette.Success,
    onTertiary = BloviatePalette.BgBase,
    tertiaryContainer = BloviatePalette.SuccessContainer,
    onTertiaryContainer = BloviatePalette.Success,
    error = BloviatePalette.Danger,
    onError = BloviatePalette.BgBase,
    errorContainer = BloviatePalette.DangerContainer,
    onErrorContainer = BloviatePalette.Danger,
    background = BloviatePalette.BgBase,
    onBackground = BloviatePalette.TextPrimary,
    surface = BloviatePalette.Surface1,
    onSurface = BloviatePalette.TextPrimary,
    surfaceVariant = BloviatePalette.Surface2,
    onSurfaceVariant = BloviatePalette.TextSecondary,
    surfaceContainerLowest = BloviatePalette.BgBase,
    surfaceContainerLow = BloviatePalette.BgSubtle,
    surfaceContainer = BloviatePalette.Surface1,
    surfaceContainerHigh = BloviatePalette.Surface2,
    surfaceContainerHighest = BloviatePalette.Surface3,
    outline = BloviatePalette.BorderDefault,
    outlineVariant = BloviatePalette.BorderSubtle,
    inverseSurface = BloviatePalette.TextPrimary,
    inverseOnSurface = BloviatePalette.BgBase,
    inversePrimary = BloviatePalette.AccentDeep,
    scrim = BloviatePalette.BgBase,
)

/**
 * The one app theme: a fixed dark Material 3 scheme carrying the
 * canonical palette (ENC-85; dark-only since ENC-75) — no light variant
 * and no dynamic color, so every screen and the bubble overlay read the
 * same on every device.
 */
@Composable
fun BloviateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBloviateExtendedColors provides GospelExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = BloviateDarkColorScheme,
            content = content,
        )
    }
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
