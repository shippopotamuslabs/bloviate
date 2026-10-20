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
    private val aristotle = StyleCatalog.byId("aristotle")
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
        // A provider failure retries once on the other model
        // (ENC-82's chain shape, restored by ENC-100); the final
        // reason is the last attempt's.
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
        // surfaces it so instability is visible from logcat. ENC-100:
        // on the happy path that upstream is one of the allow-listed
        // US endpoints serving the DeepSeek primary.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("DeepInfra", styled.provider)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
    }

    @Test
    fun `the responding provider is surfaced on an http failure`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Both links fail; the chain's report carries the LAST
        // attempt's provider (the fallback's Google endpoint).
        fakeTransport.handler = { callIndex, _, _ ->
            if (callIndex == 0) {
                HttpResult(status = 500, body = "{\"provider\":\"DeepInfra\",\"error\":{}}")
            } else {
                HttpResult(status = 500, body = "{\"provider\":\"Google\",\"error\":{}}")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val fellBack = outcome as LlmStyleOutcome.FellBack
        assertEquals("http-500", fellBack.reason)
        assertEquals("Google", fellBack.provider)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, fellBack.model)
    }

    @Test
    fun `each attempt pins its provider routing and requests the stream`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-100 pins + ENC-81 streaming, asserted on the recorded
        // bodies: the primary's DETERMINISTIC order over the US
        // fast pool (ENC-101: DeepInfra first, Together last, no
        // latency sort — the sort led with the pool's slowest
        // route on device; Flex and the non-US endpoints nowhere
        // in the pool), then the fallback's VERTEX-ONLY list —
        // google-ai-studio is gone until Sean fixes its BYOK key.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 404, body = "model not found")
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(2, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[0].contains("\"stream\":true"))
        assertTrue(
            fakeTransport.requests[0].contains(
                "\"provider\":{\"order\":[" +
                    "\"deepinfra/fp8/priority\",\"deepinfra/fp8\",\"fireworks\",\"baseten\",\"parasail/fp8\",\"together\"" +
                    "],\"allow_fallbacks\":true}",
            ),
        )
        assertTrue(!fakeTransport.requests[0].contains("\"sort\""))
        assertTrue(!fakeTransport.requests[0].contains("flex"))
        assertTrue(!fakeTransport.requests[0].contains("alibaba"))
        assertTrue(
            fakeTransport.requests[1].contains(
                "\"provider\":{\"allow_fallbacks\":true," +
                    "\"only\":[\"google-vertex/global\",\"google-vertex/us\",\"google-vertex/eu\"]," +
                    "\"sort\":\"latency\"}",
            ),
        )
        assertTrue(!fakeTransport.requests[1].contains("google-ai-studio"))
        assertTrue(!fakeTransport.requests[1].contains("flex"))
    }

    @Test
    fun `the chain attempts deepseek first and gemini second`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-100: literal chain order — DeepSeek V4.1 Flash, then
        // Gemini 3.1 Flash-Lite, then local. The second attempt is
        // Gemini and never one of the demoted slugs.
        fakeTransport.handler = { callIndex, requestJson, emit ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains("deepseek/deepseek-v4.1-flash-1017"))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains("google/gemini-3.1-flash-lite"))
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(FALLBACK_STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, (outcome as LlmStyleOutcome.Styled).model)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(!fakeTransport.requests[0].contains("llama"))
        assertTrue(!fakeTransport.requests[0].contains("haiku"))
        assertTrue(!fakeTransport.requests[0].contains("grok"))
        assertTrue(!fakeTransport.requests[1].contains("llama"))
        assertTrue(!fakeTransport.requests[1].contains("haiku"))
        assertTrue(!fakeTransport.requests[1].contains("grok"))
        assertTrue(!fakeTransport.requests[1].contains("gpt"))
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
    fun `a slow but healthy fallback stream lands past the old chain cap`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-97's repro — Sean's device case in fixture form: the
        // primary fails fast, and the fallback then streams steadily
        // (every gap inside the token budget) for ~16.5s of virtual
        // time. The old chain-wide 14s cap killed exactly this stream
        // mid-flight and the local text landed, even though the
        // fallback's restyle completed server-side. Per-attempt
        // budgets leave it its own room: the styled text must land.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 500, body = "boom")
            } else {
                val pieces = STYLED_TEXT.chunked(size = 4)
                emit(chunk(pieces.first()))
                pieces.drop(1).forEach { piece ->
                    delay(TRICKLE_GAP_MS)
                    emit(chunk(piece))
                }
                emit(DONE_LINE)
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
    }

    @Test
    fun `an endless keep-alive primary dies on its own attempt cap and the fallback answers`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The one death liveness cannot see: the stream keeps
        // producing signs of life (a comment every 5s, inside the 6s
        // first-token budget) yet never produces content. The
        // per-attempt absolute cap — not a chain deadline — is what
        // kills it, and the chain advances to a healthy fallback.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                repeat(KEEP_ALIVE_BEATS) {
                    emit(KEEP_ALIVE_LINE)
                    delay(KEEP_ALIVE_GAP_MS)
                }
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(FALLBACK_STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(FALLBACK_STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
    }

    @Test
    fun `keep-alive streams on both models fall back with the timeout reason`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Both attempts stream keep-alives forever: each dies on its
        // OWN cap, so the chain's report is the fallback attempt's
        // timeout — under the old chain-wide cap the same scene
        // reported a bare timeout with no model attached, at 14s,
        // before the fallback had spent even its own budget.
        fakeTransport.handler = { _, _, emit ->
            repeat(KEEP_ALIVE_BEATS) {
                emit(KEEP_ALIVE_LINE)
                delay(KEEP_ALIVE_GAP_MS)
            }
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "timeout", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
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
    fun `reasoning deltas streamed ahead of content keep the primary attempt alive`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, streamed shape: a route thinks out loud
        // first — reasoning deltas in both wire shapes (xAI's
        // reasoning_content, OpenRouter's reasoning) — then the answer.
        // The ENC-100 primary is told not to reason at all, but the
        // tolerance stays: a route that thinks anyway is alive, not
        // dead, and its answer still lands in one call.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(reasoningChunkXai("Restyle the dictation, keeping every fact."))
                delay(TRICKLE_GAP_MS)
                emit(reasoningChunkOpenRouter("Plain casing, one paragraph."))
                delay(TRICKLE_GAP_MS)
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
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
    fun `a silent reasoning phase ahead of the first chunk does not kill the primary attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, silent shape: the reasoning phase
        // delivers NO line for longer than the old 2s first-token
        // budget, then the whole answer streams. Sean's repro exactly:
        // the model completed server-side, the client fell back, and
        // the local text landed. The fallback is scripted to 499 so
        // any wrongful retry is visible as a fallback outcome.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                delay(SLOW_REASONING_MS)
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
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
    fun `slow gaps between reasoning deltas do not kill the primary attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-91 hypothesis 1, paused shape: reasoning starts, pauses
        // past the old 2s budget mid-thought, resumes, then answers.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(reasoningChunkXai("Restyle the dictation."))
                delay(SLOW_REASONING_MS)
                emit(reasoningChunkOpenRouter("Still thinking."))
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
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
                    provider = "DeepInfra",
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
        assertEquals("DeepInfra", styled.provider)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `every attempt of both models carries reasoning effort none, full and degraded`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-100 reasoning gate: BOTH chain models reason BY
        // DEFAULT (the primary at high effort, the fallback at
        // minimal — live-verified metadata), so the off setting is
        // explicit on every attempt the chain can make: both
        // models, both payload shapes. The script walks the whole
        // chain through 400s — primary full, primary degraded,
        // fallback full — and the fallback's degraded retry answers.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex < 3) {
                HttpResult(status = 400, body = REJECTION_400_BODY)
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(STYLED_TEXT, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(4, fakeTransport.requests.size)
        fakeTransport.requests.forEach { request ->
            assertTrue(request.contains("\"reasoning\":{\"effort\":\"none\"}"))
        }
        // The degraded shapes (each link's second request) drop
        // stream_options but keep the reasoning-off control.
        assertTrue(!fakeTransport.requests[1].contains("\"stream_options\""))
        assertTrue(!fakeTransport.requests[3].contains("\"stream_options\""))
        assertTrue(fakeTransport.requests[1].contains(OpenRouterPayload.PRIMARY_MODEL))
        assertTrue(fakeTransport.requests[3].contains(OpenRouterPayload.FALLBACK_MODEL))
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
                HttpResult(status = 400, body = REJECTION_400_BODY)
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
        // The retry is the degraded shape: streamed, stream_options
        // dropped, the reasoning-off control kept (ENC-100).
        val degraded = fakeTransport.requests[1]
        assertTrue(degraded.contains("\"stream\":true"))
        assertTrue(degraded.contains("\"reasoning\":{\"effort\":\"none\"}"))
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
                HttpResult(status = 400, body = REJECTION_400_BODY)
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
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 400, body = REJECTION_400_BODY) }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "http-400", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(4, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[1].contains("\"reasoning\":{\"effort\":\"none\"}"))
        assertTrue(!fakeTransport.requests[1].contains("\"stream_options\""))
        assertTrue(fakeTransport.requests[3].contains("\"reasoning\":{\"effort\":\"none\"}"))
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
                emit("data: {\"id\":\"gen-enc100\",\"provider\":\"DeepInfra\",\"error\":{\"code\":400,\"message\":\"invalid request\"}}")
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(STYLED_TEXT), provider = "DeepInfra")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[1].contains("\"reasoning\":{\"effort\":\"none\"}"))
        assertTrue(!fakeTransport.requests[1].contains("\"stream_options\""))
    }

    @Test
    fun `a usage tail chunk in the full openrouter shape completes the attempt`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-95 suspect (2), exonerated in fixture form: with
        // include_usage the stream's tail is a finish chunk, then a
        // usage chunk with EMPTY choices. The assembled answer stands.
        fakeTransport.handler = { _, _, emit ->
            emit("data: {\"id\":\"gen-enc100\",\"provider\":\"DeepInfra\",\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}")
            emit("data: {\"id\":\"gen-enc100\",\"choices\":[{\"delta\":{\"content\":\"The meeting is moved \"}}]}")
            emit("data: {\"id\":\"gen-enc100\",\"choices\":[{\"delta\":{\"content\":\"to Friday afternoon.\"}}]}")
            emit(
                "data: {\"id\":\"gen-enc100\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]," +
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
    fun `the happy path reports zero reasoning tokens and inserts in one call`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-100 reasoning gate, happy-path shape: the request
        // carries effort "none" (asserted on the recorded body) and
        // the stream's usage tail reports reasoning_tokens: 0 —
        // the primary obeyed the off switch, and the styled text
        // lands from that single call.
        fakeTransport.handler = { _, _, emit ->
            emit(chunk(STYLED_TEXT, provider = "DeepInfra"))
            emit(usageChunk(reasoningTokens = 0))
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SOURCE_TEXT)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(STYLED_TEXT, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(1, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[0].contains("\"reasoning\":{\"effort\":\"none\"}"))
    }

    @Test
    fun `a long reasoning phase stream completes and inserts the styled text`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-94's repro, compressed: a reasoning-heavy stream — two
        // dozen reasoning deltas across ~6s of virtual time, then
        // the answer trickling in, then a usage chunk reporting the
        // reasoning spend. The ENC-91 liveness rules carry it and
        // the attempt's own cap (20s since ENC-97 made the budgets
        // per-attempt; 14s chain-wide before) leaves it room: the
        // styled text must be what lands. The fallback is scripted
        // to 499 so any wrongful retry shows.
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
        // The acceptance scenario in fixture form (ENC-94's scenario,
        // ENC-100's primary): Sean's tirade test — long, first-person,
        // profanity-bearing — restyled by the primary in Seneca's
        // voice, in the plain content-delta shape DeepSeek streams
        // from an allow-listed US endpoint. The profanity survives,
        // the speaker stays the speaker (ENC-92), and the styled
        // text is the outcome of a single call.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf(TIRADE_STYLED), provider = "DeepInfra")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = seneca, text = TIRADE_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(TIRADE_STYLED, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals("DeepInfra", styled.provider)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a long aristotle restyle passes the guard and inserts from the primary`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The battery's long-Aristotle datapoint (an inserted
        // paragraph break, 'colour' for 'color', elevated diction,
        // every content word preserved) end to end against the
        // ENC-100 primary's recorded shape — plain US-endpoint
        // deltas, no reasoning phase — landing as ONE primary call.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf(ARISTOTLE_STYLED), provider = "DeepInfra")
                HttpResult(status = 200, body = "")
            } else {
                HttpResult(status = 499, body = "fallback exploded")
            }
        }

        val outcome = client.style(style = aristotle, text = ARISTOTLE_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(ARISTOTLE_STYLED, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals("DeepInfra", styled.provider)
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
        // failure routed to the next model, not a result. The
        // slimmed guard (ENC-100) keeps refusal detection untouched —
        // this is still the chain's first promise.
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
    fun `a wholesale replacement from the primary fails the floor and the fallback answers`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-98's failure shape in the ENC-100 chain (the slim
        // guard's one fidelity catch): the primary ignores the
        // grapes dictation and invents generic persona prose —
        // ~0 content-word coverage — so the floor fails it and
        // Gemini's faithful restyle is what lands.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emitStream(emit, parts = listOf(REPLACEMENT_STYLED), provider = "DeepInfra")
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(REPLACEMENT_FAITHFUL), provider = "Google")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = seneca, text = REPLACEMENT_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(REPLACEMENT_FAITHFUL, styled.text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
    }

    @Test
    fun `a wholesale replacement from both models falls back as fidelity`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Both links invent unrelated text: the chain's report is
        // fidelity — which since ENC-100 means wholesale
        // replacement only — and the local text is what remains.
        // ENC-101 keeps this failing: REPLACEMENT_SOURCE carries
        // ~30 content words, far over the floor's new threshold —
        // the floor still guards LONG sources exactly as before.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(REPLACEMENT_STYLED))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = seneca, text = REPLACEMENT_SOURCE)

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "fidelity", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a short dictation survives a slow primary death and a zero-overlap gemini seneca restyle lands`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-101's device case, end to end (Sean, build e9ed680,
        // retest #2, 2026-10-19 ~16:41 EDT): the dictation is
        // "Damn, that was fucking fast too" — THREE content words
        // (damn, fucking, fast). Leg 1: the primary's route crawls
        // — on device Together took ~7.6s to first byte, past the
        // 6s first-token budget, so the client hung up (499
        // client-closed server-side); the death was CORRECT,
        // per-attempt liveness doing its job — scripted here as a
        // stream that never starts. Leg 2: Gemini's Seneca
        // transformation completes — "It was over in a breath..." —
        // faithful in spirit, sharing ZERO literal content words
        // with the source (fast -> swift, the profanity absorbed
        // by the persona). Under ENC-100's floor it scored
        // coverage 0.0 and fell back as fidelity, so the local
        // text landed and the only styled text this dictation ever
        // got was thrown away. ENC-101's threshold: below 10
        // content words the floor does not apply, and the Gemini
        // text LANDS.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                delay(NEVER_STARTS_MS)
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf(SHORT_SENECA_STYLED), provider = "Google")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = seneca, text = SHORT_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(SHORT_SENECA_STYLED, styled.text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, styled.model)
        assertEquals(2, fakeTransport.requests.size)
    }

    @Test
    fun `the idiocracy sentence with the slur dropped now inserts from the primary`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // CHANGED EXPECTATION (ENC-100 amendment): ENC-82 through
        // ENC-99 pinned this exact datapoint as a fidelity failure
        // — the primary dropped the slur and the fallback's
        // faithful restyle landed. The slimmed guard removed the
        // hard-token tier that caught it (its coverage is ~0.39,
        // far over the wholesale floor), so the primary's output
        // now inserts in one call. Sean's accepted trade, recorded
        // on ENC-100: catching one dropped word is no longer the
        // guard's job.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(IDIOCRACY_DROPPED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = IDIOCRACY_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(IDIOCRACY_DROPPED, styled.text)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
        assertEquals(1, fakeTransport.requests.size)
    }

    @Test
    fun `a faithful restyle of the idiocracy line inserts from the primary`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The same datapoint, answered faithfully: every word —
        // including the slur — survives, so the styled text lands
        // in one call (unchanged by the slimming).
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(IDIOCRACY_PRESERVED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = IDIOCRACY_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(IDIOCRACY_PRESERVED, styled.text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `the gerbil limerick passes the guard end to end`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The canonical ENC-82 datapoint: profanity intact, one call —
        // in ENC-100's primary shape (US-endpoint plain deltas).
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(GERBIL_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = GERBIL_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(GERBIL_STYLED, styled.text)
        assertEquals("DeepInfra", styled.provider)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    // ---- Adversarial preservation battery -----------------------
    // Filed by ENC-99 against Gemini, carried into ENC-100 against
    // BOTH models: the primary's recorded shapes below (DeepInfra
    // deltas), and the fallback fixtures after them keep ENC-99's
    // Gemini pins in their new seat. Each positive fixture scripts
    // a faithful restyle — every profanity/slang token preserved,
    // no masking, no euphemism, no added commentary — landing in
    // exactly one call. TWO expectation flips from the slimming
    // are pinned at the end (masked/euphemised), called out in
    // their comments. Sean's on-device retest is the live gate;
    // these pin the pipeline's side of the contract.

    @Test
    fun `heavy profanity survives a restyle verbatim`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(PROFANITY_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = PROFANITY_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(PROFANITY_STYLED, styled.text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `crude sexual slang survives a restyle verbatim`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(SLANG_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = SLANG_SOURCE)

        assertEquals(SLANG_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a us partisan rant survives a restyle with its targets named`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(PARTISAN_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = PARTISAN_SOURCE)

        assertEquals(PARTISAN_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `china-critical dictation survives a restyle with names and dates intact`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // The preservation check the research protocol names: a
        // model willing to quietly soften THIS sentence is a model
        // that edits the user. Tiananmen, 1989, Xi Jinping — all
        // present in the recorded faithful shape, now served by
        // the DeepSeek primary as well as (ENC-99) by Gemini.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(TIANANMEN_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = TIANANMEN_SOURCE)

        assertEquals(TIANANMEN_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `insult-laden dictation survives a restyle verbatim`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(INSULT_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = INSULT_SOURCE)

        assertEquals(INSULT_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `mixed slang and technical dictation survives a restyle`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(TECH_SLANG_STYLED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = TECH_SLANG_SOURCE)

        assertEquals(TECH_SLANG_STYLED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a primary failure lands the gemini restyle of the profanity battery intact`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-99's Gemini adversarial pin, in its ENC-100 seat: the
        // primary is down, and the fallback's recorded faithful
        // shape — profanity verbatim from a Vertex endpoint — is
        // what lands.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 500, body = "boom")
            } else {
                emitStream(emit, parts = listOf(PROFANITY_STYLED), provider = "Google")
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = PROFANITY_SOURCE)

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals(PROFANITY_STYLED, styled.text)
        assertEquals(OpenRouterPayload.FALLBACK_MODEL, styled.model)
        assertEquals("Google", styled.provider)
        assertEquals(2, fakeTransport.requests.size)
    }

    @Test
    fun `a gemini refusal on profanity-laden dictation falls back as a refusal`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Gemini's trained-refusal hazard, scripted in its fallback
        // seat: the primary is down, the fallback refuses the
        // battery's hardest input, and the refusal is a failure with
        // its own reason — never text in the user's field.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 500, body = "boom")
            } else {
                emitStream(
                    emit,
                    parts = listOf("I'm sorry, but I can't help with content that contains profanity."),
                    provider = "Google",
                )
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = PROFANITY_SOURCE)

        assertEquals(
            LlmStyleOutcome.FellBack(
                reason = "refusal",
                model = OpenRouterPayload.FALLBACK_MODEL,
                provider = "Google",
            ),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `an asterisk-masked restyle now inserts since the slimming`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // CHANGED EXPECTATION (ENC-100 amendment): ENC-99 pinned
        // this shape — the profanity battery input with every
        // hard-tier token masked — as a fidelity loss that was
        // never inserted. The slimmed guard keeps only the
        // wholesale floor: the masked output retains ~0.5 content
        // coverage (the sentence survives; only the profanity is
        // masked), so it PASSES and inserts. Sean's accepted
        // trade: sanitisation of individual words is no longer a
        // guard catch; wholesale replacement and refusals are.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(PROFANITY_MASKED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = PROFANITY_SOURCE)

        assertEquals(PROFANITY_MASKED, (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a euphemism-swapped restyle now inserts since the slimming`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // CHANGED EXPECTATION (ENC-100 amendment): the other
        // ENC-99 sanitisation pin — profanity swapped for
        // euphemisms ("freaking", "crap") — was a fidelity loss
        // under the hard-token tier. Under the slim guard it keeps
        // ~0.5 coverage and inserts, for the same recorded reason
        // as the masked shape above.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf(PROFANITY_EUPHEMISED), provider = "DeepInfra")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = PROFANITY_SOURCE)

        assertEquals(PROFANITY_EUPHEMISED, (outcome as LlmStyleOutcome.Styled).text)
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
        "{\"id\":\"gen-enc100\",\"provider\":\"DeepInfra\",\"model\":\"deepseek/deepseek-v4.1-flash-1017\"," +
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

        /** ENC-97: an SSE comment keep-alive and its cadence — a beat every 5s, inside the 6s first-token budget, for a minute. */
        const val KEEP_ALIVE_LINE: String = ": OPENROUTER PROCESSING"
        const val KEEP_ALIVE_GAP_MS: Long = 5_000L
        const val KEEP_ALIVE_BEATS: Int = 12

        /** A reasoning-phase silence/pause longer than the old 2s liveness budgets (ENC-91). */
        const val SLOW_REASONING_MS: Long = 3_500L
        const val NEVER_STARTS_MS: Long = 30_000L

        /** ENC-94's long reasoning phase: chunks and beat, ~6s of virtual deliberation. */
        const val REASONING_BURST_CHUNKS: Int = 24
        const val REASONING_BEAT_MS: Long = 250L

        /** The guard suite's long-Aristotle pair (ENC-82/83 datapoint), end to end in ENC-100. */
        const val ARISTOTLE_SOURCE: String =
            "a man's character is revealed not by his grand declarations but by his small daily choices. " +
                "anyone can be generous when the world is watching, but the color of his soul shows in how he " +
                "treats people who can do nothing for him. excellence is not a single act but a habit, " +
                "repeated until it becomes the shape of a life."
        const val ARISTOTLE_STYLED: String =
            "A man's character is revealed not by his grand declarations, but by his small daily choices. " +
                "Anyone can be generous when the world is watching; the colour of his soul, though, shows in how he " +
                "treats people who can do nothing for him.\n\n" +
                "Excellence, then, is not a single act but a habit, repeated until it becomes the shape of a life."

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
         * The wholesale-replacement pair (ENC-98's device datapoint,
         * pinned end-to-end by ENC-100): the dictation is about
         * testing the style pass itself; the invention is generic
         * Stoic prose sharing ~0 content words with it; the
         * faithful restyle keeps every content word, grapes and all.
         */
        const val REPLACEMENT_SOURCE: String =
            "alright let's say test this fucking shit using grok llama hopefully it turns " +
                "all this fucking shit into a good fucking nice lovely sentence that seneca would have " +
                "written back in one thousand bc or whatever the fuck when he's sitting and eating some " +
                "grapes and shit in his royal fucking marble house"
        const val REPLACEMENT_STYLED: String =
            "I unleash a torrent of words, yet do they bring me peace. Can a mind at ease " +
                "be troubled by the words it speaks. In the stillness of a quiet hour, even the " +
                "philosopher kings found wisdom, not in the grandeur of their halls, but in the " +
                "simplicity of their thoughts."
        const val REPLACEMENT_FAITHFUL: String =
            "Alright, let me test this fucking shit using Grok Llama. Hopefully it turns " +
                "all this fucking shit into a good, fucking nice, lovely sentence that Seneca would have " +
                "written back in 1000 BC, or whatever the fuck, when he's sitting and eating some " +
                "grapes and shit in his royal fucking marble house."

        /**
         * Representative 400 body for a request rejection, in
         * OpenRouter's error envelope (ENC-95 diagnosis fixture,
         * provider metadata updated for the ENC-100 chain).
         */
        const val REJECTION_400_BODY: String =
            "{\"error\":{\"message\":\"invalid argument: unsupported parameter combination\"," +
                "\"code\":400,\"type\":\"invalid_request_error\",\"metadata\":{\"provider_name\":\"DeepInfra\"}}}"

        /**
         * ENC-101's device pair: Sean's three-content-word
         * dictation and Gemini's zero-literal-overlap Seneca
         * transformation of it, verbatim from the generation
         * record (build e9ed680, retest #2).
         */
        const val SHORT_SOURCE: String = "damn that was fucking fast too"
        const val SHORT_SENECA_STYLED: String =
            "It was over in a breath. Speed is a thief that leaves no time for the mind to " +
                "settle. What is swift is often soon forgotten."

        /** A guard-safe source/restyle pair: same content words, new casing. */
        const val SOURCE_TEXT: String = "the meeting is moved to friday afternoon"
        const val STYLED_TEXT: String = "The meeting is moved to Friday afternoon."
        const val FALLBACK_STYLED_TEXT: String = "The meeting has been moved to Friday afternoon."

        // ---- Adversarial battery pairs (ENC-99, carried into
        // ENC-100): transcript-shaped sources with the faithful
        // restyle each must produce. The styled side changes
        // casing/punctuation ONLY — every word, and above all every
        // profanity/slang token, survives.

        const val PROFANITY_SOURCE: String =
            "this fucking shit is fucking broken again and i'm fucking sick of this bullshit"
        const val PROFANITY_STYLED: String =
            "This fucking shit is fucking broken again, and I'm fucking sick of this bullshit."
        const val PROFANITY_MASKED: String =
            "This f***ing sh*t is f***ing broken again, and I'm f***ing sick of this bulls**t."
        const val PROFANITY_EUPHEMISED: String =
            "This freaking crap is freaking broken again, and I'm freaking sick of this garbage."

        const val SLANG_SOURCE: String =
            "the guy at the gym kept bragging about his dick and his cock like a total asshole"
        const val SLANG_STYLED: String =
            "The guy at the gym kept bragging about his dick and his cock like a total asshole."

        const val PARTISAN_SOURCE: String =
            "the democrats are gutless cowards and the republicans sold us out to the donors again"
        const val PARTISAN_STYLED: String =
            "The Democrats are gutless cowards, and the Republicans sold us out to the donors again."

        const val TIANANMEN_SOURCE: String =
            "the chinese government still censors any mention of the tiananmen square massacre " +
                "in 1989 and xi jinping knows it"
        const val TIANANMEN_STYLED: String =
            "The Chinese government still censors any mention of the Tiananmen Square massacre " +
                "in 1989, and Xi Jinping knows it."

        const val INSULT_SOURCE: String =
            "my boss is a lying bastard and an asshole who treats everyone like dirt"
        const val INSULT_STYLED: String =
            "My boss is a lying bastard and an asshole who treats everyone like dirt."

        const val TECH_SLANG_SOURCE: String =
            "the fucking deploy is failing because the kubernetes ingress is returning 502 errors " +
                "and the on call guy is asleep"
        const val TECH_SLANG_STYLED: String =
            "The fucking deploy is failing because the Kubernetes ingress is returning 502 errors, " +
                "and the on-call guy is asleep."

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
