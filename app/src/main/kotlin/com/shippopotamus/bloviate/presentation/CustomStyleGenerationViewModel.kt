package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.usecase.GenerateCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.MAX_GENERATE_DESCRIPTION_CHARS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI style-draft Start ViewModel (BLO-160 S7). Depends on the generation
 * use case only: the description streams into [uiState], generation runs
 * through [GenerateCustomStyleUseCase], and success emits a
 * [GeneratedHandoff] the route observes to open the shared editor
 * prefilled. The draft is never stored here — the handoff carries it to
 * the editor ViewModel, which owns the review state.
 *
 * Backing out while generating cancels the in-flight job: the server call
 * is abandoned and no handoff is emitted, so a late draft can never open
 * an editor the user already left. Leaving the tab keeps the composition
 * but the ViewModel (and a running generation) survives — coming back
 * shows the live generating state.
 */
@HiltViewModel
class CustomStyleGenerationViewModel @Inject constructor(
    private val generate: GenerateCustomStyleUseCase,
) : ViewModel() {

    private val description = MutableStateFlow("")
    private val generating = MutableStateFlow(false)
    private val error = MutableStateFlow<CustomStyleGenerationError?>(null)
    private var generationJob: Job? = null

    val uiState: StateFlow<CustomStyleGenerationUiState> = combine(
        description,
        generating,
        error,
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = buildUiState(
            description.value,
            generating.value,
            error.value,
        ),
    )

    /** One-shot: the draft landed, so the route can open the shared editor prefilled. */
    private val generateSucceededChannel = Channel<GeneratedHandoff>(Channel.BUFFERED)
    val generateSucceeded: Flow<GeneratedHandoff> = generateSucceededChannel.receiveAsFlow()

    fun onDescriptionChange(value: String) {
        description.update { value }
        error.update { null }
    }

    /** Dismisses the generation error line; the description is kept. */
    fun onErrorDismiss() {
        error.update { null }
    }

    /**
     * Starts generation for the current description. Disabled while blank,
     * over the cap, or already generating — those taps are no-ops, so the
     * use case's [InvalidInput] gate is unreachable from this screen and
     * maps defensively below.
     */
    fun onGenerateClick() {
        val trimmed = description.value.trim()
        if (generating.value || trimmed.isEmpty() || trimmed.length > MAX_GENERATE_DESCRIPTION_CHARS) {
            return
        }
        generationJob?.cancel()
        error.update { null }
        generationJob = viewModelScope.launch {
            generating.update { true }
            try {
                when (val outcome = generate(trimmed)) {
                    is CustomStyleGenerationOutcome.Ok ->
                        generateSucceededChannel.send(
                            GeneratedHandoff(
                                draft = outcome.draft,
                                sourceDescription = trimmed,
                            ),
                        )
                    is CustomStyleGenerationOutcome.Failed ->
                        error.update { outcome.failure.toGenerationError() }
                }
            } finally {
                generating.update { false }
            }
        }
    }

    /**
     * Cancels the in-flight generation without emitting: the route calls
     * this when the user backs out of the Start screen mid-generation, so
     * no handoff opens an editor behind their back.
     */
    fun onCancelInFlight() {
        generationJob?.cancel()
        generationJob = null
        generating.update { false }
    }

    private fun buildUiState(
        description: String,
        generating: Boolean,
        error: CustomStyleGenerationError?,
    ): CustomStyleGenerationUiState {
        val trimmed = description.trim()
        return CustomStyleGenerationUiState(
            description = description,
            isGenerating = generating,
            error = error,
            canGenerate = !generating && trimmed.isNotEmpty() && trimmed.length <= MAX_GENERATE_DESCRIPTION_CHARS,
        )
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS: Long = 5_000L
    }
}
