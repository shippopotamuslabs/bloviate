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
import kotlinx.coroutines.Deferred
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
 * Model chain (ENC-100; the saga is in [OpenRouterPayload]'s
 * model docs): DeepSeek V4.1 Flash ([OpenRouterPayload.PRIMARY_MODEL],
 * pinned to its US fast endpoints, latency-sorted) → Gemini 3.1
 * Flash-Lite ([OpenRouterPayload.FALLBACK_MODEL], vertex-only) →
 * the local text. ENC-99 shipped Gemini as the ONLY model; its
 * device retest showed both halves of why the chain is two models
 * again — the Gemini completion euphemized the dictation and the
 * guard rightly failed it, and with no second model the local
 * text was the only landing (its other lesson, an AI Studio BYOK
 * 400 polluting the route, is answered by the fallback's
 * vertex-only pin). A failed primary attempt now advances the
 * chain instead of ending it; only when the fallback also fails
 * does the outcome become [LlmStyleOutcome.FellBack] — carrying
 * the LAST attempt's reason — and the caller keeps the local
 * text, which never blocks insertion. Each attempt logs its
 * `model=` / `provider=` so dogfood telemetry shows which
 * endpoint actually answered.
 *
 * Output guard (ENC-82, slimmed by ENC-100): every attempt's
 * cleaned output passes [StyleOutputGuard] before it may become a
 * result. The guard's contract is two promises — never insert a
 * refusal, never insert unrelated text. A refusal-shaped response
 * is a failure (reason `refusal`), never inserted; a response that
 * replaces the dictation wholesale (near-zero content-word
 * overlap — invented or off-topic text) fails as `fidelity`,
 * which since ENC-100 means wholesale replacement ONLY: the
 * hard-token tier and the 0.35 coverage floor were removed on
 * Sean's call after they false-positived faithful persona
 * restyles (see [StyleOutputGuard]). Both verdicts are failure
 * classes like any other — HTTP errors, transport failures,
 * liveness deaths, stream errors, empty responses — and the
 * reported fallback reason is the LAST attempt's.
 *
 * Streaming (ENC-81): the response is consumed as an SSE stream, but
 * insertion semantics do not change — chunks are assembled in memory,
 * cleaned, and only the complete result is ever returned; any failure
 * yields the complete local text. A fragment can never reach the
 * field. Timeouts judge LIVENESS, not total time: a first-token budget,
 * a reasoning-phase budget, and an inter-token gap budget (any line —
 * token, reasoning, or keep-alive — counts as a sign of life), plus a
 * generous per-attempt absolute cap as backstop (ENC-97). A healthy stream may therefore
 * run far past the old 4s total cap on long dictations. ENC-91
 * re-based the pre-content budgets for a reasoning primary: Grok
 * thinks before it speaks, and a budget that cannot tell thinking
 * from death kills healthy calls — the server completes them, the
 * client has already fallen back.
 *
 * Reasoning control (ENC-100; ENC-99 and ENC-94/95 before it):
 * the unified `reasoning` block rides EVERY attempt of BOTH links
 * at effort "none" (see [OpenRouterPayload.REASONING_EFFORT]) —
 * both models reason by default (DeepSeek at high effort, Gemini
 * at minimal), so the off setting must be explicit or every
 * restyle pays a thinking tax. The usage chunk's reasoning-token
 * count is logged with each attempt's outcome (ENC-94), so a
 * route that ignores the setting is visible in telemetry, not
 * just in OpenRouter's dashboard: on the happy path it reads 0.
 * The ENC-91 liveness machinery that tolerates a reasoning phase
 * stays — a misbehaving route can still think out loud, and
 * thinking is not death.
 *
 * Degraded retry (ENC-95, reshaped by ENC-99/100): ENC-94's first
 * shape shipped `reasoning: {effort, max_tokens}` on every
 * attempt — a combination OpenRouter's unified parameter does
 * not accept (see the payload docs) — and the attempts began
 * dying instantly with 400-class rejections. The client is
 * structurally hardened against the whole class: when an attempt
 * fails with a 400-class rejection (an HTTP 4xx, or an in-stream
 * error chunk carrying a 4xx code), the same model is retried
 * ONCE with a degraded payload before the chain advances. In the
 * current shape, degraded means `stream_options` (the one
 * purely-optional block) is dropped, while the reasoning-off
 * block — the control that defines the link — is kept: on both
 * models it is a plain supported parameter, not the ENC-95
 * poison shape, and a retry that dropped it would silently
 * re-enable the model's default reasoning. Provider parameter
 * pickiness can slow the pass by one round trip; it can no
 * longer zero it. Failed attempts log their HTTP status and the
 * provider's error code/type, so the next rejection is
 * diagnosable from logcat alone.
 *
 * Per-attempt budgets (ENC-97): until then the whole chain shared
 * one absolute cap ([ABSOLUTE_CAP_MS] as was, 14s) — designed in
 * ENC-94 to bound ONE reasoning attempt's worst case. Sean's
 * ENC-96 retest exposed the structural flaw: the primary
 * hallucinated (ENC-98), the guard failed it, and the fallback
 * model then produced the faithful restyle server-side — but
 * the chain deadline, partly spent by the primary, killed the
 * slow-but-healthy fallback stream mid-flight and the local text
 * landed. Budgets are now per attempt: each attempt (full and
 * degraded alike, on both links) runs under its own liveness
 * rules plus its own [ATTEMPT_ABSOLUTE_CAP_MS]; a per-attempt
 * timeout advances the chain carrying that attempt's `timeout`
 * reason instead of being masked by a chain deadline, and
 * telemetry names the attempt (model + payload shape) and the
 * reason on every death. The only chain-wide bound left is
 * [CHAIN_BACKSTOP_MS] — the attempt caps plus slack — which
 * exists purely as pathology insurance and can only fire if an
 * attempt's own cap somehow fails to.
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
            // Pathology insurance only (ENC-97): the sum of the four
            // per-attempt caps (two links × full/degraded) plus
            // slack. The attempts' own caps and liveness budgets do
            // the real judging (ENC-81/97); this bound can only
            // fire if one of them somehow fails to.
            withTimeout(CHAIN_BACKSTOP_MS) {
                when (
                    val first = attemptPrimary(key = key, prompt = prompt, sourceText = text, startedMs = startedMs)
                ) {
                    is AttemptResult.Done -> first.outcome
                    is AttemptResult.Retry -> fallbackOutcome(
                        key = key,
                        prompt = prompt,
                        sourceText = text,
                        startedMs = startedMs,
                    )
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

    /**
     * The chain's second step (ENC-100): the fallback link. Its
     * success is the outcome; its failure is the chain's report —
     * the [LlmStyleOutcome.FellBack] carries the fallback attempt's
     * reason, model, and provider, and the caller keeps the local
     * text.
     */
    private suspend fun fallbackOutcome(
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

    /** One chain step: a model, its provider pin, and its reasoning profile. */
    private data class ChainLink(
        val model: String,
        /** Endpoint slugs to try in this exact order, or null for no `order` pin. */
        val providerOrder: List<String>? = null,
        /** Allow-listed endpoint slugs for this link, or empty for no `only` pin. */
        val providerOnly: List<String>,
        /** Live-metric ordering for the link's endpoint pool, or null for none. */
        val providerSort: String?,
        /**
         * The unified `reasoning` effort for this link, or null for
         * no block. Both ENC-100 links set "none" (both models
         * reason by default); the field is how a future link states
         * its own profile (ENC-94/95 machinery).
         */
        val reasoningEffort: String?,
    )

    /**
     * One chain step, streamed. Success is [AttemptResult.Done];
     * EVERY failure class is [AttemptResult.Retry] (ENC-82) — the
     * chain advances to the next link, or ends when the failing
     * link was the last.
     *
     * ENC-95 hardening: a 400-class rejection (the request itself was
     * refused — see [AttemptResult.Retry.clientError]) earns ONE
     * degraded retry on the SAME model before the chain advances:
     * the optional block the provider could object to does not ride
     * the degraded payload.
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
     * A single call against the model. [degraded] strips the one
     * purely-optional block (ENC-95, reshaped by ENC-99): no
     * `stream_options`. The link's reasoning setting rides both
     * shapes — it defines the link, and dropping it would silently
     * re-enable the model's default reasoning on the retry.
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
            providerOnly = link.providerOnly,
            providerSort = link.providerSort,
            stream = true,
            reasoningEffort = link.reasoningEffort,
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
        // Telemetry names the attempt on every death (ENC-97): model
        // plus which payload shape of it is running.
        val attemptLabel = "model=$model shape=${if (degraded) "degraded" else "full"}"
        // The attempt's own absolute cap (ENC-97): the liveness budgets
        // inside judge silence; this bounds an attempt that never goes
        // silent yet never finishes (an endless keep-alive stream).
        // Expiry ends the chain with this attempt's own reason — it
        // never masks the attempt the way the old shared cap did.
        val result = withTimeoutOrNull(ATTEMPT_ABSOLUTE_CAP_MS) {
            runStream(
                call = call,
                lines = lines,
                attemptLabel = attemptLabel,
                startedMs = startedMs,
                model = model,
                sourceText = sourceText,
            )
        }
        if (result != null) return result
        call.cancel()
        Log.d(TAG, "style attempt $attemptLabel outcome=$REASON_TIMEOUT capMs=$ATTEMPT_ABSOLUTE_CAP_MS")
        return AttemptResult.Retry(reason = REASON_TIMEOUT)
    }

    /**
     * Consumes one attempt's stream to a verdict: liveness assembly,
     * HTTP status, then the guard via [finish]. Suspends until the
     * stream concludes or a budget (liveness, or the caller's
     * per-attempt cap) cancels it.
     */
    private suspend fun runStream(
        call: Deferred<HttpResult>,
        lines: Channel<String>,
        attemptLabel: String,
        startedMs: Long,
        model: String,
        sourceText: String,
    ): AttemptResult {
        val assembly = try {
            consumeLines(lines)
        } catch (liveness: LivenessException) {
            call.cancel()
            Log.d(TAG, "style attempt $attemptLabel outcome=${liveness.reason}")
            return AttemptResult.Retry(reason = liveness.reason)
        }
        if (assembly.errorData != null) {
            call.cancel()
            return streamErrorResult(data = assembly.errorData, attemptLabel = attemptLabel)
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
            // failure like any other — a retry verdict.
            Log.d(TAG, "style attempt $attemptLabel outcome=$REASON_TRANSPORT_ERROR detail=${exception.javaClass.simpleName}")
            return AttemptResult.Retry(reason = REASON_TRANSPORT_ERROR)
        }
        if (result.status != HttpURLConnection.HTTP_OK) return httpFailureResult(result = result, attemptLabel = attemptLabel)
        return finish(
            content = assembly.content,
            provider = assembly.provider,
            reasoningTokens = assembly.reasoningTokens,
            startedMs = startedMs,
            attemptLabel = attemptLabel,
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

    private fun streamErrorResult(data: String, attemptLabel: String): AttemptResult {
        val provider = OpenRouterPayload.extractProvider(data)
        if (OpenRouterPayload.isModelUnavailable(status = HttpURLConnection.HTTP_OK, body = data)) {
            Log.d(TAG, "style attempt $attemptLabel outcome=model-unavailable (stream error)")
            return AttemptResult.Retry(reason = REASON_MODEL_UNAVAILABLE, provider = provider)
        }
        // ENC-95 telemetry: an in-stream rejection names its code/type
        // too — a 400-class one also arms the degraded retry.
        val errorInfo = OpenRouterPayload.extractErrorInfo(data)
        Log.d(
            TAG,
            "style attempt $attemptLabel outcome=$REASON_STREAM_ERROR " +
                "errorCode=${errorInfo?.code?.toString() ?: "-"} errorType=${errorInfo?.type ?: "-"}",
        )
        return AttemptResult.Retry(
            reason = REASON_STREAM_ERROR,
            provider = provider,
            clientError = errorInfo?.code != null && errorInfo.code in CLIENT_ERROR_RANGE,
        )
    }

    private fun httpFailureResult(result: HttpResult, attemptLabel: String): AttemptResult {
        if (OpenRouterPayload.isModelUnavailable(status = result.status, body = result.body)) {
            Log.d(TAG, "style attempt $attemptLabel outcome=model-unavailable")
            return AttemptResult.Retry(reason = REASON_MODEL_UNAVAILABLE)
        }
        // ENC-95 telemetry: status plus the provider's error code/type
        // (metadata only, never body text) — the next 400-class death
        // must be diagnosable from logcat alone.
        val errorInfo = OpenRouterPayload.extractErrorInfo(result.body)
        Log.d(
            TAG,
            "style attempt $attemptLabel outcome=${REASON_HTTP_PREFIX + result.status} " +
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
        attemptLabel: String,
        model: String,
        sourceText: String,
    ): AttemptResult {
        // ENC-94 telemetry: the reasoning spend rides every verdict's
        // log line ("-" when the response reported no usage).
        val reasoningLabel = reasoningTokens?.toString() ?: "-"
        val cleaned = cleaner.clean(content)
        if (cleaned.isBlank()) {
            Log.d(TAG, "style attempt $attemptLabel outcome=$REASON_EMPTY_RESPONSE reasoningTokens=$reasoningLabel")
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
                    "style attempt $attemptLabel provider=${outcome.providerLabel()} " +
                        "outcome=${outcome.logLabel()} reasoningTokens=$reasoningLabel",
                )
                AttemptResult.Done(outcome)
            }
            StyleOutputGuard.Verdict.REFUSAL -> {
                Log.d(TAG, "style attempt $attemptLabel outcome=$REASON_REFUSAL reasoningTokens=$reasoningLabel")
                AttemptResult.Retry(reason = REASON_REFUSAL, provider = provider)
            }
            StyleOutputGuard.Verdict.FIDELITY_LOSS -> {
                Log.d(TAG, "style attempt $attemptLabel outcome=$REASON_FIDELITY reasoningTokens=$reasoningLabel")
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
         * model's one degraded retry before the chain gives up
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
         * The chain's head (ENC-100, routing reshaped by ENC-101):
         * DeepSeek V4.1 Flash, routed by a DETERMINISTIC provider
         * order — DeepInfra first, Together last — over its US
         * fast endpoints, with reasoning explicitly off — its
         * models-API metadata has reasoning ON by default at high
         * effort, so the effort block is a load-bearing part of
         * this link, not an optional extra (see
         * [OpenRouterPayload]).
         */
        private val PRIMARY_LINK: ChainLink = ChainLink(
            model = OpenRouterPayload.PRIMARY_MODEL,
            providerOrder = OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
            providerOnly = emptyList(),
            providerSort = null,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )

        /**
         * The chain's second link (ENC-100): Gemini 3.1 Flash-Lite,
         * demoted from the ENC-99 head, with its ENC-99 profile
         * intact — reasoning explicitly off on every attempt — and
         * its routing narrowed to Vertex-only: the AI Studio
         * endpoint, whose stored BYOK key on Sean's account is
         * invalid, leaves the pool (see [OpenRouterPayload]).
         */
        private val FALLBACK_LINK: ChainLink = ChainLink(
            model = OpenRouterPayload.FALLBACK_MODEL,
            providerOnly = OpenRouterPayload.FALLBACK_PROVIDER_ONLY,
            providerSort = OpenRouterPayload.FALLBACK_PROVIDER_SORT,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
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
         * Absolute cap for ONE attempt (ENC-97; the 14s ENC-94 value
         * was this number's ancestor, but it bounded the WHOLE chain,
         * so a slow-but-healthy fallback inherited only the time the
         * primary hadn't spent — Sean's ENC-96 retest lost a faithful
         * 630-token Haiku restyle to exactly that starvation). The
         * liveness budgets judge silence far earlier on every real
         * death; this cap exists for the one shape liveness cannot
         * see — a stream that keeps producing signs of life yet never
         * finishes (endless keep-alives, a trickle that never ends).
         * 20s clears the slowest legitimate attempt in the device
         * evidence (a long restyle streaming at a reasoning-era
         * route's pace) with room, while a pathological attempt still
         * dies young enough for its failure to surface usefully.
         */
        const val ATTEMPT_ABSOLUTE_CAP_MS: Long = 20_000L

        /**
         * The chain's only outer bound (ENC-97; attempt count
         * updated by ENC-100): the four possible attempts (two
         * links × full/degraded) at [ATTEMPT_ABSOLUTE_CAP_MS]
         * each, plus generous slack for the non-network work
         * between them (guard, cleaning, channel hand-offs). Pure
         * pathology insurance — every attempt's own cap fires
         * first by construction, so this bound can only catch a
         * failure of the caps themselves.
         */
        const val CHAIN_BACKSTOP_MS: Long = 120_000L

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

        /** The model's output replaced the dictation wholesale (ENC-82; slimmed meaning since ENC-100). */
        const val REASON_FIDELITY: String = "fidelity"

        const val DATA_PREFIX: String = "data:"
        const val DONE_MARKER: String = "[DONE]"
    }
}
