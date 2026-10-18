package com.shippopotamus.bloviate.service

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.ScreenBounds
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.BubbleActionsPort
import com.shippopotamus.bloviate.domain.port.BubbleOverlayAccessPort
import com.shippopotamus.bloviate.domain.port.BubbleServiceStatePort
import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import com.shippopotamus.bloviate.domain.port.RecordingSessionPort
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import com.shippopotamus.bloviate.domain.usecase.ObserveBubblePositionUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleVisibilityUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveBubblePositionUseCase
import com.shippopotamus.bloviate.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service hosting the floating dictation bubble.
 *
 * Collects field-focus state (published by the field watcher) through
 * [ObserveBubbleVisibilityUseCase] and shows the bubble window only while an
 * editable, non-password field is focused — never on password fields. The
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
 * The recording session itself is driven by the bubble UI (ENC-61).
 *
 * Started explicitly by the user from the main screen; it never starts
 * itself. Without overlay permission every show command is a safe no-op
 * (logged), and the notification's Stop action remains available.
 */
@AndroidEntryPoint
class BubbleOverlayService : Service() {

    @Inject
    lateinit var observeBubbleVisibility: ObserveBubbleVisibilityUseCase

    @Inject
    lateinit var observeBubblePosition: ObserveBubblePositionUseCase

    @Inject
    lateinit var saveBubblePosition: SaveBubblePositionUseCase

    @Inject
    lateinit var bubbleServiceState: BubbleServiceStatePort

    @Inject
    lateinit var bubbleOverlayAccess: BubbleOverlayAccessPort

    @Inject
    lateinit var bubbleActions: BubbleActionsPort

    @Inject
    lateinit var modelStore: ModelStorePort

    @Inject
    lateinit var transcriptionEngine: TranscriptionEngine

    @Inject
    lateinit var micPermission: MicPermissionPort

    @Inject
    lateinit var recordingSession: RecordingSessionPort

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var bubbleView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val inDismissZone = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        micPermission.refresh()
        val micGranted = micPermission.isGrantedNow()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            if (micGranted) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            },
        )
        Log.d(
            TAG,
            if (micGranted) {
                "Foreground service started with the microphone type"
            } else {
                "Foreground service started with the specialUse type (microphone permission not granted)"
            },
        )
        bubbleServiceState.publishServiceRunning(true)
        if (!bubbleOverlayAccess.canDrawOverlays()) {
            Log.w(TAG, "Started without overlay permission; the bubble will not show until it is granted")
        }
        serviceScope.launch {
            observeBubbleVisibility().collect { command -> applyCommand(command) }
        }
        serviceScope.launch {
            modelStore.state.collect { state -> onModelStateChanged(state) }
        }
        serviceScope.launch {
            recordingSession.state.collect { state -> Log.d(TAG, "Recording session state: $state") }
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
        // the next dictation. At RUNNING_LOW there are no cached buffers
        // worth releasing yet (waveform history arrives with ENC-61).
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "onTrimMemory RUNNING_CRITICAL (level $level): releasing the warm whisper context")
                serviceScope.launch(Dispatchers.Default) {
                    transcriptionEngine.release()
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.d(TAG, "onTrimMemory RUNNING_LOW (level $level): nothing cached to release")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop requested from notification")
            hideBubble()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / fold-unfold: keep the same window and re-clamp the current
        // position into the new bounds; the persisted normalized position is
        // left untouched so the user's anchor survives posture changes.
        reclampBubble()
    }

    override fun onDestroy() {
        hideBubble()
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
        if (state is ModelState.Ready && !transcriptionEngine.isWarm()) {
            if (thermalAllowsModelLoad()) {
                serviceScope.launch(Dispatchers.Default) {
                    transcriptionEngine.warmUp()
                }
            } else {
                Log.w(TAG, "Model ready but thermal status is severe; deferring engine warm-up")
            }
        }
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
    }

    private suspend fun showBubble() {
        if (bubbleView != null) return
        if (!bubbleOverlayAccess.canDrawOverlays()) {
            Log.w(TAG, "Show requested but overlay permission is not granted; no-op")
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
            actions = bubbleActions,
            onDismissRequested = ::onBubbleDismissRequested,
            onDragEnded = { position -> serviceScope.launch { saveBubblePosition(position) } },
            inDismissZone = inDismissZone,
        )
        view.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                BubbleContent(
                    inDismissZone = inDismissZone.value,
                    onTap = dragController::onTap,
                    onLongPress = dragController::onLongPress,
                    onDragStart = dragController::onDragStart,
                    onDrag = dragController::onDrag,
                    onDragEnd = dragController::onDragEnd,
                    onDragCancel = dragController::onDragCancel,
                )
            }
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
        val view = bubbleView ?: return
        windowManager.removeViewImmediate(view)
        bubbleView = null
        layoutParams = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        inDismissZone.value = false
        Log.d(TAG, "Bubble hidden")
    }

    private fun onBubbleDismissRequested() {
        val command = observeBubbleVisibility.onBubbleDismissed() ?: return
        serviceScope.launch { applyCommand(command) }
    }

    /** Apply the persisted (or default) position once the view has been measured. */
    private suspend fun restorePosition(view: ComposeView, params: WindowManager.LayoutParams) {
        val saved = observeBubblePosition().first() ?: BubblePosition.DEFAULT
        view.post {
            if (bubbleView !== view) return@post
            val bounds = currentBounds()
            val pixels = BubblePositionMath.toPixels(
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

    private fun reclampBubble() {
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        val bounds = currentBounds()
        val clamped = BubblePositionMath.clamp(
            topLeft = BubblePixelPosition(xPx = params.x, yPx = params.y),
            bubbleWidthPx = view.width,
            bubbleHeightPx = view.height,
            bounds = bounds,
        )
        if (clamped.xPx != params.x || clamped.yPx != params.y) {
            params.x = clamped.xPx
            params.y = clamped.yPx
            windowManager.updateViewLayout(view, params)
        }
        Log.d(TAG, "Configuration changed; bubble re-clamped into ${bounds.widthPx}x${bounds.heightPx}")
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

    private fun buildNotification(): Notification {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(R.string.bubble_notification_text))
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.bubble_notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
        const val CHANNEL_ID: String = "bubble_overlay"
        const val NOTIFICATION_ID: Int = 1001
        const val REQUEST_CODE_OPEN_APP: Int = 0
        const val REQUEST_CODE_STOP: Int = 1
        const val ACTION_STOP: String = "com.shippopotamus.bloviate.action.STOP_BUBBLE"
    }
}
