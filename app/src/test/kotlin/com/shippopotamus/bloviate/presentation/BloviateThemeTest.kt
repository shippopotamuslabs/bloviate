package com.shippopotamus.bloviate.presentation

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
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
