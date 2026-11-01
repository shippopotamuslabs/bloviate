package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.BuildConfig
import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.BubbleStartRequest
import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.LiveSetupState
import com.shippopotamus.bloviate.application.port.OutputReportReason
import com.shippopotamus.bloviate.application.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.application.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.application.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.application.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.ClearLastStyledResultUseCase
import com.shippopotamus.bloviate.application.usecase.DeleteAccountUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveLastStyledResultUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveLiveSetupStateUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStartUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStopUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.application.usecase.SaveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.application.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import com.shippopotamus.bloviate.application.usecase.SubmitOutputReportOutcome
import com.shippopotamus.bloviate.application.usecase.SubmitOutputReportUseCase
import com.shippopotamus.bloviate.application.usecase.SetAutoStartEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.SignInWithGoogleUseCase
import com.shippopotamus.bloviate.application.usecase.SignOutUseCase
import com.shippopotamus.bloviate.application.usecase.StartBubbleIfPresenceAllowsUseCase
import com.shippopotamus.bloviate.domain.evaluator.BubbleServiceStartPolicy
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Status-screen ViewModel. Depends on use cases only; maps the domain
 * [ModelState] into the presentation-only [MainUiState] so no domain
 * types reach composables. The durable streams arrive as one
 * [LiveSetupState] from [ObserveLiveSetupStateUseCase] (BLO-175) —
 * this ViewModel composes nothing itself; it folds in only its local
 * transient state: the permission re-reads refreshed on resume and the
 * sign-in surface state. The live field-focus stream is not one of its
 * inputs (ENC-142): the bubble pipeline consumes it directly, and
 * Setup no longer renders a focus readout.
 */
@Suppress("LongParameterList") // One injected use case per capability, per the ViewModel rules.
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeLiveSetupState: ObserveLiveSetupStateUseCase,
    private val isFieldWatcherEnabled: IsFieldWatcherEnabledUseCase,
    private val isOverlayPermissionGranted: IsOverlayPermissionGrantedUseCase,
    private val isMicPermissionGranted: IsMicPermissionGrantedUseCase,
    private val refreshMicPermission: RefreshMicPermissionUseCase,
    private val ensureModel: EnsureModelUseCase,
    private val ensureParakeetModel: EnsureParakeetModelUseCase,
    private val selectEngine: SelectEngineUseCase,
    private val saveBubbleAppearance: SaveBubbleAppearanceUseCase,
    private val isBatteryOptimizationExempt: IsBatteryOptimizationExemptUseCase,
    private val setAutoStartEnabled: SetAutoStartEnabledUseCase,
    private val recordBubbleUserStart: RecordBubbleUserStartUseCase,
    private val recordBubbleUserStop: RecordBubbleUserStopUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
    private val refreshCustomStyles: RefreshCustomStylesUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val submitOutputReport: SubmitOutputReportUseCase,
    private val observeLastStyledResult: ObserveLastStyledResultUseCase,
    private val observeSelectedStyle: ObserveSelectedStyleUseCase,
    private val selectStyle: SelectStyleUseCase,
    private val clearLastStyledResult: ClearLastStyledResultUseCase,
    private val startBubbleIfPresenceAllows: StartBubbleIfPresenceAllowsUseCase,
) : ViewModel() {

    init {
        // Custom-styles sync (BLO-25 S4): refresh the account cache
        // whenever the gate shows a signed-in user — a fresh sign-in and
        // an already-signed-in process start alike (the StateFlow replays
        // the current state to this collector). Fire-and-forget: the
        // outcome stays in the use case, so a failed refresh never
        // blocks the status screen or the sign-in flow.
        viewModelScope.launch {
            observeLiveSetupState().collect { state ->
                if (state.authState is AuthState.Authenticated) {
                    refreshCustomStyles()
                }
            }
        }
        // Desired-state convergence (BLO-189): the trigger, not the
        // decision — the use case re-checks its gates and is the one
        // decision point both this foreground start and the boot
        // receiver funnel through. distinctUntilChanged makes one
        // attempt per entry into the due state; a refused start waits
        // for the manual refresh in Setup instead of retrying.
        viewModelScope.launch {
            observeLiveSetupState().map { state ->
                state.authState is AuthState.Authenticated &&
                    state.onboardingProgress.finished &&
                    BubbleServiceStartPolicy.shouldAutoStart(state.residentPresence) &&
                    !state.bubbleServiceRunning
            }.distinctUntilChanged().collect { due ->
                if (due) startBubbleIfPresenceAllows(BubbleStartRequest.USER)
            }
        }
    }

    private val watcherEnabled = MutableStateFlow(isFieldWatcherEnabled())
    private val overlayPermissionGranted = MutableStateFlow(isOverlayPermissionGranted())
    private val micPermissionGranted = MutableStateFlow(isMicPermissionGranted())
    private val batteryExempt = MutableStateFlow(isBatteryOptimizationExempt())

    /**
     * Transient sign-in surface state (ENC-197): in-flight flag plus
     * the last failure's user-facing message. Not persisted — the
     * auth gate itself is the live [LiveSetupState.authState] stream.
     */
    private val signInUi = MutableStateFlow(SignInUi())

    /** Transient account-deletion surface state (BLO-178): dialog step, progress, error, deleted note. */
    private val deletionUi = MutableStateFlow(AccountDeletionUi())

    /** Transient output-report surface state (BLO-148): picker, progress, error, reported marker. */
    private val reportUi = MutableStateFlow(ReportUi())

    /**
     * The idempotency id for the current picker session (BLO-148):
     * generated when the picker opens for a result, reused by every
     * retry of that session, and dropped after a successful submit, so
     * a double-tap or a retry can never duplicate the report server-side.
     */
    private var clientReportId: String? = null
    private var clientReportKey: String? = null

    /** The transient slices as one group for the [uiState] combine. */
    private data class TransientUi(
        val signIn: SignInUi,
        val deletion: AccountDeletionUi,
        val report: ReportUi,
    )

    private val transientUi = combine(
        signInUi,
        deletionUi,
        reportUi,
        ::TransientUi,
    )

    /**
     * The four permission re-reads as one local slice: settings-posture
     * reads refreshed on resume (watcher enablement and battery
     * exemption are settings reads, overlay and microphone are platform
     * grants re-read the same way). Local transient state, folded into
     * the observed durable state at the [uiState] combine.
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

    val uiState: StateFlow<MainUiState> = combine(
        observeLiveSetupState(),
        permissionReads,
        transientUi,
        observeLastStyledResult(),
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
            transient = TransientUi(
                signIn = signInUi.value,
                deletion = deletionUi.value,
                report = reportUi.value,
            ),
            lastResult = observeLastStyledResult().value,
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
            saveBubbleAppearance(transform(observeLiveSetupState().value.bubbleAppearance))
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
     * Sign-out tapped in Setup (ENC-197; BLO-178): the auth listener
     * flips the account card to its signed-out state and the owner gate
     * locks the previous account's cloud styles. On-device dictation
     * and mechanical styles keep working signed out.
     *
     * Cache hygiene: on success this session's last styled result is
     * dropped (it must never be reported or shown under another
     * account), and a custom-style selection is reset to the catalog
     * default — the previous account's cache stays on disk behind the
     * owner gate, but its selected id must not linger as the visible
     * selection for the next account. [SignOutUseCase] itself is
     * unchanged.
     */
    fun onSignOutClick() {
        viewModelScope.launch {
            val result = signOut()
            if (result.isSuccess) {
                clearLastStyledResult()
                reportUi.update { ReportUi() }
                clientReportId = null
                clientReportKey = null
                if (observeSelectedStyle().value.startsWith(CUSTOM_STYLE_ID_PREFIX)) {
                    selectStyle(StyleCatalog.DEFAULT.id)
                }
            }
        }
    }

    /** "Delete account" tapped in Setup (BLO-178): opens confirmation step 1. */
    fun onDeleteAccountClick() {
        deletionUi.update {
            it.copy(step = DeletionStepUi.CONFIRM, error = null, accountDeleted = false)
        }
    }

    /** Step 1 confirmed (BLO-178): advances to the final step. Nothing is deleted yet. */
    fun onDeleteAccountContinue() {
        deletionUi.update { it.copy(step = DeletionStepUi.FINAL, error = null) }
    }

    /** Either deletion step dismissed (BLO-178): closes the flow; an in-flight delete keeps running. */
    fun onDeleteAccountDismiss() {
        if (deletionUi.value.isDeleting) return
        deletionUi.update { it.copy(step = DeletionStepUi.NONE, error = null) }
    }

    /**
     * Final "Delete account" tapped (BLO-178): re-authenticates first
     * (a fresh Google sign-in freshens the token's auth time, which the
     * server requires), then runs the deletion use case.
     *
     * A re-auth dismissal closes the flow silently; any other re-auth
     * failure reports in the dialog and stops — the account was not
     * deleted. [AccountDeletionOutcome.RequiresRecentLogin] after a
     * fresh sign-in is rare (a slow Credential Manager round-trip can
     * still age the token); it reports and stays on the final step for
     * a retry. [AccountDeletionOutcome.Unavailable] does the same.
     * [AccountDeletionOutcome.Deleted] closes the flow and leaves the
     * deleted note on Setup; the auth listener flips the account card
     * to signed out.
     */
    fun onDeleteAccountConfirm() {
        if (deletionUi.value.isDeleting) return
        deletionUi.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            val signInResult = signInWithGoogle()
            if (signInResult.isFailure) {
                val failure = signInResult.exceptionOrNull()
                deletionUi.update {
                    if (failure != null && failure.isSignInDismissal()) {
                        it.copy(isDeleting = false, step = DeletionStepUi.NONE, error = null)
                    } else {
                        it.copy(isDeleting = false, error = MSG_DELETE_SIGN_IN_FAILED)
                    }
                }
                return@launch
            }
            when (deleteAccount()) {
                AccountDeletionOutcome.Deleted -> {
                    deletionUi.update { AccountDeletionUi(accountDeleted = true) }
                    reportUi.update { ReportUi() }
                    clientReportId = null
                    clientReportKey = null
                }
                AccountDeletionOutcome.RequiresRecentLogin ->
                    deletionUi.update {
                        it.copy(isDeleting = false, error = MSG_DELETE_RECENT_LOGIN)
                    }
                AccountDeletionOutcome.Unavailable ->
                    deletionUi.update {
                        it.copy(isDeleting = false, error = MSG_DELETE_UNAVAILABLE)
                    }
                AccountDeletionOutcome.SignedOut ->
                    deletionUi.update { AccountDeletionUi() }
            }
        }
    }

    /** Dismisses the "Your account was deleted." note on Setup (BLO-178). */
    fun onAccountDeletedDismiss() {
        deletionUi.update { it.copy(accountDeleted = false) }
    }

    /**
     * "Report this output" tapped on the Last dictation card (BLO-148):
     * opens the reason picker and mints this picker session's
     * idempotency id. Opening the picker sends nothing — content leaves
     * the device only when a reason is tapped.
     */
    fun onReportClick() {
        val last = observeLastStyledResult().value ?: return
        val key = last.reportKey()
        if (clientReportId == null || clientReportKey != key) {
            clientReportId = UUID.randomUUID().toString()
            clientReportKey = key
        }
        reportUi.update { it.copy(pickerOpen = true, error = null, reportSent = false) }
    }

    /** Picker dismissed without a reason (BLO-148): nothing was sent. */
    fun onReportDismiss() {
        if (reportUi.value.isSubmitting) return
        reportUi.update { it.copy(pickerOpen = false, error = null) }
    }

    /**
     * A reason tapped in the picker (BLO-148): submits immediately with
     * this session's idempotency id. Further taps while a submission is
     * in flight are ignored, so a double-tap cannot submit twice. A
     * failure keeps the picker open with an error line and the same id,
     * ready to retry; a success closes the picker, marks this result
     * reported, and leaves the sent note on Setup.
     */
    fun onReportReasonClick(reason: ReportReasonUi) {
        val current = reportUi.value
        if (!current.pickerOpen || current.isSubmitting) return
        val last = observeLastStyledResult().value ?: run {
            reportUi.update { ReportUi() }
            return
        }
        val reportId = clientReportId ?: UUID.randomUUID().toString().also { fresh ->
            clientReportId = fresh
            clientReportKey = last.reportKey()
        }
        val key = last.reportKey()
        reportUi.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            when (submitOutputReport(reason.toDomain(), appVersion, reportId)) {
                is SubmitOutputReportOutcome.Submitted -> {
                    reportUi.update {
                        ReportUi(reportedKey = key, reportSent = true)
                    }
                    clientReportId = null
                    clientReportKey = null
                }
                SubmitOutputReportOutcome.NoResult ->
                    reportUi.update { ReportUi() }
                SubmitOutputReportOutcome.SignedOut ->
                    reportUi.update {
                        it.copy(isSubmitting = false, error = MSG_REPORT_SIGNED_OUT)
                    }
                SubmitOutputReportOutcome.Unavailable ->
                    reportUi.update {
                        it.copy(isSubmitting = false, error = MSG_REPORT_UNAVAILABLE)
                    }
            }
        }
    }

    /** Dismisses the "Report sent." note on Setup (BLO-148). */
    fun onReportSentDismiss() {
        reportUi.update { it.copy(reportSent = false) }
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
        live: LiveSetupState,
        permissions: PermissionReads,
        transient: TransientUi,
        lastResult: LastStyledResult?,
    ): MainUiState =
        MainUiState(
            watcherEnabled = permissions.watcherEnabled,
            watcherConnected = live.watcherConnected,
            overlayPermissionGranted = permissions.overlayPermissionGranted,
            bubbleServiceRunning = live.bubbleServiceRunning,
            micPermissionGranted = permissions.micPermissionGranted,
            auth = live.authState.toUi(),
            isSigningIn = transient.signIn.isSigningIn,
            signInError = transient.signIn.signInError,
            whisperModelStatus = mapModelStatus(live.whisperModelState),
            parakeetModelStatus = mapModelStatus(live.parakeetModelState),
            selectedEngine = mapEngine(live.selectedEngine),
            engineFallback = mapEngineFallback(live),
            bubbleAppearance = live.bubbleAppearance.toUi(),
            bubbleEnabled = !live.residentPresence.userStopped,
            autoStartEnabled = live.residentPresence.autoStartEnabled,
            batteryExempt = permissions.batteryExempt,
            deletion = transient.deletion,
            report = transient.report,
            lastDictation = lastResult?.let { result ->
                LastDictationUi(
                    styleId = result.styleId,
                    styledOutput = result.styledOutput,
                    reportKey = result.reportKey(),
                    isReported = transient.report.reportedKey == result.reportKey(),
                )
            },
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

    /**
     * The fallback note's data, from the read model's precomputed
     * effective engine: present only while a fallback engine is
     * actually in use — the selected engine's model is not Ready but
     * the other engine's is.
     */
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
        const val CUSTOM_STYLE_ID_PREFIX = "custom-"
    }
}

/** The identity a report is keyed by: when it was recorded plus which style produced it. */
private fun LastStyledResult.reportKey(): String = "$recordedAtEpochMillis:$styleId"

private fun ReportReasonUi.toDomain(): OutputReportReason =
    when (this) {
        ReportReasonUi.OFFENSIVE -> OutputReportReason.OFFENSIVE
        ReportReasonUi.SEXUAL -> OutputReportReason.SEXUAL
        ReportReasonUi.HATEFUL -> OutputReportReason.HATEFUL
        ReportReasonUi.WRONG -> OutputReportReason.WRONG
        ReportReasonUi.OTHER -> OutputReportReason.OTHER
    }

private fun mapEngine(engine: DictationEngine): DictationEngineUi =
    when (engine) {
        DictationEngine.WHISPER -> DictationEngineUi.WHISPER
        DictationEngine.PARAKEET -> DictationEngineUi.PARAKEET
    }

// User-facing deletion/report copy (English), in the GoogleSignInErrorMapper pattern.
internal const val MSG_DELETE_SIGN_IN_FAILED = "Sign-in failed. Your account was not deleted."
internal const val MSG_DELETE_RECENT_LOGIN = "Sign-in is too old. Sign in again and retry."
internal const val MSG_DELETE_UNAVAILABLE =
    "Could not reach the server. Your account was not deleted. Try again."
internal const val MSG_REPORT_SIGNED_OUT = "You are signed out. Sign in to send a report."
internal const val MSG_REPORT_UNAVAILABLE =
    "Could not send the report. Check your connection and try again."


/** Transient sign-in surface state: never persisted (ENC-197). */
private data class SignInUi(
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
)
