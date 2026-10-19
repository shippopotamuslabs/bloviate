package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
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
 * The Style tab (ENC-72 + ENC-73, dashboard form ENC-75/ENC-78): the
 * header pill carries the AI connection state, the key card is one
 * masked field + its action with a one-line explanation, and the
 * styles are a radio LIST — name, one-line description, and an
 * on-device/AI tag — with the sample text rendered only under the
 * selected style. LLM rows stay non-selectable until an OpenRouter
 * key is saved; the key card's line says why. The key field is a
 * password field whose draft is transient; only the encrypted key is
 * ever stored. Selection and key behavior are unchanged from the card
 * layout this replaced.
 *
 * The column scrolls: the key card plus nine rows must stay reachable
 * on short viewports.
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
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_style)) },
                actions = {
                    StatusPill(
                        text = stringResource(
                            if (uiState.hasApiKey) {
                                R.string.style_pill_connected
                            } else {
                                R.string.style_pill_off
                            },
                        ),
                        tone = if (uiState.hasApiKey) StatusTone.POSITIVE else StatusTone.NEUTRAL,
                        modifier = Modifier.padding(end = BloviateSpacing.s16),
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
                .padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s16),
        ) {
            DashboardSectionHeader(text = stringResource(R.string.style_section_key))
            AiKeyCard(
                apiKeyDraft = uiState.apiKeyDraft,
                hasApiKey = uiState.hasApiKey,
                onApiKeyDraftChange = onApiKeyDraftChange,
                onSaveApiKeyClick = onSaveApiKeyClick,
                onClearApiKeyClick = onClearApiKeyClick,
            )
            DashboardSectionHeader(
                text = stringResource(R.string.style_section_styles),
            )
            DashboardCard {
                uiState.options.forEachIndexed { index, option ->
                    StyleListRow(
                        option = option,
                        dividerAbove = index > 0,
                        onSelect = { onStyleSelected(option.id) },
                    )
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
}

/**
 * The compact key card (ENC-78): a masked password field, its Save (or
 * Replace, once a key exists) action, Clear when a key is stored, and
 * one line of explanation that doubles as the reason AI rows are dim.
 */
@Composable
private fun AiKeyCard(
    apiKeyDraft: String,
    hasApiKey: Boolean,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKeyClick: () -> Unit,
    onClearApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyLabel = stringResource(R.string.style_api_key_label)
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(BloviateSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BloviateSpacing.s8),
            ) {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = onApiKeyDraftChange,
                    placeholder = {
                        Text(
                            text = stringResource(
                                if (hasApiKey) {
                                    R.string.style_api_key_masked
                                } else {
                                    R.string.style_api_key_label
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = keyLabel },
                )
                Button(
                    onClick = onSaveApiKeyClick,
                    enabled = apiKeyDraft.isNotBlank(),
                ) {
                    Text(
                        text = stringResource(
                            if (hasApiKey) {
                                R.string.style_api_key_replace
                            } else {
                                R.string.style_api_key_save
                            },
                        ),
                    )
                }
                if (hasApiKey) {
                    TextButton(onClick = onClearApiKeyClick) {
                        Text(text = stringResource(R.string.style_api_key_clear))
                    }
                }
            }
            Text(
                text = stringResource(
                    if (hasApiKey) R.string.style_key_line_saved else R.string.style_key_line_needed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
                enabled = option.isEnabled,
                onClick = null,
            )
        },
        onClick = onSelect,
        clickRole = Role.RadioButton,
        enabled = option.isEnabled,
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
 * The selected style's sample, inset under its row so the list pays for
 * one sample, not nine. The start inset (s48) aligns the sample with
 * the row's text: row padding s16 + leading gap s12 + the radio glyph.
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
@Preview(name = "Style populated - Dark", showBackground = true)
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
@Preview(name = "Style with key - Dark", showBackground = true)
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
@Preview(name = "Style empty - Dark", showBackground = true)
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
