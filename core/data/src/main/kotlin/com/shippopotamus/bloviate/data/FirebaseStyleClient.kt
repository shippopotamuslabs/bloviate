package com.shippopotamus.bloviate.data

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.LlmStyleOutcome
import com.shippopotamus.bloviate.application.port.LlmStylePort
import com.shippopotamus.bloviate.application.port.StyleTelemetryPort
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import kotlinx.coroutines.CancellationException

/**
 * [LlmStylePort] via the `styleText` Firebase Function (ENC-151). No key
 * exists on device anymore; anonymous auth identifies the caller and the
 * Function holds the provider key. Every failure becomes
 * [LlmStyleOutcome.FellBack] with a short reason code; only cancellation
 * is rethrown. Logs carry reason codes only — never transcript, styled
 * text, or key material. Every non-blank outcome is also reported to
 * [StyleTelemetryPort] exactly once (BLO-167), fail-safe: a throwing
 * telemetry can never change the returned outcome.
 */
class FirebaseStyleClient(
    private val gateway: StyleFunctionGateway,
    private val auth: AuthSessionPort,
    private val telemetry: StyleTelemetryPort,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : LlmStylePort {

    override suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome {
        if (text.isBlank()) return LlmStyleOutcome.Styled(text = text, llmMs = 0L)
        val startedMs = clock()
        return try {
            if (auth.ensureSignedIn() == null) {
                Log.d(TAG, "style outcome=fell-back reason=no-auth")
                recordSafely(styleId = style.id, styled = false, reason = "no-auth")
                return LlmStyleOutcome.FellBack(reason = "no-auth")
            }
            val styledText = gateway.callStyleText(styleId = style.id, transcript = text)
            Log.d(TAG, "style outcome=styled")
            recordSafely(styleId = style.id, styled = true, reason = null)
            LlmStyleOutcome.Styled(text = styledText, llmMs = clock() - startedMs, model = null, provider = null)
        } catch (exception: StyleFunctionException) {
            Log.d(TAG, "style outcome=fell-back reason=${exception.code}")
            recordSafely(styleId = style.id, styled = false, reason = exception.code, cause = exception)
            LlmStyleOutcome.FellBack(reason = exception.code)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "style outcome=fell-back reason=transport-error", exception)
            recordSafely(styleId = style.id, styled = false, reason = "transport-error", cause = exception)
            LlmStyleOutcome.FellBack(reason = "transport-error")
        }
    }

    private fun recordSafely(styleId: String, styled: Boolean, reason: String?, cause: Throwable? = null) {
        try {
            telemetry.recordStyleOutcome(styleId = styleId, styled = styled, reason = reason, cause = cause)
        } catch (exception: Exception) {
            Log.d(TAG, "style telemetry dropped reason=${reason ?: "-"}", exception)
        }
    }

    private companion object {
        const val TAG: String = "FirebaseStyle"
    }
}
