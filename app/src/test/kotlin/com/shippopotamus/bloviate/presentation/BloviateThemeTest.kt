package com.shippopotamus.bloviate.presentation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Palette Gospel is canonical by decree (ENC-85): these assertions
 * pin the exact written-spec hexes so a future "close enough" edit
 * fails loudly instead of drifting the brand one shade at a time.
 */
class BloviateThemeTest {

    @Test
    fun `background and surface tokens carry the gospel hexes`() {
        assertEquals(Color(0xFF0D0E11), BloviatePalette.BgBase)
        assertEquals(Color(0xFF111216), BloviatePalette.BgSubtle)
        assertEquals(Color(0xFF181A20), BloviatePalette.Surface1)
        assertEquals(Color(0xFF1F222A), BloviatePalette.Surface2)
        assertEquals(Color(0xFF272A33), BloviatePalette.Surface3)
    }

    @Test
    fun `border tokens carry the gospel hexes`() {
        assertEquals(Color(0xFF2A2E39), BloviatePalette.BorderDefault)
        assertEquals(Color(0xFF343A48), BloviatePalette.BorderStrong)
        assertEquals(Color(0xFF20222E), BloviatePalette.BorderSubtle)
    }

    @Test
    fun `text tokens carry the gospel hexes`() {
        assertEquals(Color(0xFFEDEEF0), BloviatePalette.TextPrimary)
        assertEquals(Color(0xFFC5C7DD), BloviatePalette.TextHigh)
        assertEquals(Color(0xFFA1A5B0), BloviatePalette.TextSecondary)
        assertEquals(Color(0xFF858994), BloviatePalette.TextTertiary)
        assertEquals(Color(0xFF6E7280), BloviatePalette.TextFaint)
    }

    @Test
    fun `accent tokens carry the gospel hexes`() {
        assertEquals(Color(0xFF7A7CFF), BloviatePalette.Accent)
        assertEquals(Color(0xFF8D8FFF), BloviatePalette.AccentHover)
        assertEquals(Color(0xFF5F62B9), BloviatePalette.AccentActive)
        assertEquals(Color(0xFF4B4EBF), BloviatePalette.AccentBorderSubtle)
        assertEquals(Color(0xFF3B3BAA), BloviatePalette.AccentDeep)
        assertEquals(Color(0x267A7CFF), BloviatePalette.AccentMutedBg)
        assertEquals(Color(0x667A7CFF), BloviatePalette.FocusRing)
    }

    @Test
    fun `semantic tokens carry the written-spec hexes, not the generated image's`() {
        assertEquals(Color(0xFF7DBE8C), BloviatePalette.Success)
        assertEquals(Color(0xFFD9B07A), BloviatePalette.Warning)
        assertEquals(Color(0xFFE07A7A), BloviatePalette.Danger)
        assertEquals(Color(0xFF7BB5D6), BloviatePalette.Info)
    }

    @Test
    fun `mobile extra tokens carry the gospel values`() {
        assertEquals(Color(0xCC0D0E11), BloviatePalette.Scrim)
        assertEquals(Color(0xF21F222A), BloviatePalette.SurfaceOverlay)
    }

    @Test
    fun `derived containers are the documented alpha composites`() {
        // Accent at 30% over BgBase; semantics at 16% over Surface1.
        assertEquals(Color(0xFF2E2F58), BloviatePalette.PrimaryContainer)
        assertEquals(Color(0xFF283431), BloviatePalette.SuccessContainer)
        assertEquals(Color(0xFF37322E), BloviatePalette.WarningContainer)
        assertEquals(Color(0xFF38292E), BloviatePalette.DangerContainer)
        assertEquals(Color(0xFF28333D), BloviatePalette.InfoContainer)
    }

    @Test
    fun `the scheme maps material roles onto the gospel`() {
        val scheme = BloviateDarkColorScheme
        assertEquals(BloviatePalette.Accent, scheme.primary)
        // The higher-contrast on-accent foreground: bg-base at 5.66:1
        // beats white at 3.41:1 on #7A7CFF (ENC-85).
        assertEquals(BloviatePalette.BgBase, scheme.onPrimary)
        assertEquals(BloviatePalette.PrimaryContainer, scheme.primaryContainer)
        assertEquals(BloviatePalette.TextHigh, scheme.onPrimaryContainer)
        assertEquals(BloviatePalette.BgBase, scheme.background)
        assertEquals(BloviatePalette.TextPrimary, scheme.onBackground)
        assertEquals(BloviatePalette.Surface1, scheme.surface)
        assertEquals(BloviatePalette.TextPrimary, scheme.onSurface)
        assertEquals(BloviatePalette.TextSecondary, scheme.onSurfaceVariant)
        assertEquals(BloviatePalette.BorderDefault, scheme.outline)
        assertEquals(BloviatePalette.BorderSubtle, scheme.outlineVariant)
    }

    @Test
    fun `the scheme container ladder climbs the gospel lightness steps`() {
        val scheme = BloviateDarkColorScheme
        assertEquals(BloviatePalette.BgBase, scheme.surfaceContainerLowest)
        assertEquals(BloviatePalette.BgSubtle, scheme.surfaceContainerLow)
        assertEquals(BloviatePalette.Surface1, scheme.surfaceContainer)
        assertEquals(BloviatePalette.Surface2, scheme.surfaceContainerHigh)
        assertEquals(BloviatePalette.Surface3, scheme.surfaceContainerHighest)
        assertEquals(BloviatePalette.Surface2, scheme.surfaceVariant)
    }

    @Test
    fun `the scheme carries success on tertiary and danger on error`() {
        val scheme = BloviateDarkColorScheme
        assertEquals(BloviatePalette.Success, scheme.tertiary)
        assertEquals(BloviatePalette.SuccessContainer, scheme.tertiaryContainer)
        assertEquals(BloviatePalette.Success, scheme.onTertiaryContainer)
        assertEquals(BloviatePalette.Danger, scheme.error)
        assertEquals(BloviatePalette.DangerContainer, scheme.errorContainer)
        assertEquals(BloviatePalette.Danger, scheme.onErrorContainer)
    }

    @Test
    fun `semantic colors are the palette tokens with dark foregrounds`() {
        val semantic = GospelExtendedColors.semantic
        assertEquals(BloviatePalette.Success, semantic.success)
        assertEquals(BloviatePalette.SuccessContainer, semantic.successContainer)
        assertEquals(BloviatePalette.Warning, semantic.warning)
        assertEquals(BloviatePalette.WarningContainer, semantic.warningContainer)
        assertEquals(BloviatePalette.Danger, semantic.danger)
        assertEquals(BloviatePalette.DangerContainer, semantic.dangerContainer)
        assertEquals(BloviatePalette.Info, semantic.info)
        assertEquals(BloviatePalette.InfoContainer, semantic.infoContainer)
        assertEquals(BloviatePalette.BgBase, semantic.onSuccess)
        assertEquals(BloviatePalette.BgBase, semantic.onWarning)
        assertEquals(BloviatePalette.BgBase, semantic.onDanger)
        assertEquals(BloviatePalette.BgBase, semantic.onInfo)
    }

    @Test
    fun `spacing scale is exactly the gospel rhythm`() {
        // Base 4dp, major grid 8dp; off-rhythm values are banned in
        // presentation code, so the object carries only these (ENC-86).
        assertEquals(4.dp, BloviateSpacing.s4)
        assertEquals(8.dp, BloviateSpacing.s8)
        assertEquals(12.dp, BloviateSpacing.s12)
        assertEquals(16.dp, BloviateSpacing.s16)
        assertEquals(24.dp, BloviateSpacing.s24)
        assertEquals(32.dp, BloviateSpacing.s32)
        assertEquals(48.dp, BloviateSpacing.s48)
        assertEquals(64.dp, BloviateSpacing.s64)
    }

    @Test
    fun `shape scale is the gospel radius ladder and the pill is a full capsule`() {
        val scale = BloviateShapes.Scale
        assertEquals(RoundedCornerShape(4.dp), scale.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), scale.small)
        assertEquals(RoundedCornerShape(12.dp), scale.medium)
        assertEquals(RoundedCornerShape(16.dp), scale.large)
        assertEquals(RoundedCornerShape(28.dp), scale.extraLarge)
        assertEquals(CircleShape, BloviateShapes.Pill)
    }

    @Test
    fun `display and headline styles carry the gospel sizes at regular weight`() {
        val type = BloviateTypography
        assertStyle(type.displayLarge, size = 57, lineHeight = 64, weight = FontWeight.Normal)
        assertStyle(type.displayMedium, size = 45, lineHeight = 52, weight = FontWeight.Normal)
        assertStyle(type.displaySmall, size = 36, lineHeight = 44, weight = FontWeight.Normal)
        assertStyle(type.headlineLarge, size = 32, lineHeight = 40, weight = FontWeight.Normal)
        assertStyle(type.headlineMedium, size = 28, lineHeight = 36, weight = FontWeight.Normal)
        assertStyle(type.headlineSmall, size = 24, lineHeight = 32, weight = FontWeight.Normal)
        assertEquals((-0.25).sp, type.displayLarge.letterSpacing)
    }

    @Test
    fun `title styles carry the gospel sizes, titleLarge emphasized SemiBold`() {
        val type = BloviateTypography
        // The gospel's headline deviation from stock M3: titles are
        // SemiBold (600), never the stock Regular.
        assertStyle(type.titleLarge, size = 22, lineHeight = 28, weight = FontWeight.SemiBold)
        assertStyle(type.titleMedium, size = 16, lineHeight = 24, weight = FontWeight.Medium)
        assertStyle(type.titleSmall, size = 14, lineHeight = 20, weight = FontWeight.Medium)
    }

    @Test
    fun `body styles carry the gospel sizes, bodySmall letter-spacing plus 0_15`() {
        val type = BloviateTypography
        assertStyle(type.bodyLarge, size = 16, lineHeight = 24, weight = FontWeight.Normal)
        assertStyle(type.bodyMedium, size = 14, lineHeight = 20, weight = FontWeight.Normal)
        assertStyle(type.bodySmall, size = 12, lineHeight = 16, weight = FontWeight.Normal)
        assertEquals(0.15.sp, type.bodySmall.letterSpacing)
    }

    @Test
    fun `label styles carry the gospel sizes at Medium, labelLarge is the button label`() {
        val type = BloviateTypography
        assertStyle(type.labelLarge, size = 14, lineHeight = 20, weight = FontWeight.Medium)
        assertStyle(type.labelMedium, size = 12, lineHeight = 16, weight = FontWeight.Medium)
        assertStyle(type.labelSmall, size = 11, lineHeight = 16, weight = FontWeight.Medium)
    }

    @Test
    fun `no type style uses a Light weight on dark`() {
        // The gospel's astigmatism rule: never weight 300 on dark.
        val type = BloviateTypography
        val styles = listOf(
            type.displayLarge, type.displayMedium, type.displaySmall,
            type.headlineLarge, type.headlineMedium, type.headlineSmall,
            type.titleLarge, type.titleMedium, type.titleSmall,
            type.bodyLarge, type.bodyMedium, type.bodySmall,
            type.labelLarge, type.labelMedium, type.labelSmall,
        )
        styles.forEach { style ->
            assertNotEquals(FontWeight.Light, style.fontWeight)
            assertNotEquals(FontWeight.ExtraLight, style.fontWeight)
            assertNotEquals(FontWeight.Thin, style.fontWeight)
        }
    }

    @Test
    fun `switch off-state pairings separate thumb, rim, and card (ENC-88)`() {
        // The bug: stock M3 switch colors on this scheme paired the
        // outline thumb with the surfaceVariant track — pinned here so
        // the regression stays visible as a number, not a vibe.
        assertTrue(contrast(BloviatePalette.BorderDefault, BloviatePalette.Surface2) < 1.5f)
        // The gospel pairings bloviateSwitchColors() is built from:
        // the knob must read against its track (WCAG 4.5:1), and the
        // rim must read against BOTH backgrounds Setup presents
        // (non-text graphics floor, 3:1).
        assertTrue(contrast(BloviatePalette.TextPrimary, BloviatePalette.BorderDefault) >= 4.5f)
        assertTrue(contrast(BloviatePalette.TextTertiary, BloviatePalette.Surface1) >= 3.0f)
        assertTrue(contrast(BloviatePalette.TextTertiary, BloviatePalette.BgBase) >= 3.0f)
    }

    @Test
    fun `switch on-state pairing keeps the settled on-accent contrast`() {
        assertTrue(contrast(BloviatePalette.BgBase, BloviatePalette.Accent) >= 4.5f)
    }

    /** WCAG contrast ratio between two opaque colors. */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun assertStyle(style: TextStyle, size: Int, lineHeight: Int, weight: FontWeight) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(lineHeight.sp, style.lineHeight)
        assertEquals(weight, style.fontWeight)
    }

    @Test
    fun `the theme motion scheme is the expressive scheme (ENC-90)`() {
        // The theme's motion source is the library's expressive scheme
        // itself, not a hand-rolled copy: if a future edit swaps in
        // standard() or a local scheme, this pin fails by identity.
        assertSame(MotionScheme.expressive(), BloviateMotionScheme)

        // And its signature spring is the expressive one: default
        // spatial = damping 0.8 / stiffness 380, against the standard
        // scheme's 0.9 / 700 (the two schemes' token values).
        val expressive = BloviateMotionScheme.defaultSpatialSpec<Float>() as SpringSpec<Float>
        assertEquals(0.8f, expressive.dampingRatio, 0f)
        assertEquals(380f, expressive.stiffness, 0f)
        val standard = MotionScheme.standard().defaultSpatialSpec<Float>() as SpringSpec<Float>
        assertEquals(0.9f, standard.dampingRatio, 0f)
        assertEquals(700f, standard.stiffness, 0f)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun `morph vocabulary is the library polygon presets (ENC-90)`() {
        // BloviateMorphShapes re-exports the material3 presets by
        // identity — the design system morphs with the library's
        // polygons, never local lookalikes.
        assertEquals(MaterialShapes.Circle, BloviateMorphShapes.Circle)
        assertEquals(MaterialShapes.Pill, BloviateMorphShapes.Pill)
        assertEquals(MaterialShapes.Cookie4Sided, BloviateMorphShapes.Cookie4Sided)
        assertEquals(MaterialShapes.Clover4Leaf, BloviateMorphShapes.Clover4Leaf)
        assertEquals(MaterialShapes.Arch, BloviateMorphShapes.Arch)
        assertNotEquals(BloviateMorphShapes.Circle, BloviateMorphShapes.Pill)
    }

    @Test
    fun `extended colors expose the tokens material has no role for`() {
        val extended = GospelExtendedColors
        assertEquals(BloviatePalette.BgSubtle, extended.bgSubtle)
        assertEquals(BloviatePalette.TextHigh, extended.textHigh)
        assertEquals(BloviatePalette.TextTertiary, extended.textTertiary)
        assertEquals(BloviatePalette.TextFaint, extended.textFaint)
        assertEquals(BloviatePalette.BorderStrong, extended.borderStrong)
        assertEquals(BloviatePalette.AccentHover, extended.accentHover)
        assertEquals(BloviatePalette.AccentActive, extended.accentActive)
        assertEquals(BloviatePalette.AccentDeep, extended.accentDeep)
        assertEquals(BloviatePalette.Scrim, extended.scrim)
        assertEquals(BloviatePalette.SurfaceOverlay, extended.surfaceOverlay)
    }
}
