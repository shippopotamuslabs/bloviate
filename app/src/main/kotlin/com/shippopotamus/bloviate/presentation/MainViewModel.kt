package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
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
    private val isOverlayPermissionGranted: IsOverlayPermissionGrantedUseCase,
    observeBubbleServiceRunning: ObserveBubbleServiceRunningUseCase,
) : ViewModel() {
    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())
    private val overlayPermissionGranted = MutableStateFlow(isOverlayPermissionGranted())

    val uiState: StateFlow<MainUiState> = combine(
        observeFieldFocus(),
        watcherEnabled,
        overlayPermissionGranted,
        observeBubbleServiceRunning(),
    ) { focusState, watcherOn, overlayGranted, bubbleRunning ->
        mapToUiState(
            focusState = focusState,
            watcherEnabled = watcherOn,
            overlayPermissionGranted = overlayGranted,
            bubbleServiceRunning = bubbleRunning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = mapToUiState(
            focusState = FieldFocusState.NoEditableFocus,
            watcherEnabled = isFieldWatcherEnabled(),
            overlayPermissionGranted = isOverlayPermissionGranted(),
            bubbleServiceRunning = observeBubbleServiceRunning().value,
        ),
    )

    /** Re-reads the system settings; called when the screen resumes after a settings detour. */
    fun onResumed() {
        watcherEnabled.update { isFieldWatcherEnabled() }
        overlayPermissionGranted.update { isOverlayPermissionGranted() }
    }

    private fun mapToUiState(
        focusState: FieldFocusState,
        watcherEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        bubbleServiceRunning: Boolean,
    ): MainUiState {
        val (classification, packageName) = when (focusState) {
            FieldFocusState.NoEditableFocus -> FocusClassification.NONE to null
            is FieldFocusState.EditableFocus -> FocusClassification.EDITABLE to focusState.packageName
            is FieldFocusState.PasswordFocus -> FocusClassification.PASSWORD to focusState.packageName
        }
        return MainUiState(
            watcherEnabled = watcherEnabled,
            focusClassification = classification,
            focusPackageName = packageName,
            overlayPermissionGranted = overlayPermissionGranted,
            bubbleServiceRunning = bubbleServiceRunning,
        )
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
