package com.shippopotamus.bloviate.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.content.ComponentCallbacks2
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.shippopotamus.bloviate.presentation.BloviateTheme
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
import com.shippopotamus.bloviate.domain.evaluator.WatcherSupervisionEvaluator
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleForegroundType
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.WatcherSupervisionState
import com.shippopotamus.bloviate.domain.port.BubbleOverlayAccessPort
import com.shippopotamus.bloviate.domain.port.BubbleServiceStatePort
import com.shippopotamus.bloviate.domain.port.ActiveModelStorePort
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import com.shippopotamus.bloviate.domain.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.domain.port.RecordingSessionPort
import com.shippopotamus.bloviate.domain.port.ResidentPresencePort
import com.shippopotamus.bloviate.domain.port.WatcherAccessPort
import com.shippopotamus.bloviate.domain.port.WatcherConnectionPort
import com.shippopotamus.bloviate.domain.evaluator.StylingTextPostProcessor
import com.shippopotamus.bloviate.domain.port.TranscriptSinkPort
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubblePositionUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleVisibilityUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveBubblePositionUseCase
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
 * notification doubles as the watcher-supervision surface: its text
 * reports the field watcher's supervised state, so an
 * enabled-but-disconnected watcher is visible instead of silent.
 * Without overlay permission every show command is a safe no-op
 * (logged), and the notification's Stop action remains available.
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
    lateinit var watcherAccess: WatcherAccessPort

    @Inject
    lateinit var watcherConnection: WatcherConnectionPort

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The foreground type the service currently runs under; every start begins `specialUse` (ENC-129). */
    private var currentForegroundType: BubbleForegroundType = BubbleForegroundType.SPECIAL_USE

    /** The supervision state the notification currently shows; the text refreshes only on change. */
    private var lastNotificationStatus: WatcherSupervisionState? = null

    /** False once stopping begins: a torn-down service must never re-post its notification. */
    private var notificationUpdatesEnabled: Boolean = true

    private lateinit var windowManager: WindowManager
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var bubbleView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
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

    // The persisted anchor's in-memory mirror (ENC-123): collected for the
    // service's whole lifetime and updated synchronously wherever this
    // service persists a position, so re-deriving the resting position
    // (see reclampBubble) never lags the store.
    private var restingPosition: BubblePosition = BubblePosition.DEFAULT

    // Bar-form window placement (ENC-65): the recording bar is far wider
    // than the bubble and the window is anchored top-left, so the morph
    // repositions the window to keep the whole bar on-screen. These fields
    // remember the bubble's own anchor while the bar is up; the bar's
    // placement is never persisted as the bubble's position.
    private var previousBarState: RecordingBarState = RecordingBarState.Idle
    private var barFormAnchorXPx: Int? = null
    private var barFormAnchorWidthPx: Int = 0
    private var barFormAnchorHeightPx: Int = 0
    private var barFormMoved: Boolean = false

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
        val initialStatus = currentSupervision
        lastNotificationStatus = initialStatus
        startForeground(
            NOTIFICATION_ID,
            buildNotification(initialStatus),
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
                barStateSnapshot.value = state
                onBarStateChanged(state)
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
            // Watcher supervision (ENC-129): the notification text
            // follows the watcher's connection state for the service's
            // whole lifetime, so a silent disconnect surfaces there too.
            watcherConnection.isConnected.collect { refreshNotificationStatus() }
        }
        serviceScope.launch {
            observeBubbleAppearance().collect { appearance -> appearanceSnapshot.value = appearance }
        }
        serviceScope.launch {
            observeBubblePosition().collect { saved -> restingPosition = saved ?: BubblePosition.DEFAULT }
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
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop requested from notification")
            // The user's Stop is final (ENC-129): record it before
            // tearing down, so neither a sticky restart nor the boot
            // receiver resurrects the bubble behind the user's back.
            // The save is fire-and-forget on a fresh scope — stopSelf()
            // follows immediately and serviceScope dies with the service
            // (same pattern as the engine release in onDestroy).
            notificationUpdatesEnabled = false
            val presence = residentPresence.state.value
            if (!presence.userStopped) {
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    residentPresence.save(presence.copy(userStopped = true))
                }
            }
            hideBubble()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val origin = when (intent?.action) {
            ACTION_USER_START -> BubbleServiceStartOrigin.USER
            ACTION_BOOT_START -> BubbleServiceStartOrigin.BOOT
            // A null intent is the system's sticky-restart delivery; any
            // other unrecognized action is treated the same conservative
            // way — promotion is attempted under a guard, never assumed.
            else -> BubbleServiceStartOrigin.RESTART
        }
        promoteForegroundType(origin)
        refreshNotificationStatus()
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
            startForeground(NOTIFICATION_ID, buildNotification(currentSupervision), target.toServiceInfoType())
            currentForegroundType = target
            lastNotificationStatus = currentSupervision
            Log.d(TAG, "Foreground type promoted to $target for origin $origin")
        } catch (exception: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Promotion to $target refused for origin $origin; staying $currentForegroundType", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Promotion to $target denied for origin $origin; staying $currentForegroundType", exception)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / fold-unfold: keep the same window and re-clamp the current
        // position into the new bounds; the persisted normalized position is
        // left untouched so the user's anchor survives posture changes.
        reclampBubble()
    }

    override fun onDestroy() {
        notificationUpdatesEnabled = false
        hideBubble()
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
        when (command) {
            BubbleVisibilityCommand.ShowBubble -> showBubble()
            BubbleVisibilityCommand.HideBubble -> hideBubble()
        }
        // ENC-127 reconciliation: report the window's actual presence
        // back to the state machine after every command. If the command's
        // effect didn't materialize (a show swallowed by a missing
        // overlay permission, say), the machine's visibility belief
        // resyncs instead of swallowing every later show; if a window is
        // present that should not be, the corrective hide comes back
        // here and is applied like any other command (it terminates:
        // applying it removes the window, so the nested report agrees).
        val windowPresent = bubbleView != null
        Log.d(TAG, "visibility command $command applied; window present: $windowPresent")
        val corrective = observeBubbleVisibility.onWindowPresenceChanged(windowPresent)
        if (corrective != null) {
            Log.d(TAG, "visibility reconcile: window present but not desired; applying $corrective")
            applyCommand(corrective)
        }
    }

    private suspend fun showBubble() {
        if (bubbleView != null) return
        if (!bubbleOverlayAccess.canDrawOverlays()) {
            Log.w(TAG, "Show requested but overlay permission is not granted; no-op")
            return
        }
        // A warm-up deferred (thermal) or not yet triggered retries here:
        // the bubble is about to be on screen, warming indication and all.
        maybeWarmUp()
        val controller = recordingController
        if (controller == null) {
            Log.w(TAG, "Show requested before the recording controller exists; no-op")
            return
        }
        val owner = ServiceLifecycleOwner()
        val params = createLayoutParams()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
        // Gestures are detected inside the composition (see BubbleContent):
        // the drag controller only handles the high-level events it reports.
        val dragController = BubbleDragController(
            windowManager = windowManager,
            bubbleView = view,
            layoutParams = params,
            onTapGesture = controller::onBubbleTap,
            onDismissRequested = ::onBubbleDismissRequested,
            onDragEnded = ::handleDragEnded,
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
                    onTap = dragController::onTap,
                    onCancelRecording = controller::onCancelTap,
                    onSendRecording = controller::onSendTap,
                    onTransientStateElapsed = controller::onTransientStateElapsed,
                    onDragStart = dragController::onDragStart,
                    onDrag = dragController::onDrag,
                    onDragEnd = dragController::onDragEnd,
                    onDragCancel = dragController::onDragCancel,
                )
            }
        }
        // Bar-form placement rides the window's own layout: whenever the
        // view's width changes (the bar morph settling, elapsed-time
        // growth), the placement check keeps the whole bar on-screen.
        // An appearance size change (ENC-68) resizes the idle bubble in
        // the same way; the re-clamp then re-derives the resting position
        // from the persisted anchor at the new size (ENC-123) — the live
        // view size is never trusted mid-morph (see reclampBubble).
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = right - left != oldRight - oldLeft
            val heightChanged = bottom - top != oldBottom - oldTop
            if (widthChanged) applyBarPlacement()
            if ((widthChanged || heightChanged) && barFormAnchorXPx == null) reclampBubble()
        }
        windowManager.addView(view, params)
        lifecycleOwner = owner
        bubbleView = view
        layoutParams = params
        owner.create()
        restorePosition(view, params)
        Log.d(TAG, "Bubble shown")
    }

    private fun hideBubble() {
        // The window going away cancels any active recording and discards
        // any in-flight transcription — critically when a password field
        // takes focus, nothing may be transcribed for it.
        recordingController?.onHostHidden()
        // The window is gone; any bar-form placement bookkeeping dies with
        // it. The next show starts from the persisted bubble position.
        barFormAnchorXPx = null
        barFormMoved = false
        previousBarState = RecordingBarState.Idle
        val view = bubbleView ?: return
        windowManager.removeViewImmediate(view)
        bubbleView = null
        layoutParams = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        inDismissZone.value = false
        Log.d(TAG, "Bubble hidden")
    }

    /**
     * Drag-end routing: a normal bubble drag persists its end position.
     * A drag that ends while the recording bar is up must NOT persist the
     * position the drag controller computed — it normalized against the
     * bar's width, and the bar's placement is not the bubble's position.
     * Instead the drag is remembered and the bubble's anchor is re-derived
     * (and persisted) when the bar closes, in [exitBarForm].
     */
    private fun handleDragEnded(position: BubblePosition) {
        if (barFormAnchorXPx != null) {
            barFormMoved = true
        } else {
            restingPosition = position
            serviceScope.launch { saveBubblePosition(position) }
        }
    }

    /** Tracks bar-state transitions for window placement (see [enterBarForm] / [exitBarForm]). */
    private fun onBarStateChanged(newState: RecordingBarState) {
        val previous = previousBarState
        previousBarState = newState
        when {
            previous == RecordingBarState.Idle && newState is RecordingBarState.Recording ->
                enterBarForm()
            newState == RecordingBarState.Idle && previous != RecordingBarState.Idle ->
                exitBarForm()
        }
    }

    /**
     * The bubble is morphing into the recording bar: capture the bubble's
     * own anchor (position + size) before the bar lays out, then place the
     * window so the bar grows toward the screen interior (ENC-65).
     */
    private fun enterBarForm() {
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        barFormAnchorXPx = params.x
        barFormAnchorWidthPx = view.width
        barFormAnchorHeightPx = view.height
        barFormMoved = false
        applyBarPlacement()
    }

    /**
     * The bar is closing back to the mic bubble: the window returns to the
     * bubble's own anchor. If the bar was dragged, the bubble takes the
     * bar's final left edge (clamped to the bubble's own travel range) as
     * its new anchor, and that — computed with the bubble's size, never
     * the bar's — is what gets persisted.
     */
    private fun exitBarForm() {
        val anchorXPx = barFormAnchorXPx
        barFormAnchorXPx = null
        val moved = barFormMoved
        barFormMoved = false
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        if (anchorXPx == null) return
        if (moved) {
            val bounds = currentBounds()
            val landed = BubblePositionMath.clamp(
                topLeft = BubblePixelPosition(xPx = params.x, yPx = params.y),
                bubbleWidthPx = barFormAnchorWidthPx,
                bubbleHeightPx = barFormAnchorHeightPx,
                bounds = bounds,
            )
            params.x = landed.xPx
            params.y = landed.yPx
            val landedPosition = BubblePositionMath.toNormalized(
                topLeft = landed,
                bubbleWidthPx = barFormAnchorWidthPx,
                bubbleHeightPx = barFormAnchorHeightPx,
                bounds = bounds,
            )
            restingPosition = landedPosition
            serviceScope.launch { saveBubblePosition(landedPosition) }
        } else {
            params.x = anchorXPx
        }
        windowManager.updateViewLayout(view, params)
    }

    /**
     * (Re)place the window so the current bar form sits fully on-screen:
     * [BubblePositionMath.barWindowX] from the bubble's anchor — or, once
     * the user has dragged the bar, from its dragged position, so a width
     * change re-clamps instead of snapping the bar back to the anchor.
     * Runs when the bar form is entered and whenever the window's width
     * changes while the bar is up.
     */
    private fun applyBarPlacement() {
        val anchorXPx = barFormAnchorXPx ?: return
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        if (view.width <= 0) return
        val baseXPx = if (barFormMoved) params.x else anchorXPx
        val targetXPx = BubblePositionMath.barWindowX(
            bubbleXPx = baseXPx,
            barWidthPx = view.width,
            bounds = currentBounds(),
            edgeMarginPx = barEdgeMarginPx(),
        )
        if (targetXPx != params.x) {
            params.x = targetXPx
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun barEdgeMarginPx(): Int =
        (BAR_EDGE_MARGIN_DP * resources.displayMetrics.density).roundToInt()

    private fun onBubbleDismissRequested() {
        val command = observeBubbleVisibility.onBubbleDismissed() ?: return
        serviceScope.launch { applyCommand(command) }
    }

    /** Apply the persisted (or default) position once the view has been measured. */
    private suspend fun restorePosition(view: ComposeView, params: WindowManager.LayoutParams) {
        val saved = observeBubblePosition().first() ?: BubblePosition.DEFAULT
        restingPosition = saved
        view.post {
            if (bubbleView !== view) return@post
            val bounds = currentBounds()
            val pixels = BubblePositionMath.restingTopLeft(
                position = saved,
                bubbleWidthPx = view.width,
                bubbleHeightPx = view.height,
                bounds = bounds,
            )
            params.x = pixels.xPx
            params.y = pixels.yPx
            windowManager.updateViewLayout(view, params)
        }
    }

    /**
     * Keep the resting bubble on-screen when its size or the bounds change
     * around it (ENC-123).
     *
     * Outside bar form the resting position is RE-DERIVED from the
     * persisted anchor against the bubble's own appearance size — never
     * pixel-clamped against the view's live size. The old clamp read the
     * view mid-morph: on collapse the view is still bar-wide for the whole
     * settle animation, so each frame's clamp pulled a docked bubble's x
     * left toward the bar's width, and the one-way clamp never let it
     * back — the bubble came to rest displaced toward mid-screen after
     * every record cycle. The same clamp stranded a bubble off its docked
     * edge when an ENC-68 size step shrank it. Re-derivation is a
     * constant during a morph (the anchor and the bubble's size do not
     * change), preserves the docked edge at every size step, and moves
     * mid-screen positions only as the anchor model prescribes.
     *
     * In bar form the window's placement belongs to [applyBarPlacement] /
     * [exitBarForm]; the clamp below only keeps the bar itself on-screen
     * across a posture change.
     */
    private fun reclampBubble() {
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        val bounds = currentBounds()
        val target = if (barFormAnchorXPx != null) {
            BubblePositionMath.clamp(
                topLeft = BubblePixelPosition(xPx = params.x, yPx = params.y),
                bubbleWidthPx = view.width,
                bubbleHeightPx = view.height,
                bounds = bounds,
            )
        } else {
            val (bubbleWidthPx, bubbleHeightPx) = bubbleSizePx()
            BubblePositionMath.restingTopLeft(
                position = restingPosition,
                bubbleWidthPx = bubbleWidthPx,
                bubbleHeightPx = bubbleHeightPx,
                bounds = bounds,
            )
        }
        if (target.xPx != params.x || target.yPx != params.y) {
            params.x = target.xPx
            params.y = target.yPx
            windowManager.updateViewLayout(view, params)
        }
        Log.d(TAG, "Bubble re-clamped into ${bounds.widthPx}x${bounds.heightPx}")
    }

    /** The bubble's own extent in pixels, from the appearance's size step (ENC-68). */
    private fun bubbleSizePx(): Pair<Int, Int> {
        val sizePx = (BubbleAppearanceMath.bubbleSizeDp(appearanceSnapshot.value.size) *
            resources.displayMetrics.density).roundToInt()
        return sizePx to sizePx
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bubble_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The watcher's supervised state right now (ENC-129), from its two live signals. */
    private val currentSupervision: WatcherSupervisionState
        get() = WatcherSupervisionEvaluator.resolve(
            watcherEnabled = watcherAccess.isFieldWatcherEnabled(),
            watcherConnected = watcherConnection.isConnected.value,
        )

    /**
     * Re-posts the foreground notification when the watcher's supervised
     * state has changed, so the persistent notification reads as an
     * honest status surface (ENC-129): watching, watcher off, or watcher
     * disconnected. No-op once stopping has begun.
     */
    private fun refreshNotificationStatus() {
        if (!notificationUpdatesEnabled) return
        val status = currentSupervision
        if (status == lastNotificationStatus) return
        lastNotificationStatus = status
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
        Log.d(TAG, "Notification status refreshed: $status")
    }

    private fun buildNotification(status: WatcherSupervisionState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, BubbleOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val statusText = when (status) {
            WatcherSupervisionState.CONNECTED -> R.string.bubble_notification_text
            WatcherSupervisionState.DISABLED -> R.string.bubble_notification_text_watcher_off
            WatcherSupervisionState.DISCONNECTED -> R.string.bubble_notification_text_watcher_disconnected
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(statusText))
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.bubble_notification_stop), stopIntent)
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
        const val REQUEST_CODE_STOP: Int = 1
        const val ACTION_STOP: String = "com.shippopotamus.bloviate.action.STOP_BUBBLE"

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
