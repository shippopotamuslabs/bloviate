package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.domain.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveEnginePreferenceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveOnboardingProgressUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveParakeetModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveOnboardingProgressUseCase
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
 * First-run wizard ViewModel (ENC-67). Depends on use cases only.
 *
 * The wizard is a flow over the SAME live state the Setup screen
 * reads — watcher, overlay, microphone, both model stores, the engine
 * preference, the bubble's running state — plus one persisted record,
 * the user's place in the walk. Navigation (continue/back/skip/
 * finish/re-run) only ever writes that record; the shell shows the
 * wizard whenever the record is unfinished, so progress, resume, and
 * re-runs are the same mechanism.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observeOnboardingProgress: ObserveOnboardingProgressUseCase,
    private val saveOnboardingProgress: SaveOnboardingProgressUseCase,
    private val isFieldWatcherEnabled: IsFieldWatcherEnabledUseCase,
    private val isOverlayPermissionGranted: IsOverlayPermissionGrantedUseCase,
    private val isMicPermissionGranted: IsMicPermissionGrantedUseCase,
    private val refreshMicPermission: RefreshMicPermissionUseCase,
    observeBubbleServiceRunning: ObserveBubbleServiceRunningUseCase,
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
    private val testFieldText = MutableStateFlow("")

    /**
     * The wizard's source of truth: navigation reads its current value
     * directly instead of the derived [uiState], whose `WhileSubscribed`
     * sharing only stays live while a surface collects it.
     */
    private val onboardingProgress: StateFlow<OnboardingProgress> = observeOnboardingProgress()

    private val baseUiState: StateFlow<OnboardingUiState> = combine(
        onboardingProgress,
        watcherEnabled,
        overlayPermissionGranted,
        micPermissionGranted,
        observeBubbleServiceRunning(),
    ) { progress, watcherOn, overlayGranted, micGranted, bubbleRunning ->
        OnboardingUiState(
            showWizard = !progress.finished,
            step = progress.step.toUi(),
            watcherEnabled = watcherOn,
            overlayPermissionGranted = overlayGranted,
            micPermissionGranted = micGranted,
            bubbleServiceRunning = bubbleRunning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = with(onboardingProgress.value) {
            OnboardingUiState(
                showWizard = !finished,
                step = step.toUi(),
                watcherEnabled = isFieldWatcherEnabled(),
                overlayPermissionGranted = isOverlayPermissionGranted(),
                micPermissionGranted = isMicPermissionGranted(),
                bubbleServiceRunning = observeBubbleServiceRunning().value,
            )
        },
    )

    val uiState: StateFlow<OnboardingUiState> = combine(
        baseUiState,
        observeModelState(),
        observeParakeetModelState(),
        observeEnginePreference(),
        testFieldText,
    ) { baseState, whisperState, parakeetState, engine, testText ->
        baseState.copy(
            whisperModelStatus = mapModelStatus(whisperState),
            parakeetModelStatus = mapModelStatus(parakeetState),
            selectedEngine = mapEngine(engine),
            engineFallback = mapEngineFallback(engine, whisperState, parakeetState),
            testFieldText = testText,
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

    /** Continue: advance one step and persist the new resume point; on the test step, finish. */
    fun onContinue() {
        val current = onboardingProgress.value.step
        val next = current.next()
        if (next == null) {
            onFinish()
        } else {
            persist(OnboardingProgress(step = next, finished = false))
        }
    }

    /** Back: return one step and persist it; a no-op on the welcome step. */
    fun onBack() {
        val previous = onboardingProgress.value.step.previous() ?: return
        persist(OnboardingProgress(step = previous, finished = false))
    }

    /** Skip: close the wizard now, keeping the resume step for a later re-run. Never traps. */
    fun onSkip() {
        persist(
            OnboardingProgress(
                step = onboardingProgress.value.step,
                finished = true,
            ),
        )
    }

    /** Finish: the walk is done; the wizard stops opening on launch. */
    fun onFinish() {
        persist(
            OnboardingProgress(
                step = onboardingProgress.value.step,
                finished = true,
            ),
        )
    }

    /** Setup's "Run setup again": restart the walk at the welcome step, unfinished. */
    fun onRunSetupAgain() {
        persist(OnboardingProgress.NOT_STARTED)
    }

    /** Whisper row download/retry on the model step: the store's ensure, in the background. */
    fun onWhisperDownloadClick() {
        viewModelScope.launch {
            ensureModel()
        }
    }

    /** Parakeet row download/retry on the model step: downloads + verifies in the background. */
    fun onParakeetDownloadClick() {
        viewModelScope.launch {
            ensureParakeetModel()
        }
    }

    /** Engine choice on the model step: persists; the next dictation transcribes with it. */
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

    /** The scratch field's text — typed, pasted, or dictated into it on the test step. */
    fun onTestFieldTextChange(text: String) {
        testFieldText.update { text }
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

    private fun persist(progress: OnboardingProgress) {
        viewModelScope.launch {
            saveOnboardingProgress(progress)
        }
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

    /** The fallback note's data, via the shared resolver — same rule as the Setup screen. */
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
