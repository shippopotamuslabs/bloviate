package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R

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
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onRequestMicPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val onboardingUiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    if (onboardingUiState.showWizard) {
        OnboardingScreen(
            uiState = onboardingUiState,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
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
    AppShell(
        setupContent = {
            SetupScreen(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onStartBubble = onStartBubble,
                onStopBubble = onStopBubble,
                onRequestMicPermission = onRequestMicPermission,
                onWhisperDownloadClick = viewModel::onWhisperDownloadClick,
                onParakeetDownloadClick = viewModel::onParakeetDownloadClick,
                onSelectEngine = viewModel::onSelectEngine,
                onBubbleOpacityChange = viewModel::onBubbleOpacityChange,
                onBubbleSizeChange = viewModel::onBubbleSizeChange,
                onBubbleShapeChange = viewModel::onBubbleShapeChange,
                onBubbleTintChange = viewModel::onBubbleTintChange,
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
 * Each tab screen owns its own `Scaffold` + `TopAppBar`
 * (contracts/ARCHITECTURE_RULES.md §4) — the shell is a plain column, not
 * a root Scaffold, and no padding values are threaded into the screens.
 * The [NavigationBar] owns the system navigation-bar inset, which is why
 * the screens no longer pad for it themselves.
 *
 * While the IME is up, the bar leaves the layout entirely (ENC-124).
 * The screens seat their content on the keyboard with `imePadding()`,
 * which measures the IME inset from the *window's* bottom edge — but
 * this column's content box ends above the bar, so a reserved bar slab
 * (80dp + the bar's own inset) stacked on top of the IME inset and left
 * a dead gap between content and keyboard on every tab. With the bar
 * gone, the content box reaches the window's bottom edge and the
 * screens' `imePadding()` lands content exactly on the keyboard. The
 * wizard never had the gap: it replaces this shell, so nothing sits
 * below its `imePadding()`.
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
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (selectedTab) {
                MainTab.SETUP -> setupContent()
                MainTab.STYLE -> styleContent()
                MainTab.DICTIONARY -> dictionaryContent()
            }
        }
        if (tabBarVisible(imeVisible = imeVisible)) {
            NavigationBar(containerColor = BloviateColors.extended.bgSubtle) {
                MainTab.entries.forEach { tab ->
                    val label = stringResource(tab.labelRes())
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTabValue = tab.toSavedValue() },
                        icon = {
                            Icon(
                                imageVector = tab.icon(),
                                contentDescription = label,
                            )
                        },
                        label = { Text(text = label) },
                    )
                }
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
