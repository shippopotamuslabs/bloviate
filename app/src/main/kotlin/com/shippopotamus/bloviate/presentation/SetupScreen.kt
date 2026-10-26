package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.BuildConfig
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import kotlin.math.roundToInt

/**
 * The Setup tab, dashboard form (ENC-75): the header pill carries the
 * whole verdict (derived from this same state by [setupStatus]), and
 * every control below is a row — status as a chip, the row itself as
 * the settings link, a switch for the bubble, a segmented control for
 * the engine — instead of cards full of sentences and verb buttons.
 * Same state and callbacks as the card layout it replaced; the rework
 * is presentation only. Below the cards sit the quiet sections: the
 * setup-guide row that re-runs the ENC-67 wizard, and the footer.
 *
 * The column scrolls: the sections together exceed short viewports
 * (fold cover screens, split windows), and every row must stay
 * reachable (ENC-65). The shell's glass bar overlays the content, so
 * the column draws full-bleed as the bar's haze source and reserves
 * its top offset and bottom clearance inside the scroll.
 */
@Suppress("LongParameterList") // Fully state-hoisted screen: one callback per control, by design.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onWhisperDownloadClick: () -> Unit,
    onParakeetDownloadClick: () -> Unit,
    onSelectEngine: (DictationEngineUi) -> Unit,
    onBubbleOpacityChange: (Float) -> Unit,
    onBubbleSizeChange: (BubbleSizeUi) -> Unit,
    onBubbleShapeChange: (BubbleShapeUi) -> Unit,
    onBubbleTintChange: (BubbleTintUi) -> Unit,
    onSignOut: () -> Unit,
    onRunSetupAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberScreenHazeState()
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(scrollState)
    Scaffold(
        topBar = {
            ShippopotamusTopBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
                actions = {
                    SetupStatusPill(
                        status = uiState.setupStatus(),
                        modifier = Modifier.padding(end = BloviateSpacing.s16),
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState, zIndex = 0f)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = glassBottomBarClearance(),
                )
                .padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
        ) {
            DashboardSectionHeader(text = stringResource(R.string.setup_section_permissions))
            PermissionsCard(
                uiState = uiState,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onRequestMicPermission = onRequestMicPermission,
                onStartBubble = onStartBubble,
                onStopBubble = onStopBubble,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.setup_section_dictation),
            )
            DictationCard(
                uiState = uiState,
                onStartBubble = onStartBubble,
                onAutoStartEnabledChange = onAutoStartEnabledChange,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.bubble_look_title),
            )
            BubbleLookCard(
                appearance = uiState.bubbleAppearance,
                onOpacityChange = onBubbleOpacityChange,
                onSizeChange = onBubbleSizeChange,
                onShapeChange = onBubbleShapeChange,
                onTintChange = onBubbleTintChange,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.model_card_title),
            )
            VoiceModelsCard(
                uiState = uiState,
                onWhisperDownloadClick = onWhisperDownloadClick,
                onParakeetDownloadClick = onParakeetDownloadClick,
                onSelectEngine = onSelectEngine,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.setup_section_guide),
            )
            SetupGuideCard(onRunSetupAgain = onRunSetupAgain)
            DashboardSectionHeader(
                text = stringResource(R.string.setup_section_account),
            )
            AccountCard(
                auth = uiState.auth,
                onSignOut = onSignOut,
            )
            SetupFooter()
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
                    SetupBlocker.WATCHER_DISCONNECTED -> R.string.setup_status_watcher_disconnected
                    SetupBlocker.OVERLAY -> R.string.setup_status_overlay
                    SetupBlocker.MICROPHONE -> R.string.setup_status_mic
                    SetupBlocker.MODEL -> R.string.setup_status_model
                    SetupBlocker.BUBBLE -> R.string.setup_status_bubble
                    SetupBlocker.BUBBLE_NOT_RUNNING -> R.string.setup_status_bubble_not_running
                },
            ),
            tone = StatusTone.ATTENTION,
            modifier = modifier,
        )
    }
}

/**
 * The permission gates as one grouped card (ENC-75; battery row added
 * by ENC-129). Each row's chip carries the state; the row itself is
 * the fix when one exists — the accessibility row always opens
 * Settings (that is also how a user turns it back off), the microphone
 * and overlay rows act only while their permission is missing, and the
 * battery row raises the system's one-tap exemption dialog (the
 * settings list on builds without it) only while Bloviate is not
 * exempt. The detection row's sub-line is a static description of
 * what the watcher does (ENC-142 removed the live focus readout —
 * Setup is only ever viewed from inside Bloviate, where no other
 * app's field can be focused). An enabled-but-disconnected watcher
 * (ENC-129 supervision) reads "Not connected" here instead of a
 * misleading On, with the connection-lost sub-line to match. The last
 * row (BLO-189) is the bubble's user-side gate: not a system grant
 * but the user's own on/off, alone in taking no Settings deep-link.
 */
@Composable
private fun PermissionsCard(
    uiState: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = stringResource(R.string.watcher_row_label),
            icon = Icons.Filled.Visibility,
            sub = stringResource(
                if (uiState.watcherEnabled && !uiState.watcherConnected) {
                    R.string.watcher_row_disconnected_sub
                } else {
                    R.string.watcher_row_sub
                },
            ),
            onClick = onOpenAccessibilitySettings,
            trailing = {
                StatusChip(
                    text = stringResource(
                        when {
                            !uiState.watcherEnabled -> R.string.chip_off
                            !uiState.watcherConnected -> R.string.chip_not_connected
                            else -> R.string.chip_on
                        },
                    ),
                    tone = if (uiState.watcherEnabled && uiState.watcherConnected) {
                        StatusTone.POSITIVE
                    } else {
                        StatusTone.ATTENTION
                    },
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
        DashboardRow(
            label = stringResource(R.string.battery_row_label),
            icon = Icons.Filled.BatteryStd,
            sub = stringResource(R.string.battery_row_sub),
            onClick = if (uiState.batteryExempt) null else onOpenBatterySettings,
            showChevron = !uiState.batteryExempt,
            dividerAbove = true,
            trailing = {
                StatusChip(
                    text = stringResource(
                        if (uiState.batteryExempt) R.string.chip_allowed else R.string.chip_not_allowed,
                    ),
                    tone = if (uiState.batteryExempt) StatusTone.POSITIVE else StatusTone.ATTENTION,
                )
            },
        )
        DashboardRow(
            label = stringResource(R.string.bubble_row_label),
            icon = Icons.Filled.ChatBubbleOutline,
            sub = stringResource(R.string.bubble_row_permission_sub),
            dividerAbove = true,
            trailing = {
                Switch(
                    checked = uiState.bubbleEnabled,
                    onCheckedChange = { turnOn ->
                        if (turnOn) onStartBubble() else onStopBubble()
                    },
                    colors = bloviateSwitchColors(),
                )
            },
        )
    }
}

/**
 * The dictation card (ENC-75; status row per BLO-189): the bubble row
 * reports instead of switching — a chip for the steady state plus a
 * refresh button while wanted-but-down that re-issues the user start.
 * The on/off switch lives in [PermissionsCard], the row alone owning
 * the user's gate. The service stays live regardless of the overlay
 * permission, exactly as before: the service is the thing that runs,
 * the permission gate is its own row. Below it, the
 * resident-presence switch (ENC-129): whether the bubble starts on
 * its own after a reboot and comes back after a system stop.
 */
@Composable
private fun DictationCard(
    uiState: MainUiState,
    onStartBubble: () -> Unit,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleStatus = uiState.bubbleServiceStatus()
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = stringResource(R.string.bubble_status_row_label),
            icon = Icons.Filled.ChatBubbleOutline,
            sub = stringResource(R.string.bubble_row_sub),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
                ) {
                    if (bubbleStatus == BubbleStatusUi.NOT_RUNNING) {
                        IconButton(
                            onClick = onStartBubble,
                            shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.bubble_refresh_description),
                            )
                        }
                    }
                    StatusChip(
                        text = stringResource(
                            when (bubbleStatus) {
                                BubbleStatusUi.RUNNING -> R.string.chip_on
                                BubbleStatusUi.NOT_RUNNING -> R.string.chip_not_running
                                BubbleStatusUi.OFF -> R.string.chip_off
                            },
                        ),
                        tone = when (bubbleStatus) {
                            BubbleStatusUi.RUNNING -> StatusTone.POSITIVE
                            BubbleStatusUi.NOT_RUNNING -> StatusTone.ATTENTION
                            BubbleStatusUi.OFF -> StatusTone.NEUTRAL
                        },
                    )
                }
            },
        )
        DashboardRow(
            label = stringResource(R.string.autostart_row_label),
            icon = Icons.Filled.RestartAlt,
            sub = stringResource(R.string.autostart_row_sub),
            dividerAbove = true,
            trailing = {
                Switch(
                    checked = uiState.autoStartEnabled,
                    onCheckedChange = onAutoStartEnabledChange,
                    colors = bloviateSwitchColors(),
                )
            },
        )
    }
}

/**
 * The Bubble look card (ENC-68): the four controls for the bubble's
 * appearance, each the control that matches its job — a slider for
 * opacity (with the live preview beside it, so the drag is judged
 * against the thing itself), a segmented control for the size steps,
 * and radio swatch groups for silhouette and tint. Every change
 * persists immediately and restyles the showing bubble; the preview
 * here is rendered with the same shape/tint mapping the overlay uses,
 * so what is picked is what floats.
 *
 * The opacity slider keeps a local draft while dragging: the persisted
 * value (and the bubble over the host app) updates on release, while
 * the preview tracks the draft continuously.
 */
@Composable
private fun BubbleLookCard(
    appearance: BubbleAppearanceUi,
    onOpacityChange: (Float) -> Unit,
    onSizeChange: (BubbleSizeUi) -> Unit,
    onShapeChange: (BubbleShapeUi) -> Unit,
    onTintChange: (BubbleTintUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftOpacity by remember(appearance.opacity) { mutableFloatStateOf(appearance.opacity) }
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
            ) {
                BubblePreview(appearance = appearance, opacity = draftOpacity)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BubbleLookLabel(text = stringResource(R.string.bubble_look_opacity))
                        Text(
                            text = stringResource(
                                R.string.bubble_look_opacity_format,
                                (draftOpacity * PERCENT_WHOLE).roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = draftOpacity,
                        onValueChange = { draftOpacity = it },
                        onValueChangeFinished = { onOpacityChange(draftOpacity) },
                        valueRange = BubbleAppearance.MIN_OPACITY..BubbleAppearance.MAX_OPACITY,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8)) {
                BubbleLookLabel(text = stringResource(R.string.bubble_look_size))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BubbleSizeUi.entries.forEachIndexed { index, size ->
                        SizeSegment(
                            label = stringResource(size.labelRes()),
                            selected = appearance.size == size,
                            index = index,
                            onClick = { onSizeChange(size) },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8)) {
                BubbleLookLabel(text = stringResource(R.string.bubble_look_shape))
                BubbleShapeOptions(
                    selected = appearance.shape,
                    appearance = appearance,
                    onShapeChange = onShapeChange,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8)) {
                BubbleLookLabel(text = stringResource(R.string.bubble_look_color))
                BubbleTintOptions(
                    selected = appearance.tint,
                    onTintChange = onTintChange,
                )
            }
        }
    }
}

/** A control group's label inside the Bubble look card: the row label voice, without the row. */
@Composable
private fun BubbleLookLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * The live preview: the bubble itself, at a card-sized rendering of the
 * chosen size step, wearing the chosen silhouette and tint at the
 * (draft) opacity — same mapping functions as the overlay, so the card
 * can never promise a look the bubble doesn't wear.
 */
@Composable
private fun BubblePreview(
    appearance: BubbleAppearanceUi,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val previewSize = when (appearance.size) {
        BubbleSizeUi.SMALL -> PREVIEW_SIZE_SMALL
        BubbleSizeUi.MEDIUM -> PREVIEW_SIZE_MEDIUM
        BubbleSizeUi.LARGE -> PREVIEW_SIZE_LARGE
    }
    val tint = appearance.tint.toDomain().tintColors()
    val description = stringResource(R.string.bubble_look_preview_description)
    Surface(
        modifier = modifier
            .size(previewSize)
            .graphicsLayer { alpha = opacity }
            .semantics { contentDescription = description },
        shape = appearance.shape.toDomain().bubbleShape(),
        color = tint.container,
        contentColor = tint.content,
        shadowElevation = PREVIEW_ELEVATION,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(previewSize * PREVIEW_ICON_FRACTION),
            )
        }
    }
}

/** The silhouette radio group: a swatch of the shape itself (in the current tint), captioned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleShapeOptions(
    selected: BubbleShapeUi,
    appearance: BubbleAppearanceUi,
    onShapeChange: (BubbleShapeUi) -> Unit,
) {
    val tint = appearance.tint.toDomain().tintColors()
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
    ) {
        BubbleShapeUi.entries.forEach { shape ->
            val isSelected = shape == selected
            Column(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onShapeChange(shape) },
                    )
                    .padding(BloviateSpacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH_SIZE)
                        .background(color = tint.container, shape = shape.toDomain().bubbleShape())
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = SELECTED_RING_WIDTH,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = shape.toDomain().bubbleShape(),
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
                Text(
                    text = stringResource(shape.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The tint radio group: each swatch is the tint's container disc with its content dot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleTintOptions(
    selected: BubbleTintUi,
    onTintChange: (BubbleTintUi) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
    ) {
        BubbleTintUi.entries.forEach { tint ->
            val isSelected = tint == selected
            val colors = tint.toDomain().tintColors()
            Column(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onTintChange(tint) },
                    )
                    .padding(BloviateSpacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH_SIZE)
                        .background(color = colors.container, shape = CircleShape)
                        .border(
                            width = if (isSelected) SELECTED_RING_WIDTH else HAIRLINE_WIDTH,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(SWATCH_DOT_SIZE)
                            .background(color = colors.content, shape = CircleShape),
                    )
                }
                Text(
                    text = stringResource(tint.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.SizeSegment(
    label: String,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = SIZE_SEGMENT_COUNT),
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primary,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            inactiveContainerColor = MaterialTheme.colorScheme.surface,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        icon = {},
    ) {
        Text(text = label)
    }
}

private fun BubbleSizeUi.labelRes(): Int =
    when (this) {
        BubbleSizeUi.SMALL -> R.string.bubble_size_small
        BubbleSizeUi.MEDIUM -> R.string.bubble_size_medium
        BubbleSizeUi.LARGE -> R.string.bubble_size_large
    }

private fun BubbleShapeUi.labelRes(): Int =
    when (this) {
        BubbleShapeUi.PILL -> R.string.bubble_shape_pill
        BubbleShapeUi.SQUIRCLE -> R.string.bubble_shape_squircle
        BubbleShapeUi.COOKIE -> R.string.bubble_shape_cookie
        BubbleShapeUi.CLOVER -> R.string.bubble_shape_clover
        BubbleShapeUi.ARCH -> R.string.bubble_shape_arch
    }

private fun BubbleTintUi.labelRes(): Int =
    when (this) {
        BubbleTintUi.INDIGO -> R.string.bubble_tint_indigo
        BubbleTintUi.NEUTRAL -> R.string.bubble_tint_neutral
        BubbleTintUi.GREEN -> R.string.bubble_tint_green
        BubbleTintUi.AMBER -> R.string.bubble_tint_amber
        BubbleTintUi.ROSE -> R.string.bubble_tint_rose
        BubbleTintUi.SKY -> R.string.bubble_tint_sky
    }

/** Card-sized renderings of the size steps, for the preview only (the real bubble is 48/56/64dp). */
private val PREVIEW_SIZE_SMALL: Dp = 40.dp
private val PREVIEW_SIZE_MEDIUM: Dp = 48.dp
private val PREVIEW_SIZE_LARGE: Dp = 56.dp
private val PREVIEW_ELEVATION: Dp = 6.dp
private const val PREVIEW_ICON_FRACTION: Float = 0.5f
private val SWATCH_SIZE: Dp = 40.dp
private val SWATCH_DOT_SIZE: Dp = 14.dp
private val SELECTED_RING_WIDTH: Dp = 2.dp
private val HAIRLINE_WIDTH: Dp = 1.dp
private const val PERCENT_WHOLE: Float = 100f
private const val SIZE_SEGMENT_COUNT: Int = 3

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
            modifier = Modifier.padding(
                start = BloviateSpacing.s16,
                end = BloviateSpacing.s16,
                bottom = BloviateSpacing.s12,
            ),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
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
                ModelUiStatus.Absent -> Button(
                    onClick = onDownloadClick,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(text = stringResource(R.string.model_download))
                }
                is ModelUiStatus.Downloading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                is ModelUiStatus.Failed -> Button(
                    onClick = onDownloadClick,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
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
 * The setup-guide card (ENC-67): one row that restarts the first-run
 * wizard at its welcome step. The wizard's record flips back to
 * unfinished, the shell hands the screen over to it, and the walk's
 * live chips show everything already in place — re-running is a
 * review, not a reset of the settings themselves.
 */
@Composable
private fun SetupGuideCard(
    onRunSetupAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = stringResource(R.string.setup_rerun_row_label),
            icon = Icons.Filled.RestartAlt,
            sub = stringResource(R.string.setup_rerun_row_sub),
            onClick = onRunSetupAgain,
        )
    }
}

/**
 * The signed-in account (ENC-197): who the app is signed in as, with
 * the way out. Shown only while the gate holds a Google session — the
 * launcher shows the sign-in screen otherwise, so there is no
 * signed-out rendering of this card.
 */
@Composable
private fun AccountCard(
    auth: AuthUi,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signedIn = auth as? AuthUi.SignedIn ?: return
    DashboardCard(modifier = modifier) {
        DashboardRow(
            label = signedIn.displayName ?: signedIn.email
                ?: stringResource(R.string.account_unknown),
            icon = Icons.Filled.AccountCircle,
            sub = signedIn.displayName?.let { signedIn.email }
                ?: stringResource(R.string.account_google),
            trailing = {
                Button(
                    onClick = onSignOut,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(text = stringResource(R.string.account_sign_out))
                }
            },
        )
    }
}

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
            .padding(horizontal = BloviateSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
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
            onOpenBatterySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onAutoStartEnabledChange = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onBubbleOpacityChange = {},
            onBubbleSizeChange = {},
            onBubbleShapeChange = {},
            onBubbleTintChange = {},
            onSignOut = {},
            onRunSetupAgain = {},
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
                watcherConnected = true,
                overlayPermissionGranted = true,
                bubbleServiceRunning = true,
                bubbleEnabled = true,
                batteryExempt = true,
                micPermissionGranted = true,
                whisperModelStatus = ModelUiStatus.Ready,
                parakeetModelStatus = ModelUiStatus.Ready,
                selectedEngine = DictationEngineUi.PARAKEET,
                bubbleAppearance = BubbleAppearanceUi(
                    opacity = 0.7f,
                    size = BubbleSizeUi.LARGE,
                    shape = BubbleShapeUi.CLOVER,
                    tint = BubbleTintUi.SKY,
                ),
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onOpenBatterySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onAutoStartEnabledChange = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onBubbleOpacityChange = {},
            onBubbleSizeChange = {},
            onBubbleShapeChange = {},
            onBubbleTintChange = {},
            onSignOut = {},
            onRunSetupAgain = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Downloading - Dark", showBackground = true)
@Composable
private fun SetupScreenDownloadingPreview() {
    PreviewTheme {
        SetupScreen(
            uiState = MainUiState(
                watcherEnabled = true,
                watcherConnected = true,
                overlayPermissionGranted = true,
                bubbleServiceRunning = true,
                bubbleEnabled = true,
                batteryExempt = true,
                micPermissionGranted = true,
                whisperModelStatus = ModelUiStatus.Downloading(percent = 42),
                parakeetModelStatus = ModelUiStatus.Absent,
                selectedEngine = DictationEngineUi.WHISPER,
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onOpenBatterySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onAutoStartEnabledChange = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onBubbleOpacityChange = {},
            onBubbleSizeChange = {},
            onBubbleShapeChange = {},
            onBubbleTintChange = {},
            onSignOut = {},
            onRunSetupAgain = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Off - Dark", showBackground = true)
@Composable
private fun SetupScreenOffPreview() {
    PreviewTheme {
        SetupScreen(
            uiState = MainUiState(
                watcherEnabled = true,
                watcherConnected = true,
                overlayPermissionGranted = true,
                bubbleEnabled = false,
                batteryExempt = true,
                micPermissionGranted = true,
                whisperModelStatus = ModelUiStatus.Ready,
                parakeetModelStatus = ModelUiStatus.Ready,
                selectedEngine = DictationEngineUi.PARAKEET,
            ),
            onOpenAccessibilitySettings = {},
            onOpenOverlaySettings = {},
            onOpenBatterySettings = {},
            onStartBubble = {},
            onStopBubble = {},
            onAutoStartEnabledChange = {},
            onRequestMicPermission = {},
            onWhisperDownloadClick = {},
            onParakeetDownloadClick = {},
            onSelectEngine = {},
            onBubbleOpacityChange = {},
            onBubbleSizeChange = {},
            onBubbleShapeChange = {},
            onBubbleTintChange = {},
            onSignOut = {},
            onRunSetupAgain = {},
        )
    }
}
