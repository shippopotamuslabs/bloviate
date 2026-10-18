package com.shippopotamus.bloviate.presentation

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R

/** Wires [MainViewModel] state into [MainScreen]; the route is the only place the ViewModel is touched. */
@Composable
fun MainRoute(
    onOpenAccessibilitySettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        uiState = uiState,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        modifier = modifier,
    )
}

/**
 * Status screen: whether the accessibility watcher is enabled, a deep link to
 * the system setting, and the live field-focus classification published by the
 * watcher (package + classification only — never field content).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.app_name)) })
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            WatcherStatusCard(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
        }
    }
}

@Composable
private fun WatcherStatusCard(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.watcher_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (uiState.watcherEnabled) {
                        R.string.watcher_status_enabled
                    } else {
                        R.string.watcher_status_disabled
                    },
                ),
            )
            Text(text = focusLabel(uiState.focusClassification))
            uiState.focusPackageName?.let { packageName ->
                Text(text = stringResource(R.string.focus_package_format, packageName))
            }
            Button(onClick = onOpenAccessibilitySettings) {
                Text(text = stringResource(R.string.watcher_open_settings))
            }
            Text(
                text = stringResource(R.string.watcher_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun focusLabel(classification: FocusClassification): String =
    stringResource(
        when (classification) {
            FocusClassification.NONE -> R.string.focus_label_none
            FocusClassification.EDITABLE -> R.string.focus_label_editable
            FocusClassification.PASSWORD -> R.string.focus_label_password
        },
    )

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Empty - Light", showBackground = true)
@Preview(
    name = "Empty - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainScreenEmptyPreview() {
    PreviewTheme {
        MainScreen(
            uiState = MainUiState(),
            onOpenAccessibilitySettings = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Populated - Light", showBackground = true)
@Preview(
    name = "Populated - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainScreenPopulatedPreview() {
    PreviewTheme {
        MainScreen(
            uiState = MainUiState(
                watcherEnabled = true,
                focusClassification = FocusClassification.EDITABLE,
                focusPackageName = "com.google.android.gm",
            ),
            onOpenAccessibilitySettings = {},
        )
    }
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
