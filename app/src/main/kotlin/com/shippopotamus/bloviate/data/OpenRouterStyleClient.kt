package com.shippopotamus.bloviate.data

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.domain.evaluator.LlmResponseCleaner
import com.shippopotamus.bloviate.domain.evaluator.StyleOutputGuard
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
 * `~x-ai/grok-latest` alias) first; on ANY attempt failure, one retry
 * on [OpenRouterPayload.FALLBACK_MODEL] (Haiku 5.5); after that the
 * caller keeps the local text. Each attempt pins its OpenRouter
 * provider routing (ENC-80) and logs its `model=` / `provider=` so
 * dogfood telemetry shows which link actually answered.
 *
 * Output guard (ENC-82): every attempt's cleaned output passes
 * [StyleOutputGuard] before it may become a result. A refusal-shaped
 * response is a failure (reason `refusal`), never inserted; a response
 * that silently drops protected content (names, numbers, the profanity
 * class) or most content words fails as `fidelity`. Both join every
 * other failure class — HTTP errors, transport failures, liveness
 * deaths, stream errors, empty responses — in retrying once on the
 * other model; the final fallback reports the last attempt's reason.
 *
 * Streaming (ENC-81): the response is consumed as an SSE stream, but
 * insertion semantics do not change — chunks are assembled in memory,
 * cleaned, and only the complete result is ever returned; any failure
 * yields the complete local text. A fragment can never reach the
 * field. Timeouts judge LIVENESS, not total time: a first-token budget,
 * a reasoning-phase budget, and an inter-token gap budget (any line —
 * token, reasoning, or keep-alive — counts as a sign of life), plus a
 * generous absolute cap as backstop. A healthy stream may therefore
 * run far past the old 4s total cap on long dictations. ENC-91
 * re-based the pre-content budgets for a reasoning primary: Grok
 * thinks before it speaks, and a budget that cannot tell thinking
 * from death kills healthy calls — the server completes them, the
 * client has already fallen back.
 *
 * Reasoning control (ENC-94): ENC-91 taught the client to tolerate
 * Grok's thinking; ENC-94 stops the thinking from being the problem.
 * The primary's request carries OpenRouter's unified `reasoning`
 * control at effort low (see [OpenRouterPayload.REASONING_EFFORT]
 * for why "off" is not honored by Grok 4.7 and "exclude" saves no
 * time), so a restyle's generation is content-dominated and the
 * healthy path lands in seconds. The chain cap is re-tuned to that
 * constrained profile, and the usage chunk's reasoning-token count
 * is logged with each attempt's outcome, so a future blowout is
 * visible in telemetry, not just in OpenRouter's dashboard.
 *
 * Degraded retry (ENC-95): ENC-94's first shape shipped
 * `reasoning: {effort, max_tokens}` on every attempt — a combination
 * OpenRouter's unified parameter does not accept, with fallback
 * arithmetic that was invalid by construction (see the payload
 * docs) — and both attempts began dying instantly with 400-class
 * rejections. The block is now effort-only and primary-only, and
 * the client is structurally hardened against the whole class: when
 * an attempt fails with a 400-class rejection (an HTTP 4xx, or an
 * in-stream error chunk carrying a 4xx code), that same model is
 * retried ONCE with a degraded payload — no reasoning block, no
 * `stream_options`, nothing optional left to reject — before the
 * chain advances. Provider parameter pickiness can slow the chain
 * by one round trip; it can no longer zero the style pass. Failed
 * attempts log their HTTP status and the provider's error code/type,
 * so the next rejection is diagnosable from logcat alone.
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
                when (val first = attemptPrimary(key = key, prompt = prompt, sourceText = text, startedMs = startedMs)) {
                    is AttemptResult.Done -> first.outcome
                    is AttemptResult.Retry -> retryOutcome(key = key, prompt = prompt, sourceText = text, startedMs = startedMs)
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

    private suspend fun retryOutcome(
        key: String,
        prompt: StylePrompt,
        sourceText: String,
        startedMs: Long,
    ): LlmStyleOutcome =
        when (
            val second = attempt(
                key = key,
                link = FALLBACK_LINK,
                prompt = prompt,
                sourceText = sourceText,
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

    private suspend fun attemptPrimary(
        key: String,
        prompt: StylePrompt,
        sourceText: String,
        startedMs: Long,
    ): AttemptResult =
        attempt(
            key = key,
            link = PRIMARY_LINK,
            prompt = prompt,
            sourceText = sourceText,
            startedMs = startedMs,
        )

    /** One chain step: a model, its provider pin, and its reasoning profile. */
    private data class ChainLink(
        val model: String,
        val providerOrder: List<String>?,
        val providerIgnore: List<String>,
        /** The unified `reasoning` effort for this link, or null for no block (ENC-95). */
        val reasoningEffort: String?,
    )

    /**
     * One chain step, streamed. Success is [AttemptResult.Done];
     * EVERY failure class is [AttemptResult.Retry] — worth the other
     * model's one try (ENC-82). The chain gives up only when the
     * fallback attempt also fails, reporting its reason.
     *
     * ENC-95 hardening: a 400-class rejection (the request itself was
     * refused — see [AttemptResult.Retry.clientError]) earns ONE
     * degraded retry on the SAME model before the chain advances:
     * whatever optional parameter the provider objected to, the
     * degraded payload doesn't carry it.
     */
    private suspend fun attempt(
        key: String,
        link: ChainLink,
        prompt: StylePrompt,
        sourceText: String,
        startedMs: Long,
    ): AttemptResult {
        val full = attemptOnce(key = key, link = link, prompt = prompt, sourceText = sourceText, startedMs = startedMs, degraded = false)
        if (full !is AttemptResult.Retry || !full.clientError) return full
        Log.d(TAG, "style attempt model=${link.model} outcome=$REASON_DEGRADED_RETRY after=${full.reason}")
        return attemptOnce(key = key, link = link, prompt = prompt, sourceText = sourceText, startedMs = startedMs, degraded = true)
    }

    /**
     * A single call against one model. [degraded] strips the payload
     * to the bare streamed request (ENC-95): no reasoning block, no
     * `stream_options` — nothing left for a provider to reject.
     */
    private suspend fun attemptOnce(
        key: String,
        link: ChainLink,
        prompt: StylePrompt,
        sourceText: String,
        startedMs: Long,
        degraded: Boolean,
    ): AttemptResult {
        val model = link.model
        val request = OpenRouterPayload.buildChatRequest(
            model = model,
            system = prompt.system,
            user = prompt.user,
            providerOrder = link.providerOrder,
            providerIgnore = link.providerIgnore,
            stream = true,
            reasoningEffort = if (degraded) null else link.reasoningEffort,
            includeUsage = !degraded,
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
        val result = try {
            if (assembly.doneReceived) {
                // [DONE] only ever terminates a 2xx stream; the producer may
                // still be draining the socket, so don't wait on it.
                call.cancel()
                HttpResult(status = HttpURLConnection.HTTP_OK, body = "")
            } else {
                call.await()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // The producer died mid-stream (socket, IO): a provider
            // failure like any other — worth the other model's try.
            Log.d(TAG, "style attempt model=$model outcome=$REASON_TRANSPORT_ERROR detail=${exception.javaClass.simpleName}")
            return AttemptResult.Retry(reason = REASON_TRANSPORT_ERROR)
        }
        if (result.status != HttpURLConnection.HTTP_OK) return httpFailureResult(result = result, model = model)
        return finish(
            content = assembly.content,
            provider = assembly.provider,
            reasoningTokens = assembly.reasoningTokens,
            startedMs = startedMs,
            model = model,
            sourceText = sourceText,
        )
    }

    /**
     * Assembles one stream from its lines, judging liveness as it goes.
     * The wait budget is phase-aware (ENC-91): before the first line,
     * [FIRST_TOKEN_TIMEOUT_MS] covers connection setup plus a reasoning
     * model's time-to-first-token; once reasoning deltas have been seen
     * the model is demonstrably thinking, and pre-content waits get
     * [REASONING_GAP_TIMEOUT_MS] — a reasoning pause is not a stall.
     * Only once content flows does the tight [TOKEN_GAP_TIMEOUT_MS]
     * apply. Any received line (token, reasoning, or keep-alive)
     * restarts the current window.
     *
     * A line that is not SSE at all can still be the whole answer: a
     * cached replay or a non-streaming gateway may deliver one plain
     * JSON completion body on the 200, and its message content is
     * salvaged whole instead of being dropped as an empty stream.
     */
    private suspend fun consumeLines(lines: Channel<String>): StreamAssembly {
        val content = StringBuilder()
        var provider: String? = null
        var reasoningTokens: Int? = null
        var sawContent = false
        var sawReasoning = false
        while (true) {
            val budget = when {
                sawContent -> TOKEN_GAP_TIMEOUT_MS
                sawReasoning -> REASONING_GAP_TIMEOUT_MS
                else -> FIRST_TOKEN_TIMEOUT_MS
            }
            val received = withTimeoutOrNull(budget) { lines.receiveCatching() }
                ?: throw LivenessException(
                    if (sawContent) REASON_STREAM_STALL else REASON_FIRST_TOKEN_TIMEOUT,
                )
            if (received.isClosed) {
                return StreamAssembly(content = content.toString(), provider = provider, reasoningTokens = reasoningTokens)
            }
            val line = received.getOrThrow()
            val data = sseData(line)
            if (data == null) {
                val wholeBody = OpenRouterPayload.extractMessageContent(line)
                if (wholeBody != null) {
                    provider = OpenRouterPayload.extractProvider(line) ?: provider
                    reasoningTokens = OpenRouterPayload.extractReasoningTokens(line) ?: reasoningTokens
                    return StreamAssembly(
                        content = wholeBody,
                        provider = provider,
                        doneReceived = true,
                        reasoningTokens = reasoningTokens,
                    )
                }
                continue
            }
            if (data == DONE_MARKER) {
                return StreamAssembly(
                    content = content.toString(),
                    provider = provider,
                    doneReceived = true,
                    reasoningTokens = reasoningTokens,
                )
            }
            provider = OpenRouterPayload.extractProvider(data) ?: provider
            reasoningTokens = OpenRouterPayload.extractReasoningTokens(data) ?: reasoningTokens
            if (OpenRouterPayload.isStreamError(data)) {
                return StreamAssembly(
                    content = content.toString(),
                    provider = provider,
                    errorData = data,
                    reasoningTokens = reasoningTokens,
                )
            }
            if (OpenRouterPayload.extractReasoningDelta(data) != null) {
                sawReasoning = true
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
        // ENC-95 telemetry: an in-stream rejection names its code/type
        // too — a 400-class one also arms the degraded retry.
        val errorInfo = OpenRouterPayload.extractErrorInfo(data)
        Log.d(
            TAG,
            "style attempt model=$model outcome=$REASON_STREAM_ERROR " +
                "errorCode=${errorInfo?.code?.toString() ?: "-"} errorType=${errorInfo?.type ?: "-"}",
        )
        return AttemptResult.Retry(
            reason = REASON_STREAM_ERROR,
            provider = provider,
            clientError = errorInfo?.code != null && errorInfo.code in CLIENT_ERROR_RANGE,
        )
    }

    private fun httpFailureResult(result: HttpResult, model: String): AttemptResult {
        if (OpenRouterPayload.isModelUnavailable(status = result.status, body = result.body)) {
            Log.d(TAG, "style attempt model=$model outcome=model-unavailable")
            return AttemptResult.Retry(reason = REASON_MODEL_UNAVAILABLE)
        }
        // ENC-95 telemetry: status plus the provider's error code/type
        // (metadata only, never body text) — the next 400-class death
        // must be diagnosable from logcat alone.
        val errorInfo = OpenRouterPayload.extractErrorInfo(result.body)
        Log.d(
            TAG,
            "style attempt model=$model outcome=${REASON_HTTP_PREFIX + result.status} " +
                "errorCode=${errorInfo?.code?.toString() ?: "-"} errorType=${errorInfo?.type ?: "-"}",
        )
        return AttemptResult.Retry(
            reason = REASON_HTTP_PREFIX + result.status,
            provider = OpenRouterPayload.extractProvider(result.body),
            clientError = result.status in CLIENT_ERROR_RANGE,
        )
    }

    /**
     * Cleans one attempt's assembled content and puts it through the
     * ENC-82 guard: only a guarded pass may become [AttemptResult.Done]
     * with a styled outcome; refusal and fidelity verdicts retry.
     */
    private fun finish(
        content: String,
        provider: String?,
        reasoningTokens: Int?,
        startedMs: Long,
        model: String,
        sourceText: String,
    ): AttemptResult {
        // ENC-94 telemetry: the reasoning spend rides every verdict's
        // log line ("-" when the response reported no usage).
        val reasoningLabel = reasoningTokens?.toString() ?: "-"
        val cleaned = cleaner.clean(content)
        if (cleaned.isBlank()) {
            Log.d(TAG, "style attempt model=$model outcome=$REASON_EMPTY_RESPONSE reasoningTokens=$reasoningLabel")
            return AttemptResult.Retry(reason = REASON_EMPTY_RESPONSE, provider = provider)
        }
        return when (StyleOutputGuard.classify(source = sourceText, styled = cleaned)) {
            StyleOutputGuard.Verdict.PASS -> {
                val outcome = LlmStyleOutcome.Styled(
                    text = cleaned,
                    llmMs = clock() - startedMs,
                    model = model,
                    provider = provider,
                )
                Log.d(
                    TAG,
                    "style attempt model=$model provider=${outcome.providerLabel()} " +
                        "outcome=${outcome.logLabel()} reasoningTokens=$reasoningLabel",
                )
                AttemptResult.Done(outcome)
            }
            StyleOutputGuard.Verdict.REFUSAL -> {
                Log.d(TAG, "style attempt model=$model outcome=$REASON_REFUSAL reasoningTokens=$reasoningLabel")
                AttemptResult.Retry(reason = REASON_REFUSAL, provider = provider)
            }
            StyleOutputGuard.Verdict.FIDELITY_LOSS -> {
                Log.d(TAG, "style attempt model=$model outcome=$REASON_FIDELITY reasoningTokens=$reasoningLabel")
                AttemptResult.Retry(reason = REASON_FIDELITY, provider = provider)
            }
        }
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

        /**
         * A failed attempt. [clientError] marks a 400-class rejection
         * — the request itself was refused — which arms the same
         * model's one degraded retry before the chain advances
         * (ENC-95). Server failures, liveness deaths, and guard
         * verdicts are not request rejections and never set it.
         */
        data class Retry(
            val reason: String,
            val provider: String? = null,
            val clientError: Boolean = false,
        ) : AttemptResult
    }

    /** The assembled stream: full content, responding provider, and how it ended. */
    private data class StreamAssembly(
        val content: String,
        val provider: String?,
        val doneReceived: Boolean = false,
        val errorData: String? = null,
        /** Reasoning tokens the response's usage reported, when it reported any (ENC-94). */
        val reasoningTokens: Int? = null,
    )

    /** A liveness budget expired mid-stream; [reason] is the telemetry code. */
    private class LivenessException(val reason: String) : Exception(reason)

    internal companion object {
        const val TAG: String = "OpenRouterStyle"

        /**
         * The chain, in order (ENC-84/95). The reasoning block rides
         * the primary only: Grok's default effort is the blowout risk
         * ENC-94 leashed, while Haiku's proven shape is the pre-ENC-94
         * payload with no block — and a block on the Anthropic route
         * is one translation away from an invalid request (ENC-95).
         */
        private val PRIMARY_LINK: ChainLink = ChainLink(
            model = OpenRouterPayload.PRIMARY_MODEL,
            providerOrder = OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
            providerIgnore = emptyList(),
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )
        private val FALLBACK_LINK: ChainLink = ChainLink(
            model = OpenRouterPayload.FALLBACK_MODEL,
            providerOrder = OpenRouterPayload.FALLBACK_PROVIDER_ORDER,
            providerIgnore = OpenRouterPayload.FALLBACK_PROVIDER_IGNORE,
            reasoningEffort = null,
        )

        /** HTTP statuses that mean "your request was refused" — the degraded retry's trigger (ENC-95). */
        val CLIENT_ERROR_RANGE: IntRange = 400..499

        /**
         * Time a stream may stay silent before its first line of any
         * kind (ENC-81, re-based ENC-91). The 2s Haiku-era value fired
         * during Grok's reasoning phase — connection setup plus a
         * reasoning model's time-to-first-token routinely exceeds it
         * on a phone — so the healthy attempt was declared dead while
         * the server completed it. 6s still fails a truly dead route
         * fast against the chain cap.
         */
        const val FIRST_TOKEN_TIMEOUT_MS: Long = 6_000L

        /**
         * Time a stream may pause mid-reasoning, before any content,
         * once reasoning deltas have shown the model is thinking
         * (ENC-91). Reasoning arrives in bursts with multi-second
         * pauses; a pause is thought, not a stall.
         */
        const val REASONING_GAP_TIMEOUT_MS: Long = 6_000L

        /** Time a stream may go silent between tokens before it is declared dead (ENC-81). */
        const val TOKEN_GAP_TIMEOUT_MS: Long = 2_000L

        /**
         * Absolute backstop for the whole chain, re-tuned in ENC-94
         * to the reasoning-constrained profile (the 20s ENC-81 value
         * was sized for unconstrained generation — and still lost to
         * a 41s Grok generation). The constrained healthy path: ~0.5s
         * routing/connect, reasoning held at effort low (in practice
         * near zero tokens on a surface transform), then content at
         * the ~150 tok/s rate observed in the ENC-83/91 evidence —
         * a long ~700-token restyle lands in ~5–6s end to end. The
         * slowest legitimate chain is two full liveness deaths
         * (2 × [FIRST_TOKEN_TIMEOUT_MS] = 12s), so 14s leaves headroom
         * over that while a pathological stream still dies young.
         * The ENC-95 degraded retries fire on instant 400-class
         * deaths, so they add round trips, not liveness waits; only
         * a pathological stream reaches the cap.
         */
        const val ABSOLUTE_CAP_MS: Long = 14_000L

        const val REASON_NO_KEY: String = "no-key"
        const val REASON_TIMEOUT: String = "timeout"
        const val REASON_TRANSPORT_ERROR: String = "transport-error"
        const val REASON_MODEL_UNAVAILABLE: String = "model-unavailable"
        const val REASON_FIRST_TOKEN_TIMEOUT: String = "first-token-timeout"
        const val REASON_STREAM_STALL: String = "stream-stall"
        const val REASON_STREAM_ERROR: String = "stream-error"

        /** A 400-class rejection is being retried once on the same model, degraded (ENC-95). */
        const val REASON_DEGRADED_RETRY: String = "degraded-retry"
        const val REASON_HTTP_PREFIX: String = "http-"
        const val REASON_EMPTY_RESPONSE: String = "empty-response"

        /** The model's output was refusal-shaped (ENC-82) — a failure, never a result. */
        const val REASON_REFUSAL: String = "refusal"

        /** The model's output silently dropped protected content (ENC-82). */
        const val REASON_FIDELITY: String = "fidelity"

        const val DATA_PREFIX: String = "data:"
        const val DONE_MARKER: String = "[DONE]"
    }
}
