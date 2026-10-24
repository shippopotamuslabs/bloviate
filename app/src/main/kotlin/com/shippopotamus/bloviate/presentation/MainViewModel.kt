package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.application.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.application.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.application.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveAuthStateUseCase
import com.shippopotamus.bloviate.application.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveEnginePreferenceUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveModelStateUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveParakeetModelStateUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveResidentPresenceUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveWatcherConnectionUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStartUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStopUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.application.usecase.SaveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.application.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.application.usecase.SetAutoStartEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.SignInWithGoogleUseCase
import com.shippopotamus.bloviate.application.usecase.SignOutUseCase
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
 * [ModelState] into the presentation-only [MainUiState] so no domain
 * types reach composables. The live field-focus stream is not one of
 * its inputs (ENC-142): the bubble pipeline consumes it directly, and
 * Setup no longer renders a focus readout.
 */
@Suppress("LongParameterList") // One injected use case per capability, per the ViewModel rules.
@HiltViewModel
class MainViewModel @Inject constructor(
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
    private val observeBubbleAppearance: ObserveBubbleAppearanceUseCase,
    private val saveBubbleAppearance: SaveBubbleAppearanceUseCase,
    observeWatcherConnection: ObserveWatcherConnectionUseCase,
    private val isBatteryOptimizationExempt: IsBatteryOptimizationExemptUseCase,
    observeResidentPresence: ObserveResidentPresenceUseCase,
    private val setAutoStartEnabled: SetAutoStartEnabledUseCase,
    private val recordBubbleUserStart: RecordBubbleUserStartUseCase,
    private val recordBubbleUserStop: RecordBubbleUserStopUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {
    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())
    private val overlayPermissionGranted = MutableStateFlow(isOverlayPermissionGranted())
    private val micPermissionGranted = MutableStateFlow(isMicPermissionGranted())
    private val batteryExempt = MutableStateFlow(isBatteryOptimizationExempt())

    // The watcher's two supervision signals (ENC-129) ride together:
    // enablement is a settings read refreshed on resume, connection is
    // the service's own live stream.
    private val watcherSignals = combine(watcherEnabled, observeWatcherConnection()) { enabled, connected ->
        enabled to connected
    }

    /**
     * Transient sign-in surface state (ENC-197): in-flight flag plus
     * the last failure's user-facing message. Not persisted — the
     * auth gate itself is the live [observeAuthState] stream.
     */
    private val signInUi = MutableStateFlow(SignInUi())

    /**
     * Gate plus its surface state as one stream (ENC-197): folded
     * together so the screen-wide combines keep five inputs each.
     */
    private val authAndSignIn = combine(observeAuthState(), signInUi) { authState, signIn ->
        authState to signIn
    }

    private val baseUiState: StateFlow<MainUiState> = combine(
        watcherSignals,
        overlayPermissionGranted,
        observeBubbleServiceRunning(),
        micPermissionGranted,
        authAndSignIn,
    ) { watcherOnConnected, overlayGranted, bubbleRunning, micGranted, authStateSignIn ->
        val (watcherOn, watcherConnected) = watcherOnConnected
        val (authState, signIn) = authStateSignIn
        mapToUiState(
            watcherEnabled = watcherOn,
            watcherConnected = watcherConnected,
            overlayPermissionGranted = overlayGranted,
            bubbleServiceRunning = bubbleRunning,
            micPermissionGranted = micGranted,
            auth = authState.toUi(),
            isSigningIn = signIn.isSigningIn,
            signInError = signIn.signInError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = mapToUiState(
            watcherEnabled = isFieldWatcherEnabled(),
            watcherConnected = observeWatcherConnection().value,
            overlayPermissionGranted = isOverlayPermissionGranted(),
            bubbleServiceRunning = observeBubbleServiceRunning().value,
            micPermissionGranted = isMicPermissionGranted(),
            auth = observeAuthState().value.toUi(),
            isSigningIn = signInUi.value.isSigningIn,
            signInError = signInUi.value.signInError,
        ),
    )

    // Appearance, resident presence, and battery exemption ride
    // together into the second combine (ENC-129): appearance restyles
    // the bubble-look card, presence drives the auto-start switch, and
    // the exemption is a settings read refreshed on resume.
    private val appearancePresenceBattery = combine(
        observeBubbleAppearance(),
        observeResidentPresence(),
        batteryExempt,
    ) { appearance, presence, exempt -> Triple(appearance, presence, exempt) }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        observeModelState(),
        observeParakeetModelState(),
        observeEnginePreference(),
        appearancePresenceBattery,
    ) { baseState, whisperState, parakeetState, engine, appearancePresenceExempt ->
        val (appearance, presence, exempt) = appearancePresenceExempt
        baseState.copy(
            whisperModelStatus = mapModelStatus(whisperState),
            parakeetModelStatus = mapModelStatus(parakeetState),
            selectedEngine = mapEngine(engine),
            engineFallback = mapEngineFallback(engine, whisperState, parakeetState),
            bubbleAppearance = appearance.toUi(),
            autoStartEnabled = presence.autoStartEnabled,
            batteryExempt = exempt,
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
            bubbleAppearance = observeBubbleAppearance().value.toUi(),
            autoStartEnabled = observeResidentPresence().value.autoStartEnabled,
            batteryExempt = isBatteryOptimizationExempt(),
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

    /** Bubble opacity slider committed: persists just that field of the current appearance. */
    fun onBubbleOpacityChange(opacity: Float) {
        updateBubbleAppearance { current ->
            current.copy(
                opacity = opacity.coerceIn(BubbleAppearance.MIN_OPACITY, BubbleAppearance.MAX_OPACITY),
            )
        }
    }

    /** Bubble size segment tapped: persists just that field of the current appearance. */
    fun onBubbleSizeChange(size: BubbleSizeUi) {
        updateBubbleAppearance { current -> current.copy(size = size.toDomain()) }
    }

    /** Bubble silhouette tapped: persists just that field of the current appearance. */
    fun onBubbleShapeChange(shape: BubbleShapeUi) {
        updateBubbleAppearance { current -> current.copy(shape = shape.toDomain()) }
    }

    /** Bubble tint tapped: persists just that field of the current appearance. */
    fun onBubbleTintChange(tint: BubbleTintUi) {
        updateBubbleAppearance { current -> current.copy(tint = tint.toDomain()) }
    }

    /**
     * One appearance edit: read the live preference (never a cached UI
     * copy — the overlay may be showing it), transform one field, save.
     * The store's next emission restyles both this screen's preview and
     * the already-showing bubble.
     */
    private fun updateBubbleAppearance(transform: (BubbleAppearance) -> BubbleAppearance) {
        viewModelScope.launch {
            saveBubbleAppearance(transform(observeBubbleAppearance().value))
        }
    }

    /**
     * The user started the bubble from the app (ENC-129): the explicit
     * start is what lifts a previous Stop, so resident presence may
     * resume it again from here. The activity performs the actual
     * service start alongside this call.
     */
    fun onBubbleStartRequested() {
        viewModelScope.launch {
            recordBubbleUserStart()
        }
    }

    /**
     * The user stopped the bubble from the app (ENC-129): record the
     * stop so boot start and sticky restart honor it. The activity
     * performs the actual service stop alongside this call.
     */
    fun onBubbleStopRequested() {
        viewModelScope.launch {
            recordBubbleUserStop()
        }
    }

    /** "Start on its own" switch: persists the resident-presence posture (ENC-129). */
    fun onAutoStartEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            setAutoStartEnabled(enabled)
        }
    }

    /**
     * Sign-in button tapped (ENC-197): runs the Credential Manager
     * flow through the use case. Success flips the gate via the auth
     * listener; a user dismissal stays silent; any other failure lands
     * its user-facing message in the state until dismissed or retried.
     */
    fun onSignInClick() {
        viewModelScope.launch {
            signInUi.update { it.copy(isSigningIn = true, signInError = null) }
            val result = signInWithGoogle()
            signInUi.update {
                it.copy(
                    isSigningIn = false,
                    signInError = result.exceptionOrNull()?.let { failure ->
                        if (failure.isSignInDismissal()) {
                            null
                        } else {
                            failure.toGoogleSignInMessage()
                        }
                    },
                )
            }
        }
    }

    /** Dismisses the sign-in error line. */
    fun onSignInErrorDismiss() {
        signInUi.update { it.copy(signInError = null) }
    }

    /**
     * Sign-out tapped in Setup (ENC-197): the auth listener flips the
     * gate back to the sign-in screen. Fire-and-forget — the listener
     * is the source of truth, not this call's result.
     */
    fun onSignOutClick() {
        viewModelScope.launch {
            signOut()
        }
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

    private fun mapToUiState(
        watcherEnabled: Boolean,
        watcherConnected: Boolean,
        overlayPermissionGranted: Boolean,
        bubbleServiceRunning: Boolean,
        micPermissionGranted: Boolean,
        auth: AuthUi,
        isSigningIn: Boolean,
        signInError: String?,
    ): MainUiState =
        MainUiState(
            watcherEnabled = watcherEnabled,
            watcherConnected = watcherConnected,
            overlayPermissionGranted = overlayPermissionGranted,
            bubbleServiceRunning = bubbleServiceRunning,
            micPermissionGranted = micPermissionGranted,
            auth = auth,
            isSigningIn = isSigningIn,
            signInError = signInError,
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

/** Transient sign-in surface state: never persisted (ENC-197). */
private data class SignInUi(
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
)
