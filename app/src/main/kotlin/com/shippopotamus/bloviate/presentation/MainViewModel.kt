package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveFieldFocusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Status-screen ViewModel. Depends on use cases only; maps the domain
 * [FieldFocusState] into the presentation-only [MainUiState] so no domain
 * types reach composables.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeFieldFocus: ObserveFieldFocusUseCase,
    private val isFieldWatcherEnabled: IsFieldWatcherEnabledUseCase,
) : ViewModel() {
    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())

    val uiState: StateFlow<MainUiState> = combine(
        observeFieldFocus(),
        watcherEnabled,
    ) { focusState, enabled -> mapToUiState(focusState, enabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
            initialValue = mapToUiState(FieldFocusState.NoEditableFocus, isFieldWatcherEnabled()),
        )

    /** Re-reads the system setting; called when the screen resumes after a settings detour. */
    fun onResumed() {
        watcherEnabled.update { isFieldWatcherEnabled() }
    }

    private fun mapToUiState(
        focusState: FieldFocusState,
        watcherEnabled: Boolean,
    ): MainUiState =
        when (focusState) {
            FieldFocusState.NoEditableFocus -> MainUiState(watcherEnabled = watcherEnabled)
            is FieldFocusState.EditableFocus ->
                MainUiState(
                    watcherEnabled = watcherEnabled,
                    focusClassification = FocusClassification.EDITABLE,
                    focusPackageName = focusState.packageName,
                )
            is FieldFocusState.PasswordFocus ->
                MainUiState(
                    watcherEnabled = watcherEnabled,
                    focusClassification = FocusClassification.PASSWORD,
                    focusPackageName = focusState.packageName,
                )
        }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
