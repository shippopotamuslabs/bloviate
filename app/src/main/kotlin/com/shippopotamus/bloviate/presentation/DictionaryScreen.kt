package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R

/** Wires [DictionaryViewModel] state into the Dictionary tab; the route is the only place the ViewModel is touched. */
@Composable
fun DictionaryRoute(
    viewModel: DictionaryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DictionaryScreen(
        uiState = uiState,
        onWordDraftChange = viewModel::onWordDraftChange,
        onHeardAsDraftChange = viewModel::onHeardAsDraftChange,
        onReplacementDraftChange = viewModel::onReplacementDraftChange,
        onAddWordClick = viewModel::onAddWordClick,
        onRemoveWordClick = viewModel::onRemoveWordClick,
        onAddCorrectionClick = viewModel::onAddCorrectionClick,
        onRemoveCorrectionClick = viewModel::onRemoveCorrectionClick,
        modifier = modifier,
    )
}

/**
 * The Dictionary tab (ENC-74, dashboard form ENC-75/ENC-79): the user's
 * personal dictionary — words whose spelling and capitalization
 * Bloviate enforces, and corrections for the mis-hearings that keep
 * happening. Dashboard tightening only: section headers carry the
 * counts, the intro paragraphs became one-line footers, and the add
 * forms slimmed down to placeholder-led fields inside the cards. The
 * store and edit behavior are untouched; changes still reach the very
 * next dictation, because the cleanup pipeline reads the dictionary at
 * send time — the header pill says so.
 *
 * The column scrolls, like Setup's: the lists grow with the user, and
 * every row must stay reachable on short viewports (ENC-65).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    uiState: DictionaryUiState,
    onWordDraftChange: (String) -> Unit,
    onHeardAsDraftChange: (String) -> Unit,
    onReplacementDraftChange: (String) -> Unit,
    onAddWordClick: () -> Unit,
    onRemoveWordClick: (String) -> Unit,
    onAddCorrectionClick: () -> Unit,
    onRemoveCorrectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberScreenHazeState()
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(scrollState)
    Scaffold(
        topBar = {
            ShippopotamusTopBar(
                title = { Text(text = stringResource(R.string.tab_dictionary)) },
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
                actions = {
                    StatusPill(
                        text = stringResource(R.string.dictionary_pill),
                        tone = StatusTone.ACCENT,
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
            DashboardSectionHeader(
                text = stringResource(R.string.dictionary_words_title),
                trailingText = uiState.words.size.toString(),
            )
            WordsCard(
                uiState = uiState,
                onWordDraftChange = onWordDraftChange,
                onAddWordClick = onAddWordClick,
                onRemoveWordClick = onRemoveWordClick,
            )
            DictionaryFooter(text = stringResource(R.string.dictionary_words_footer))
            DashboardSectionHeader(
                text = stringResource(R.string.dictionary_corrections_title),
                trailingText = uiState.corrections.size.toString(),
            )
            CorrectionsCard(
                uiState = uiState,
                onHeardAsDraftChange = onHeardAsDraftChange,
                onReplacementDraftChange = onReplacementDraftChange,
                onAddCorrectionClick = onAddCorrectionClick,
                onRemoveCorrectionClick = onRemoveCorrectionClick,
            )
            DictionaryFooter(text = stringResource(R.string.dictionary_footer))
        }
    }
}

@Composable
private fun WordsCard(
    uiState: DictionaryUiState,
    onWordDraftChange: (String) -> Unit,
    onAddWordClick: () -> Unit,
    onRemoveWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        if (uiState.words.isEmpty()) {
            EmptyNote(text = stringResource(R.string.dictionary_words_empty))
        } else {
            uiState.words.forEachIndexed { index, word ->
                DashboardRow(
                    label = word,
                    dividerAbove = index > 0,
                    trailing = {
                        IconButton(
                            onClick = { onRemoveWordClick(word) },
                            shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.dictionary_remove_word,
                                    word,
                                ),
                            )
                        }
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
            ) {
                val fieldLabel = stringResource(R.string.dictionary_word_field_label)
                OutlinedTextField(
                    value = uiState.wordDraft,
                    onValueChange = onWordDraftChange,
                    placeholder = { Text(text = stringResource(R.string.dictionary_word_field_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = fieldLabel },
                )
                Button(
                    onClick = onAddWordClick,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(text = stringResource(R.string.dictionary_add))
                }
            }
            uiState.wordError?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CorrectionsCard(
    uiState: DictionaryUiState,
    onHeardAsDraftChange: (String) -> Unit,
    onReplacementDraftChange: (String) -> Unit,
    onAddCorrectionClick: () -> Unit,
    onRemoveCorrectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        if (uiState.corrections.isEmpty()) {
            EmptyNote(text = stringResource(R.string.dictionary_corrections_empty))
        } else {
            uiState.corrections.forEachIndexed { index, correction ->
                DashboardRow(
                    label = stringResource(
                        R.string.dictionary_correction_format,
                        correction.heardAs,
                        correction.replacement,
                    ),
                    dividerAbove = index > 0,
                    trailing = {
                        IconButton(
                            onClick = { onRemoveCorrectionClick(correction.heardAs) },
                            shapes = IconButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.dictionary_remove_correction,
                                    correction.heardAs,
                                ),
                            )
                        }
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
            ) {
                val heardAsLabel = stringResource(R.string.dictionary_heard_as_label)
                OutlinedTextField(
                    value = uiState.heardAsDraft,
                    onValueChange = onHeardAsDraftChange,
                    placeholder = { Text(text = heardAsLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = heardAsLabel },
                )
                val replacementLabel = stringResource(R.string.dictionary_replacement_label)
                OutlinedTextField(
                    value = uiState.replacementDraft,
                    onValueChange = onReplacementDraftChange,
                    placeholder = { Text(text = replacementLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = replacementLabel },
                )
                Button(
                    onClick = onAddCorrectionClick,
                    shapes = ButtonDefaults.shapes(pressedShape = ShippopotamusPressedShape),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(text = stringResource(R.string.dictionary_add))
                }
            }
            uiState.correctionError?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** An empty list's one quiet line inside its card, in place of rows. */
@Composable
private fun EmptyNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(BloviateSpacing.s16),
    )
}

/** A section's one-line footer — where the intro paragraphs went (ENC-79). */
@Composable
private fun DictionaryFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = BloviateSpacing.s4),
    )
}

/** Each card only ever holds its own cases (the ViewModel maps add outcomes per form); the mapping is total regardless. */
private fun DictionaryEditError.messageRes(): Int =
    when (this) {
        DictionaryEditError.BLANK_WORD -> R.string.dictionary_error_blank_word
        DictionaryEditError.DUPLICATE_WORD -> R.string.dictionary_error_duplicate_word
        DictionaryEditError.BLANK_CORRECTION -> R.string.dictionary_error_blank_correction
        DictionaryEditError.DUPLICATE_CORRECTION -> R.string.dictionary_error_duplicate_correction
        DictionaryEditError.SELF_CORRECTION -> R.string.dictionary_error_self_correction
    }

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Dictionary empty - Dark", showBackground = true)
@Composable
private fun DictionaryScreenEmptyPreview() {
    PreviewTheme {
        DictionaryScreen(
            uiState = DictionaryUiState(),
            onWordDraftChange = {},
            onHeardAsDraftChange = {},
            onReplacementDraftChange = {},
            onAddWordClick = {},
            onRemoveWordClick = {},
            onAddCorrectionClick = {},
            onRemoveCorrectionClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Dictionary populated - Dark", showBackground = true)
@Composable
private fun DictionaryScreenPopulatedPreview() {
    PreviewTheme {
        DictionaryScreen(
            uiState = DictionaryUiState(
                words = listOf("Bloviate", "Parakeet", "Shippopotamus"),
                corrections = listOf(
                    CorrectionUi(heardAs = "perikit", replacement = "Parakeet"),
                    CorrectionUi(heardAs = "below v8", replacement = "Bloviate"),
                ),
            ),
            onWordDraftChange = {},
            onHeardAsDraftChange = {},
            onReplacementDraftChange = {},
            onAddWordClick = {},
            onRemoveWordClick = {},
            onAddCorrectionClick = {},
            onRemoveCorrectionClick = {},
        )
    }
}
