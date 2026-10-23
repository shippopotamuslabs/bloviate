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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.application.usecase.MAX_GENERATE_DESCRIPTION_CHARS

/**
 * The AI style-draft Start screen (BLO-160 S7): the in-tab destination the
 * create entry opens. One description field with a 500-character counter,
 * the off-device disclosure, and a Generate button; "Start blank instead"
 * drops to the blank manual editor. While generating, the button is
 * disabled and a progress line shows — backing out cancels the call (the
 * route owns that), so leaving is always safe.
 *
 * The screen never sees domain types — errors arrive as
 * [CustomStyleGenerationError], shared with the editor's regenerate line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomStyleGenerationScreen(
    uiState: CustomStyleGenerationUiState,
    onBack: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onStartBlankClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.style_generate_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.style_editor_back),
                        )
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
            Text(
                text = stringResource(R.string.style_generate_disclosure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BloviateSpacing.s4),
            )
            val fieldLabel = stringResource(R.string.style_generate_field_label)
            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text(text = fieldLabel) },
                placeholder = { Text(text = stringResource(R.string.style_generate_field_hint)) },
                singleLine = false,
                minLines = DESCRIPTION_MIN_LINES,
                supportingText = {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "",
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.style_editor_counter_format,
                                uiState.description.length,
                                MAX_GENERATE_DESCRIPTION_CHARS,
                            ),
                            color = if (uiState.description.length > MAX_GENERATE_DESCRIPTION_CHARS) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = fieldLabel },
            )
            uiState.error?.let { error ->
                Column(modifier = Modifier.padding(horizontal = BloviateSpacing.s4)) {
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onErrorDismiss) {
                        Text(text = stringResource(R.string.style_error_dismiss))
                    }
                }
            }
            Button(
                onClick = onGenerateClick,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.style_generate_generating),
                        modifier = Modifier.padding(start = BloviateSpacing.s8),
                    )
                } else {
                    Text(text = stringResource(R.string.style_generate_button))
                }
            }
            TextButton(
                onClick = onStartBlankClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.style_generate_start_blank))
            }
        }
    }
}

/** Each generation failure maps to exactly one documented message; the mapping is total regardless of outcome. */
internal fun CustomStyleGenerationError.messageRes(): Int =
    when (this) {
        CustomStyleGenerationError.NOT_A_STYLE -> R.string.style_generate_error_not_a_style
        CustomStyleGenerationError.QUOTA_REACHED -> R.string.style_generate_error_quota
        CustomStyleGenerationError.NOT_ENTITLED -> R.string.style_generate_error_not_entitled
        CustomStyleGenerationError.SIGNED_OUT -> R.string.style_generate_error_signed_out
        CustomStyleGenerationError.INVALID_GENERATION -> R.string.style_generate_error_invalid_generation
        CustomStyleGenerationError.UNAVAILABLE -> R.string.style_generate_error_unavailable
    }

private const val DESCRIPTION_MIN_LINES: Int = 3

/** Every preview renders the screen with a fake state — never a ViewModel. */
@Composable
private fun GenerationScreenPreview(uiState: CustomStyleGenerationUiState) {
    PreviewTheme {
        CustomStyleGenerationScreen(
            uiState = uiState,
            onBack = {},
            onDescriptionChange = {},
            onGenerateClick = {},
            onStartBlankClick = {},
            onErrorDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Generate start - Dark", showBackground = true)
@Composable
private fun GenerationStartPreview() {
    GenerationScreenPreview(
        uiState = CustomStyleGenerationUiState(
            description = "Like a noir detective",
            canGenerate = true,
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Generate generating - Dark", showBackground = true)
@Composable
private fun GenerationGeneratingPreview() {
    GenerationScreenPreview(
        uiState = CustomStyleGenerationUiState(
            description = "Like a noir detective — short sentences, rain everywhere.",
            isGenerating = true,
            canGenerate = false,
        ),
    )
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Generate error - Dark", showBackground = true)
@Composable
private fun GenerationErrorPreview() {
    GenerationScreenPreview(
        uiState = CustomStyleGenerationUiState(
            description = "Make my shopping list red.",
            error = CustomStyleGenerationError.NOT_A_STYLE,
            canGenerate = true,
        ),
    )
}
