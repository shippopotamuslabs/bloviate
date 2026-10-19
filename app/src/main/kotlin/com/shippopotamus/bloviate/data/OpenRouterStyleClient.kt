package com.shippopotamus.bloviate.data

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.domain.evaluator.LlmResponseCleaner
import com.shippopotamus.bloviate.domain.evaluator.StylePrompt
import com.shippopotamus.bloviate.domain.evaluator.StylePromptBuilder
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.port.ApiKeyPort
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.domain.port.LlmStylePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection

/**
 * [LlmStylePort] over OpenRouter chat-completions (ENC-73).
 *
 * Ported from Enclave's `OpenRouterInferenceServiceImpl` shape — the
 * endpoint, Bearer auth, a model id, and a model/messages payload — minus
 * the parts Bloviate does not need: no OpenAI SDK (plain
 * `HttpURLConnection` via [StyleHttpTransport]), no tools, no streaming,
 * one single-shot call per dictation. Every transport or model failure
 * becomes [LlmStyleOutcome.FellBack] with a short reason code; only
 * coroutine cancellation is rethrown. Reasons never carry response text.
 */
class OpenRouterStyleClient(
    private val transport: StyleHttpTransport,
    private val prompts: StylePromptBuilder,
    private val cleaner: LlmResponseCleaner,
    private val apiKeys: ApiKeyPort,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : LlmStylePort {

    override suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome {
        if (text.isBlank()) return LlmStyleOutcome.Styled(text = text, llmMs = 0L)
        val key = apiKeys.currentKey() ?: return LlmStyleOutcome.FellBack(reason = REASON_NO_KEY)
        val prompt = prompts.build(style = style, transcript = text)
        val startedMs = clock()
        return try {
            // One hard cap for the whole call, primary plus the single
            // fallback-model retry — a slow network can never stall a send.
            withTimeout(LLM_TIMEOUT_MS) {
                attempt(key = key, model = OpenRouterPayload.PRIMARY_MODEL, prompt = prompt, startedMs = startedMs)
                    ?: retryOnce(key = key, prompt = prompt, startedMs = startedMs)
                    ?: LlmStyleOutcome.FellBack(reason = REASON_MODEL_UNAVAILABLE)
            }
        } catch (timeout: TimeoutCancellationException) {
            // The reason code is the pipeline's contract; the log keeps the
            // cause for on-device diagnosis. Neither carries the key.
            Log.w(TAG, "OpenRouter style call timed out", timeout)
            LlmStyleOutcome.FellBack(reason = REASON_TIMEOUT)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.w(TAG, "OpenRouter style call failed", exception)
            LlmStyleOutcome.FellBack(reason = REASON_TRANSPORT_ERROR)
        }
    }

    /**
     * One model attempt: null means "model unavailable, worth the fallback
     * retry"; anything else (success or a definitive failure) is final.
     */
    private suspend fun attempt(
        key: String,
        model: String,
        prompt: StylePrompt,
        startedMs: Long,
    ): LlmStyleOutcome? {
        val request = OpenRouterPayload.buildChatRequest(model = model, system = prompt.system, user = prompt.user)
        val result = transport.postChatCompletion(apiKey = key, requestJson = request)
        if (result.status == HttpURLConnection.HTTP_OK) return finish(body = result.body, startedMs = startedMs)
        if (OpenRouterPayload.isModelUnavailable(status = result.status, body = result.body)) return null
        return LlmStyleOutcome.FellBack(reason = REASON_HTTP_PREFIX + result.status)
    }

    private suspend fun retryOnce(
        key: String,
        prompt: StylePrompt,
        startedMs: Long,
    ): LlmStyleOutcome? {
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.FALLBACK_MODEL,
            system = prompt.system,
            user = prompt.user,
        )
        val result = transport.postChatCompletion(apiKey = key, requestJson = request)
        if (result.status == HttpURLConnection.HTTP_OK) return finish(body = result.body, startedMs = startedMs)
        return null
    }

    private fun finish(body: String, startedMs: Long): LlmStyleOutcome {
        val content = OpenRouterPayload.extractAssistantContent(body)
            ?: return LlmStyleOutcome.FellBack(reason = REASON_BAD_RESPONSE)
        val cleaned = cleaner.clean(content)
        if (cleaned.isBlank()) return LlmStyleOutcome.FellBack(reason = REASON_EMPTY_RESPONSE)
        return LlmStyleOutcome.Styled(text = cleaned, llmMs = clock() - startedMs)
    }

    private companion object {
        const val TAG: String = "OpenRouterStyle"

        /** Hard cap for primary + fallback retry combined (TRANSCRIPTION_RULES §7: latency is measured, not assumed). */
        const val LLM_TIMEOUT_MS: Long = 4_000L

        const val REASON_NO_KEY: String = "no-key"
        const val REASON_TIMEOUT: String = "timeout"
        const val REASON_TRANSPORT_ERROR: String = "transport-error"
        const val REASON_MODEL_UNAVAILABLE: String = "model-unavailable"
        const val REASON_HTTP_PREFIX: String = "http-"
        const val REASON_BAD_RESPONSE: String = "bad-response"
        const val REASON_EMPTY_RESPONSE: String = "empty-response"
    }
}
