package com.shippopotamus.bloviate.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.shippopotamus.bloviate.application.port.BubbleServiceStarterPort
import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.application.port.BubbleStartRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The application-layer bubble-service starter (BLO-189): maps a
 * start request onto its service intent action and owns the
 * foreground-service mechanics. A platform refusal is a
 * [BubbleServiceStartOutcome.Refused], never a throw — presence
 * resumes at the next app open instead of taking the caller down.
 */
class AndroidBubbleServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : BubbleServiceStarterPort {

    override fun startBubble(request: BubbleStartRequest): BubbleServiceStartOutcome {
        val action = when (request) {
            BubbleStartRequest.BOOT -> BubbleOverlayService.ACTION_BOOT_START
            BubbleStartRequest.USER -> BubbleOverlayService.ACTION_USER_START
        }
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BubbleOverlayService::class.java).setAction(action),
            )
            Log.d(TAG, "Bubble start ($request): bubble service requested")
            BubbleServiceStartOutcome.Started
        } catch (exception: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException lands here: the
            // platform refused the start outright. Presence resumes at
            // the next app open instead of taking the caller down.
            Log.w(TAG, "Bubble start ($request) refused by the platform; deferring to the next app open", exception)
            BubbleServiceStartOutcome.Refused
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bubble start ($request) denied by the platform; deferring to the next app open", exception)
            BubbleServiceStartOutcome.Refused
        }
    }
}

private const val TAG: String = "AndroidBubbleServiceStarter"
