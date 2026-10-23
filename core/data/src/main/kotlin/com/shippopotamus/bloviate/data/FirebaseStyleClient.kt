package com.shippopotamus.bloviate.data

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.LlmStyleOutcome
import com.shippopotamus.bloviate.application.port.LlmStylePort
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import kotlinx.coroutines.CancellationException

/**
 * [LlmStylePort] via the `styleText` Firebase Function (ENC-151). No key
 * exists on device anymore; anonymous auth identifies the caller and the
 * Function holds the provider key. Every failure becomes
 * [LlmStyleOutcome.FellBack] with a short reason code; only cancellation
 * is rethrown. Logs carry reason codes only — never transcript, styled
 * text, or key material.
 */
class FirebaseStyleClient(
    private val gateway: StyleFunctionGateway,
    private val auth: AuthSessionPort,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : LlmStylePort {

    override suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome {
        if (text.isBlank()) return LlmStyleOutcome.Styled(text = text, llmMs = 0L)
        val startedMs = clock()
        return try {
            if (auth.ensureSignedIn() == null) {
                Log.d(TAG, "style outcome=fell-back reason=no-auth")
                return LlmStyleOutcome.FellBack(reason = "no-auth")
            }
            val styledText = gateway.callStyleText(styleId = style.id, transcript = text)
            Log.d(TAG, "style outcome=styled")
            LlmStyleOutcome.Styled(text = styledText, llmMs = clock() - startedMs, model = null, provider = null)
        } catch (exception: StyleFunctionException) {
            Log.d(TAG, "style outcome=fell-back reason=${exception.code}")
            LlmStyleOutcome.FellBack(reason = exception.code)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "style outcome=fell-back reason=transport-error", exception)
            LlmStyleOutcome.FellBack(reason = "transport-error")
        }
    }

    private companion object {
        const val TAG: String = "FirebaseStyle"
    }
}
