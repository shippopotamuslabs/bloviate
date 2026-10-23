package com.shippopotamus.bloviate.presentation

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
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
        modifier = modifier,
    )
}

/**
 * The Style tab (ENC-72 + ENC-73, dashboard form ENC-75/ENC-78): the
 * styles are a radio LIST — name, one-line description, and an
 * on-device/AI tag — with the sample text rendered only under the
 * selected style. Every style in the list is selectable.
 *
 * The column scrolls: the nine rows must stay reachable on short
 * viewports.
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
            Text(
                text = stringResource(R.string.style_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BloviateSpacing.s4),
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

private fun previewUiState(): StyleUiState =
    StyleUiState(
        sections = listOf(
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
                        isSelected = true,
                    ),
                ),
            ),
            StyleSectionUi(
                headerRes = R.string.style_section_ancient_philosophers,
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

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Style populated - Dark", showBackground = true)
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
@Preview(name = "Style empty - Dark", showBackground = true)
@Composable
private fun StyleScreenEmptyPreview() {
    PreviewTheme {
        StyleScreen(
            uiState = StyleUiState(),
            onStyleSelected = {},
        )
    }
}
