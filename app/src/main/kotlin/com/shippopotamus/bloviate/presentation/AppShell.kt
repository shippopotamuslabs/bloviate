package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R
import dev.chrisbanes.haze.rememberHazeState

/**
 * The app's root route: the first-run wizard (ENC-67) while its
 * persisted record is unfinished, the tab shell otherwise. Both
 * ViewModels are touched only here (and in the activity, which
 * forwards resume/mic-result events to each). The wizard and the
 * shell read the same live gate state through their own ViewModels,
 * so the two surfaces can never disagree about a permission.
 */
@Composable
fun MainRoute(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onRequestMicPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    // The launcher auth gate (ENC-197): signed-out users see the
    // sign-in screen instead of the whole shell; the gate reads the
    // same ViewModel state Setup's account card signs out from.
    val authState by viewModel.uiState.collectAsStateWithLifecycle()
    if (authState.auth !is AuthUi.SignedIn) {
        SignInScreen(
            isSigningIn = authState.isSigningIn,
            signInError = authState.signInError,
            onSignInClick = viewModel::onSignInClick,
            onErrorDismiss = viewModel::onSignInErrorDismiss,
            modifier = modifier,
        )
        return
    }
    val onboardingUiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    if (onboardingUiState.showWizard) {
        OnboardingScreen(
            uiState = onboardingUiState,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenBatterySettings = onOpenBatterySettings,
            onRequestMicPermission = onRequestMicPermission,
            onStartBubble = onStartBubble,
            onWhisperDownloadClick = onboardingViewModel::onWhisperDownloadClick,
            onParakeetDownloadClick = onboardingViewModel::onParakeetDownloadClick,
            onSelectEngine = onboardingViewModel::onSelectEngine,
            onTestFieldTextChange = onboardingViewModel::onTestFieldTextChange,
            onContinue = onboardingViewModel::onContinue,
            onBack = onboardingViewModel::onBack,
            onSkip = onboardingViewModel::onSkip,
            onFinish = onboardingViewModel::onFinish,
            modifier = modifier,
        )
        return
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Desired-state convergence (BLO-189): if the shell is showing —
    // the auth and wizard early-returns above mean setup is finished,
    // so this reads the same eagerly-loaded resident record the boot
    // path gates on — and the bubble is wanted, allowed to start on
    // its own, but not running, re-issue the user start. onStartBubble
    // records the start and issues ACTION_USER_START, the in-app start
    // the platform always allows. The gate never resurrects an
    // explicit Stop (bubbleEnabled false) or an opted-out posture
    // (autoStartEnabled false); a start the platform refuses stays
    // NOT_RUNNING for the manual refresh in Setup.
    val reArmDue = uiState.bubbleAutoStartDue()
    LaunchedEffect(reArmDue) {
        if (reArmDue) onStartBubble()
    }
    AppShell(
        setupContent = {
            SetupScreen(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onStartBubble = onStartBubble,
                onStopBubble = onStopBubble,
                onAutoStartEnabledChange = viewModel::onAutoStartEnabledChange,
                onRequestMicPermission = onRequestMicPermission,
                onWhisperDownloadClick = viewModel::onWhisperDownloadClick,
                onParakeetDownloadClick = viewModel::onParakeetDownloadClick,
                onSelectEngine = viewModel::onSelectEngine,
                onBubbleOpacityChange = viewModel::onBubbleOpacityChange,
                onBubbleSizeChange = viewModel::onBubbleSizeChange,
                onBubbleShapeChange = viewModel::onBubbleShapeChange,
                onBubbleTintChange = viewModel::onBubbleTintChange,
                onSignOut = viewModel::onSignOutClick,
                onRunSetupAgain = onboardingViewModel::onRunSetupAgain,
            )
        },
        styleContent = { StyleRoute() },
        dictionaryContent = { DictionaryRoute() },
        modifier = modifier,
    )
}

/**
 * The app shell (ENC-71): a bottom navigation bar over the Setup, Style,
 * and Dictionary tabs. State-hoisted and deliberately not Navigation
 * Compose: the app has exactly three top-level destinations with no deep
 * links or nested graphs, so a remembered tab selection is the whole
 * navigation model. The selection lives in [rememberSaveable] as the
 * tab's saved value, so it survives rotation and process recreation
 * without any persisted schema.
 *
 * Each tab screen owns its own `Scaffold` + top bar
 * (contracts/ARCHITECTURE_RULES.md §4) — the shell is a plain overlay
 * box, not a root Scaffold, and no padding values are threaded into
 * the screens. The bottom bar is glass (BLO-2; the Glass Bars Gospel,
 * contracts/UI_DESIGN_SPEC.md §6.9): it overlays the full-height
 * content instead of reserving a slab, so the tabs scroll under it.
 * The bar shares the current screen's `HazeState` — provided here
 * through [LocalScreenHazeState] and read by the screens with
 * [rememberScreenHazeState] — because an effect must reference the
 * state holding its source. The shell holds exactly one state: only
 * one tab is composed at a time, so tab switches hand the state over
 * cleanly as the old source unregisters.
 *
 * Inset ownership: the screens draw full-bleed to the window's
 * bottom edge and reserve [glassBottomBarClearance] (bar height plus
 * the system navigation-bar inset) inside their scroll content while
 * the bar is showing, so the last row rests above the glass at rest
 * yet scrolls under it. The bar itself carries the navigation-bars
 * inset, so the gesture pill sits over glass, not over content.
 *
 * While the IME is up, the bar leaves the layout entirely (ENC-124):
 * [LocalGlassBottomBarVisible] goes false, the clearance drops to
 * zero, and the screens' `imePadding()` — measured from the
 * *window's* bottom edge against the same full-bleed box — lands
 * content exactly on the keyboard with no dead gap. The wizard never
 * had the gap: it replaces this shell, so nothing sits below its
 * `imePadding()`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppShell(
    setupContent: @Composable () -> Unit,
    styleContent: @Composable () -> Unit,
    dictionaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabValue by rememberSaveable { mutableStateOf(MainTab.DEFAULT.toSavedValue()) }
    val selectedTab = MainTab.fromSavedValue(selectedTabValue)
    val imeVisible = WindowInsets.isImeVisible
    val barVisible = tabBarVisible(imeVisible = imeVisible)
    val hazeState = rememberHazeState()
    CompositionLocalProvider(
        LocalScreenHazeState provides hazeState,
        LocalGlassBottomBarVisible provides barVisible,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    MainTab.SETUP -> setupContent()
                    MainTab.STYLE -> styleContent()
                    MainTab.DICTIONARY -> dictionaryContent()
                }
            }
            if (barVisible) {
                val items = MainTab.entries.map { tab ->
                    val label = stringResource(tab.labelRes())
                    GlassBottomBarItem(
                        label = label,
                        icon = tab.icon(),
                        contentDescription = label,
                    )
                }
                ShippopotamusBottomBar(
                    items = items,
                    selectedIndex = MainTab.entries.indexOf(selectedTab),
                    onItemSelected = { index ->
                        selectedTabValue = MainTab.entries[index].toSavedValue()
                    },
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Whether the shell's tab bar reserves its slab of the column
 * (ENC-124): only while no IME is up. An open keyboard is itself the
 * bottom surface of the screen, and the bar hidden behind it must not
 * keep its height — see [AppShell]'s doc.
 */
internal fun tabBarVisible(imeVisible: Boolean): Boolean = !imeVisible

private fun MainTab.labelRes(): Int =
    when (this) {
        MainTab.SETUP -> R.string.tab_setup
        MainTab.STYLE -> R.string.tab_style
        MainTab.DICTIONARY -> R.string.tab_dictionary
    }

private fun MainTab.icon(): ImageVector =
    when (this) {
        MainTab.SETUP -> Icons.Default.Settings
        MainTab.STYLE -> Icons.Default.AutoAwesome
        MainTab.DICTIONARY -> Icons.Default.MenuBook
    }

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Shell - Dark", showBackground = true)
@Composable
private fun AppShellPreview() {
    PreviewTheme {
        AppShell(
            setupContent = { Text(text = "Setup tab content") },
            styleContent = { Text(text = "Style tab content") },
            dictionaryContent = { Text(text = "Dictionary tab content") },
        )
    }
}
