package com.shippopotamus.bloviate.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R

/** Wires [StyleViewModel] state into the Style tab; the route is the only place the ViewModels are touched. */
@Composable
fun StyleRoute(
    viewModel: StyleViewModel = hiltViewModel(),
    editorViewModel: CustomStyleEditorViewModel = hiltViewModel(),
    generationViewModel: CustomStyleGenerationViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // In-tab destinations: the list, the generation Start screen, or the
    // editor (null id is a create). No NavController — the app shell has
    // no nested graphs (AppShell), so remembered destinations are the
    // whole navigation model. Leaving the tab drops this composition,
    // which resets the destination to the list.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var generateOpen by rememberSaveable { mutableStateOf(false) }
    // The transient handoff from a successful generation: the draft plus
    // its source description, consumed once by the editor effect below.
    // Plain remember — a generated draft is transient UI state, never
    // persisted (see the schema lock).
    var pendingHandoff by remember { mutableStateOf<GeneratedHandoff?>(null) }
    var handoffEpoch by remember { mutableIntStateOf(0) }
    if (generateOpen) {
        val generationState by generationViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            generationViewModel.generateSucceeded.collect { handoff ->
                pendingHandoff = handoff
                handoffEpoch++
                editingId = null
                generateOpen = false
                editorOpen = true
            }
        }
        // Backing out mid-generation cancels the call, so no late draft
        // can open an editor the user already left.
        val cancelGenerationAndClose = {
            generationViewModel.onCancelInFlight()
            generateOpen = false
        }
        BackHandler(onBack = cancelGenerationAndClose)
        CustomStyleGenerationScreen(
            uiState = generationState,
            onBack = cancelGenerationAndClose,
            onDescriptionChange = generationViewModel::onDescriptionChange,
            onGenerateClick = generationViewModel::onGenerateClick,
            onStartBlankClick = {
                editingId = null
                generateOpen = false
                editorOpen = true
            },
            onErrorDismiss = generationViewModel::onErrorDismiss,
            modifier = modifier,
        )
    } else if (editorOpen) {
        val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
        var discardConfirm by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(editingId, handoffEpoch) {
            val handoff = pendingHandoff
            if (handoff != null && editingId == null) {
                pendingHandoff = null
                editorViewModel.loadGenerated(handoff.draft, handoff.sourceDescription)
            } else {
                editorViewModel.load(editingId)
            }
        }
        LaunchedEffect(Unit) {
            editorViewModel.saveSucceeded.collect { editorOpen = false }
        }
        BackHandler(enabled = editorState.isDirty) {
            discardConfirm = true
        }
        CustomStyleEditorScreen(
            uiState = editorState,
            onBack = {
                if (editorState.isDirty) {
                    discardConfirm = true
                } else {
                    editorOpen = false
                }
            },
            onDisplayNameChange = editorViewModel::onDisplayNameChange,
            onDescriptionChange = editorViewModel::onDescriptionChange,
            onSampleChange = editorViewModel::onSampleChange,
            onInstructionsChange = editorViewModel::onInstructionsChange,
            onPairRawChange = editorViewModel::onPairRawChange,
            onPairStyledChange = editorViewModel::onPairStyledChange,
            onAddPair = editorViewModel::onAddPair,
            onRemovePair = editorViewModel::onRemovePair,
            onSaveClick = editorViewModel::onSaveClick,
            onSaveErrorDismiss = editorViewModel::onSaveErrorDismiss,
            onRegenerateClick = editorViewModel::onRegenerateClick,
            modifier = modifier,
        )
        if (discardConfirm) {
            AlertDialog(
                onDismissRequest = { discardConfirm = false },
                title = { Text(text = stringResource(R.string.style_editor_discard_title)) },
                text = { Text(text = stringResource(R.string.style_editor_discard_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            discardConfirm = false
                            editorOpen = false
                        },
                    ) {
                        Text(text = stringResource(R.string.style_editor_discard_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { discardConfirm = false }) {
                        Text(text = stringResource(R.string.style_editor_discard_keep))
                    }
                },
            )
        }
        // Regenerating over a dirty draft discards its edits first, like
        // the back-navigation discard above; a clean draft regenerates
        // without asking.
        if (editorState.regenerateConfirmPending) {
            AlertDialog(
                onDismissRequest = editorViewModel::onRegenerateDismissConfirm,
                title = { Text(text = stringResource(R.string.style_generate_confirm_regenerate_title)) },
                text = { Text(text = stringResource(R.string.style_generate_confirm_regenerate_message)) },
                confirmButton = {
                    TextButton(onClick = editorViewModel::onRegenerateConfirm) {
                        Text(text = stringResource(R.string.style_generate_confirm_regenerate_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = editorViewModel::onRegenerateDismissConfirm) {
                        Text(text = stringResource(R.string.style_editor_discard_keep))
                    }
                },
            )
        }
    } else {
        StyleScreen(
            uiState = uiState,
            onStyleSelected = viewModel::onStyleSelected,
            onCreateCustomClick = {
                generateOpen = true
            },
            onEditCustomClick = { styleId ->
                editingId = styleId
                editorOpen = true
            },
            onDeleteCustomClick = viewModel::onDeleteCustomClick,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onDeleteErrorDismiss = viewModel::onDeleteErrorDismiss,
            modifier = modifier,
        )
    }
}

/**
 * The Style tab (ENC-72 + ENC-73, dashboard form ENC-75/ENC-78): the
 * styles are a radio LIST — name, one-line description, and an
 * on-device/AI tag — with the sample text rendered only under the
 * selected style. Every style in the list is selectable.
 *
 * The My-styles section always renders: the create entry opens the
 * generation Start screen (BLO-160 S7), and custom rows carry Edit and
 * Delete next to the radio.
 * Deletes confirm first; a failed delete surfaces as an inline error
 * with a dismiss, like the Dictionary tab's rejections.
 *
 * The column scrolls: every row must stay reachable on short viewports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleScreen(
    uiState: StyleUiState,
    onStyleSelected: (String) -> Unit,
    onCreateCustomClick: () -> Unit,
    onEditCustomClick: (String) -> Unit,
    onDeleteCustomClick: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_style)) },
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
            uiState.sections.forEach { section ->
                DashboardSectionHeader(
                    text = stringResource(section.headerRes),
                )
                if (section.headerRes == R.string.style_section_custom) {
                    DashboardCard {
                        CreateCustomRow(onClick = onCreateCustomClick)
                        section.styles.forEach { option ->
                            CustomStyleRow(
                                option = option,
                                onSelect = { onStyleSelected(option.id) },
                                onEdit = { onEditCustomClick(option.id) },
                                onDelete = { onDeleteCustomClick(option.id) },
                            )
                        }
                    }
                    uiState.deleteError?.let { error ->
                        DeleteErrorLine(
                            error = error,
                            onDismiss = onDeleteErrorDismiss,
                        )
                    }
                } else {
                    DashboardCard {
                        section.styles.forEachIndexed { index, option ->
                            StyleListRow(
                                option = option,
                                dividerAbove = index > 0,
                                onSelect = { onStyleSelected(option.id) },
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.style_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BloviateSpacing.s4),
            )
        }
    }
    uiState.pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = stringResource(R.string.style_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.style_delete_message_format,
                        pending.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(text = stringResource(R.string.style_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(text = stringResource(R.string.style_delete_cancel))
                }
            },
        )
    }
}

/** The create entry: always the first row of the My-styles card, even with zero customs. */
@Composable
private fun CreateCustomRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.style_create_custom)
    DashboardRow(
        label = label,
        sub = stringResource(R.string.style_create_custom_sub),
        icon = Icons.Filled.Add,
        onClick = onClick,
        contentDescription = label,
        dividerAbove = false,
        modifier = modifier,
    )
}

/** One style in the radio list; the sample renders only under the selected row (ENC-78). */
@Composable
private fun StyleListRow(
    option: StyleOptionUi,
    dividerAbove: Boolean,
    onSelect: () -> Unit,
) {
    val tag = stringResource(
        if (option.requiresAi) R.string.style_tag_ai else R.string.style_tag_on_device,
    )
    DashboardRow(
        label = option.displayName,
        sub = "${option.description} · $tag",
        leading = {
            RadioButton(
                selected = option.isSelected,
                onClick = null,
            )
        },
        onClick = onSelect,
        clickRole = Role.RadioButton,
        contentDescription = stringResource(R.string.style_row_select, option.displayName),
        showChevron = false,
        dividerAbove = dividerAbove,
        below = if (option.isSelected) {
            { StyleSample(sample = option.sample) }
        } else {
            null
        },
    )
}

/**
 * One custom style: the same radio select as every style, plus Edit and
 * Delete icon buttons. The row itself selects; the buttons are the only
 * edit/delete affordances, each labeled for TalkBack.
 */
@Composable
private fun CustomStyleRow(
    option: StyleOptionUi,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = stringResource(
        if (option.requiresAi) R.string.style_tag_ai else R.string.style_tag_on_device,
    )
    DashboardRow(
        label = option.displayName,
        sub = "${option.description} · $tag",
        leading = {
            RadioButton(
                selected = option.isSelected,
                onClick = null,
            )
        },
        onClick = onSelect,
        clickRole = Role.RadioButton,
        contentDescription = stringResource(R.string.style_row_select, option.displayName),
        showChevron = false,
        dividerAbove = true,
        trailing = {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.style_edit_custom, option.displayName),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.style_delete_custom, option.displayName),
                )
            }
        },
        below = if (option.isSelected) {
            { StyleSample(sample = option.sample) }
        } else {
            null
        },
        modifier = modifier,
    )
}

/** A failed delete as an inline error line with a dismiss, like the Dictionary tab's rejections. */
@Composable
private fun DeleteErrorLine(
    error: CustomStyleDeleteError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = BloviateSpacing.s4)) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.style_error_dismiss))
        }
    }
}

/** Each delete failure maps to exactly one documented message; the mapping is total regardless of outcome. */
private fun CustomStyleDeleteError.messageRes(): Int =
    when (this) {
        CustomStyleDeleteError.SIGNED_OUT -> R.string.style_delete_error_signed_out
        CustomStyleDeleteError.NOT_ENTITLED -> R.string.style_delete_error_not_entitled
        CustomStyleDeleteError.INVALID -> R.string.style_delete_error_invalid
        CustomStyleDeleteError.CONFLICT -> R.string.style_delete_error_conflict
        CustomStyleDeleteError.LIMIT_REACHED -> R.string.style_delete_error_limit
        CustomStyleDeleteError.UNAVAILABLE -> R.string.style_delete_error_unavailable
    }

/**
 * The selected style's sample, inset under its row so the list pays for
 * one sample, not nine. The start inset (s48) aligns the sample with the
 * row's text: row padding s16 + leading gap s12 + the radio glyph.
 */
@Composable
private fun StyleSample(
    sample: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = BloviateSpacing.s48,
                end = BloviateSpacing.s16,
                bottom = BloviateSpacing.s12,
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = BloviateSpacing.s12, vertical = BloviateSpacing.s8),
    ) {
        Text(
            text = sample,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun previewUiState(): StyleUiState =
    StyleUiState(
        sections = listOf(
            StyleSectionUi(
                headerRes = R.string.style_section_custom,
                styles = listOf(
                    StyleOptionUi(
                        id = "custom-noir",
                        displayName = "Noir Detective",
                        description = "Rain, neon, and regret.",
                        sample = "The rain kept falling, and so did I.",
                        requiresAi = true,
                        isSelected = true,
                        isCustom = true,
                    ),
                ),
            ),
            StyleSectionUi(
                headerRes = R.string.style_section_serious,
                styles = listOf(
                    StyleOptionUi(
                        id = "lowercase-casual",
                        displayName = "lowercase casual",
                        description = "all lowercase, relaxed and off-the-cuff.",
                        sample = "yeah so i was thinking we could just go tomorrow",
                        requiresAi = false,
                        isSelected = false,
                    ),
                    StyleOptionUi(
                        id = "normal-caps-casual",
                        displayName = "Normal Caps Casual",
                        description = "Just how you said it, cleaned up.",
                        sample = "Yeah, so I was thinking we could just go tomorrow.",
                        requiresAi = false,
                        isSelected = false,
                    ),
                ),
            ),
            StyleSectionUi(
                headerRes = R.string.style_section_philosophers,
                styles = listOf(
                    StyleOptionUi(
                        id = "seneca",
                        displayName = "Seneca",
                        description = "Stoic letters on time, anger, and fortune.",
                        sample = "You act as if you will live forever; you never read the terms.",
                        requiresAi = true,
                        isSelected = false,
                    ),
                ),
            ),
        ),
    )

/** Every preview renders the screen with a fake state — never a ViewModel. */
@Composable
private fun StyleScreenPreview(uiState: StyleUiState) {
    PreviewTheme {
        StyleScreen(
            uiState = uiState,
            onStyleSelected = {},
            onCreateCustomClick = {},
            onEditCustomClick = {},
            onDeleteCustomClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onDeleteErrorDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style populated - Dark", showBackground = true)
@Composable
private fun StyleScreenPopulatedPreview() {
    StyleScreenPreview(uiState = previewUiState())
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style empty customs - Dark", showBackground = true)
@Composable
private fun StyleScreenEmptyCustomsPreview() {
    StyleScreenPreview(
        uiState = StyleUiState(
            sections = listOf(
                StyleSectionUi(headerRes = R.string.style_section_custom),
                StyleSectionUi(
                    headerRes = R.string.style_section_serious,
                    styles = listOf(
                        StyleOptionUi(
                            id = "normal-caps-casual",
                            displayName = "Normal Caps Casual",
                            description = "Just how you said it, cleaned up.",
                            sample = "Yeah, so I was thinking we could just go tomorrow.",
                            requiresAi = false,
                            isSelected = true,
                        ),
                    ),
                ),
            ),
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style delete confirm - Dark", showBackground = true)
@Composable
private fun StyleScreenDeleteConfirmPreview() {
    StyleScreenPreview(
        uiState = previewUiState().copy(
            pendingDelete = PendingStyleDelete(
                styleId = "custom-noir",
                displayName = "Noir Detective",
            ),
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style delete error - Dark", showBackground = true)
@Composable
private fun StyleScreenDeleteErrorPreview() {
    StyleScreenPreview(
        uiState = previewUiState().copy(deleteError = CustomStyleDeleteError.UNAVAILABLE),
    )
}
