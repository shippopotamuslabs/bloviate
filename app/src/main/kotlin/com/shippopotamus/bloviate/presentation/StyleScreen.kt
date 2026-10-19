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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        modifier = modifier,
    )
}

/**
 * The Style tab (ENC-72): the writing-style presets Bloviate shapes
 * dictation into. Each row is a radio-button selectable at least 48.dp
 * tall; the two mechanical styles apply on-device today, while the LLM
 * styles say plainly that they need the ENC-73 AI setup — picking one now
 * is remembered but changes nothing until then.
 *
 * The column scrolls: nine rows must stay reachable on short viewports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleScreen(
    uiState: StyleUiState,
    onStyleSelected: (String) -> Unit,
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
                    onClick = onSelect,
                    role = Role.RadioButton,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(
                selected = option.isSelected,
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
                if (option.requiresAi) {
                    Text(
                        text = stringResource(R.string.style_requires_ai_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val ROW_MIN_HEIGHT = 48.dp

private fun previewUiState(): StyleUiState =
    StyleUiState(
        options = listOf(
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
                isSelected = true,
            ),
            StyleOptionUi(
                id = "seneca",
                displayName = "Seneca",
                description = "Stoic letters on time, anger, and fortune.",
                sample = "You act as if you will live forever; you never read the terms.",
                requiresAi = true,
                isSelected = false,
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
            uiState = previewUiState(),
            onStyleSelected = {},
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
        )
    }
}
