package com.shippopotamus.bloviate.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.shippopotamus.bloviate.application.port.StyleTelemetryPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StyleTelemetryPort] over Firebase Crashlytics (BLO-167). Every
 * outcome lands as custom keys plus one log line carrying reason
 * codes only — never transcript, styled text, or key material — and
 * real function/transport failures are reported as non-fatals so a
 * dogfood permission-denied is visible in Crashlytics without adb.
 * Successful styled outcomes set keys and log but never
 * [FirebaseCrashlytics.recordException]. Everything is fail-safe:
 * a missing FirebaseApp in odd runtimes degrades to the local
 * [Log] line only. Device-verified; there is no JVM seam for the
 * Crashlytics static surface.
 */
@Singleton
class CrashlyticsStyleTelemetry @Inject constructor() : StyleTelemetryPort {

    override fun recordStyleOutcome(styleId: String, styled: Boolean, reason: String?, cause: Throwable?) {
        val outcome = if (styled) OUTCOME_STYLED else OUTCOME_FELL_BACK
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey(KEY_STYLE_ID, styleId)
            crashlytics.setCustomKey(KEY_OUTCOME, outcome)
            if (reason != null) crashlytics.setCustomKey(KEY_REASON, reason)
            crashlytics.log("style outcome=$outcome style_id=$styleId reason=${reason ?: "-"}")
            if (!styled && cause != null) crashlytics.recordException(cause)
        } catch (exception: Exception) {
            Log.d(TAG, "style telemetry dropped outcome=$outcome style_id=$styleId reason=${reason ?: "-"}", exception)
        }
    }

    private companion object {
        const val TAG: String = "StyleTelemetry"
        const val KEY_STYLE_ID: String = "style_id"
        const val KEY_OUTCOME: String = "style_outcome"
        const val KEY_REASON: String = "style_fallback_reason"
        const val OUTCOME_STYLED: String = "styled"
        const val OUTCOME_FELL_BACK: String = "fell-back"
    }
}
