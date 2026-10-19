package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Wires [MainViewModel] state into the shell's Setup tab; the route is the only place the ViewModel is touched. */
@Composable
fun MainRoute(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onRequestMicPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
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
 */
@Composable
fun AppShell(
    setupContent: @Composable () -> Unit,
    styleContent: @Composable () -> Unit,
    dictionaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabValue by rememberSaveable { mutableStateOf(MainTab.DEFAULT.toSavedValue()) }
    val selectedTab = MainTab.fromSavedValue(selectedTabValue)
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
