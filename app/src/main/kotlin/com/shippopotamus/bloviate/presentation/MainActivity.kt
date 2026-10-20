package com.shippopotamus.bloviate.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.shippopotamus.bloviate.domain.evaluator.BatteryExemptionIntentPolicy
import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt
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

    private companion object {
        const val TAG: String = "MainActivity"
    }

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
     * The battery-exemption ask (ENC-139), walked per
     * [BatteryExemptionIntentPolicy]: the one-tap system dialog first
     * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with our package
     * URI — the system asks in place and the user never leaves the
     * app), falling back to the battery-optimization settings list
     * when an OEM build strips the direct intent and starting it
     * throws [ActivityNotFoundException]. The direct intent is
     * Play-policy-restricted; the flag is recorded on ENC-139 for the
     * launch checklist and deliberately does not degrade this
     * sideload-first flow back to the list (ENC-129's behavior).
     * Neither intent needs a manifest permission to fire.
     */
    private fun openBatteryOptimizationSettings() {
        var attempt: BatteryExemptionAttempt? = BatteryExemptionIntentPolicy.firstAttempt()
        while (attempt != null) {
            try {
                startActivity(attempt.intent())
                return
            } catch (notFound: ActivityNotFoundException) {
                // A build that strips this attempt's intent is the
                // signal to advance the plan — logged because the
                // fallback being exercised is field evidence about
                // the device, not an error in the flow.
                Log.w(TAG, "Battery exemption attempt $attempt unavailable; advancing the plan", notFound)
                attempt = BatteryExemptionIntentPolicy.attemptAfter(attempt)
            }
        }
    }

    /** The platform intent a [BatteryExemptionAttempt] names. */
    private fun BatteryExemptionAttempt.intent(): Intent =
        when (this) {
            BatteryExemptionAttempt.DIRECT_REQUEST ->
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )

            BatteryExemptionAttempt.SETTINGS_LIST ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
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
