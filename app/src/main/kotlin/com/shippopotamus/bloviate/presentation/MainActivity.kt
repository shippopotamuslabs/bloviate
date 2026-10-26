package com.shippopotamus.bloviate.presentation

import android.Manifest
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
import com.shippopotamus.bloviate.application.port.BubbleServiceStarterPort
import com.shippopotamus.bloviate.domain.evaluator.BatteryExemptionIntentPolicy
import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import com.shippopotamus.bloviate.service.BubbleOverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher activity hosting the app's root route — the first-run
 * wizard (ENC-67) until its persisted record is finished, the tab
 * shell after.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG: String = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var bubbleServiceStarter: BubbleServiceStarterPort

    /**
     * The wizard's ViewModel (ENC-67): the same activity-scoped
     * instance the composition's `hiltViewModel()` resolves to, held
     * here only so lifecycle events reach it while the wizard — not
     * the shell — is the visible surface.
     */
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    /**
     * Whether the battery dialog attempt has already fired in this
     * activity session (ENC-141): the escalation input for
     * [BatteryExemptionIntentPolicy.firstAttempt]. Set only when the
     * direct intent actually starts — a start that throws never
     * fired, and the same tap's walk handles it.
     */
    private var batteryDirectAttemptFired: Boolean = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onMicPermissionResult()
        onboardingViewModel.onMicPermissionResult()
    }

    // ENC-163: the foreground notice is minimal and silent, so the
    // explicit bubble start no longer asks for POST_NOTIFICATIONS.
    // The manifest permission stays (a foreground service still posts
    // its notice), but no launcher or pre-start ask remains.

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
     * app), falling back to the battery-optimization settings list.
     *
     * Hardened by ENC-141 after a dead tap on Motorola: ANY exception
     * from [startActivity] advances the plan, not just
     * `ActivityNotFoundException` — OEM builds fail the direct intent
     * in more ways than one, and some start it "successfully" into a
     * no-op, which the policy's session escalation answers on the
     * next tap (a direct attempt that already fired goes straight to
     * the list). Every attempt is logged, fired or failed, so a field
     * report can arbitrate dialog-vs-list on a given device (the
     * ENC-128 decision-seam pattern). The direct intent is
     * Play-policy-restricted; the flag is recorded on ENC-139 for the
     * launch checklist and deliberately does not degrade this
     * sideload-first flow back to the list (ENC-129's behavior).
     * The direct intent needs the manifest's
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declaration (ENC-143);
     * the settings-list intent needs none.
     */
    private fun openBatteryOptimizationSettings() {
        var attempt: BatteryExemptionAttempt? =
            BatteryExemptionIntentPolicy.firstAttempt(directAlreadyTried = batteryDirectAttemptFired)
        while (attempt != null) {
            try {
                startActivity(attempt.intent())
                Log.i(TAG, "Battery exemption attempt $attempt started")
                if (attempt == BatteryExemptionAttempt.DIRECT_REQUEST) {
                    batteryDirectAttemptFired = true
                }
                return
            } catch (failure: Exception) {
                // A failed start — whatever its type — is the signal
                // to advance the plan, logged with the exception
                // because which attempt failed, and how, is field
                // evidence about the device, not an error in the flow.
                Log.w(TAG, "Battery exemption attempt $attempt failed to start; advancing the plan", failure)
                attempt = BatteryExemptionIntentPolicy.attemptAfter(attempt)
            }
        }
        Log.w(TAG, "Battery exemption plan exhausted; no attempt could be started")
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
        bubbleServiceStarter.startBubble(BubbleServiceStartOrigin.USER)
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
