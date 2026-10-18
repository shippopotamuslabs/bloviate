package com.shippopotamus.bloviate.presentation

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
import com.shippopotamus.bloviate.BuildConfig
import com.shippopotamus.bloviate.R

/** Wires [MainViewModel] state into [MainScreen]; the route is the only place the ViewModel is touched. */
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
    MainScreen(
        uiState = uiState,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onOpenOverlaySettings = onOpenOverlaySettings,
        onStartBubble = onStartBubble,
        onStopBubble = onStopBubble,
        onRequestMicPermission = onRequestMicPermission,
        onWhisperDownloadClick = viewModel::onWhisperDownloadClick,
        onParakeetDownloadClick = viewModel::onParakeetDownloadClick,
        onSelectEngine = viewModel::onSelectEngine,
        modifier = modifier,
    )
}

/**
 * Status screen: whether the accessibility watcher is enabled, a deep link to
 * the system setting, the live field-focus classification published by the
 * watcher (package + classification only — never field content), the
 * bubble overlay permission + service controls, the microphone
 * permission state + request, and the model download state + retry. The
 * card column scrolls: the cards together exceed short viewports (fold
 * cover screens, split windows), and every card must stay reachable
 * (ENC-65).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WatcherStatusCard(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
            BubbleStatusCard(
                uiState = uiState,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onStartBubble = onStartBubble,
                onStopBubble = onStopBubble,
            )
            MicPermissionCard(
                uiState = uiState,
                onRequestMicPermission = onRequestMicPermission,
            )
            EnginesCard(
                uiState = uiState,
                onWhisperDownloadClick = onWhisperDownloadClick,
                onParakeetDownloadClick = onParakeetDownloadClick,
                onSelectEngine = onSelectEngine,
            )
            // Exact build identity, readable in-app (ENC-69): one-off test
            // builds all report version 1.0, so the commit SHA is the only
            // reliable way to tell which build is installed.
            Text(
                text = stringResource(R.string.build_tag_format, BuildConfig.GIT_SHA),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Per-engine model rows + the engine toggle (ENC-54): each engine shows its
 * own download state and download/retry button; the "Dictation engine"
 * choice reflects the persisted preference and is enabled only for engines
 * whose model is Ready. While the selected engine's model is not Ready
 * but the other engine's is, dictation falls back to the ready engine —
 * the note under the toggle says so rather than letting the toggle imply
 * the selected engine is the one transcribing.
 */
@Composable
private fun EnginesCard(
    uiState: MainUiState,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.model_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            EngineRow(
                name = stringResource(R.string.engine_whisper_name),
                detail = stringResource(R.string.engine_whisper_detail),
                status = uiState.whisperModelStatus,
                onDownloadClick = onWhisperDownloadClick,
            )
            EngineRow(
                name = stringResource(R.string.engine_parakeet_name),
                detail = stringResource(R.string.engine_parakeet_detail),
                status = uiState.parakeetModelStatus,
                onDownloadClick = onParakeetDownloadClick,
            )
            Text(
                text = stringResource(R.string.engine_selector_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineChoiceButton(
                    label = stringResource(R.string.engine_whisper_short),
                    selected = uiState.selectedEngine == DictationEngineUi.WHISPER,
                    enabled = uiState.whisperModelStatus is ModelUiStatus.Ready,
                    onClick = { onSelectEngine(DictationEngineUi.WHISPER) },
                )
                EngineChoiceButton(
                    label = stringResource(R.string.engine_parakeet_short),
                    selected = uiState.selectedEngine == DictationEngineUi.PARAKEET,
                    enabled = uiState.parakeetModelStatus is ModelUiStatus.Ready,
                    onClick = { onSelectEngine(DictationEngineUi.PARAKEET) },
                )
            }
            uiState.engineFallback?.let { fallback ->
                Text(
                    text = stringResource(
                        R.string.engine_fallback_note,
                        engineShortName(fallback.selected),
                        engineShortName(fallback.effective),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.model_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.engine_attribution),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EngineRow(
    name: String,
    detail: String,
    status: ModelUiStatus,
    onDownloadClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = name, style = MaterialTheme.typography.titleSmall)
        Text(text = modelStatusLabel(status))
        Button(
            onClick = onDownloadClick,
            enabled = status is ModelUiStatus.Absent || status is ModelUiStatus.Failed,
        ) {
            Text(text = stringResource(R.string.model_download))
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EngineChoiceButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // The selected engine stays clickable (it is a no-op re-selection); an
    // unselected engine is clickable only when its model is Ready.
    val clickable = enabled || selected
    if (selected) {
        Button(onClick = onClick, enabled = clickable) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = clickable) {
            Text(text = label)
        }
    }
}

@Composable
private fun engineShortName(engine: DictationEngineUi): String =
    stringResource(
        when (engine) {
            DictationEngineUi.WHISPER -> R.string.engine_whisper_short
            DictationEngineUi.PARAKEET -> R.string.engine_parakeet_short
        },
    )

@Composable
private fun modelStatusLabel(status: ModelUiStatus): String =
    when (status) {
        ModelUiStatus.Absent -> stringResource(R.string.model_status_absent)
        is ModelUiStatus.Downloading ->
            stringResource(R.string.model_status_downloading_format, status.percent)
        ModelUiStatus.Ready -> stringResource(R.string.model_status_ready)
        is ModelUiStatus.Failed ->
            stringResource(R.string.model_status_failed_format, status.reason)
    }

@Composable
private fun MicPermissionCard(
    uiState: MainUiState,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.mic_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (uiState.micPermissionGranted) {
                        R.string.mic_status_granted
                    } else {
                        R.string.mic_status_missing
                    },
                ),
            )
            Button(
                onClick = onRequestMicPermission,
                enabled = !uiState.micPermissionGranted,
            ) {
                Text(text = stringResource(R.string.mic_request))
            }
            Text(
                text = stringResource(R.string.mic_hint),
                style = MaterialTheme.typography.bodySmall,
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
private fun BubbleStatusCard(
    uiState: MainUiState,
    onOpenOverlaySettings: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.bubble_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (uiState.overlayPermissionGranted) {
                        R.string.bubble_overlay_granted
                    } else {
                        R.string.bubble_overlay_missing
                    },
                ),
            )
            Text(
                text = stringResource(
                    if (uiState.bubbleServiceRunning) {
                        R.string.bubble_service_running
                    } else {
                        R.string.bubble_service_stopped
                    },
                ),
            )
            Button(
                onClick = onOpenOverlaySettings,
                enabled = !uiState.overlayPermissionGranted,
            ) {
                Text(text = stringResource(R.string.bubble_overlay_open_settings))
            }
            Button(
                onClick = onStartBubble,
                enabled = !uiState.bubbleServiceRunning,
            ) {
                Text(text = stringResource(R.string.bubble_start))
            }
            Button(
                onClick = onStopBubble,
                enabled = uiState.bubbleServiceRunning,
            ) {
                Text(text = stringResource(R.string.bubble_stop))
            }
            Text(
                text = stringResource(R.string.bubble_hint),
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
            onOpenOverlaySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
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
                overlayPermissionGranted = true,
                bubbleServiceRunning = true,
                micPermissionGranted = true,
                whisperModelStatus = ModelUiStatus.Ready,
                parakeetModelStatus = ModelUiStatus.Ready,
                selectedEngine = DictationEngineUi.PARAKEET,
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
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
