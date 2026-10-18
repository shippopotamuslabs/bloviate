package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.domain.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveEnginePreferenceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveFieldFocusUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveParakeetModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.domain.usecase.SelectEngineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Status-screen ViewModel. Depends on use cases only; maps the domain
 * [FieldFocusState] and [ModelState] into the presentation-only [MainUiState]
 * so no domain types reach composables.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeFieldFocus: ObserveFieldFocusUseCase,
    private val isFieldWatcherEnabled: IsFieldWatcherEnabledUseCase,
    private val isOverlayPermissionGranted: IsOverlayPermissionGrantedUseCase,
    observeBubbleServiceRunning: ObserveBubbleServiceRunningUseCase,
    private val isMicPermissionGranted: IsMicPermissionGrantedUseCase,
    private val refreshMicPermission: RefreshMicPermissionUseCase,
    observeModelState: ObserveModelStateUseCase,
    private val ensureModel: EnsureModelUseCase,
    observeParakeetModelState: ObserveParakeetModelStateUseCase,
    private val ensureParakeetModel: EnsureParakeetModelUseCase,
    observeEnginePreference: ObserveEnginePreferenceUseCase,
    private val selectEngine: SelectEngineUseCase,
) : ViewModel() {
    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())
    private val overlayPermissionGranted = MutableStateFlow(isOverlayPermissionGranted())
    private val micPermissionGranted = MutableStateFlow(isMicPermissionGranted())

    private val baseUiState: StateFlow<MainUiState> = combine(
        observeFieldFocus(),
        watcherEnabled,
        overlayPermissionGranted,
        observeBubbleServiceRunning(),
        micPermissionGranted,
    ) { focusState, watcherOn, overlayGranted, bubbleRunning, micGranted ->
        mapToUiState(
            focusState = focusState,
            watcherEnabled = watcherOn,
            overlayPermissionGranted = overlayGranted,
            bubbleServiceRunning = bubbleRunning,
            micPermissionGranted = micGranted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = mapToUiState(
            focusState = FieldFocusState.NoEditableFocus,
            watcherEnabled = isFieldWatcherEnabled(),
            overlayPermissionGranted = isOverlayPermissionGranted(),
            bubbleServiceRunning = observeBubbleServiceRunning().value,
            micPermissionGranted = isMicPermissionGranted(),
        ),
    )

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        observeModelState(),
        observeParakeetModelState(),
        observeEnginePreference(),
    ) { baseState, whisperState, parakeetState, engine ->
        baseState.copy(
            whisperModelStatus = mapModelStatus(whisperState),
            parakeetModelStatus = mapModelStatus(parakeetState),
            selectedEngine = mapEngine(engine),
            engineFallback = mapEngineFallback(engine, whisperState, parakeetState),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = baseUiState.value.copy(
            whisperModelStatus = mapModelStatus(observeModelState().value),
            parakeetModelStatus = mapModelStatus(observeParakeetModelState().value),
            selectedEngine = mapEngine(observeEnginePreference().value),
            engineFallback = mapEngineFallback(
                observeEnginePreference().value,
                observeModelState().value,
                observeParakeetModelState().value,
            ),
        ),
    )

    /** Whisper row download/retry: re-runs the store's ensure (download + verify) in the background. */
    fun onWhisperDownloadClick() {
        viewModelScope.launch {
            ensureModel()
        }
    }

    /** Parakeet row download/retry: downloads + verifies the ~640 MiB model set in the background. */
    fun onParakeetDownloadClick() {
        viewModelScope.launch {
            ensureParakeetModel()
        }
    }

    /** Engine toggle: persists the choice; the next dictation transcribes with it. */
    fun onSelectEngine(engine: DictationEngineUi) {
        viewModelScope.launch {
            selectEngine(
                when (engine) {
                    DictationEngineUi.WHISPER -> DictationEngine.WHISPER
                    DictationEngineUi.PARAKEET -> DictationEngine.PARAKEET
                },
            )
        }
    }

    /** Re-reads the system settings; called when the screen resumes after a settings detour. */
    fun onResumed() {
        watcherEnabled.update { isFieldWatcherEnabled() }
        overlayPermissionGranted.update { isOverlayPermissionGranted() }
        refreshMicPermissionState()
    }

    /** Called with the permission-request result; re-reads the live grant either way. */
    fun onMicPermissionResult() {
        refreshMicPermissionState()
    }

    private fun refreshMicPermissionState() {
        refreshMicPermission()
        micPermissionGranted.update { isMicPermissionGranted() }
    }

    private fun mapToUiState(
        focusState: FieldFocusState,
        watcherEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        bubbleServiceRunning: Boolean,
        micPermissionGranted: Boolean,
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
            micPermissionGranted = micPermissionGranted,
        )
    }

    private fun mapModelStatus(modelState: ModelState): ModelUiStatus =
        when (modelState) {
            ModelState.Absent -> ModelUiStatus.Absent
            is ModelState.Downloading ->
                ModelUiStatus.Downloading(
                    percent = (modelState.progressFraction * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX),
                )
            ModelState.Ready -> ModelUiStatus.Ready
            is ModelState.Failed -> ModelUiStatus.Failed(reason = modelState.reason)
        }

    private fun mapEngine(engine: DictationEngine): DictationEngineUi =
        when (engine) {
            DictationEngine.WHISPER -> DictationEngineUi.WHISPER
            DictationEngine.PARAKEET -> DictationEngineUi.PARAKEET
        }

    /**
     * The fallback note's data, via the shared `EffectiveEngineResolver`:
     * present only while a fallback engine is actually in use — the
     * selected engine's model is not Ready but the other engine's is.
     */
    private fun mapEngineFallback(
        selected: DictationEngine,
        whisperState: ModelState,
        parakeetState: ModelState,
    ): EngineFallbackUi? {
        val effective = EffectiveEngineResolver.resolve(selected, whisperState, parakeetState)
        return if (effective != null && effective != selected) {
            EngineFallbackUi(selected = mapEngine(selected), effective = mapEngine(effective))
        } else {
            null
        }
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
        const val PERCENT_MAX = 100
    }
}
