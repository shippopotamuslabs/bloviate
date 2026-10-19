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
 *
 * Model chain (ENC-84): [OpenRouterPayload.PRIMARY_MODEL] (the floating
 * `~x-ai/grok-latest` alias) first; on a model-unavailable answer, one
 * retry on [OpenRouterPayload.FALLBACK_MODEL] (Haiku 5.5); after that
 * the caller keeps the local text. Each attempt logs its `model=` so
 * dogfood telemetry shows which link of the chain actually answered.
 * Each attempt also pins its OpenRouter provider routing (ENC-80:
 * [OpenRouterPayload.PRIMARY_PROVIDER_ORDER] /
 * [OpenRouterPayload.FALLBACK_PROVIDER_ORDER]) and surfaces the
 * responding provider OpenRouter stamps on the response.
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
                    ?: LlmStyleOutcome.FellBack(
                        reason = REASON_MODEL_UNAVAILABLE,
                        model = OpenRouterPayload.FALLBACK_MODEL,
                    )
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
        providerOrder: List<String>? = OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
        providerIgnore: List<String> = emptyList(),
    ): LlmStyleOutcome? {
        val request = OpenRouterPayload.buildChatRequest(
            model = model,
            system = prompt.system,
            user = prompt.user,
            providerOrder = providerOrder,
            providerIgnore = providerIgnore,
        )
        val result = transport.postChatCompletion(apiKey = key, requestJson = request)
        if (result.status == HttpURLConnection.HTTP_OK) {
            val outcome = finish(body = result.body, startedMs = startedMs, model = model)
            Log.d(TAG, "style attempt model=$model provider=${outcome.providerLabel()} outcome=${outcome.logLabel()}")
            return outcome
        }
        if (OpenRouterPayload.isModelUnavailable(status = result.status, body = result.body)) {
            Log.d(TAG, "style attempt model=$model outcome=model-unavailable")
            return null
        }
        Log.d(TAG, "style attempt model=$model outcome=${REASON_HTTP_PREFIX + result.status}")
        return LlmStyleOutcome.FellBack(
            reason = REASON_HTTP_PREFIX + result.status,
            model = model,
            provider = OpenRouterPayload.extractProvider(result.body),
        )
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
            providerOrder = OpenRouterPayload.FALLBACK_PROVIDER_ORDER,
            providerIgnore = OpenRouterPayload.FALLBACK_PROVIDER_IGNORE,
        )
        val result = transport.postChatCompletion(apiKey = key, requestJson = request)
        if (result.status == HttpURLConnection.HTTP_OK) {
            val outcome = finish(body = result.body, startedMs = startedMs, model = OpenRouterPayload.FALLBACK_MODEL)
            Log.d(
                TAG,
                "style attempt model=${OpenRouterPayload.FALLBACK_MODEL}" +
                    " provider=${outcome.providerLabel()} outcome=${outcome.logLabel()}",
            )
            return outcome
        }
        Log.d(TAG, "style attempt model=${OpenRouterPayload.FALLBACK_MODEL} outcome=failed status=${result.status}")
        return null
    }

    /** Short telemetry label for an outcome; never carries response text. */
    private fun LlmStyleOutcome.logLabel(): String =
        when (this) {
            is LlmStyleOutcome.Styled -> "styled"
            is LlmStyleOutcome.FellBack -> "fell-back:$reason"
        }

    /** The responding provider for telemetry, or "-" when unknown (ENC-80). */
    private fun LlmStyleOutcome.providerLabel(): String =
        when (this) {
            is LlmStyleOutcome.Styled -> provider ?: "-"
            is LlmStyleOutcome.FellBack -> provider ?: "-"
        }

    private fun finish(body: String, startedMs: Long, model: String): LlmStyleOutcome {
        val provider = OpenRouterPayload.extractProvider(body)
        val content = OpenRouterPayload.extractAssistantContent(body)
            ?: return LlmStyleOutcome.FellBack(reason = REASON_BAD_RESPONSE, model = model, provider = provider)
        val cleaned = cleaner.clean(content)
        if (cleaned.isBlank()) {
            return LlmStyleOutcome.FellBack(reason = REASON_EMPTY_RESPONSE, model = model, provider = provider)
        }
        return LlmStyleOutcome.Styled(
            text = cleaned,
            llmMs = clock() - startedMs,
            model = model,
            provider = provider,
        )
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
