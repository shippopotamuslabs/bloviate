package com.shippopotamus.bloviate.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.shippopotamus.bloviate.service.BubbleOverlayService
import com.shippopotamus.whisper.WhisperEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher activity hosting the app's root route — the first-run
 * wizard (ENC-67) until its persisted record is finished, the tab
 * shell after. The [WhisperEngine] injection proves the
 * composite-build substitution and the whisper-android-hilt bindings
 * resolve in the app's Hilt graph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var whisperEngine: WhisperEngine

    private val viewModel: MainViewModel by viewModels()

    /**
     * The wizard's ViewModel (ENC-67): the same activity-scoped
     * instance the composition's `hiltViewModel()` resolves to, held
     * here only so lifecycle events reach it while the wizard — not
     * the shell — is the visible surface.
     */
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onMicPermissionResult()
        onboardingViewModel.onMicPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloviateTheme {
                MainRoute(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onStartBubble = ::startBubbleService,
                    onStopBubble = ::stopBubbleService,
                    onRequestMicPermission = ::requestMicPermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
        onboardingViewModel.onResumed()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    /**
     * The battery-optimization list (ENC-129). The direct exemption
     * request (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is
     * Play-policy-restricted, so the user is sent to the list to set
     * Bloviate to Unrestricted instead.
     */
    private fun openBatteryOptimizationSettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun startBubbleService() {
        // An explicit in-app start lifts a previous Stop in the
        // resident-presence record (ENC-129) and tags the start as a
        // user start, the origin always eligible for the microphone type.
        viewModel.onBubbleStartRequested()
        ContextCompat.startForegroundService(
            this,
            Intent(this, BubbleOverlayService::class.java)
                .setAction(BubbleOverlayService.ACTION_USER_START),
        )
    }

    private fun stopBubbleService() {
        // Record the stop (ENC-129): boot start and sticky restart
        // honor it until the next explicit start.
        viewModel.onBubbleStopRequested()
        stopService(Intent(this, BubbleOverlayService::class.java))
    }

    private fun requestMicPermission() {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
