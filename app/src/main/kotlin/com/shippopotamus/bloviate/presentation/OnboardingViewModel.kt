package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.application.port.LiveSetupState
import com.shippopotamus.bloviate.application.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.application.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.application.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.application.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveLiveSetupStateUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.application.usecase.SaveOnboardingProgressUseCase
import com.shippopotamus.bloviate.application.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
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
 * the user's place in the walk. That shared state arrives as one
 * [LiveSetupState] from [ObserveLiveSetupStateUseCase] (BLO-175); this
 * ViewModel folds in only its local transient state: the permission
 * re-reads refreshed on resume and the test field's text. Navigation
 * (continue/back/skip/finish/re-run) only ever writes that record; the
 * shell shows the wizard whenever the record is unfinished, so
 * progress, resume, and re-runs are the same mechanism.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val observeLiveSetupState: ObserveLiveSetupStateUseCase,
    private val saveOnboardingProgress: SaveOnboardingProgressUseCase,
    private val isFieldWatcherEnabled: IsFieldWatcherEnabledUseCase,
    private val isOverlayPermissionGranted: IsOverlayPermissionGrantedUseCase,
    private val isMicPermissionGranted: IsMicPermissionGrantedUseCase,
    private val refreshMicPermission: RefreshMicPermissionUseCase,
    private val ensureModel: EnsureModelUseCase,
    private val ensureParakeetModel: EnsureParakeetModelUseCase,
    private val selectEngine: SelectEngineUseCase,
    private val isBatteryOptimizationExempt: IsBatteryOptimizationExemptUseCase,
) : ViewModel() {
    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())
    private val overlayPermissionGranted = MutableStateFlow(isOverlayPermissionGranted())
    private val micPermissionGranted = MutableStateFlow(isMicPermissionGranted())
    private val batteryExempt = MutableStateFlow(isBatteryOptimizationExempt())
    private val testFieldText = MutableStateFlow("")

    /**
     * The wizard's current place, read straight from the observed
     * state's latest value: navigation reads it directly instead of the
     * derived [uiState], whose `WhileSubscribed` sharing only stays
     * live while a surface collects it. The observed state is shared
     * eagerly at app scope, so its value is always the live record.
     */
    private val currentProgress: OnboardingProgress
        get() = observeLiveSetupState().value.onboardingProgress

    /**
     * The four permission re-reads as one local slice: settings-posture
     * reads refreshed on resume, folded into the observed durable state
     * at the [uiState] combine.
     */
    private data class PermissionReads(
        val watcherEnabled: Boolean,
        val overlayPermissionGranted: Boolean,
        val micPermissionGranted: Boolean,
        val batteryExempt: Boolean,
    )

    private val permissionReads = combine(
        watcherEnabled,
        overlayPermissionGranted,
        micPermissionGranted,
        batteryExempt,
        ::PermissionReads,
    )

    val uiState: StateFlow<OnboardingUiState> = combine(
        observeLiveSetupState(),
        permissionReads,
        testFieldText,
        ::mapToUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = mapToUiState(
            live = observeLiveSetupState().value,
            permissions = PermissionReads(
                watcherEnabled = watcherEnabled.value,
                overlayPermissionGranted = overlayPermissionGranted.value,
                micPermissionGranted = micPermissionGranted.value,
                batteryExempt = batteryExempt.value,
            ),
            testText = testFieldText.value,
        ),
    )

    /** Continue: advance one step and persist the new resume point; on the test step, finish. */
    fun onContinue() {
        val current = currentProgress.step
        val next = current.next()
        if (next == null) {
            onFinish()
        } else {
            persist(OnboardingProgress(step = next, finished = false))
        }
    }

    /** Back: return one step and persist it; a no-op on the welcome step. */
    fun onBack() {
        val previous = currentProgress.step.previous() ?: return
        persist(OnboardingProgress(step = previous, finished = false))
    }

    /** Skip: close the wizard now, keeping the resume step for a later re-run. Never traps. */
    fun onSkip() {
        persist(
            OnboardingProgress(
                step = currentProgress.step,
                finished = true,
            ),
        )
    }

    /** Finish: the walk is done; the wizard stops opening on launch. */
    fun onFinish() {
        persist(
            OnboardingProgress(
                step = currentProgress.step,
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
        batteryExempt.update { isBatteryOptimizationExempt() }
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

    private fun mapToUiState(
        live: LiveSetupState,
        permissions: PermissionReads,
        testText: String,
    ): OnboardingUiState =
        OnboardingUiState(
            showWizard = !live.onboardingProgress.finished,
            step = live.onboardingProgress.step.toUi(),
            watcherEnabled = permissions.watcherEnabled,
            watcherConnected = live.watcherConnected,
            overlayPermissionGranted = permissions.overlayPermissionGranted,
            micPermissionGranted = permissions.micPermissionGranted,
            batteryExempt = permissions.batteryExempt,
            bubbleServiceRunning = live.bubbleServiceRunning,
            whisperModelStatus = mapModelStatus(live.whisperModelState),
            parakeetModelStatus = mapModelStatus(live.parakeetModelState),
            selectedEngine = mapEngine(live.selectedEngine),
            engineFallback = mapEngineFallback(live),
            testFieldText = testText,
        )

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

    /** The fallback note's data, from the read model's precomputed effective engine — same rule as Setup. */
    private fun mapEngineFallback(live: LiveSetupState): EngineFallbackUi? {
        val effective = live.effectiveEngine
        return if (effective != null && effective != live.selectedEngine) {
            EngineFallbackUi(selected = mapEngine(live.selectedEngine), effective = mapEngine(effective))
        } else {
            null
        }
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
        const val PERCENT_MAX = 100
    }
}
