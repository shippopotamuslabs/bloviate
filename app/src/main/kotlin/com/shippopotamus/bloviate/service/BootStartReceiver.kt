package com.shippopotamus.bloviate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shippopotamus.bloviate.application.usecase.BubbleAutoStartResult
import com.shippopotamus.bloviate.application.usecase.StartBubbleIfPresenceAllowsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resident presence at boot and after an app update (ENC-129, BLO-189):
 * after the device restarts — or after Bloviate itself is updated, which
 * tears the bubble service down — the bubble comes back on its own, but
 * only when setup is finished, the resident-presence record allows it,
 * and the user has not explicitly stopped the bubble.
 *
 * This receiver only translates the two system broadcasts
 * ([Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_MY_PACKAGE_REPLACED]) into
 * [StartBubbleIfPresenceAllowsUseCase]: the decision lives there and
 * in `BubbleServiceStartPolicy`, and the start mechanics live in the
 * application's bubble-service starter. The start is tagged
 * [BubbleOverlayService.ACTION_BOOT_START], so the service begins as
 * `specialUse` and never attempts the microphone type from here:
 * Android 15 refuses microphone-typed foreground-service starts from
 * a boot receiver. A platform refusal is logged by the starter and
 * deferred to the next in-app start, never a crash.
 *
 * Neither broadcast is delivered while the app is force-stopped, so a
 * stopped app stays stopped until the user opens it again.
 */
@AndroidEntryPoint
class BootStartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var startBubbleIfPresenceAllows: StartBubbleIfPresenceAllowsUseCase

    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        // No super call: BroadcastReceiver.onReceive is abstract, and
        // Hilt's bytecode transform injects this receiver's fields at
        // the head of this method (the generated base's onReceive).
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        appScope.launch {
            try {
                when (startBubbleIfPresenceAllows()) {
                    BubbleAutoStartResult.Started ->
                        Log.d(TAG, "Auto start ($action): bubble service requested")
                    BubbleAutoStartResult.NotAllowed ->
                        Log.d(TAG, "Auto start ($action) skipped: resident-presence record does not allow it")
                    BubbleAutoStartResult.StartRefused ->
                        Log.d(TAG, "Auto start ($action) not started: platform refused the request")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val TAG: String = "BootStartReceiver"
