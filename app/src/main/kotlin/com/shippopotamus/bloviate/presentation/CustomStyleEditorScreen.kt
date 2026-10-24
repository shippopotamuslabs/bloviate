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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shippopotamus.bloviate.R

/**
 * The manual custom-style editor (BLO-25 S5): the in-tab destination for
 * creating a style or editing one by id. Owns its own Scaffold + TopAppBar
 * (ARCHITECTURE_RULES.md §4) with an Up affordance; the route owns the
 * dirty-discards confirmation and the back navigation, so every exit goes
 * through [onBack].
 *
 * Fields carry character hints from the validator caps; validation is
 * live, so the save button stays disabled until the draft is valid. The
 * screen never sees domain types — errors arrive as string resources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomStyleEditorScreen(
    uiState: CustomStyleEditorUiState,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSampleChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onPairRawChange: (Int, String) -> Unit,
    onPairStyledChange: (Int, String) -> Unit,
    onAddPair: () -> Unit,
    onRemovePair: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onSaveErrorDismiss: () -> Unit,
    onRegenerateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isCreate) {
                                R.string.style_editor_title_create
                            } else {
                                R.string.style_editor_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.style_editor_back),
                        )
                    }
                },
                actions = {
                    // Generated creates (BLO-160 S7) can redraw the draft
                    // from the retained description; edit-existing and
                    // manual creates never show this.
                    if (uiState.isGenerated) {
                        if (uiState.isRegenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = BloviateSpacing.s12)
                                    .size(BloviateSpacing.s24),
                            )
                        } else {
                            IconButton(
                                onClick = onRegenerateClick,
                                shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                                enabled = !uiState.isSaving,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.style_generate_regenerate),
                                )
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        if (uiState.loadFailed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(BloviateSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
            ) {
                Text(
                    text = stringResource(R.string.style_editor_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
        ) {
            Text(
                text = stringResource(R.string.style_editor_fidelity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BloviateSpacing.s4),
            )
            EditorField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChange,
                label = stringResource(R.string.style_editor_name_label),
                placeholder = stringResource(R.string.style_editor_name_hint),
                errorRes = uiState.fieldErrors[CustomStyleEditorField.DISPLAY_NAME],
                counter = stringResource(
                    R.string.style_editor_counter_format,
                    uiState.displayName.length,
                    MAX_CUSTOM_STYLE_NAME_CHARS,
                ),
                singleLine = true,
            )
            EditorField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.style_editor_description_label),
                placeholder = stringResource(R.string.style_editor_description_hint),
                errorRes = uiState.fieldErrors[CustomStyleEditorField.DESCRIPTION],
                counter = stringResource(
                    R.string.style_editor_counter_format,
                    uiState.description.length,
                    MAX_CUSTOM_STYLE_DESCRIPTION_CHARS,
                ),
                singleLine = true,
            )
            EditorField(
                value = uiState.sample,
                onValueChange = onSampleChange,
                label = stringResource(R.string.style_editor_sample_label),
                placeholder = stringResource(R.string.style_editor_sample_hint),
                errorRes = uiState.fieldErrors[CustomStyleEditorField.SAMPLE],
                counter = stringResource(
                    R.string.style_editor_counter_format,
                    uiState.sample.length,
                    MAX_CUSTOM_STYLE_SAMPLE_CHARS,
                ),
                singleLine = false,
            )
            EditorField(
                value = uiState.systemInstructions,
                onValueChange = onInstructionsChange,
                label = stringResource(R.string.style_editor_instructions_label),
                placeholder = stringResource(R.string.style_editor_instructions_hint),
                errorRes = uiState.fieldErrors[CustomStyleEditorField.INSTRUCTIONS],
                counter = stringResource(
                    R.string.style_editor_counter_format,
                    uiState.systemInstructions.length,
                    MAX_CUSTOM_STYLE_INSTRUCTIONS_CHARS,
                ),
                singleLine = false,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s4),
                modifier = Modifier.padding(horizontal = BloviateSpacing.s4),
            ) {
                Text(
                    text = stringResource(R.string.style_editor_pairs_title).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.style_editor_pairs_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DashboardCard {
                uiState.pairs.forEachIndexed { index, pair ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    PairEditor(
                        pair = pair,
                        index = index,
                        canRemove = uiState.canRemovePair,
                        onRawChange = { value -> onPairRawChange(index, value) },
                        onStyledChange = { value -> onPairStyledChange(index, value) },
                        onRemove = { onRemovePair(index) },
                    )
                }
            }
            if (uiState.canAddPair) {
                TextButton(
                    onClick = onAddPair,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(text = stringResource(R.string.style_editor_pair_add))
                }
            }
            uiState.saveError?.let { error ->
                Column(modifier = Modifier.padding(horizontal = BloviateSpacing.s4)) {
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onSaveErrorDismiss,
                        shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(text = stringResource(R.string.style_error_dismiss))
                    }
                }
            }
            // A failed regenerate keeps the draft and reports inline, like
            // a failed save; the next keystroke or retry clears it.
            uiState.regenerateError?.let { error ->
                Column(modifier = Modifier.padding(horizontal = BloviateSpacing.s4)) {
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onSaveErrorDismiss,
                        shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(text = stringResource(R.string.style_error_dismiss))
                    }
                }
            }
            Button(
                onClick = onSaveClick,
                shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                contentPadding = ButtonDefaults.ContentPadding,
                enabled = uiState.isValid && !uiState.isSaving && !uiState.isRegenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.style_editor_saving),
                        modifier = Modifier.padding(start = BloviateSpacing.s8),
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (uiState.isCreate) {
                                R.string.style_editor_save_create
                            } else {
                                R.string.style_editor_save_edit
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** One labeled form field with its character hint and, when invalid, its message. */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    errorRes: Int?,
    counter: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        singleLine = singleLine,
        isError = errorRes != null,
        supportingText = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (errorRes != null) stringResource(errorRes) else "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = counter,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    )
}

/** One few-shot example: the dictated side, the styled side, and its remove button. */
@Composable
private fun PairEditor(
    pair: FewShotPairUi,
    index: Int,
    canRemove: Boolean,
    onRawChange: (String) -> Unit,
    onStyledChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        modifier = modifier.padding(BloviateSpacing.s16),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.style_editor_pair_title_format, index + 1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.style_editor_pair_remove_format,
                            index + 1,
                        ),
                    )
                }
            }
        }
        val rawLabel = stringResource(R.string.style_editor_pair_raw_label)
        OutlinedTextField(
            value = pair.raw,
            onValueChange = onRawChange,
            label = { Text(text = rawLabel) },
            isError = pair.rawErrorRes != null,
            supportingText = pair.rawErrorRes?.let { error ->
                { Text(text = stringResource(error), color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = rawLabel },
        )
        val styledLabel = stringResource(R.string.style_editor_pair_styled_label)
        OutlinedTextField(
            value = pair.styled,
            onValueChange = onStyledChange,
            label = { Text(text = styledLabel) },
            isError = pair.styledErrorRes != null,
            supportingText = pair.styledErrorRes?.let { error ->
                { Text(text = stringResource(error), color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = styledLabel },
        )
    }
}

/** Each save failure maps to exactly one documented message; the mapping is total regardless of outcome. */
private fun CustomStyleSaveError.messageRes(): Int =
    when (this) {
        CustomStyleSaveError.CONFLICT -> R.string.style_editor_save_error_conflict
        CustomStyleSaveError.LIMIT_REACHED -> R.string.style_editor_save_error_limit
        CustomStyleSaveError.NOT_ENTITLED -> R.string.style_editor_save_error_not_entitled
        CustomStyleSaveError.SIGNED_OUT -> R.string.style_editor_save_error_signed_out
        CustomStyleSaveError.UNAVAILABLE -> R.string.style_editor_save_error_unavailable
        CustomStyleSaveError.INVALID -> R.string.style_editor_save_error_invalid
    }

/** Every preview renders the editor with a fake state — never a ViewModel. */
@Composable
private fun EditorScreenPreview(uiState: CustomStyleEditorUiState) {
    PreviewTheme {
        CustomStyleEditorScreen(
            uiState = uiState,
            onBack = {},
            onDisplayNameChange = {},
            onDescriptionChange = {},
            onSampleChange = {},
            onInstructionsChange = {},
            onPairRawChange = { _, _ -> },
            onPairStyledChange = { _, _ -> },
            onAddPair = {},
            onRemovePair = {},
            onSaveClick = {},
            onSaveErrorDismiss = {},
        )
    }
}

private fun previewPair(raw: String, styled: String): FewShotPairUi =
    FewShotPairUi(raw = raw, styled = styled)

private fun populatedEditorState(): CustomStyleEditorUiState =
    CustomStyleEditorUiState(
        isCreate = false,
        displayName = "Noir Detective",
        description = "Rain, neon, and regret.",
        sample = "The rain kept falling, and so did I.",
        systemInstructions = "Short sentences. Rain everywhere. NEVER change facts.",
        pairs = listOf(
            previewPair(raw = "i walked home late", styled = "I walked home late, the rain keeping time."),
            previewPair(raw = "the meeting is at noon", styled = "The meeting is at noon — high noon, the dangerous hour."),
        ),
        isValid = true,
        canAddPair = true,
        canRemovePair = false,
        isDirty = true,
    )

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor create empty - Dark", showBackground = true)
@Composable
private fun EditorCreateEmptyPreview() {
    EditorScreenPreview(
        uiState = CustomStyleEditorUiState(
            pairs = listOf(FewShotPairUi(), FewShotPairUi()),
            isDirty = false,
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor edit populated - Dark", showBackground = true)
@Composable
private fun EditorEditPopulatedPreview() {
    EditorScreenPreview(uiState = populatedEditorState())
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor validation error - Dark", showBackground = true)
@Composable
private fun EditorValidationErrorPreview() {
    EditorScreenPreview(
        uiState = populatedEditorState().copy(
            displayName = "",
            fieldErrors = mapOf(
                CustomStyleEditorField.DISPLAY_NAME to R.string.style_editor_error_name_blank,
            ),
            pairs = listOf(
                previewPair(raw = "", styled = "The rain kept falling."),
                previewPair(raw = "the meeting is at noon", styled = ""),
            ).mapIndexed { index, pair ->
                if (index == 0) {
                    pair.copy(rawErrorRes = R.string.style_editor_error_pair_raw_blank)
                } else {
                    pair.copy(styledErrorRes = R.string.style_editor_error_pair_styled_blank)
                }
            },
            isValid = false,
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor saving - Dark", showBackground = true)
@Composable
private fun EditorSavingPreview() {
    EditorScreenPreview(
        uiState = populatedEditorState().copy(isSaving = true),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor generated prefilled - Dark", showBackground = true)
@Composable
private fun EditorGeneratedPreview() {
    EditorScreenPreview(
        uiState = populatedEditorState().copy(isGenerated = true, isDirty = false),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor regenerating - Dark", showBackground = true)
@Composable
private fun EditorRegeneratingPreview() {
    EditorScreenPreview(
        uiState = populatedEditorState().copy(isGenerated = true, isDirty = false, isRegenerating = true),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Editor regenerate error - Dark", showBackground = true)
@Composable
private fun EditorRegenerateErrorPreview() {
    EditorScreenPreview(
        uiState = populatedEditorState().copy(
            isGenerated = true,
            regenerateError = CustomStyleGenerationError.UNAVAILABLE,
        ),
    )
}
