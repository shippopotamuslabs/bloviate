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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection

/**
 * [LlmStylePort] over OpenRouter chat-completions (ENC-73).
 *
 * Ported from Enclave's `OpenRouterInferenceServiceImpl` shape — the
 * endpoint, Bearer auth, a model id, and a model/messages payload — minus
 * the parts Bloviate does not need: no OpenAI SDK (plain
 * `HttpURLConnection` via [StyleHttpTransport]) and no tools. Every
 * transport or model failure becomes [LlmStyleOutcome.FellBack] with a
 * short reason code; only coroutine cancellation is rethrown. Reasons
 * never carry response text.
 *
 * Model chain (ENC-84): [OpenRouterPayload.PRIMARY_MODEL] (the floating
 * `~x-ai/grok-latest` alias) first; on a model-unavailable answer or a
 * liveness failure, one retry on [OpenRouterPayload.FALLBACK_MODEL]
 * (Haiku 5.5); after that the caller keeps the local text. Each attempt
 * pins its OpenRouter provider routing (ENC-80) and logs its `model=` /
 * `provider=` so dogfood telemetry shows which link actually answered.
 *
 * Streaming (ENC-81): the response is consumed as an SSE stream, but
 * insertion semantics do not change — chunks are assembled in memory,
 * cleaned, and only the complete result is ever returned; any failure
 * yields the complete local text. A fragment can never reach the
 * field. Timeouts judge LIVENESS, not total time: a first-token budget
 * and an inter-token gap budget (any line — token or keep-alive —
 * counts as a sign of life), plus a generous absolute cap as backstop.
 * A healthy stream may therefore run far past the old 4s total cap on
 * long dictations, while a dead route fails in ~2s.
 */
class OpenRouterStyleClient(
    private val transport: StyleHttpTransport,
    private val prompts: StylePromptBuilder,
    private val cleaner: LlmResponseCleaner,
    private val apiKeys: ApiKeyPort,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    producerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LlmStylePort {

    /**
     * The stream's producer side runs on this client-owned supervisor
     * scope, never as a child of the caller's send path: when a
     * liveness budget kills an attempt, the abandoned producer drains
     * on its own (bounded by the transport's socket backstop) instead
     * of holding the dictation hostage.
     */
    private val producerScope = CoroutineScope(SupervisorJob() + producerDispatcher)

    override suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome {
        if (text.isBlank()) return LlmStyleOutcome.Styled(text = text, llmMs = 0L)
        val key = apiKeys.currentKey() ?: return LlmStyleOutcome.FellBack(reason = REASON_NO_KEY)
        val prompt = prompts.build(style = style, transcript = text)
        val startedMs = clock()
        return try {
            // One absolute backstop for the whole chain; the per-attempt
            // liveness budgets inside do the real judging (ENC-81).
            withTimeout(ABSOLUTE_CAP_MS) {
                when (val first = attemptPrimary(key = key, prompt = prompt, startedMs = startedMs)) {
                    is AttemptResult.Done -> first.outcome
                    is AttemptResult.Retry -> retryOutcome(key = key, prompt = prompt, startedMs = startedMs)
                }
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

    private suspend fun retryOutcome(key: String, prompt: StylePrompt, startedMs: Long): LlmStyleOutcome =
        when (
            val second = attempt(
                key = key,
                model = OpenRouterPayload.FALLBACK_MODEL,
                providerOrder = OpenRouterPayload.FALLBACK_PROVIDER_ORDER,
                providerIgnore = OpenRouterPayload.FALLBACK_PROVIDER_IGNORE,
                prompt = prompt,
                startedMs = startedMs,
            )
        ) {
            is AttemptResult.Done -> second.outcome
            is AttemptResult.Retry -> LlmStyleOutcome.FellBack(
                reason = second.reason,
                model = OpenRouterPayload.FALLBACK_MODEL,
                provider = second.provider,
            )
        }

    private suspend fun attemptPrimary(key: String, prompt: StylePrompt, startedMs: Long): AttemptResult =
        attempt(
            key = key,
            model = OpenRouterPayload.PRIMARY_MODEL,
            providerOrder = OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
            providerIgnore = emptyList(),
            prompt = prompt,
            startedMs = startedMs,
        )

    /**
     * One model attempt, streamed. [AttemptResult.Retry] means the
     * failure class is worth the other model's one try (model
     * unavailable, liveness death, retryable stream error); anything
     * else — success or a definitive failure — is [AttemptResult.Done].
     */
    private suspend fun attempt(
        key: String,
        model: String,
        providerOrder: List<String>?,
        providerIgnore: List<String>,
        prompt: StylePrompt,
        startedMs: Long,
    ): AttemptResult {
        val request = OpenRouterPayload.buildChatRequest(
            model = model,
            system = prompt.system,
            user = prompt.user,
            providerOrder = providerOrder,
            providerIgnore = providerIgnore,
            stream = true,
        )
        val lines = Channel<String>(Channel.RENDEZVOUS)
        val call = producerScope.async {
            try {
                transport.postChatCompletion(apiKey = key, requestJson = request) { line -> lines.send(line) }
            } finally {
                lines.close()
            }
        }
        val assembly = try {
            consumeLines(lines)
        } catch (liveness: LivenessException) {
            call.cancel()
            Log.d(TAG, "style attempt model=$model outcome=${liveness.reason}")
            return AttemptResult.Retry(reason = liveness.reason)
        }
        if (assembly.errorData != null) {
            call.cancel()
            return streamErrorResult(data = assembly.errorData, model = model)
        }
        val result = if (assembly.doneReceived) {
            // [DONE] only ever terminates a 2xx stream; the producer may
            // still be draining the socket, so don't wait on it.
            call.cancel()
            HttpResult(status = HttpURLConnection.HTTP_OK, body = "")
        } else {
            call.await()
        }
        if (result.status != HttpURLConnection.HTTP_OK) return httpFailureResult(result = result, model = model)
        val outcome = finish(content = assembly.content, provider = assembly.provider, startedMs = startedMs, model = model)
        Log.d(TAG, "style attempt model=$model provider=${outcome.providerLabel()} outcome=${outcome.logLabel()}")
        return AttemptResult.Done(outcome)
    }

    /**
     * Assembles one stream from its lines, judging liveness as it goes:
     * the wait for the first content token gets [FIRST_TOKEN_TIMEOUT_MS],
     * every later wait gets [TOKEN_GAP_TIMEOUT_MS]; any received line
     * (token or keep-alive) restarts the current window.
     */
    private suspend fun consumeLines(lines: Channel<String>): StreamAssembly {
        val content = StringBuilder()
        var provider: String? = null
        var sawContent = false
        while (true) {
            val budget = if (sawContent) TOKEN_GAP_TIMEOUT_MS else FIRST_TOKEN_TIMEOUT_MS
            val received = withTimeoutOrNull(budget) { lines.receiveCatching() }
                ?: throw LivenessException(
                    if (sawContent) REASON_STREAM_STALL else REASON_FIRST_TOKEN_TIMEOUT,
                )
            if (received.isClosed) {
                return StreamAssembly(content = content.toString(), provider = provider)
            }
            val line = received.getOrThrow()
            val data = sseData(line) ?: continue
            if (data == DONE_MARKER) {
                return StreamAssembly(content = content.toString(), provider = provider, doneReceived = true)
            }
            provider = OpenRouterPayload.extractProvider(data) ?: provider
            if (OpenRouterPayload.isStreamError(data)) {
                return StreamAssembly(content = content.toString(), provider = provider, errorData = data)
            }
            val delta = OpenRouterPayload.extractStreamDelta(data)
            if (delta != null) {
                sawContent = true
                content.append(delta)
            }
        }
    }

    /** The SSE payload after `data:`, or null for keep-alives/comments/blank lines. */
    private fun sseData(line: String): String? =
        line.trim().takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

    private fun streamErrorResult(data: String, model: String): AttemptResult {
        val provider = OpenRouterPayload.extractProvider(data)
        if (OpenRouterPayload.isModelUnavailable(status = HttpURLConnection.HTTP_OK, body = data)) {
            Log.d(TAG, "style attempt model=$model outcome=model-unavailable (stream error)")
            return AttemptResult.Retry(reason = REASON_MODEL_UNAVAILABLE, provider = provider)
        }
        Log.d(TAG, "style attempt model=$model outcome=$REASON_STREAM_ERROR")
        return AttemptResult.Done(
            LlmStyleOutcome.FellBack(reason = REASON_STREAM_ERROR, model = model, provider = provider),
        )
    }

    private fun httpFailureResult(result: HttpResult, model: String): AttemptResult {
        if (OpenRouterPayload.isModelUnavailable(status = result.status, body = result.body)) {
            Log.d(TAG, "style attempt model=$model outcome=model-unavailable")
            return AttemptResult.Retry(reason = REASON_MODEL_UNAVAILABLE)
        }
        Log.d(TAG, "style attempt model=$model outcome=${REASON_HTTP_PREFIX + result.status}")
        return AttemptResult.Done(
            LlmStyleOutcome.FellBack(
                reason = REASON_HTTP_PREFIX + result.status,
                model = model,
                provider = OpenRouterPayload.extractProvider(result.body),
            ),
        )
    }

    private fun finish(content: String, provider: String?, startedMs: Long, model: String): LlmStyleOutcome {
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

    /** What one attempt concluded: a final outcome, or a retryable failure with its reason. */
    private sealed interface AttemptResult {
        data class Done(val outcome: LlmStyleOutcome) : AttemptResult
        data class Retry(val reason: String, val provider: String? = null) : AttemptResult
    }

    /** The assembled stream: full content, responding provider, and how it ended. */
    private data class StreamAssembly(
        val content: String,
        val provider: String?,
        val doneReceived: Boolean = false,
        val errorData: String? = null,
    )

    /** A liveness budget expired mid-stream; [reason] is the telemetry code. */
    private class LivenessException(val reason: String) : Exception(reason)

    internal companion object {
        const val TAG: String = "OpenRouterStyle"

        /** Time a stream may stay silent before its first content token (ENC-81). */
        const val FIRST_TOKEN_TIMEOUT_MS: Long = 2_000L

        /** Time a stream may go silent between tokens before it is declared dead (ENC-81). */
        const val TOKEN_GAP_TIMEOUT_MS: Long = 2_000L

        /** Generous absolute backstop for the whole chain — only a pathological stream reaches it. */
        const val ABSOLUTE_CAP_MS: Long = 20_000L

        const val REASON_NO_KEY: String = "no-key"
        const val REASON_TIMEOUT: String = "timeout"
        const val REASON_TRANSPORT_ERROR: String = "transport-error"
        const val REASON_MODEL_UNAVAILABLE: String = "model-unavailable"
        const val REASON_FIRST_TOKEN_TIMEOUT: String = "first-token-timeout"
        const val REASON_STREAM_STALL: String = "stream-stall"
        const val REASON_STREAM_ERROR: String = "stream-error"
        const val REASON_HTTP_PREFIX: String = "http-"
        const val REASON_EMPTY_RESPONSE: String = "empty-response"

        const val DATA_PREFIX: String = "data:"
        const val DONE_MARKER: String = "[DONE]"
    }
}
