package com.shippopotamus.bloviate.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.diagnostics.CadenceCounters
import com.shippopotamus.bloviate.domain.evaluator.BubbleAppearanceMath
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.evaluator.BubbleServiceStartPolicy
import com.shippopotamus.bloviate.domain.evaluator.EngineWarmingEvaluator
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleForegroundType
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import com.shippopotamus.bloviate.application.port.ActiveModelStorePort
import com.shippopotamus.bloviate.application.port.BubbleOverlayAccessPort
import com.shippopotamus.bloviate.application.port.BubbleServiceStatePort
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import com.shippopotamus.bloviate.application.port.MicPermissionPort
import com.shippopotamus.bloviate.application.port.ModelStorePort
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.application.port.RecordingSessionPort
import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import com.shippopotamus.bloviate.application.port.ShadePresencePort
import com.shippopotamus.bloviate.presentation.BloviateSpacing
import com.shippopotamus.bloviate.presentation.BloviateTheme
import com.shippopotamus.bloviate.application.evaluator.StylingTextPostProcessor
import com.shippopotamus.bloviate.application.port.TranscriptSinkPort
import com.shippopotamus.bloviate.application.port.TranscriptionEngine
import com.shippopotamus.bloviate.application.usecase.ObserveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveBubblePositionUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveBubbleVisibilityUseCase
import com.shippopotamus.bloviate.application.usecase.SaveBubblePositionUseCase
import com.shippopotamus.bloviate.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service hosting the floating dictation bubble.
 *
 * Collects field-focus and keyboard-visibility state (published by the
 * field watcher) through [ObserveBubbleVisibilityUseCase] and shows the
 * bubble window only while the keyboard is up and an editable,
 * non-password field is focused (ENC-125) — never on password fields. The
 * window is `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE`
 * (non-negotiable: a focusable overlay would steal the host field's caret)
 * and `FLAG_LAYOUT_NO_LIMITS`.
 *
 * Foreground type: `microphone` once `RECORD_AUDIO` is granted (the
 * ENC-59 intermediate step, completed by ENC-60). Android 14+ refuses to
 * start a microphone-typed foreground service without the permission, so
 * until it is granted the service still starts as `specialUse` and simply
 * cannot record yet — the pair (permission, mic type) is decided at start.
 *
 * The service also owns the voice plumbing lifecycle (ENC-60): it ensures
 * the transcription model is present, keeps the [TranscriptionEngine]
 * context warm while running, and releases it under memory pressure
 * ([onTrimMemory]) or on destroy, per `contracts/TRANSCRIPTION_RULES.md`.
 * The bubble window doubles as the recording bar (ENC-61): a
 * [BubbleRecordingController] drives the session from the bar's taps and
 * feeds the bar state back into the same composition.
 *
 * Resident presence (ENC-129): the service is started explicitly from
 * the app, by the boot receiver once setup is finished, or by the
 * system itself after a kill (sticky restart) — the persisted
 * [ResidentPresencePort] record gates the automatic starts, and a
 * user-pressed Stop is honored by all of them until the next explicit
 * in-app start. Every start begins as `specialUse` (the only type
 * legal in every context, boot included) and promotes per
 * [BubbleServiceStartPolicy] once the start's origin is known. The
 * foreground notification is a minimal silent notice (ENC-163). The
 * bubble window itself is resident (ENC-163): added once while
 * overlay permission holds and toggled by visibility only, so a
 * hidden bubble keeps its composition (and any in-flight recording)
 * alive. Without overlay permission every show command is a safe
 * no-op (logged).
 */
@AndroidEntryPoint
class BubbleOverlayService : Service() {

    @Inject
    lateinit var observeBubbleVisibility: ObserveBubbleVisibilityUseCase

    @Inject
    lateinit var observeBubblePosition: ObserveBubblePositionUseCase

    @Inject
    lateinit var observeBubbleAppearance: ObserveBubbleAppearanceUseCase

    @Inject
    lateinit var saveBubblePosition: SaveBubblePositionUseCase

    @Inject
    lateinit var bubbleServiceState: BubbleServiceStatePort

    @Inject
    lateinit var bubbleOverlayAccess: BubbleOverlayAccessPort

    @Inject
    lateinit var transcriptSink: TranscriptSinkPort

    @Inject
    lateinit var stylingProcessor: StylingTextPostProcessor

    @Inject
    lateinit var modelStore: ActiveModelStorePort

    @Inject
    lateinit var whisperModelStore: ModelStorePort

    @Inject
    lateinit var parakeetModelStore: ParakeetModelStorePort

    @Inject
    lateinit var enginePreference: EnginePreferencePort

    @Inject
    lateinit var transcriptionEngine: TranscriptionEngine

    @Inject
    lateinit var micPermission: MicPermissionPort

    @Inject
    lateinit var recordingSession: RecordingSessionPort

    @Inject
    lateinit var residentPresence: ResidentPresencePort

    @Inject
    lateinit var shadePresence: ShadePresencePort

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Whether the notification shade currently covers the bubble
     * (ENC-164). While true, [applyCommand] swallows every visibility
     * command so the shade is a deterministic no-op for the bubble —
     * the window stays exactly as commanded and recording is
     * untouched. Collected from [ShadePresencePort] below.
     */
    private var shadePresent: Boolean = false

    /** The foreground type the service currently runs under; every start begins `specialUse` (ENC-129). */
    private var currentForegroundType: BubbleForegroundType = BubbleForegroundType.SPECIAL_USE

    private lateinit var windowManager: WindowManager

    // Resident window (ENC-163): added once while overlay permission
    // holds, toggled by visibility only. The lambdas mirror
    // BubbleWindowPlacement's pushLayout pattern so the seam is
    // fakeable without a real WindowManager (see ResidentBubbleWindow).
    private val residentWindow = ResidentBubbleWindow(
        canDrawOverlays = { bubbleOverlayAccess.canDrawOverlays() },
        addView = { view, params -> windowManager.addView(view, params) },
        removeView = { view -> windowManager.removeViewImmediate(view) },
        updateView = { view, params -> windowManager.updateViewLayout(view, params) },
        onHostHidden = { isPasswordFocus ->
            recordingController?.onHostHidden(isPasswordFocus)
        },
        isPasswordFocus = { observeBubbleVisibility.isPasswordFocus() },
    )
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private val inDismissZone = mutableStateOf(false)
    private var recordingController: BubbleRecordingController? = null
    private val barStateSnapshot = mutableStateOf<RecordingBarState>(RecordingBarState.Idle)

    // Engine warming (ENC-126): while the effective engine's model is
    // present but the initial warm-up has not completed, the bubble
    // renders its warming indication and taps are no-ops — an absent
    // bubble in that window read as broken. Derived by
    // EngineWarmingEvaluator from the active model state and the
    // warm-up bookkeeping below; read by the composition and by the
    // recording controller's tap gate.
    private var activeModelState: ModelState = ModelState.Absent
    private var engineWarmUpCompleted: Boolean = false
    private var warmUpJob: Job? = null
    private val warmingSnapshot = mutableStateOf(false)

    // The user's bubble look (ENC-68): collected for the service's whole
    // lifetime, so a Setup edit restyles the showing bubble on the next
    // emission — the window is never recreated for an appearance change.
    private val appearanceSnapshot = mutableStateOf(BubbleAppearance.DEFAULT)

    // Window placement (ENC-65 bar form, ENC-123 drag-end/resting, ENC-154
    // single-writer gate): all window-position math lives in
    // [BubbleWindowPlacement] so it is unit-testable; this service owns
    // the window lifecycle and forwards events. The drag controller below
    // feeds the gate — while it reports an active drag, it owns the
    // window's layout exclusively.
    private val windowPlacement = BubbleWindowPlacement(
        pushLayout = { view, params -> windowManager.updateViewLayout(view, params) },
        bubbleSizePx = ::bubbleSizePx,
        barEdgeMarginPx = ::barEdgeMarginPx,
        savePosition = { position -> serviceScope.launch { saveBubblePosition(position) } },
        isDragging = { dragController?.isDragging == true },
        barContentWidthPx = ::barContentWidthPx,
    )
    private var dragController: BubbleDragController? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        micPermission.refresh()
        // ENC-129: every start begins as specialUse — onCreate runs
        // before the starting intent arrives, so the origin is unknown
        // here, and specialUse is the only type legal in every start
        // context (a microphone-typed start from the boot receiver is
        // refused on Android 15). onStartCommand promotes per
        // BubbleServiceStartPolicy once the origin is known.
        currentForegroundType = BubbleServiceStartPolicy.initialType()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            currentForegroundType.toServiceInfoType(),
        )
        Log.d(
            TAG,
            "Foreground service started with the ${currentForegroundType} type; " +
                "microphone permission granted: ${micPermission.isGrantedNow()}",
        )
        bubbleServiceState.publishServiceRunning(true)
        if (!bubbleOverlayAccess.canDrawOverlays()) {
            Log.w(TAG, "Started without overlay permission; the bubble will not show until it is granted")
        }
        val controller = BubbleRecordingController(
            session = recordingSession,
            transcriptionEngine = transcriptionEngine,
            stylingProcessor = stylingProcessor,
            transcriptSink = transcriptSink,
            enginePreference = enginePreference,
            whisperStore = whisperModelStore,
            parakeetStore = parakeetModelStore,
            scope = serviceScope,
            isEngineWarming = { warmingSnapshot.value },
        )
        recordingController = controller
        serviceScope.launch {
            controller.barState.collect { state ->
                CadenceCounters.snapshotWrite() // ENC-69 cadence counters (L3)
                // Place the window BEFORE publishing the new state: the
                // snapshot write can compose the wide recording row in the
                // same frame, so the one-shot x must already be set or a
                // right-side row renders off-screen for a frame and then
                // visibly shoots in when the layout is pushed (ENC-167).
                windowPlacement.onBarStateChanged(state, currentBounds())
                barStateSnapshot.value = state
                // ENC-125: bar activity arms the visibility machine's
                // keyboard-hide exemption — while the bar is up a keyboard
                // hide cannot hide the window; when the bar settles back
                // to idle, a deferred hide (if one is owed) comes back
                // here and is applied like any other command.
                val deferredCommand =
                    observeBubbleVisibility.onBarActivityChanged(state != RecordingBarState.Idle)
                if (deferredCommand != null) applyCommand(deferredCommand)
            }
        }
        serviceScope.launch {
            observeBubbleVisibility().collect { command -> applyCommand(command) }
        }
        serviceScope.launch {
            // Shade no-op (ENC-164, second layer): track presence so
            // visibility commands are swallowed while the shade is up.
            // On the lift, resync the reducer's belief against the
            // resident window's actual visibility — a poisoned signal
            // leaked past the ENC-132/156 freeze may have flipped the
            // belief while the window itself never moved.
            shadePresence.shadePresent.collect { present ->
                val wasPresent = shadePresent
                shadePresent = present
                if (wasPresent && !present) {
                    val corrective =
                        observeBubbleVisibility.onWindowVisibilityChanged(residentWindow.isVisible)
                    if (corrective != null) {
                        Log.d(TAG, "shade lifted: resyncing visibility with $corrective")
                        applyCommand(corrective)
                    }
                }
            }
        }
        serviceScope.launch {
            observeBubbleAppearance().collect { appearance -> appearanceSnapshot.value = appearance }
        }
        serviceScope.launch {
            observeBubblePosition().collect { saved ->
                windowPlacement.restingPosition = saved ?: BubblePosition.DEFAULT
            }
        }
        serviceScope.launch {
            modelStore.state.collect { state -> onModelStateChanged(state) }
        }
        serviceScope.launch {
            // Amplitude publishes at ~30 Hz (ENC-66): log state *transitions*
            // only, or logcat drowns in Recording updates.
            var lastLoggedSessionKind: String? = null
            recordingSession.state.collect { state ->
                val kind = state::class.simpleName
                if (kind != lastLoggedSessionKind) {
                    lastLoggedSessionKind = kind
                    Log.d(TAG, "Recording session state: $state")
                }
            }
        }
        serviceScope.launch(Dispatchers.Default) {
            modelStore.ensureModel()
        }
        // ENC-163: add the resident window eagerly at service start
        // (hidden: VISIBLE view at alpha 0 + NOT_TOUCHABLE, so nothing
        // flashes and the system notice survives) instead of
        // on the focus path — the add at focus time is where overlay
        // windows fail (ENC-145: stale folded-display bounds). A no-op
        // without overlay permission; the lazy path in showResident
        // covers permission granted later.
        serviceScope.launch {
            if (ensureResidentAttached()) {
                Log.d(TAG, "Resident window attached eagerly at service start")
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Memory watchdog (TRANSCRIPTION_RULES §4): at RUNNING_CRITICAL the
        // warm native context is the biggest reclaimable asset — release it
        // rather than let the LMK kill the process; it re-loads lazily on
        // the next dictation. At RUNNING_LOW the waveform history is the
        // releasable cache.
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "onTrimMemory RUNNING_CRITICAL (level $level): releasing the warm whisper context")
                serviceScope.launch(Dispatchers.Default) {
                    transcriptionEngine.release()
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                recordingController?.onLowMemory()
                Log.d(TAG, "onTrimMemory RUNNING_LOW (level $level): waveform history released")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // In-app stop (MainActivity.stopBubbleService + the user-stop
        // use case) is the only stop path now that the notification
        // carries no Stop action (ENC-163): it records userStopped
        // itself and stops the service, so no ACTION_STOP branch is
        // needed here. A revoked overlay permission tears the window
        // down but leaves the service running for its lazy return.
        handlePermissionRevocation()
        val origin = when (intent?.action) {
            ACTION_USER_START -> BubbleServiceStartOrigin.USER
            ACTION_BOOT_START -> BubbleServiceStartOrigin.BOOT
            // A null intent is the system's sticky-restart delivery; any
            // other unrecognized action is treated the same conservative
            // way — promotion is attempted under a guard, never assumed.
            else -> BubbleServiceStartOrigin.RESTART
        }
        promoteForegroundType(origin)
        val sticky = BubbleServiceStartPolicy.restartIsSticky(residentPresence.state.value)
        Log.d(TAG, "Start handled: origin $origin, foreground type $currentForegroundType, sticky $sticky")
        return if (sticky) START_STICKY else START_NOT_STICKY
    }

    /**
     * Promotes the running foreground type per [BubbleServiceStartPolicy]
     * for this start's [origin]. The attempt is guarded: a restart the
     * platform refuses (background-start restrictions) leaves the
     * service running as `specialUse` — bubble visible, recording
     * unavailable — instead of crashing, and the next eligible start
     * (the user's next in-app start) promotes again.
     */
    private fun promoteForegroundType(origin: BubbleServiceStartOrigin) {
        val target = BubbleServiceStartPolicy.promotionType(
            origin = origin,
            micPermissionGranted = micPermission.isGrantedNow(),
        ) ?: return
        if (target == currentForegroundType) return
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), target.toServiceInfoType())
            currentForegroundType = target
            Log.d(TAG, "Foreground type promoted to $target for origin $origin")
        } catch (exception: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Promotion to $target refused for origin $origin; staying $currentForegroundType", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Promotion to $target denied for origin $origin; staying $currentForegroundType", exception)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / fold-unfold: the resident window stays added and
        // re-clamps the current position into the new bounds — even
        // while hidden, so it returns on-screen; never removed and
        // re-added. The persisted normalized position is left untouched
        // so the user's anchor survives posture changes.
        if (handlePermissionRevocation()) return
        if (residentWindow.isAttached) {
            windowPlacement.onConfigurationChanged(currentBounds())
        }
    }

    override fun onDestroy() {
        removeResidentWindow()
        recordingController?.shutdown()
        recordingController = null
        observeBubbleVisibility.reset()
        bubbleServiceState.publishServiceRunning(false)
        // Release the native context on a fire-and-forget scope: serviceScope
        // is cancelled below and runBlocking is banned by the style contract.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            transcriptionEngine.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onModelStateChanged(state: ModelState) {
        Log.d(TAG, "Model state: $state")
        activeModelState = state
        refreshWarmingState()
        maybeWarmUp()
    }

    /**
     * Starts the engine warm-up when the model is Ready and the engine
     * is not warm yet, unless the device is thermally severe
     * (TRANSCRIPTION_RULES §4) — a deferred warm-up is retried on the
     * next model-state change and on every bubble show. Idempotent:
     * an in-flight warm-up is never doubled.
     */
    private fun maybeWarmUp() {
        if (activeModelState !is ModelState.Ready || transcriptionEngine.isWarm()) {
            refreshWarmingState()
            return
        }
        if (warmUpJob?.isActive == true) return
        if (!thermalAllowsModelLoad()) {
            Log.w(TAG, "Model ready but thermal status is severe; deferring engine warm-up")
            return
        }
        warmUpJob = serviceScope.launch(Dispatchers.Default) {
            transcriptionEngine.warmUp()
            refreshWarmingState()
        }
    }

    /**
     * Recomputes the bubble's warming indication (ENC-126). Observing
     * the engine already warm — however it got there — completes the
     * initial warm-up for this service's lifetime, so a later
     * memory-pressure release never brings the warming state back.
     */
    private fun refreshWarmingState() {
        if (transcriptionEngine.isWarm()) {
            engineWarmUpCompleted = true
        }
        warmingSnapshot.value =
            EngineWarmingEvaluator.isWarming(activeModelState, engineWarmUpCompleted)
    }

    /** Never start a model load while the device is thermally severe (TRANSCRIPTION_RULES §4). */
    private fun thermalAllowsModelLoad(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return true
        return powerManager.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun applyCommand(command: BubbleVisibilityCommand) {
        // Overlay permission is poll-only: a revocation noticed at any
        // command tears the resident window down (the service keeps
        // running, and the next show-worthy command lazily re-adds it).
        // The visibility belief resyncs to the hidden reality here, so
        // the dropped command is re-driven by the next transition.
        // Revocation teardown is independent of the shade gate below.
        if (handlePermissionRevocation()) {
            observeBubbleVisibility.onWindowVisibilityChanged(false)
            return
        }
        // Shade no-op (ENC-164): while the shade is present every
        // visibility command is swallowed — no show/hide, no reconcile
        // report, no host-hidden effects — whatever the watcher leaked
        // past the freeze. Recording-controller deferred hides flow
        // through here too, so they are swallowed the same way; after
        // the lift the watcher's re-derivation drives the real state.
        if (shadePresent) {
            Log.d(TAG, "visibility command swallowed while shade present (ENC-164)")
            return
        }
        when (command) {
            BubbleVisibilityCommand.ShowBubble -> showResident()
            BubbleVisibilityCommand.HideBubble -> hideResident()
        }
        // ENC-127 reconciliation: report the resident window's actual
        // visibility back to the state machine after every command. If
        // the command's effect didn't materialize (a show swallowed by
        // a missing overlay permission, say), the machine's visibility
        // belief resyncs instead of swallowing every later show; if a
        // window is shown that should not be, the corrective hide comes
        // back here and is applied like any other command (it
        // terminates: applying it hides the window, so the nested
        // report agrees).
        val windowVisible = residentWindow.isVisible
        // ENC-145: the machine's inputs ride every command line, so a
        // device repro where the bubble never appears (or never hides)
        // resolves to the exact stuck signal — focus, keyboard, bar
        // exemption, dismissal suppression, or belief — without guessing.
        Log.d(
            TAG,
            "visibility command $command applied; " +
                "${observeBubbleVisibility.describeStateForLog()}; window visible: $windowVisible",
        )
        val corrective = observeBubbleVisibility.onWindowVisibilityChanged(windowVisible)
        if (corrective != null) {
            Log.d(TAG, "visibility reconcile: window shown but not desired; applying $corrective")
            applyCommand(corrective)
        }
    }

    /**
     * Shows the resident window. The window is added ONCE (ENC-163) —
     * eagerly at service start, or lazily here when permission was
     * missing at start and granted later: every show only flips
     * visibility, so show/hide cycles never touch add/remove. A show
     * while permission is missing is a safe no-op — the reconcile
     * report above resyncs the belief so the next transition
     * re-drives it.
     */
    private suspend fun showResident() {
        if (!bubbleOverlayAccess.canDrawOverlays()) {
            Log.w(TAG, "Show requested but overlay permission is not granted; no-op")
            return
        }
        // A warm-up deferred (thermal) or not yet triggered retries here:
        // the bubble is about to be on screen, warming indication and all.
        maybeWarmUp()
        if (recordingController == null) {
            Log.w(TAG, "Show requested before the recording controller exists; no-op")
            return
        }
        if (!ensureResidentAttached()) {
            Log.w(TAG, "Show lost overlay permission mid-build; no-op")
            return
        }
        residentWindow.setVisible(true)
        Log.d(TAG, "Bubble shown")
    }

    /**
     * Builds and attaches the resident window if it is not attached
     * yet (ENC-163: the add lives here and at service start, never on
     * the per-show visibility path). The composition, lifecycle owner,
     * drag controller, and layout are created ONCE and live until the
     * window is removed. The composition stays alive across hides (a
     * shade-hidden recording must keep finishing, ENC-156).
     * [restorePosition] runs at attach time so the first show lands
     * correctly. Returns whether the window is attached afterwards.
     */
    private suspend fun ensureResidentAttached(): Boolean {
        if (residentWindow.isAttached) return true
        if (!bubbleOverlayAccess.canDrawOverlays()) return false
        val controller = recordingController ?: return false
        val owner = ServiceLifecycleOwner()
        val params = createLayoutParams()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
        // Gestures are detected inside the composition (see BubbleContent):
        // the drag controller only handles the high-level events it reports.
        // It is kept as a field so the placement gate can see the active
        // drag: while it drags, it owns the window's layout (ENC-154).
        val drag = BubbleDragController(
            windowManager = windowManager,
            bubbleView = view,
            layoutParams = params,
            onTapGesture = controller::onBubbleTap,
            onDismissRequested = ::onBubbleDismissRequested,
            onDragEnded = { position -> windowPlacement.onDragEnded(position, currentBounds()) },
            inDismissZone = inDismissZone,
        )
        view.setContent {
            // The overlay wears the same canonical palette as the app
            // (ENC-85): one fixed dark scheme, no system-theme branch.
            BloviateTheme {
                BubbleContent(
                    barState = barStateSnapshot.value,
                    waveformLevels = controller.waveform::levels,
                    inDismissZone = inDismissZone.value,
                    engineWarming = warmingSnapshot.value,
                    appearance = appearanceSnapshot.value,
                    onTap = drag::onTap,
                    onCancelRecording = controller::onCancelTap,
                    onSendRecording = controller::onSendTap,
                    onTransientStateElapsed = controller::onTransientStateElapsed,
                    onDragStart = drag::onDragStart,
                    onDrag = drag::onDrag,
                    onDragEnd = drag::onDragEnd,
                    onDragCancel = drag::onDragCancel,
                )
            }
        }
        // Window-resize placement is resting-only (ENC-167): bar-form
        // placement is one-shot on state entry and a resize while a bar
        // form is up never moves the window. An appearance size change
        // (ENC-68) resizes the idle bubble; the re-clamp then re-derives
        // the resting position from the persisted anchor at the new size
        // (ENC-123). While a drag is active this is a no-op: the drag
        // controller owns the layout (ENC-154).
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = right - left != oldRight - oldLeft
            val heightChanged = bottom - top != oldBottom - oldTop
            windowPlacement.onWindowResized(widthChanged, heightChanged, currentBounds())
        }
        if (!residentWindow.ensureAttached(view, params)) return false
        lifecycleOwner = owner
        dragController = drag
        windowPlacement.attach(view, params)
        owner.create()
        restorePosition(view, params)
        return true
    }

    /**
     * Hides the resident window without removing it. The host-hidden
     * side effect (via [ResidentBubbleWindow]) fires only on an actual
     * visible-to-hidden transition: it cancels an active recording and
     * discards an in-flight send only when a password field takes
     * focus — nothing may ever be transcribed for it. Any other hide
     * lets a live recording finish through the stop path and lets a
     * running send deliver or retain (ENC-156); a shade peek is not a
     * password field.
     */
    private fun hideResident() {
        if (residentWindow.setVisible(false)) {
            inDismissZone.value = false
            Log.d(TAG, "Bubble hidden")
        }
    }

    /**
     * Removes the resident window: service teardown or overlay
     * permission revocation only. Fires no host-hidden side effect —
     * removal is not a hide. After a revocation the next show-worthy
     * command lazily re-adds the window. Returns true when a window
     * was removed.
     */
    private fun handlePermissionRevocation(): Boolean {
        if (!residentWindow.removeIfPermissionLost()) return false
        tearDownResidentComposition()
        Log.w(TAG, "Overlay permission revoked; resident window removed")
        return true
    }

    /** Full teardown of the resident window and its composition state. */
    private fun removeResidentWindow() {
        if (!residentWindow.isAttached) return
        residentWindow.detach()
        tearDownResidentComposition()
        Log.d(TAG, "Resident window removed")
    }

    /**
     * Drops everything built around the resident window: placement
     * bookkeeping, the single-writer drag controller, and the
     * composition's lifecycle owner. The next add rebuilds them once.
     */
    private fun tearDownResidentComposition() {
        windowPlacement.detach()
        dragController = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        inDismissZone.value = false
    }

    private fun onBubbleDismissRequested() {
        val command = observeBubbleVisibility.onBubbleDismissed() ?: return
        serviceScope.launch { applyCommand(command) }
    }

    /**
     * Apply the persisted (or default) position once the view is attached.
     * The anchor is re-derived against the bubble's own appearance size in
     * the CURRENT bounds (ENC-145) — never the live view size, which can
     * report a transient (zero or pre-posture) width on the first layout
     * after a fold/unfold and resolve the show off-screen. Every later
     * repositioning (reclamp) already uses this same size, so the show
     * lands exactly where the bubble will rest.
     */
    private suspend fun restorePosition(view: ComposeView, params: WindowManager.LayoutParams) {
        val saved = observeBubblePosition().first() ?: BubblePosition.DEFAULT
        windowPlacement.restingPosition = saved
        view.post {
            if (residentWindow.attachedView !== view) return@post
            val bounds = currentBounds()
            val (bubbleWidthPx, bubbleHeightPx) = bubbleSizePx()
            val pixels = BubblePositionMath.restingTopLeft(
                position = saved,
                bubbleWidthPx = bubbleWidthPx,
                bubbleHeightPx = bubbleHeightPx,
                bounds = bounds,
            )
            params.x = pixels.xPx
            params.y = pixels.yPx
            windowManager.updateViewLayout(view, params)
            Log.d(
                TAG,
                "Bubble show at ${pixels.xPx},${pixels.yPx} in " +
                    "${bounds.widthPx}x${bounds.heightPx} from anchor $saved",
            )
        }
    }

    /** The bubble's own extent in pixels, from the appearance's size step (ENC-68). */
    private fun bubbleSizePx(): Pair<Int, Int> {
        val sizePx = (BubbleAppearanceMath.bubbleSizeDp(appearanceSnapshot.value.size) *
            resources.displayMetrics.density).roundToInt()
        return sizePx to sizePx
    }

    private fun barEdgeMarginPx(): Int =
        (BAR_EDGE_MARGIN_DP * resources.displayMetrics.density).roundToInt()

    /**
     * Known content width for a bar-form state (ENC-167), computed from
     * fixed geometry — never a measured live view width — so placement
     * can run once before the state composes. The recording row's width
     * is its slot/pill constants; the processing pill hugs its label, so
     * its width is the label measured at the labelLarge size plus its
     * s24 insets.
     */
    private fun barContentWidthPx(state: RecordingBarState): Int {
        val density = resources.displayMetrics.density
        return when (state) {
            is RecordingBarState.Recording -> {
                val renderParams = BubbleAppearanceMath.renderParams(appearanceSnapshot.value)
                (recordingRowWidthDp(renderParams) * density).roundToInt()
            }
            RecordingBarState.Transcribing,
            RecordingBarState.Polishing,
            -> {
                val labelRes = if (state == RecordingBarState.Transcribing) {
                    R.string.bubble_stage_transcribing
                } else {
                    R.string.bubble_stage_polishing
                }
                val paint = Paint().apply {
                    textSize = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        PROCESSING_LABEL_TEXT_SIZE_SP,
                        resources.displayMetrics,
                    )
                }
                val textWidthPx = paint.measureText(getString(labelRes))
                val paddingPx = (BloviateSpacing.s24 * 2).value * density
                (textWidthPx + paddingPx).roundToInt()
            }
            RecordingBarState.Idle,
            is RecordingBarState.Done,
            RecordingBarState.Error,
            -> 0
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

    private fun currentBounds(): ScreenBounds {
        val rect = windowManager.currentWindowMetrics.bounds
        return ScreenBounds(widthPx = rect.width(), heightPx = rect.height())
    }

    private fun createNotificationChannel() {
        // ENC-163: the notice is silent, so the channel is
        // IMPORTANCE_MIN. Importance is create-only, so an existing
        // channel above MIN is deleted first to apply the downgrade on
        // existing installs (same id, so no orphaned channel lingers);
        // otherwise create is a no-op update and user customization is
        // left untouched.
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance != NotificationManager.IMPORTANCE_MIN) {
            manager.deleteNotificationChannel(CHANNEL_ID)
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bubble_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        manager.createNotificationChannel(channel)
    }

    /**
     * The minimal foreground notice (ENC-163): icon plus app name,
     * ongoing, no text and no actions. The bubble's own visibility
     * carries state now; the notification only satisfies the
     * foreground-service requirement.
     */
    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG: String = "BubbleOverlay"

        /** Clearance kept between the recording bar and the screen's right edge. */
        const val BAR_EDGE_MARGIN_DP: Float = 8f
        const val CHANNEL_ID: String = "bubble_overlay"
        const val NOTIFICATION_ID: Int = 1001
        const val REQUEST_CODE_OPEN_APP: Int = 0

        /** The processing pill's label size (labelLarge), for its known-width measurement. */
        const val PROCESSING_LABEL_TEXT_SIZE_SP: Float = 14f

        /** Explicit in-app start (ENC-129): always eligible for the microphone type. */
        const val ACTION_USER_START: String = "com.shippopotamus.bloviate.action.START_BUBBLE_USER"

        /** Boot-receiver start (ENC-129): specialUse only, never promoted at boot. */
        const val ACTION_BOOT_START: String = "com.shippopotamus.bloviate.action.START_BUBBLE_BOOT"
    }
}

/** Maps the framework-free foreground type onto the platform's `ServiceInfo` constants. */
private fun BubbleForegroundType.toServiceInfoType(): Int =
    when (this) {
        BubbleForegroundType.MICROPHONE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        BubbleForegroundType.SPECIAL_USE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }
