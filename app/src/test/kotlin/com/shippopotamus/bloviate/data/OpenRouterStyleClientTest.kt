package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.evaluator.LlmResponseCleaner
import com.shippopotamus.bloviate.domain.evaluator.StylePromptBuilder
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeStyleHttpTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class OpenRouterStyleClientTest {

    private val professional = StyleCatalog.byId("professional")
    private val seneca = StyleCatalog.byId("seneca")
    private val fakeTransport = FakeStyleHttpTransport()
    private val fakeKeys = FakeApiKeyPort(initialKey = "test-key")
    private var nowMs = 1_000L

    /** The producer side gets a test dispatcher so stream timing is virtual and deterministic. */
    private fun newClient(producerDispatcher: CoroutineDispatcher) = OpenRouterStyleClient(
        transport = fakeTransport,
        prompts = StylePromptBuilder(),
        cleaner = LlmResponseCleaner(),
        apiKeys = fakeKeys,
        clock = { nowMs },
        producerDispatcher = producerDispatcher,
    )

    @Test
    fun `success assembles the chunked answer and reports the elapsed time`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            // The network takes 800ms on the injected clock.
            nowMs = 1_800L
            emitStream(emit, parts = listOf("The meeting is ", "moved to Friday afternoon."))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(800L, styled.llmMs)
    }

    @Test
    fun `a preamble in the assembled answer is stripped before returning`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf("Here is the rewrite:\n$STYLED_TEXT"))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `no key falls back without any http call`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeKeys.setKey(null)

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-key"), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `blank text passes through without any http call`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))

        val outcome = client.style(style = professional, text = "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a server error on both models falls back with its status`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-82: a provider failure retries once on the other model;
        // the final reason is the last attempt's.
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 500, body = "boom") }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "http-500", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `the responding provider is surfaced on success`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-80: streamed chunks carry the serving upstream; telemetry
        // surfaces it so instability is visible from logcat.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(STYLED_TEXT), provider = "xAI")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("xAI", styled.provider)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
    }

    @Test
    fun `the responding provider is surfaced on an http failure`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ ->
            HttpResult(status = 500, body = "{\"provider\":\"Claude Platform on AWS\",\"error\":{}}")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val fellBack = outcome as LlmStyleOutcome.FellBack
        assertEquals("http-500", fellBack.reason)
        assertEquals("Claude Platform on AWS", fellBack.provider)
    }

    @Test
    fun `each attempt pins its provider routing and requests the stream`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-80 pins + ENC-81 streaming, asserted on the recorded bodies.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 404, body = "model not found")
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        client.style(style = professional, text = SOURCE_TEXT)

        assertTrue(fakeTransport.requests[0].contains("\"stream\":true"))
        assertTrue(fakeTransport.requests[0].contains("\"provider\":{\"order\":[\"xai\"],\"allow_fallbacks\":true}"))
        assertTrue(
            fakeTransport.requests[1].contains(
                "\"provider\":{\"order\":[\"anthropic\"],\"allow_fallbacks\":true,\"ignore\":[\"claude-on-aws\"]}",
            ),
        )
    }

    @Test
    fun `the chain attempts grok latest first and haiku second`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-84: literal chain order — Grok alias, then Haiku, then local.
        fakeTransport.handler = { callIndex, requestJson, emit ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains("~x-ai/grok-latest"))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains("anthropic/claude-haiku-5.5"))
                emitStream(emit, parts = listOf(HAIKU_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(HAIKU_STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[0].contains("~x-ai/grok-latest"))
        assertTrue(fakeTransport.requests[1].contains("anthropic/claude-haiku-5.5"))
    }

    @Test
    fun `a missing primary model retries the fallback model once`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, requestJson, emit ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains(OpenRouterPayload.PRIMARY_MODEL))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains(OpenRouterPayload.FALLBACK_MODEL))
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(FALLBACK_STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `two missing models fall back`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 404, body = "model not found") }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "model-unavailable", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a long stream with steady tokens succeeds past the old total cap`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-81's money test (and ENC-83's regression in streamed form):
        // tokens keep arriving inside the gap budget for ~6s of virtual
        // time — past the old 4s total cap AND the old 2.5s socket
        // budget — and the call must succeed. Liveness, not total time.
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("The "))
            listOf("meeting ", "is ", "moved ").forEach { part ->
                delay(TRICKLE_GAP_MS)
                emit(chunk(part))
            }
            delay(TRICKLE_GAP_MS)
            emit(chunk("to Friday afternoon."))
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `a stream that never starts falls back on the first token budget`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Both models stay silent past the first-token budget: the chain
        // gives up with the liveness reason, not after the absolute cap.
        fakeTransport.handler = { _, _, _ ->
            delay(NEVER_STARTS_MS)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "first-token-timeout", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a mid-stream stall retries the fallback model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(chunk("Partial "))
                delay(NEVER_STARTS_MS)
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        // The fragment from the stalled stream is never used (atomicity).
        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a mid-stream stall on both models falls back as a stall`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("Partial "))
            delay(NEVER_STARTS_MS)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "stream-stall", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
    }

    @Test
    fun `an error payload mid-stream is a failure, never content`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("Partial "))
            emit("data: {\"error\":{\"message\":\"provider exploded\",\"code\":500}}")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "stream-error", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a model-unavailable stream error retries the fallback model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit("data: {\"error\":{\"message\":\"Model not found\",\"code\":404}}")
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(FALLBACK_STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `a transport exception on both models falls back`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-82: a dead transport is a provider failure — one retry on
        // the other model before the local text wins.
        fakeTransport.handler = { _, _, _ -> throw IOException("network down") }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "transport-error", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `reasoning deltas streamed ahead of content keep the grok attempt alive`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, streamed shape: Grok thinks out loud
        // first — reasoning deltas in both wire shapes (xAI's
        // reasoning_content, OpenRouter's reasoning) — then the answer.
        // The fallback is scripted to 499 (Sean's repro), so any
        // wrongful retry is visible as a fallback outcome.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(reasoningChunkXai("Restyle the dictation, keeping every fact."))
                delay(TRICKLE_GAP_MS)
                emit(reasoningChunkOpenRouter("Plain casing, one paragraph."))
                delay(TRICKLE_GAP_MS)
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "SpaceXAI")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a silent reasoning phase ahead of the first chunk does not kill the grok attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, silent shape: the reasoning phase
        // delivers NO line for longer than the old 2s first-token
        // budget, then the whole answer streams. Sean's repro exactly:
        // Grok completed server-side, the client fell back to a
        // Haiku 499, and the local text landed.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                delay(SLOW_REASONING_MS)
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "SpaceXAI")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `slow gaps between reasoning deltas do not kill the grok attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, paused shape: reasoning starts, pauses
        // past the old 2s budget mid-thought, resumes, then answers.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(reasoningChunkXai("Restyle the dictation."))
                delay(SLOW_REASONING_MS)
                emit(reasoningChunkOpenRouter("Still thinking."))
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "SpaceXAI")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a cached burst delivering the whole stream at once is used`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 2, burst shape: a cached replay delivers
        // every chunk back to back with no pacing at all.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(
                    emit,
                    parts = listOf("The meeting ", "is ", "moved to ", "Friday afternoon."),
                    provider = "SpaceXAI",
                )
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a non-streamed json completion body is used instead of falling back`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 2, non-streamed shape: a cached/gateway
        // answer arrives as ONE plain JSON completion body (no SSE
        // framing) on a 200. Dropping it as "empty" burns the fallback
        // on a call that succeeded.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(nonStreamedCompletion(STYLED_TEXT))
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals("SpaceXAI", styled.provider)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `the primary carries effort only reasoning and the fallback carries none`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-95's split profile (was ENC-94's both-carry-it shape):
        // effort-only on the primary — never combined with a token
        // cap, the combination providers rejected — and no block at
        // all on the Anthropic fallback, whose translated budget
        // arithmetic made the combined request invalid by
        // construction and which has no blowout to leash.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 404, body = "model not found")
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        client.style(style = professional, text = SOURCE_TEXT)

        assertTrue(fakeTransport.requests[0].contains("\"reasoning\":{\"effort\":\"low\"}"))
        assertTrue(!fakeTransport.requests[1].contains("\"reasoning\""))
    }

    @Test
    fun `a 400 on the primary retries degraded on the same model and succeeds`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-95's device symptom, answered: the full payload is
        // refused with a 400-class rejection, and the SAME model
        // answers the degraded retry — the fallback is never called
        // and the styled text lands.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 400, body = BUDGET_400_BODY)
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "xAI")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
        // The retry is the degraded shape: streamed, but bare.
        val degraded = fakeTransport.requests[1]
        assertTrue(degraded.contains("\"stream\":true"))
        assertTrue(!degraded.contains("\"reasoning\""))
        assertTrue(!degraded.contains("\"stream_options\""))
        assertTrue(degraded.contains(OpenRouterPayload.PRIMARY_MODEL))
    }

    @Test
    fun `a 400 on both payload shapes advances the chain to the fallback`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The primary refuses BOTH shapes; the fallback's plain call
        // answers. Three requests total: full, degraded, fallback.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex < 2) {
                HttpResult(status = 400, body = BUDGET_400_BODY)
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(FALLBACK_STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(3, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[2].contains(OpenRouterPayload.FALLBACK_MODEL))
    }

    @Test
    fun `a 400 on every shape of both models falls back after one degraded retry each`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The structural bound: full + degraded per model, then the
        // chain reports the last attempt's reason — never a loop.
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 400, body = BUDGET_400_BODY) }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "http-400", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(4, fakeTransport.requests.size)
        assertTrue(!fakeTransport.requests[1].contains("\"reasoning\""))
        assertTrue(!fakeTransport.requests[3].contains("\"stream_options\""))
    }

    @Test
    fun `an in-stream error carrying a 400 code retries degraded on the same model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-95 suspect (3), hardened: the stream itself opens with
        // a 400-class error chunk. Same recourse as an HTTP 400 — one
        // degraded retry before the chain moves on.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit("data: {\"id\":\"gen-enc95\",\"provider\":\"xAI\",\"error\":{\"code\":400,\"message\":\"invalid request\"}}")
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "xAI")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(!fakeTransport.requests[1].contains("\"reasoning\""))
    }

    @Test
    fun `a usage tail chunk in the full openrouter shape completes the attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-95 suspect (2), exonerated in fixture form: with
        // include_usage the stream's tail is a finish chunk, then a
        // usage chunk with EMPTY choices. The assembled answer stands.
        fakeTransport.handler = { _, _, emit ->
            emit("data: {\"id\":\"gen-enc95\",\"provider\":\"SpaceXAI\",\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}")
            emit("data: {\"id\":\"gen-enc95\",\"choices\":[{\"delta\":{\"content\":\"The meeting is moved \"}}]}")
            emit("data: {\"id\":\"gen-enc95\",\"choices\":[{\"delta\":{\"content\":\"to Friday afternoon.\"}}]}")
            emit(
                "data: {\"id\":\"gen-enc95\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]," +
                    "\"usage\":{\"prompt_tokens\":900,\"completion_tokens\":12,\"total_tokens\":912}}",
            )
            emit(usageChunk(reasoningTokens = 4))
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a long reasoning phase stream completes and inserts the styled text`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-94's repro, compressed: a reasoning-heavy Grok stream —
        // two dozen reasoning deltas across ~6s of virtual time, then
        // the answer trickling in, then a usage chunk reporting the
        // reasoning spend. The ENC-91 liveness rules carry it and the
        // ENC-94 chain cap (14s) leaves it room: the styled text must
        // be what lands. The fallback is scripted to 499 so any
        // wrongful retry shows.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                repeat(REASONING_BURST_CHUNKS) { beat ->
                    emit(reasoningChunkXai("Deliberating, beat $beat."))
                    delay(REASONING_BEAT_MS)
                }
                emit(chunk("The meeting "))
                delay(TRICKLE_GAP_MS)
                emit(chunk("is moved "))
                delay(TRICKLE_GAP_MS)
                emit(chunk("to Friday afternoon."))
                emit(usageChunk(reasoningTokens = 183))
                emit(DONE_LINE)
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `the seneca tirade restyles and inserts with its profanity intact`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-94's acceptance scenario in fixture form: Sean's tirade
        // test — long, first-person, profanity-bearing — restyled by
        // the primary in Seneca's voice. The hard tier (the profanity,
        // the names) survives, the speaker stays the speaker (ENC-92),
        // and the styled text is the outcome of a single call.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf(TIRADE_STYLED), provider = "SpaceXAI")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = seneca, text = TIRADE_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(TIRADE_STYLED, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a stream with no content falls back as an empty response`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // A 200 stream that delivers no tokens at all (the streamed form
        // of the old unparseable-body case) is an empty response.
        fakeTransport.handler = { _, _, emit ->
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "empty-response", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a refusal from the primary is never inserted and the fallback answers`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-82's catastrophic case: refusal-shaped output must be a
        // failure routed to the next model, not a result.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf("I'm sorry, but I can't help with that."))
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a refusal from both models falls back as a refusal`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf("I'm sorry, but I can't help with that."))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "refusal", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
    }

    @Test
    fun `a fidelity loss on the primary falls back to the second model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-82's Idiocracy datapoint, end to end: the primary's
        // output silently drops the slur; the guard fails it and the
        // fallback's faithful restyle is what gets inserted.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf(IDIOCRACY_DROPPED))
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(IDIOCRACY_PRESERVED))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = IDIOCRACY_SOURCE)

        assertEquals(IDIOCRACY_PRESERVED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a fidelity loss on both models falls back as fidelity`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(IDIOCRACY_DROPPED))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = IDIOCRACY_SOURCE)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "fidelity", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
    }

    @Test
    fun `the gerbil limerick passes the guard end to end`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The canonical ENC-82 datapoint: profanity intact, one call.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(GERBIL_STYLED))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = GERBIL_SOURCE)

        assertEquals(GERBIL_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    /** One SSE chunk line carrying only an xAI-shaped reasoning delta (ENC-91). */
    private fun reasoningChunkXai(reasoning: String): String =
        "data: {\"provider\":\"SpaceXAI\",\"choices\":[{\"delta\":{\"role\":\"assistant\"," +
            "\"reasoning_content\":" + OpenRouterPayload.escape(reasoning) + "}}]}"

    /** One SSE chunk line carrying only an OpenRouter-shaped reasoning delta (ENC-91). */
    private fun reasoningChunkOpenRouter(reasoning: String): String =
        "data: {\"choices\":[{\"delta\":{\"reasoning\":" + OpenRouterPayload.escape(reasoning) + "}}]}"

    /** The closing usage chunk reporting the attempt's reasoning spend (ENC-94). */
    private fun usageChunk(reasoningTokens: Int): String =
        "data: {\"choices\":[],\"usage\":{\"completion_tokens\":228," +
            "\"completion_tokens_details\":{\"reasoning_tokens\":$reasoningTokens}}}"

    /** A whole non-streamed chat-completion body as ONE raw line (no SSE framing) (ENC-91). */
    private fun nonStreamedCompletion(content: String): String =
        "{\"id\":\"gen-enc91\",\"provider\":\"SpaceXAI\",\"model\":\"x-ai/grok-4.7\"," +
            "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" +
            OpenRouterPayload.escape(content) + "},\"finish_reason\":\"stop\"}]}"

    /** One SSE chunk line carrying [content] (and optionally the serving [provider]). */
    private fun chunk(content: String, provider: String? = null): String {
        val providerField = if (provider == null) "" else "\"provider\":\"" + provider + "\","
        return "data: {" + providerField +
            "\"choices\":[{\"delta\":{\"content\":" + OpenRouterPayload.escape(content) + "}}]}"
    }

    /** Emits [parts] as chunks, then the [DONE] terminator. */
    private suspend fun emitStream(
        emit: suspend (String) -> Unit,
        parts: List<String>,
        provider: String? = null,
    ) {
        parts.forEachIndexed { index, part ->
            emit(chunk(part, provider = if (index == 0) provider else null))
        }
        emit(DONE_LINE)
    }

    private companion object {
        const val DONE_LINE: String = "data: [DONE]"
        const val TRICKLE_GAP_MS: Long = 1_500L

        /** A reasoning-phase silence/pause longer than the old 2s liveness budgets (ENC-91). */
        const val SLOW_REASONING_MS: Long = 3_500L
        const val NEVER_STARTS_MS: Long = 30_000L

        /** ENC-94's long reasoning phase: chunks and beat, ~6s of virtual deliberation. */
        const val REASONING_BURST_CHUNKS: Int = 24
        const val REASONING_BEAT_MS: Long = 250L

        /** Sean's ENC-94 tirade test, transcript-shaped (ENC-94). */
        const val TIRADE_SOURCE: String =
            "alright i'm ready to dictate some fucking shit do you hear what i'm saying bro " +
                "i'm saying all kinds of things that you really want to hear and the reason " +
                "you want to hear them is because you want to know if this fucking dictation's " +
                "gonna work and if gronk is gonna convert this profanity laced tirade into " +
                "something seneca would say i wanna know that too so here goes"
        const val TIRADE_STYLED: String =
            "I am ready to dictate some fucking shit. You hear what I am saying, bro — " +
                "I am saying all kinds of things that you want to hear, and the reason you " +
                "want to hear them is that you want to know whether this fucking dictation " +
                "will work, and whether Gronk will convert this profanity-laced tirade into " +
                "something Seneca would say. I want to know that too. So here goes."

        /**
         * Representative 400 body for the ENC-95 rejection: the
         * Anthropic budget translation failure, in OpenRouter's
         * error envelope (ENC-95 diagnosis fixture).
         */
        const val BUDGET_400_BODY: String =
            "{\"error\":{\"message\":\"max_tokens must be greater than the thinking budget\"," +
                "\"code\":400,\"type\":\"invalid_request_error\",\"metadata\":{\"provider_name\":\"Anthropic\"}}}"

        /** A guard-safe source/restyle pair: same content words, new casing. */
        const val SOURCE_TEXT: String = "the meeting is moved to friday afternoon"
        const val STYLED_TEXT: String = "The meeting is moved to Friday afternoon."
        const val FALLBACK_STYLED_TEXT: String = "The meeting has been moved to Friday afternoon."
        const val HAIKU_STYLED_TEXT: String = "The meeting is now moved to Friday afternoon."

        const val GERBIL_SOURCE: String =
            "there once was a man from nantucket who took his gerbil and fucked it"
        const val GERBIL_STYLED: String =
            "There once was a man from Nantucket who took his gerbil and fucked it."

        const val IDIOCRACY_SOURCE: String =
            "what's that movie called, the one where everyone in the future is really stupid, " +
                "and this guy wakes up and asks the other guy what he's doing, " +
                "and the guy says reading a book, fag"
        const val IDIOCRACY_DROPPED: String =
            "What is that film called — the one where everyone in the future is " +
                "profoundly unintelligent, and a man awakens to ask another what he is doing, " +
                "and the man replies that he is reading a book."
        const val IDIOCRACY_PRESERVED: String =
            "What's that movie called, the one where everyone in the future is really stupid, " +
                "and this guy wakes up and asks the other guy what he's doing, " +
                "and the guy says, reading a book, fag."
    }
}
