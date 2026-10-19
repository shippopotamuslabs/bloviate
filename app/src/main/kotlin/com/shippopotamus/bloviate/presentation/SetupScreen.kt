package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.BuildConfig
import com.shippopotamus.bloviate.R

/**
 * The Setup tab, dashboard form (ENC-75): the header pill carries the
 * whole verdict (derived from this same state by [setupStatus]), and
 * every control below is a row — status as a chip, the row itself as
 * the settings link, a switch for the bubble, a segmented control for
 * the engine — instead of cards full of sentences and verb buttons.
 * Same state and callbacks as the card layout it replaced; the rework
 * is presentation only.
 *
 * The column scrolls: the sections together exceed short viewports
 * (fold cover screens, split windows), and every row must stay
 * reachable (ENC-65). The shell's navigation bar owns the bottom
 * system inset, so the column does not pad for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
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
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    SetupStatusPill(
                        status = uiState.setupStatus(),
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashboardSectionHeader(text = stringResource(R.string.setup_section_permissions))
            PermissionsCard(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onRequestMicPermission = onRequestMicPermission,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.setup_section_dictation),
                modifier = Modifier.padding(top = 10.dp),
            )
            DictationCard(
                uiState = uiState,
                onStartBubble = onStartBubble,
                onStopBubble = onStopBubble,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.model_card_title),
                modifier = Modifier.padding(top = 10.dp),
            )
            VoiceModelsCard(
                uiState = uiState,
                onWhisperDownloadClick = onWhisperDownloadClick,
                onParakeetDownloadClick = onParakeetDownloadClick,
                onSelectEngine = onSelectEngine,
            )
            SetupFooter(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The header pill: green "Ready to dictate", or the first blocker in gate order (ENC-75). */
@Composable
private fun SetupStatusPill(
    status: SetupStatusUi,
    modifier: Modifier = Modifier,
) {
    when (status) {
        SetupStatusUi.Ready -> StatusPill(
            text = stringResource(R.string.setup_status_ready),
            tone = StatusTone.POSITIVE,
            modifier = modifier,
        )
        is SetupStatusUi.Blocked -> StatusPill(
            text = stringResource(
                when (status.blocker) {
                    SetupBlocker.WATCHER -> R.string.setup_status_watcher
                    SetupBlocker.OVERLAY -> R.string.setup_status_overlay
                    SetupBlocker.MICROPHONE -> R.string.setup_status_mic
                    SetupBlocker.MODEL -> R.string.setup_status_model
                    SetupBlocker.BUBBLE -> R.string.setup_status_bubble
                },
            ),
            tone = StatusTone.ATTENTION,
            modifier = modifier,
        )
    }
}

/**
 * The three permission gates as one grouped card (ENC-75). Each row's
 * chip carries the state; the row itself is the fix when one exists —
 * the accessibility row always opens Settings (that is also how a user
 * turns it back off), the microphone and overlay rows act only while
 * their permission is missing. The detection row's sub-line doubles as
 * the live focus readout while the watcher is on: which kind of field
 * is focused, never its content or the app it belongs to.
 */
@Composable
private fun PermissionsCard(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = stringResource(R.string.watcher_row_label),
            icon = Icons.Filled.Visibility,
            sub = if (uiState.watcherEnabled) {
                focusSub(uiState.focusClassification)
            } else {
                stringResource(R.string.watcher_row_sub)
            },
            onClick = onOpenAccessibilitySettings,
            trailing = {
                StatusChip(
                    text = stringResource(
                        if (uiState.watcherEnabled) R.string.chip_on else R.string.chip_off,
                    ),
                    tone = if (uiState.watcherEnabled) StatusTone.POSITIVE else StatusTone.ATTENTION,
                )
            },
        )
        DashboardRow(
            label = stringResource(R.string.mic_row_label),
            icon = Icons.Filled.Mic,
            sub = stringResource(R.string.mic_row_sub),
            onClick = if (uiState.micPermissionGranted) null else onRequestMicPermission,
            showChevron = !uiState.micPermissionGranted,
            dividerAbove = true,
            trailing = {
                StatusChip(
                    text = stringResource(
                        if (uiState.micPermissionGranted) R.string.chip_on else R.string.chip_off,
                    ),
                    tone = if (uiState.micPermissionGranted) StatusTone.POSITIVE else StatusTone.ATTENTION,
                )
            },
        )
        DashboardRow(
            label = stringResource(R.string.overlay_row_label),
            icon = Icons.Filled.Layers,
            sub = stringResource(R.string.overlay_row_sub),
            onClick = if (uiState.overlayPermissionGranted) null else onOpenOverlaySettings,
            showChevron = !uiState.overlayPermissionGranted,
            dividerAbove = true,
            trailing = {
                StatusChip(
                    text = stringResource(
                        if (uiState.overlayPermissionGranted) {
                            R.string.chip_allowed
                        } else {
                            R.string.chip_not_allowed
                        },
                    ),
                    tone = if (uiState.overlayPermissionGranted) {
                        StatusTone.POSITIVE
                    } else {
                        StatusTone.ATTENTION
                    },
                )
            },
        )
    }
}

@Composable
private fun focusSub(classification: FocusClassification): String =
    stringResource(
        when (classification) {
            FocusClassification.NONE -> R.string.focus_sub_none
            FocusClassification.EDITABLE -> R.string.focus_sub_editable
            FocusClassification.PASSWORD -> R.string.focus_sub_password
        },
    )

/**
 * The dictation card (ENC-75): the bubble is a switch — the control
 * that matches an on/off job — and Bubble look is the quiet chevron
 * row ENC-68 will grow into. The switch is live regardless of the
 * overlay permission, exactly as the old start/stop buttons were: the
 * service is the thing that runs, the permission gate is its own row.
 */
@Composable
private fun DictationCard(
    uiState: MainUiState,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = stringResource(R.string.bubble_row_label),
            icon = Icons.Filled.ChatBubbleOutline,
            sub = stringResource(R.string.bubble_row_sub),
            trailing = {
                Switch(
                    checked = uiState.bubbleServiceRunning,
                    onCheckedChange = { turnOn ->
                        if (turnOn) onStartBubble() else onStopBubble()
                    },
                )
            },
        )
        DashboardRow(
            label = stringResource(R.string.bubble_look_title),
            icon = Icons.Filled.Tune,
            sub = stringResource(R.string.bubble_look_sub),
            showChevron = true,
            dividerAbove = true,
            trailing = {
                StatusChip(text = stringResource(R.string.chip_soon), tone = StatusTone.NEUTRAL)
            },
        )
    }
}

/**
 * Per-engine model rows + the engine choice (ENC-54, dashboard form
 * ENC-75): each row's trailing slot carries its lifecycle — Ready chip,
 * compact Download button, progress while downloading, Try again after
 * a failure (with the reason as the sub-line). The engine choice is a
 * segmented control, enabled only for engines whose model is
 * downloaded. While the selected engine's model is not downloaded but
 * the other engine's is, dictation falls back to the ready engine —
 * the note under the control says so rather than letting the control
 * imply the selected engine is the one transcribing.
 */
@Composable
private fun VoiceModelsCard(
    uiState: MainUiState,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        ModelRow(
            name = stringResource(R.string.engine_parakeet_name),
            detail = stringResource(R.string.engine_parakeet_sub),
            status = uiState.parakeetModelStatus,
            onDownloadClick = onParakeetDownloadClick,
        )
        ModelRow(
            name = stringResource(R.string.engine_whisper_name),
            detail = stringResource(R.string.engine_whisper_sub),
            status = uiState.whisperModelStatus,
            onDownloadClick = onWhisperDownloadClick,
            dividerAbove = true,
        )
        Column(
            modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EngineSegment(
                    label = stringResource(R.string.engine_whisper_short),
                    selected = uiState.selectedEngine == DictationEngineUi.WHISPER,
                    enabled = uiState.whisperModelStatus is ModelUiStatus.Ready ||
                        uiState.selectedEngine == DictationEngineUi.WHISPER,
                    index = 0,
                    onClick = { onSelectEngine(DictationEngineUi.WHISPER) },
                )
                EngineSegment(
                    label = stringResource(R.string.engine_parakeet_short),
                    selected = uiState.selectedEngine == DictationEngineUi.PARAKEET,
                    enabled = uiState.parakeetModelStatus is ModelUiStatus.Ready ||
                        uiState.selectedEngine == DictationEngineUi.PARAKEET,
                    index = 1,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    name: String,
    detail: String,
    status: ModelUiStatus,
    onDownloadClick: () -> Unit,
    dividerAbove: Boolean = false,
) {
    DashboardRow(
        label = name,
        sub = when (status) {
            is ModelUiStatus.Downloading ->
                stringResource(R.string.model_status_downloading_format, status.percent)
            is ModelUiStatus.Failed ->
                stringResource(R.string.model_status_failed_format, status.reason)
            ModelUiStatus.Absent, ModelUiStatus.Ready -> detail
        },
        dividerAbove = dividerAbove,
        trailing = {
            when (status) {
                ModelUiStatus.Ready -> StatusChip(
                    text = stringResource(R.string.model_chip_ready),
                    tone = StatusTone.POSITIVE,
                )
                ModelUiStatus.Absent -> Button(onClick = onDownloadClick) {
                    Text(text = stringResource(R.string.model_download))
                }
                is ModelUiStatus.Downloading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                is ModelUiStatus.Failed -> Button(onClick = onDownloadClick) {
                    Text(text = stringResource(R.string.model_retry))
                }
            }
        },
    )
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.EngineSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    // Rendered inside a SingleChoiceSegmentedButtonRow; the row scope
    // is what gives the segment its joined shape.
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = ENGINE_SEGMENT_COUNT),
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primary,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            inactiveContainerColor = MaterialTheme.colorScheme.surface,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledActiveContainerColor = MaterialTheme.colorScheme.primary,
            disabledActiveContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        icon = {},
    ) {
        Text(text = label)
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

/**
 * The quiet footer (ENC-75): the privacy story in one line, the model
 * attributions, and the exact build identity (ENC-69 — one-off test
 * builds all report version 1.0, so the commit SHA is the only reliable
 * way to tell which build is installed).
 */
@Composable
private fun SetupFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.footer_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.engine_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.about_line_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.GIT_SHA,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ENGINE_SEGMENT_COUNT = 2

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Empty - Dark", showBackground = true)
@Composable
private fun SetupScreenEmptyPreview() {
    PreviewTheme {
        SetupScreen(
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
@Preview(name = "Populated - Dark", showBackground = true)
@Composable
private fun SetupScreenPopulatedPreview() {
    PreviewTheme {
        SetupScreen(
            uiState = MainUiState(
                watcherEnabled = true,
                focusClassification = FocusClassification.EDITABLE,
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
