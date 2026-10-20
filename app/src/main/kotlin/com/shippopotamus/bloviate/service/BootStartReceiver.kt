package com.shippopotamus.bloviate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.shippopotamus.bloviate.domain.evaluator.BubbleServiceStartPolicy
import com.shippopotamus.bloviate.domain.port.OnboardingPort
import com.shippopotamus.bloviate.domain.port.ResidentPresencePort
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resident presence at boot (ENC-129): after the device restarts, the
 * bubble service comes back on its own — but only when setup is
 * finished, the resident-presence record allows it, and the user has
 * not explicitly stopped the bubble (the decision itself is
 * [BubbleServiceStartPolicy.shouldAutoStart]). The start is tagged
 * [BubbleOverlayService.ACTION_BOOT_START], so the service begins as
 * `specialUse` and never attempts the microphone type from here:
 * Android 15 refuses microphone-typed foreground-service starts from a
 * boot receiver. A platform refusal is logged and deferred to the next
 * in-app start, never a crash.
 */
@AndroidEntryPoint
class BootStartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var residentPresence: ResidentPresencePort

    @Inject
    lateinit var onboarding: OnboardingPort

    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        // No super call: BroadcastReceiver.onReceive is abstract, and
        // Hilt's bytecode transform injects this receiver's fields at
        // the head of this method (the generated base's onReceive).
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        appScope.launch {
            try {
                val shouldStart = BubbleServiceStartPolicy.shouldAutoStart(
                    setupFinished = onboarding.progress.value.finished,
                    presence = residentPresence.state.value,
                )
                if (shouldStart) {
                    startBubbleService(context)
                } else {
                    Log.d(TAG, "Boot start skipped: resident-presence record does not allow it")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun startBubbleService(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BubbleOverlayService::class.java)
                    .setAction(BubbleOverlayService.ACTION_BOOT_START),
            )
            Log.d(TAG, "Boot start: bubble service requested")
        } catch (exception: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException lands here: the
            // platform refused the start outright. Presence resumes at
            // the next app open instead of taking the boot path down.
            Log.w(TAG, "Boot start refused by the platform; deferring to the next app open", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Boot start denied by the platform; deferring to the next app open", exception)
        }
    }

    private companion object {
        const val TAG: String = "BootStartReceiver"
    }
}
