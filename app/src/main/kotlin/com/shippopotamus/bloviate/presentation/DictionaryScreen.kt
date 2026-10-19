package com.shippopotamus.bloviate.presentation

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * The Dictionary tab (ENC-74): the user's personal dictionary — words
 * whose spelling and capitalization Bloviate enforces, and corrections
 * for the mis-hearings that keep happening. Both lists are fully
 * editable here; changes reach the very next dictation, because the
 * cleanup pipeline reads the dictionary at send time.
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
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.tab_dictionary)) })
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WordsCard(
                uiState = uiState,
                onWordDraftChange = onWordDraftChange,
                onAddWordClick = onAddWordClick,
                onRemoveWordClick = onRemoveWordClick,
            )
            CorrectionsCard(
                uiState = uiState,
                onHeardAsDraftChange = onHeardAsDraftChange,
                onReplacementDraftChange = onReplacementDraftChange,
                onAddCorrectionClick = onAddCorrectionClick,
                onRemoveCorrectionClick = onRemoveCorrectionClick,
            )
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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dictionary_words_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dictionary_words_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (uiState.words.isEmpty()) {
                Text(
                    text = stringResource(R.string.dictionary_words_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.words.forEach { word ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemoveWordClick(word) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.dictionary_remove_word, word),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.wordDraft,
                    onValueChange = onWordDraftChange,
                    label = { Text(text = stringResource(R.string.dictionary_word_field_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAddWordClick) {
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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dictionary_corrections_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dictionary_corrections_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (uiState.corrections.isEmpty()) {
                Text(
                    text = stringResource(R.string.dictionary_corrections_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.corrections.forEach { correction ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.dictionary_correction_format,
                                correction.heardAs,
                                correction.replacement,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemoveCorrectionClick(correction.heardAs) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(
                                    R.string.dictionary_remove_correction,
                                    correction.heardAs,
                                ),
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = uiState.heardAsDraft,
                onValueChange = onHeardAsDraftChange,
                label = { Text(text = stringResource(R.string.dictionary_heard_as_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.replacementDraft,
                    onValueChange = onReplacementDraftChange,
                    label = { Text(text = stringResource(R.string.dictionary_replacement_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAddCorrectionClick) {
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
@Preview(name = "Dictionary empty - Light", showBackground = true)
@Preview(
    name = "Dictionary empty - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
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
@Preview(name = "Dictionary populated - Light", showBackground = true)
@Preview(
    name = "Dictionary populated - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
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
