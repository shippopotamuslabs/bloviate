package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * The Shippopotamus Compose Gospel's spacing scale (ENC-86): the ONLY
 * spacing source for the app screens and the shared dashboard
 * components. Base unit 4dp, major grid 8dp; the vertical rhythm is
 * 8 / 16 / 24 / 32 / 48 / 64 and off-rhythm values (5, 7, 10, 15, …)
 * are banned in presentation code — if a gap is not one of these, the
 * layout is wrong, not the scale. (Non-spacing geometry — icon glyph
 * sizes, border widths, the overlay's waveform measurements — is not
 * spacing and stays component-local; see UI_DESIGN_SPEC.md §6.)
 */
object BloviateSpacing {
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s48: Dp = 48.dp
    val s64: Dp = 64.dp
}

/**
 * The gospel's shape scale (ENC-86): shape is hierarchy — cards and
 * sheets step 4 / 8 / 12 / 16 / 28 by emphasis, and [Pill] is the full
 * capsule for buttons, the switch track, and the nav indicator. The
 * nested rule keeps inner corners honest: child radius = parent
 * radius − padding. Don't invent radii.
 */
object BloviateShapes {
    val Scale: Shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

    /** The full capsule (gospel "full"): buttons, toggle track, active nav indicator. */
    val Pill: Shape = CircleShape
}

/**
 * The gospel's type scale (ENC-86), the full M3 ramp with the gospel's
 * two deliberate deviations from stock M3 pinned: [Typography.titleLarge]
 * is SemiBold (emphasized titles/top bars — stock M3 is Regular) and
 * [Typography.bodySmall] letter-spacing is +0.15. The dark-mode
 * astigmatism rule is structural here: no style uses Light (300) —
 * body text is 400–500, emphasis 600.
 *
 * Font family: the gospel names "Shipopotamus Sans" with a Roboto Flex
 * fallback, but no such font asset exists yet, so the styles leave the
 * family unset — the platform default sans (Roboto Flex variable on
 * the target devices) carries the gospel's weights and scale. When the
 * brand font ships as an asset, it lands here and nowhere else.
 */
internal val BloviateTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

/**
 * The gospel switch colors, owned by the theme so every switch in the
 * app gets them (ENC-88). Geometry stays stock M3 (52×32 track, 24dp
 * checked thumb — the gospel's switch spec); colors are the fix: with
 * the stock M3 defaults on this scheme the OFF thumb was `outline`
 * (#2A2E39) on a `surfaceVariant` track (#1F222A) — a 1.17:1 pairing
 * that read as an empty pill on device. The gospel pairings separate
 * every layer:
 *
 * - OFF: text-primary thumb (#EDEEF0, 11.7:1 on the track) on the
 *   gospel's inactive track, border-default (#2A2E39), rimmed with
 *   text-tertiary (#858994 — 5.0:1 against the card surface, 5.5:1
 *   against bg-base) so the rim and the knob — not the track fill —
 *   carry the pill's edge on both backgrounds the Setup screen
 *   presents.
 * - ON: the gospel's active track (accent #7A7CFF) with the bg-base
 *   thumb — the settled on-accent foreground (5.66:1, ENC-85).
 */
@Composable
fun bloviateSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = BloviatePalette.BgBase,
    checkedTrackColor = BloviatePalette.Accent,
    checkedBorderColor = BloviatePalette.Accent,
    uncheckedThumbColor = BloviatePalette.TextPrimary,
    uncheckedTrackColor = BloviatePalette.BorderDefault,
    uncheckedBorderColor = BloviatePalette.TextTertiary,
)

/**
 * The one app theme: a fixed dark Material 3 scheme carrying the
 * canonical palette (ENC-85; dark-only since ENC-75) — no light variant
 * and no dynamic color, so every screen and the bubble overlay read the
 * same on every device. ENC-86 adds the Compose Gospel system on top:
 * the gospel type scale and shape scale, wired here.
 *
 * Staged (ENC-86, flagged for Sean): the M3 Expressive surface —
 * `MotionScheme.expressive()`, `MaterialExpressiveTheme`, and the
 * morphable polygon shapes. The classes exist inside the pinned
 * material3 1.4.0 artifact but are **internal** in its Kotlin metadata
 * (the compiler rejects them: "it is internal in file"), so they are
 * not public API in the pinned version and cannot be adopted without
 * a version bump — and this repo's pins are aligned with Enclave's
 * catalog on purpose, so the bump is a cross-repo decision. When the
 * bump lands, the expressive motion scheme slots in here and nowhere
 * else; stock components already animate on springs where M3
 * implements it.
 */
@Composable
fun BloviateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBloviateExtendedColors provides GospelExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = BloviateDarkColorScheme,
            shapes = BloviateShapes.Scale,
            typography = BloviateTypography,
            content = content,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Theme - Dark", showBackground = true)
@Composable
private fun BloviateThemePreview() {
    BloviateTheme {
        Card(modifier = Modifier.padding(BloviateSpacing.s16)) {
            Column(modifier = Modifier.padding(BloviateSpacing.s16)) {
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
