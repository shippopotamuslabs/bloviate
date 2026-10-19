package com.shippopotamus.bloviate.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shippopotamus.bloviate.R

/** Wires [StyleViewModel] state into the Style tab; the route is the only place the ViewModel is touched. */
@Composable
fun StyleRoute(
    viewModel: StyleViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StyleScreen(
        uiState = uiState,
        onStyleSelected = viewModel::onStyleSelected,
        onApiKeyDraftChange = viewModel::onApiKeyDraftChange,
        onSaveApiKeyClick = viewModel::onSaveApiKeyClick,
        onClearApiKeyClick = viewModel::onClearApiKeyClick,
        modifier = modifier,
    )
}

/**
 * The Style tab (ENC-72 + ENC-73): the writing-style presets Bloviate
 * shapes dictation into, plus the AI-styles key card. Each row is a
 * radio-button selectable at least 48.dp tall; LLM rows stay
 * non-selectable until an OpenRouter key is saved — the note on the row
 * says so. The key field is a password field whose draft is transient;
 * only the encrypted key is ever stored.
 *
 * The column scrolls: the card plus nine rows must stay reachable on
 * short viewports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleScreen(
    uiState: StyleUiState,
    onStyleSelected: (String) -> Unit,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKeyClick: () -> Unit,
    onClearApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.tab_style)) })
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiKeyCard(
                apiKeyDraft = uiState.apiKeyDraft,
                hasApiKey = uiState.hasApiKey,
                onApiKeyDraftChange = onApiKeyDraftChange,
                onSaveApiKeyClick = onSaveApiKeyClick,
                onClearApiKeyClick = onClearApiKeyClick,
            )
            uiState.options.forEach { option ->
                StyleRow(
                    option = option,
                    onSelect = { onStyleSelected(option.id) },
                )
            }
        }
    }
}

@Composable
private fun AiKeyCard(
    apiKeyDraft: String,
    hasApiKey: Boolean,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKeyClick: () -> Unit,
    onClearApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.style_ai_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.style_ai_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = apiKeyDraft,
                onValueChange = onApiKeyDraftChange,
                label = { Text(text = stringResource(R.string.style_api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSaveApiKeyClick,
                    enabled = apiKeyDraft.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.style_api_key_save))
                }
                OutlinedButton(
                    onClick = onClearApiKeyClick,
                    enabled = hasApiKey,
                ) {
                    Text(text = stringResource(R.string.style_api_key_clear))
                }
            }
            Text(
                text = stringResource(
                    if (hasApiKey) R.string.style_api_key_saved else R.string.style_api_key_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StyleRow(
    option: StyleOptionUi,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectLabel = stringResource(R.string.style_row_select, option.displayName)
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
                .semantics { contentDescription = selectLabel }
                .selectable(
                    selected = option.isSelected,
                    enabled = option.isEnabled,
                    onClick = onSelect,
                    role = Role.RadioButton,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(
                selected = option.isSelected,
                enabled = option.isEnabled,
                onClick = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = option.sample,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (option.requiresAi && !option.isEnabled) {
                    Text(
                        text = stringResource(R.string.style_needs_key_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val ROW_MIN_HEIGHT = 48.dp

private fun previewUiState(hasApiKey: Boolean): StyleUiState =
    StyleUiState(
        hasApiKey = hasApiKey,
        options = listOf(
            StyleOptionUi(
                id = "lowercase-casual",
                displayName = "lowercase casual",
                description = "all lowercase, relaxed and off-the-cuff.",
                sample = "yeah so i was thinking we could just go tomorrow",
                requiresAi = false,
                isSelected = false,
                isEnabled = true,
            ),
            StyleOptionUi(
                id = "normal-caps-casual",
                displayName = "Normal Caps Casual",
                description = "Just how you said it, cleaned up.",
                sample = "Yeah, so I was thinking we could just go tomorrow.",
                requiresAi = false,
                isSelected = true,
                isEnabled = true,
            ),
            StyleOptionUi(
                id = "seneca",
                displayName = "Seneca",
                description = "Stoic letters on time, anger, and fortune.",
                sample = "You act as if you will live forever; you never read the terms.",
                requiresAi = true,
                isSelected = false,
                isEnabled = hasApiKey,
            ),
        ),
    )

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style populated - Light", showBackground = true)
@Preview(
    name = "Style populated - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StyleScreenPopulatedPreview() {
    PreviewTheme {
        StyleScreen(
            uiState = previewUiState(hasApiKey = false),
            onStyleSelected = {},
            onApiKeyDraftChange = {},
            onSaveApiKeyClick = {},
            onClearApiKeyClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style with key - Light", showBackground = true)
@Preview(
    name = "Style with key - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StyleScreenWithKeyPreview() {
    PreviewTheme {
        StyleScreen(
            uiState = previewUiState(hasApiKey = true),
            onStyleSelected = {},
            onApiKeyDraftChange = {},
            onSaveApiKeyClick = {},
            onClearApiKeyClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style empty - Light", showBackground = true)
@Preview(
    name = "Style empty - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StyleScreenEmptyPreview() {
    PreviewTheme {
        StyleScreen(
            uiState = StyleUiState(),
            onStyleSelected = {},
            onApiKeyDraftChange = {},
            onSaveApiKeyClick = {},
            onClearApiKeyClick = {},
        )
    }
}
