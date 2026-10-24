package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The Glass Bars Gospel's components (BLO-2;
 * contracts/UI_DESIGN_SPEC.md §6.9), written against the Haze 2.0 API
 * as shipped in 2.0.1.
 *
 * 2.0 API facts (grounded in the resolved artifact, not the
 * contract's 1.x sketch): there is no `hazeChild` and no `HazeStyle`
 * in 2.x. Content opts in with `Modifier.hazeSource(state)`; a bar
 * blurs it with `Modifier.hazeBlur(HazeInput.Backdrop(state), style)`
 * using the [ShippopotamusHazeStyle] tokens from `BloviateTheme.kt`.
 * The contract's `zIndex` note refers to the *source* side
 * (`hazeSource(zIndex = 0f)` — one source per screen, so the
 * default); the effect side takes no zIndex and draws in layout
 * order. The glass-effect count per frame stays at the §6.9 cap of
 * two haze children: the top bar and the bottom bar.
 *
 * State sharing: one [HazeState] per screen, shared by that screen's
 * top bar and the shell's bottom bar. The shell provides it through
 * [LocalScreenHazeState] (a separate state per screen is not
 * possible — an effect must reference the state holding its source),
 * so screens read it with [rememberScreenHazeState] instead of
 * threading a parameter through every route.
 */
val LocalScreenHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Whether the shell's glass bottom bar is currently composed (false
 * while the IME is up, and on standalone screens that have no shell
 * bar). Screens read this in [glassBottomBarClearance] so their
 * content clears the overlaid bar exactly while it is showing.
 */
val LocalGlassBottomBarVisible = compositionLocalOf { false }

/**
 * The screen's shared Haze state: the shell-provided one inside the
 * tab shell, a screen-local one everywhere else (standalone screens
 * and previews).
 */
@Composable
fun rememberScreenHazeState(): HazeState = LocalScreenHazeState.current ?: rememberHazeState()

/**
 * The bottom bar's canonical height (contracts/UI_DESIGN_SPEC.md
 * §6.5: navigation bar 80dp). Component geometry, not spacing: it
 * sizes the bar and the clearance screens reserve for it, so the two
 * can never drift apart.
 */
val ShippopotamusBottomBarHeight: Dp = 80.dp

/** The scrolled fraction that engages the top bar's glass (§6.9). */
private const val GLASS_ENGAGE_FRACTION = 0.01f

/** Preview-only scroll offset that puts a preview bar in the scrolled state. */
private const val SCROLLED_PREVIEW_OFFSET = 10

/**
 * The bottom clearance an in-shell screen reserves inside its scroll
 * content: the overlaid bar's height plus the system navigation-bar
 * inset, so the last row rests above the glass at rest yet scrolls
 * under it. Zero when no shell bar is showing — with the IME up the
 * bar is gone and `imePadding()` alone seats the content on the
 * keyboard (no dead gap), and standalone screens never had a bar.
 */
@Composable
fun glassBottomBarClearance(): Dp {
    if (!LocalGlassBottomBarVisible.current) return 0.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return ShippopotamusBottomBarHeight + navBottom
}

/**
 * The glass top bar (§6.9): transparent at rest, glass with a 1dp
 * hairline (border-default at 40%) once scrolled. The signature
 * mirrors [TopAppBar] (title / navigationIcon / actions slots) so
 * adopting it at a call site is mechanical: rename the callee and
 * pass the screen's [hazeState] and [scrollBehavior].
 *
 * The bar itself never draws the source — glass only, behind the
 * bar's own bounds. `hazeBlur` is attached only while scrolled, so
 * the resting bar is exactly the transparent `TopAppBar` it replaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippopotamusTopBar(
    title: @Composable () -> Unit,
    hazeState: HazeState,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isScrolled = scrollBehavior.state.overlappedFraction > GLASS_ENGAGE_FRACTION
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier
            .then(
                if (isScrolled) {
                    Modifier.hazeBlur(
                        input = HazeInput.Backdrop(hazeState),
                        style = ShippopotamusHazeTopBar.toHazeBlurStyle(),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (isScrolled) Modifier.hairlineBottom() else Modifier,
            ),
    )
}

/** The 1dp scrolled hairline: border-default at 40% under the glass bar. */
private fun Modifier.hairlineBottom(): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = BloviatePalette.BorderDefault.copy(alpha = 0.4f),
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}

/** One item in the glass bottom bar: fake presentation data, never a ViewModel. */
data class GlassBottomBarItem(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
)

/**
 * The glass bottom bar (§6.9): ALWAYS glass, never solid — the
 * [ShippopotamusHazeBottomBar] program blurs the content scrolling
 * behind it. 80dp with the 32dp pill indicator (accent at 15%, the
 * gospel's accent-muted token); inactive items wear text-tertiary,
 * the active item text-primary. Borderless — the blur is the
 * separation. The system navigation-bar inset pads the bar itself,
 * so the gesture pill sits over glass, not over content.
 */
@Composable
fun ShippopotamusBottomBar(
    items: List<GlassBottomBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    // Height is the component's own 80dp container (NavigationBar's
    // TallContainerHeight — the same value as
    // [ShippopotamusBottomBarHeight]) plus the system-bar inset below
    // it, all of it glass: the insets ride the component's
    // `windowInsets` slot so they are never double-padded, and
    // [glassBottomBarClearance] reserves exactly bar + nav inset.
    NavigationBar(
        containerColor = Color.Transparent,
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = modifier
            .fillMaxWidth()
            .hazeBlur(
                input = HazeInput.Backdrop(hazeState),
                style = ShippopotamusHazeBottomBar.toHazeBlurStyle(),
            ),
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                // The pill indicator is the library's own token
                // (ItemActiveIndicatorShape = CornerFull); 1.5.0-beta01
                // exposes no per-item shape override.
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BloviatePalette.TextPrimary,
                    selectedTextColor = BloviatePalette.TextPrimary,
                    indicatorColor = BloviatePalette.AccentMutedBg,
                    unselectedIconColor = BloviatePalette.TextTertiary,
                    unselectedTextColor = BloviatePalette.TextTertiary,
                ),
            )
        }
    }
}

/** Sample bar items for previews: the app's three tabs as fake data. */
private fun previewBarItems(): List<GlassBottomBarItem> = listOf(
    GlassBottomBarItem(label = "Setup", icon = Icons.Filled.Settings, contentDescription = "Setup"),
    GlassBottomBarItem(label = "Style", icon = Icons.Filled.AutoAwesome, contentDescription = "Style"),
    GlassBottomBarItem(label = "Dictionary", icon = Icons.Filled.MenuBook, contentDescription = "Dictionary"),
)

/** Sample tall content for glass previews: cards over the canvas, like a dashboard screen. */
@Composable
private fun GlassPreviewContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(BloviateSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
    ) {
        DashboardCard {
            DashboardRow(label = "Permissions", icon = Icons.Filled.Settings, sub = "All gates green")
        }
        DashboardCard {
            DashboardRow(label = "Dictation", icon = Icons.Filled.MenuBook, sub = "Bubble running")
        }
        DashboardCard {
            DashboardRow(label = "Voice models", icon = Icons.Filled.AutoAwesome, sub = "Whisper ready")
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Glass top bar - rest", showBackground = true)
@Composable
private fun ShippopotamusTopBarRestPreview() {
    PreviewTheme {
        val hazeState = rememberHazeState()
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Box(modifier = Modifier.fillMaxSize()) {
            GlassPreviewContent(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState, zIndex = 0f),
            )
            ShippopotamusTopBar(
                title = { Text(text = "Setup") },
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Glass top bar - scrolled", showBackground = true)
@Composable
private fun ShippopotamusTopBarScrolledPreview() {
    PreviewTheme {
        val hazeState = rememberHazeState()
        // A pre-scrolled container: the behavior reads the scroll
        // offset directly, so the glass engages without interaction.
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
            rememberScrollState(initial = SCROLLED_PREVIEW_OFFSET),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            GlassPreviewContent(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState, zIndex = 0f)
                    .verticalScroll(rememberScrollState(initial = SCROLLED_PREVIEW_OFFSET)),
            )
            ShippopotamusTopBar(
                title = { Text(text = "Setup") },
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Glass bottom bar", showBackground = true)
@Composable
private fun ShippopotamusBottomBarPreview() {
    PreviewTheme {
        val hazeState = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {
            GlassPreviewContent(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState, zIndex = 0f),
            )
            ShippopotamusBottomBar(
                items = previewBarItems(),
                selectedIndex = 0,
                onItemSelected = {},
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
