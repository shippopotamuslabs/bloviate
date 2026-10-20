package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.R

/**
 * The first-run wizard (ENC-67): six steps — welcome, the four setup
 * gates in the Setup screen's own order, and a guided test dictation.
 * It is a flow over the same live state the Setup dashboard reads:
 * every gate step shows its state as a chip and carries the deep-link
 * action that fixes it, and the model step is the Setup model card's
 * rows and engine choice. Nothing here is a parallel implementation —
 * when the wizard and Setup disagree, that is a bug in one of them.
 *
 * The walk never traps: Skip sits in the app bar on every step, Back
 * and Continue are always available (a gate can be passed unsatisfied
 * and fixed later from Setup), and Finish on the test step never waits
 * for a successful dictation. Progress persists per step, so a
 * settings detour or a dead process resumes where the user left off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onStartBubble: () -> Unit,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
    onTestFieldTextChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.onboarding_title)) },
                actions = {
                    StatusChip(
                        text = stringResource(
                            R.string.onboarding_step_format,
                            uiState.step.ordinal + 1,
                            OnboardingStepUi.entries.size,
                        ),
                        tone = StatusTone.NEUTRAL,
                        modifier = Modifier.padding(end = BloviateSpacing.s8),
                    )
                    TextButton(onClick = onSkip) {
                        Text(text = stringResource(R.string.onboarding_skip))
                    }
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
                .padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
        ) {
            StepProgressBar(current = uiState.step)
            when (uiState.step) {
                OnboardingStepUi.WELCOME -> WelcomeStep()
                OnboardingStepUi.WATCHER -> WatcherStep(
                    uiState = uiState,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
                OnboardingStepUi.OVERLAY -> OverlayStep(
                    uiState = uiState,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                )
                OnboardingStepUi.MICROPHONE -> MicrophoneStep(
                    uiState = uiState,
                    onRequestMicPermission = onRequestMicPermission,
                )
                OnboardingStepUi.MODEL -> ModelStep(
                    uiState = uiState,
                    onWhisperDownloadClick = onWhisperDownloadClick,
                    onParakeetDownloadClick = onParakeetDownloadClick,
                    onSelectEngine = onSelectEngine,
                )
                OnboardingStepUi.TEST -> TestStep(
                    uiState = uiState,
                    onStartBubble = onStartBubble,
                    onTestFieldTextChange = onTestFieldTextChange,
                )
            }
            StepNavigation(
                step = uiState.step,
                onBack = onBack,
                onContinue = onContinue,
                onFinish = onFinish,
            )
        }
    }
}

/** The walk's progress: one segment per step, filled through the current one. */
@Composable
private fun StepProgressBar(
    current: OnboardingStepUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
    ) {
        OnboardingStepUi.entries.forEach { step ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PROGRESS_SEGMENT_HEIGHT)
                    .background(
                        color = if (step.ordinal <= current.ordinal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = BloviateShapes.Pill,
                    ),
            )
        }
    }
}

/** Back where there is a step behind, and the step's way forward. Both always present. */
@Composable
private fun StepNavigation(
    step: OnboardingStepUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != OnboardingStepUi.WELCOME) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.onboarding_back))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        when (step) {
            OnboardingStepUi.WELCOME -> Button(onClick = onContinue) {
                Text(text = stringResource(R.string.onboarding_get_started))
            }
            OnboardingStepUi.TEST -> Button(onClick = onFinish) {
                Text(text = stringResource(R.string.onboarding_finish))
            }
            else -> Button(onClick = onContinue) {
                Text(text = stringResource(R.string.onboarding_continue))
            }
        }
    }
}

/**
 * Step 1 — what Bloviate is and what it needs, in the dashboard's
 * plain language: the promise (dictate anywhere, on-device) and the
 * four gates as a checklist the rest of the walk will clear.
 */
@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_welcome_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DashboardSectionHeader(
            text = stringResource(R.string.onboarding_welcome_needs_title),
            modifier = Modifier.padding(
                start = BloviateSpacing.s16,
                end = BloviateSpacing.s16,
                bottom = BloviateSpacing.s4,
            ),
        )
        WelcomePoint(
            icon = Icons.Filled.Visibility,
            text = stringResource(R.string.onboarding_welcome_point_detection),
        )
        WelcomePoint(
            icon = Icons.Filled.Layers,
            text = stringResource(R.string.onboarding_welcome_point_overlay),
        )
        WelcomePoint(
            icon = Icons.Filled.Mic,
            text = stringResource(R.string.onboarding_welcome_point_mic),
        )
        WelcomePoint(
            icon = Icons.Filled.GraphicEq,
            text = stringResource(R.string.onboarding_welcome_point_model),
        )
        Spacer(modifier = Modifier.size(BloviateSpacing.s8))
    }
}

/** One checklist line of the welcome step: the gate's icon tile and its one-line promise. */
@Composable
private fun WelcomePoint(
    icon: ImageVector,
    text: String,
) {
    DashboardRow(
        label = text,
        icon = icon,
        showChevron = false,
    )
}

/**
 * One gate step's shared shape: the gate as a dashboard row (live chip
 * at the trailing edge), and the fix as a button under it while the
 * gate is unsatisfied.
 */
@Composable
private fun GateStep(
    icon: ImageVector,
    title: String,
    sub: String,
    satisfied: Boolean,
    satisfiedChipText: String,
    unsatisfiedChipText: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = title,
            icon = icon,
            sub = sub,
            showChevron = false,
            trailing = {
                StatusChip(
                    text = if (satisfied) satisfiedChipText else unsatisfiedChipText,
                    tone = if (satisfied) StatusTone.POSITIVE else StatusTone.ATTENTION,
                )
            },
            below = if (satisfied) {
                null
            } else {
                {
                    Button(
                        onClick = onAction,
                        modifier = Modifier
                            .padding(
                                start = BloviateSpacing.s16,
                                end = BloviateSpacing.s16,
                                bottom = BloviateSpacing.s16,
                            ),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            },
        )
    }
}

/** Step 2 — the accessibility watcher, deep-linked to its system settings. */
@Composable
private fun WatcherStep(
    uiState: OnboardingUiState,
    onOpenAccessibilitySettings: () -> Unit,
) {
    GateStep(
        icon = Icons.Filled.Visibility,
        title = stringResource(R.string.onboarding_watcher_title),
        sub = stringResource(R.string.onboarding_watcher_sub),
        satisfied = uiState.watcherEnabled,
        satisfiedChipText = stringResource(R.string.chip_on),
        unsatisfiedChipText = stringResource(R.string.chip_off),
        actionLabel = stringResource(R.string.onboarding_open_settings),
        onAction = onOpenAccessibilitySettings,
    )
}

/** Step 3 — display over other apps, deep-linked to its system settings. */
@Composable
private fun OverlayStep(
    uiState: OnboardingUiState,
    onOpenOverlaySettings: () -> Unit,
) {
    GateStep(
        icon = Icons.Filled.Layers,
        title = stringResource(R.string.onboarding_overlay_title),
        sub = stringResource(R.string.onboarding_overlay_sub),
        satisfied = uiState.overlayPermissionGranted,
        satisfiedChipText = stringResource(R.string.chip_allowed),
        unsatisfiedChipText = stringResource(R.string.chip_not_allowed),
        actionLabel = stringResource(R.string.onboarding_open_settings),
        onAction = onOpenOverlaySettings,
    )
}

/** Step 4 — the microphone, an in-app runtime request (no settings detour). */
@Composable
private fun MicrophoneStep(
    uiState: OnboardingUiState,
    onRequestMicPermission: () -> Unit,
) {
    GateStep(
        icon = Icons.Filled.Mic,
        title = stringResource(R.string.onboarding_mic_title),
        sub = stringResource(R.string.onboarding_mic_sub),
        satisfied = uiState.micPermissionGranted,
        satisfiedChipText = stringResource(R.string.chip_on),
        unsatisfiedChipText = stringResource(R.string.chip_off),
        actionLabel = stringResource(R.string.onboarding_allow_mic),
        onAction = onRequestMicPermission,
    )
}

/**
 * Step 5 — the voice model: both engines' rows with their live
 * download states and the engine choice, the Setup model card's
 * content in the wizard's frame. The selected engine is only a
 * preference — the fallback note appears while the ready engine is
 * the other one, exactly as in Setup.
 */
@Composable
private fun ModelStep(
    uiState: OnboardingUiState,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
    ) {
        Text(
            text = stringResource(R.string.onboarding_model_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DashboardCard {
            WizardModelRow(
                name = stringResource(R.string.engine_parakeet_name),
                detail = stringResource(R.string.engine_parakeet_sub),
                status = uiState.parakeetModelStatus,
                onDownloadClick = onParakeetDownloadClick,
            )
            WizardModelRow(
                name = stringResource(R.string.engine_whisper_name),
                detail = stringResource(R.string.engine_whisper_sub),
                status = uiState.whisperModelStatus,
                onDownloadClick = onWhisperDownloadClick,
                dividerAbove = true,
            )
            Column(
                modifier = Modifier.padding(
                    start = BloviateSpacing.s16,
                    end = BloviateSpacing.s16,
                    bottom = BloviateSpacing.s12,
                ),
                verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WizardEngineSegment(
                        label = stringResource(R.string.engine_whisper_short),
                        selected = uiState.selectedEngine == DictationEngineUi.WHISPER,
                        enabled = uiState.whisperModelStatus is ModelUiStatus.Ready ||
                            uiState.selectedEngine == DictationEngineUi.WHISPER,
                        index = 0,
                        onClick = { onSelectEngine(DictationEngineUi.WHISPER) },
                    )
                    WizardEngineSegment(
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
}

/** One engine's row on the wizard's model step: the Setup model row's states, verbatim logic. */
@Composable
private fun WizardModelRow(
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
        showChevron = false,
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
private fun SingleChoiceSegmentedButtonRowScope.WizardEngineSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
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
 * Step 6 — the guided test dictation: an in-app scratch field the real
 * pipeline serves. Focusing it is an ordinary editable focus, so the
 * watcher spots it and the real bubble appears; the user dictates and
 * the text lands here through the normal insertion path. The step
 * reports the landing when the field gains words (typed words count —
 * the point is seeing text arrive), offers the bubble switch while
 * the service is off, and names the missing gates instead of leaving
 * the user to wonder why no bubble came.
 */
@Composable
private fun TestStep(
    uiState: OnboardingUiState,
    onStartBubble: () -> Unit,
    onTestFieldTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s12),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s12),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_test_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.testDictationLanded()) {
                    StatusChip(
                        text = stringResource(R.string.onboarding_test_landed),
                        tone = StatusTone.POSITIVE,
                    )
                } else {
                    StatusChip(
                        text = stringResource(R.string.onboarding_test_waiting),
                        tone = StatusTone.NEUTRAL,
                    )
                }
            }
            Text(
                text = stringResource(R.string.onboarding_test_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.testFieldText,
                onValueChange = onTestFieldTextChange,
                label = { Text(text = stringResource(R.string.onboarding_test_field_label)) },
                minLines = TEST_FIELD_MIN_LINES,
                maxLines = TEST_FIELD_MAX_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!uiState.bubbleServiceRunning) {
                Button(onClick = onStartBubble) {
                    Text(text = stringResource(R.string.onboarding_test_start_bubble))
                }
            } else if (!uiState.stepSatisfied(OnboardingStepUi.TEST)) {
                Text(
                    text = stringResource(R.string.onboarding_test_needs_gates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PROGRESS_SEGMENT_HEIGHT = 4.dp
private const val ENGINE_SEGMENT_COUNT: Int = 2
private const val TEST_FIELD_MIN_LINES: Int = 2
private const val TEST_FIELD_MAX_LINES: Int = 4

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Wizard Watcher - Dark", showBackground = true)
@Composable
private fun OnboardingWatcherPreview() {
    PreviewTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(
                step = OnboardingStepUi.WATCHER,
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onRequestMicPermission = {},
            onStartBubble = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onTestFieldTextChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {},
            onFinish = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Wizard Test Landed - Dark", showBackground = true)
@Composable
private fun OnboardingTestLandedPreview() {
    PreviewTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(
                step = OnboardingStepUi.TEST,
                watcherEnabled = true,
                overlayPermissionGranted = true,
                micPermissionGranted = true,
                bubbleServiceRunning = true,
                parakeetModelStatus = ModelUiStatus.Ready,
                testFieldText = "The quick brown fox, dictated hands-free.",
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onRequestMicPermission = {},
            onStartBubble = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onTestFieldTextChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {},
            onFinish = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Wizard Welcome - Dark", showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    PreviewTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(step = OnboardingStepUi.WELCOME),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onRequestMicPermission = {},
            onStartBubble = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onTestFieldTextChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {},
            onFinish = {},
        )
    }
}
