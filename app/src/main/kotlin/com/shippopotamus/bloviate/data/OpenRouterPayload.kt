package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.evaluator.PromptMessage

/**
 * OpenRouter chat-completions request building and response reading
 * (ENC-73), in pure Kotlin with no JSON library.
 *
 * `org.json` ships on Android but is stubbed on the JVM, so unit tests
 * could never exercise a `JSONObject` path for real; a tiny hand-rolled
 * escaper plus a minimal string-value extractor keeps every branch of
 * this file honestly unit-testable. What was ported from Enclave's
 * OpenRouter shape: the endpoint path, Bearer auth, and the
 * model/messages payload. What changed: no OpenAI SDK, no tools — one
 * `messages` array with a low temperature, consumed as an SSE stream
 * since ENC-81 (ENC-73's single-shot response extractor went with the
 * single-shot client it served).
 */
object OpenRouterPayload {

    const val ENDPOINT_PATH: String = "https://openrouter.ai/api/v1/chat/completions"

    /**
     * Style chain primary (ENC-100, Sean 2026-10-19): DeepSeek
     * V4.1 Flash, the dated 1017 snapshot, pinned to US fast
     * providers only (see [PRIMARY_PROVIDER_ORDER]). Sean's call on
     * the ENC-99 build's device retest: "probably less censored
     * than Google as a model" — the retest's Gemini completion had
     * euphemized the dictation's profanity ('coarse words') and
     * appended an aphorism the speaker never said, the guard
     * rightly failed it, and with no fallback model in the chain
     * the local text was the only landing. This head answers the
     * censorship suspect; [FALLBACK_MODEL] existing again answers
     * the nowhere-to-go suspect.
     *
     * The pick's own numbers (live-verified 2026-10-19): $0.15 in
     * / $0.60 out per 1M, 1M context. Its reasoning profile is the
     * ENC-99 trap in a stronger form — models-API metadata reports
     * reasoning `default_enabled: true` at `default_effort:
     * "high"`, with `supported_efforts` running max → none and
     * `mandatory: false` — so the explicit effort "none" on every
     * attempt ([REASONING_EFFORT]) is what keeps this a fast
     * non-reasoning primary instead of a 19-second deliberator;
     * the slug DOES have a true reasoning-off, which the ticket's
     * reasoning gate required before any payload was written.
     */
    const val PRIMARY_MODEL: String = "deepseek/deepseek-v4.1-flash-1017"

    /**
     * Style chain fallback (ENC-100): Gemini 3.1 Flash-Lite,
     * demoted from the head it held for exactly one build
     * (ENC-99, as revised — the ticket as filed named a Grok slug
     * that is not a live OpenRouter model; Sean promoted Gemini
     * then, and demotes it now). It keeps its ENC-99 profile:
     * reasoning explicitly off on every attempt (its metadata has
     * reasoning ON by default at minimal effort), the adversarial
     * preservation pins as its acceptance. Its routing CHANGES
     * with the demotion: vertex-only (see [FALLBACK_PROVIDER_ONLY])
     * — the ENC-99 build's retest recorded a Google AI Studio
     * BYOK 400 on Sean's account (an invalid stored key) alongside
     * the Vertex 200 that carried the call, so the AI Studio
     * endpoint leaves the pool until the key is fixed.
     *
     * The pick's own numbers (ENC-99 research, live-verified):
     * $0.25 in / $1.50 out per 1M (≈$0.000575/call), TTFT
     * 0.52–0.93s at 122–185 tok/s. Its known hazard is unchanged:
     * trained Gemini refusals / sanitisation on explicit content —
     * as the fallback it is the chain's second chance, and the
     * ENC-82 guard still judges its output before it may land.
     */
    const val FALLBACK_MODEL: String = "google/gemini-3.1-flash-lite"

    /**
     * The demoted primary (ENC-84 → ENC-96). This floating `~` alias
     * for the latest Grok WAS the chain primary on 2026-10-19
     * (ENC-84, Sean's pick) and produced excellent styled text — but
     * even at effort low it emits ~1,000 reasoning tokens per restyle
     * and took ~19.3s end to end for a ~90-token answer on device
     * (ENC-94/95), over any sane stop-to-text budget. It is retained,
     * unreferenced by the default chain, so a future per-style engine
     * choice can revive it deliberately where latency is acceptable.
     */
    const val GROK_MODEL: String = "~x-ai/grok-latest"

    /**
     * The demoted primary (ENC-96 → ENC-99). Llama 3.3 70B Instruct
     * headed the chain for one build (ENC-96, "dumb fast speeds"):
     * on device it answered in 713ms — with generic persona text
     * containing nothing from the dictation it was sent (ENC-98).
     * The fidelity guard caught it, but a primary that does not
     * restyle is not a primary. Retained, unreferenced by the
     * default chain, per the [GROK_MODEL] precedent.
     */
    const val LLAMA_MODEL: String = "meta-llama/llama-3.3-70b-instruct"

    /**
     * The demoted fallback (ENC-84 → ENC-99). Haiku 5.5 was the
     * chain's second link from ENC-73 through ENC-97. It leaves the
     * default chain on Sean's call (ENC-99): user-verified
     * sanitisation / dropped words, and Anthropic's Haiku 5.5
     * system card reports the highest over-refusal of any model in
     * its audit — the two failure shapes the ENC-82 guard exists to
     * catch are this model's documented habits. Retained,
     * unreferenced by the default chain, per the [GROK_MODEL]
     * precedent.
     */
    const val HAIKU_MODEL: String = "anthropic/claude-haiku-5.5"
    const val TEMPERATURE: Double = 0.3
    const val MAX_TOKENS: Int = 1024

    /**
     * Reasoning control for style requests (ENC-100; ENC-99 and
     * ENC-94/95 before it). A restyle is a surface transformation —
     * it needs no deliberation — and BOTH links of the ENC-100
     * chain reason by default, so the explicit setting is REQUIRED
     * on every attempt of both. Live-verified models-API metadata
     * (2026-10-19): the primary (DeepSeek V4.1 Flash) reports
     * `default_enabled: true` at `default_effort: "high"`; the
     * fallback (Gemini 3.1 Flash-Lite) at `"minimal"`. Both list
     * `supported_efforts` running to "none" with `mandatory:
     * false` — so the setting is the strongest one both honor:
     * effort "none", reasoning disabled entirely, the happy path's
     * telemetry reading reasoningTokens=0 with no thought tokens
     * billed against [MAX_TOKENS]. It rides BOTH payload shapes
     * (full and the client's ENC-95 degraded retry): the degraded
     * shape drops `stream_options`, never this block, because a
     * retry that silently re-enabled a model's default reasoning
     * would re-open the latency wound the whole saga was about.
     *
     * The block stays effort-only, always: ENC-95's regression was
     * ENC-94's `reasoning: {effort, max_tokens: 512}` — OpenRouter's
     * unified parameter accepts either `effort` or `max_tokens`,
     * not both, and the combination was rejected 400-class on
     * device. [buildChatRequest] has no way to emit the pair.
     */
    const val REASONING_EFFORT: String = "none"

    /**
     * Provider pin for the primary (ENC-80's mechanism, ENC-100's
     * pool, ENC-101's shape): a deterministic `order` over
     * DeepSeek V4.1 Flash's US FAST endpoints, DeepInfra first —
     * Sean's specification: "through a really fast US provider".
     * Endpoint tags are the OpenRouter endpoints API's values,
     * queried at implementation time (2026-10-19); every endpoint
     * in the pool sat at status 0 / 100% 30-day uptime:
     *
     * - `deepinfra/fp8/priority`, `deepinfra/fp8` — DeepInfra's
     *   priority and standard fp8 tiers. DeepInfra served the
     *   build's accepted on-device landing, so it leads.
     * - `fireworks`, `baseten`, `parasail/fp8` — the rest of the
     *   US fast class, in that order.
     * - `together` — LAST, demoted by ENC-101 rather than removed:
     *   ENC-100's pin was an `only` allow-list with
     *   `sort: latency`, and OpenRouter's global latency sort led
     *   Sean's retest #2 with Together, which took ~7.6s to first
     *   byte and ate a client-closed 499 while the server ground
     *   out a 15.4s generation. A global sort is not Sean's route
     *   quality; a deterministic order is.
     * - Excluded by ABSENCE (the order positively enumerates the
     *   pool, so anything it does not name stays out by
     *   construction): `deepinfra/fp8/flex` — the Flex tier is
     *   batch-class latency, the opposite of a dictation profile;
     *   `deepseek` (DeepSeek direct) and `alibaba` — Sean specified
     *   US providers; and every other listed endpoint (Cloudflare,
     *   Morph, Crusoe, …) — not in the ticket's fast class.
     *
     * Slugs are the endpoints API's `tag` values, the same form
     * ENC-80 pinned. `allow_fallbacks` stays on (see
     * [buildChatRequest]): the router walks this order endpoint to
     * endpoint on failure.
     */
    val PRIMARY_PROVIDER_ORDER: List<String> =
        listOf(
            "deepinfra/fp8/priority",
            "deepinfra/fp8",
            "fireworks",
            "baseten",
            "parasail/fp8",
            "together",
        )

    /**
     * Provider pin for the fallback (ENC-100): VERTEX-ONLY.
     * ENC-99's pool also allow-listed `google-ai-studio`; the
     * ENC-99 build's device retest showed why it goes — OpenRouter
     * recorded a Google AI Studio BYOK 400 ("API key not valid")
     * from Sean's stored AI Studio key alongside the Vertex 200
     * that carried the call. The endpoint leaves the pool until
     * Sean fixes that key: routing noise on the chain's second
     * chance is still noise. What remains is Gemini 3.1
     * Flash-Lite's standard Vertex endpoints (tags live-verified
     * 2026-10-19, all at 100% 30-day uptime), latency-sorted; the
     * Flex tags (`google-vertex/global/flex`, status −5 / 64%
     * uptime in the ENC-99 survey) stay excluded by absence, as
     * do the `/priority` tier endpoints.
     */
    val FALLBACK_PROVIDER_ONLY: List<String> =
        listOf("google-vertex/global", "google-vertex/us", "google-vertex/eu")

    /** Sort the fallback's allow-listed endpoints by live-measured latency (ENC-100). */
    const val FALLBACK_PROVIDER_SORT: String = "latency"

    /**
     * Builds the chat-completions request body for [model] and [prompt].
     * When any routing input is given, a `provider` routing block is
     * appended (ENC-80): [providerOrder] tries those provider slugs
     * in order, [providerOnly] allow-lists exactly those endpoints,
     * [providerIgnore] skips named slugs, [providerSort] orders the
     * pool by a live metric ("latency" / "throughput" / "price");
     * `allow_fallbacks` is on whenever a block is emitted. [stream]
     * asks for the SSE form (ENC-81) and — unless [includeUsage] is
     * off — for usage in the final chunk
     * (`stream_options.include_usage`, ENC-94) so reasoning-token
     * counts reach telemetry. [reasoningEffort] adds the unified
     * `reasoning` block (effort only, never a token cap — ENC-95;
     * see [REASONING_EFFORT]); null omits it. The client's degraded
     * retry (ENC-95, reshaped by ENC-99/100) passes `includeUsage = false`
     * with the link's reasoning effort intact: a streamed request
     * carrying nothing optional but the one control that defines the
     * link.
     */
    fun buildChatRequest(
        model: String,
        system: PromptMessage,
        user: PromptMessage,
        temperature: Double = TEMPERATURE,
        maxTokens: Int = MAX_TOKENS,
        providerOrder: List<String>? = null,
        providerIgnore: List<String> = emptyList(),
        providerOnly: List<String> = emptyList(),
        providerSort: String? = null,
        stream: Boolean = false,
        reasoningEffort: String? = null,
        includeUsage: Boolean = true,
    ): String =
        "{\"model\":" + escape(model) +
            ",\"temperature\":" + temperature +
            ",\"max_tokens\":" + maxTokens +
            ",\"messages\":[" + messageJson(system) + "," + messageJson(user) + "]" +
            (if (stream && includeUsage) ",\"stream\":true,\"stream_options\":{\"include_usage\":true}" else "") +
            (if (stream && !includeUsage) ",\"stream\":true" else "") +
            providerJson(providerOrder, providerIgnore, providerOnly, providerSort) +
            // The unified `reasoning` block — effort only (ENC-95), or absent when unset.
            (if (reasoningEffort == null) "" else ",\"reasoning\":{\"effort\":" + escape(reasoningEffort) + "}") +
            "}"

    /**
     * Extracts the provider error metadata of an OpenRouter error
     * payload (`{"error":{"code":400,"type":"...",...}}`), or null
     * when the body carries no error object (ENC-95 telemetry). The
     * code (numeric or numeric-string) and type are provider
     * metadata, never response text: they exist so a failed attempt's
     * log line names the rejection class from logcat alone.
     */
    fun extractErrorInfo(body: String): ProviderError? {
        val errorIndex = findKey(body, "error", 0) ?: return null
        val codeIndex = findKey(body, "code", errorIndex)
        val code = codeIndex?.let { readIntValue(body, it) ?: readStringValue(body, it)?.toIntOrNull() }
        val typeIndex = findKey(body, "type", errorIndex)
        val type = typeIndex?.let { readStringValue(body, it) }
        return if (code == null && type == null) null else ProviderError(code = code, type = type)
    }

    /**
     * Extracts the reasoning-token count a response reports in its
     * usage (`usage.completion_tokens_details.reasoning_tokens`), or
     * null when the chunk/body carries none (ENC-94 telemetry). The
     * count never leaves the log: it exists so a reasoning blowout is
     * visible from logcat instead of only in OpenRouter's dashboard.
     */
    fun extractReasoningTokens(data: String): Int? {
        val tokensIndex = findKey(data, "reasoning_tokens", 0) ?: return null
        return readIntValue(data, tokensIndex)
    }

    /** Reads the JSON integer starting at [valueStart] (past the colon), or null. */
    private fun readIntValue(json: String, valueStart: Int): Int? {
        var index = valueStart
        while (index < json.length && json[index].isWhitespace()) index++
        val digitsStart = index
        if (index < json.length && json[index] == '-') index++
        while (index < json.length && json[index].isDigit()) index++
        if (index == digitsStart) return null
        return json.substring(digitsStart, index).toIntOrNull()
    }

    /**
     * Extracts `choices[0].delta.content` from one streamed chunk (the
     * JSON after an SSE `data:` prefix), or null when the chunk carries
     * no text (role-only openers, usage-only closers) (ENC-81).
     */
    fun extractStreamDelta(data: String): String? {
        val choicesIndex = findKey(data, "choices", 0) ?: return null
        val deltaIndex = findKey(data, "delta", choicesIndex) ?: return null
        val contentIndex = findKey(data, "content", deltaIndex) ?: return null
        return readStringValue(data, contentIndex)
    }

    /**
     * Extracts the reasoning text from one streamed chunk, or null when
     * the chunk carries none (ENC-91). Grok thinks out loud before it
     * answers: xAI streams the thought as `delta.reasoning_content`,
     * OpenRouter's unified shape as `delta.reasoning`, and some routes
     * as structured `delta.reasoning_details` (whose mere presence is
     * the sign of life — returned as "" because the detail entries
     * have no single text field). The client consumes none of it; the
     * extraction exists so a reasoning chunk registers as model
     * activity instead of looking like silence.
     */
    fun extractReasoningDelta(data: String): String? {
        val choicesIndex = findKey(data, "choices", 0) ?: return null
        val deltaIndex = findKey(data, "delta", choicesIndex) ?: return null
        for (field in REASONING_TEXT_FIELDS) {
            val fieldIndex = findKey(data, field, deltaIndex) ?: continue
            val value = readStringValue(data, fieldIndex)
            if (value != null) return value
        }
        return if (findKey(data, REASONING_DETAILS_FIELD, deltaIndex) != null) "" else null
    }

    /**
     * Extracts `choices[0].message.content` from a whole NON-streamed
     * chat-completion body, or null when [data] is not one (ENC-91).
     * A stream request can still be answered in the single-shot shape
     * — a cached replay or a gateway/provider that does not stream —
     * and the body then carries the complete answer in one line with
     * no SSE framing at all.
     */
    fun extractMessageContent(data: String): String? {
        val choicesIndex = findKey(data, "choices", 0) ?: return null
        val messageIndex = findKey(data, "message", choicesIndex) ?: return null
        val contentIndex = findKey(data, "content", messageIndex) ?: return null
        return readStringValue(data, contentIndex)?.takeIf { value -> value.isNotBlank() }
    }

    /**
     * Whether one streamed chunk is an error payload rather than a
     * completion chunk — OpenRouter can commit a 200 stream and still
     * deliver `{"error": ...}` mid-stream (ENC-81).
     */
    fun isStreamError(data: String): Boolean = findKey(data, "error", 0) != null

    /**
     * Extracts the top-level `provider` OpenRouter stamps on a response
     * (the upstream that actually served it, e.g. "xAI" / "Anthropic"),
     * or null when the body carries none (ENC-80 telemetry).
     */
    fun extractProvider(body: String): String? {
        val providerIndex = findKey(body, "provider", 0) ?: return null
        return readStringValue(body, providerIndex)
    }

    /**
     * Whether [status]/[body] means "this model id is not servable",
     * reported as its own reason class: a 404, or an error body naming
     * the model as missing/unsupported with no usable endpoint.
     */
    fun isModelUnavailable(status: Int, body: String): Boolean {
        if (status == MODEL_NOT_FOUND_STATUS) return true
        val lowered = body.lowercase()
        return MODEL_UNAVAILABLE_MARKERS.any { marker -> marker in lowered }
    }

    private fun providerJson(
        order: List<String>?,
        ignore: List<String>,
        only: List<String>,
        sort: String?,
    ): String {
        val parts = mutableListOf<String>()
        if (order != null) {
            parts += "\"order\":[" + order.joinToString(separator = ",") { slug -> escape(slug) } + "]"
        }
        if (ignore.isNotEmpty()) {
            parts += "\"ignore\":[" + ignore.joinToString(separator = ",") { slug -> escape(slug) } + "]"
        }
        if (only.isNotEmpty()) {
            parts += "\"only\":[" + only.joinToString(separator = ",") { slug -> escape(slug) } + "]"
        }
        if (sort != null) {
            parts += "\"sort\":" + escape(sort)
        }
        if (parts.isEmpty()) return ""
        // `allow_fallbacks` rides every emitted block, directly after
        // the order list when there is one (the ENC-80 wire shape).
        parts.add(if (order != null) 1 else 0, "\"allow_fallbacks\":true")
        return ",\"provider\":{" + parts.joinToString(separator = ",") + "}"
    }

    private fun messageJson(message: PromptMessage): String =
        "{\"role\":" + escape(message.role) + ",\"content\":" + escape(message.content) + "}"

    /** JSON string literal for [raw], escaping quotes, backslashes, and controls. */
    fun escape(raw: String): String =
        buildString(raw.length + 2) {
            append('"')
            raw.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
                }
            }
            append('"')
        }

    /**
     * Index just past the `:` of the next `"key":` occurrence at or after
     * [from] that sits outside any string literal, or null. Skipping
     * in-string matches keeps a `"content"` inside an error message from
     * resolving as the answer.
     */
    private fun findKey(json: String, key: String, from: Int): Int? {
        val wanted = "\"" + key + "\""
        var index = from
        var inString = false
        var escaped = false
        while (index <= json.length - wanted.length) {
            val char = json[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                index++
                continue
            }
            if (char == '"') {
                if (json.startsWith(wanted, index) && isColonAfter(json, index + wanted.length)) {
                    return index + wanted.length + jsonColonOffset(json, index + wanted.length)
                }
                inString = true
            }
            index++
        }
        return null
    }

    private fun isColonAfter(json: String, from: Int): Boolean =
        jsonColonOffset(json, from) >= 0

    private fun jsonColonOffset(json: String, from: Int): Int {
        var offset = 0
        while (from + offset < json.length) {
            val char = json[from + offset]
            if (char == ':') return offset + 1
            if (!char.isWhitespace()) return -1
            offset++
        }
        return -1
    }

    /** Reads the JSON string value starting at [valueStart] (past the colon). */
    private fun readStringValue(json: String, valueStart: Int): String? {
        var index = valueStart
        while (index < json.length && json[index].isWhitespace()) index++
        var result: String? = null
        if (index < json.length && json[index] == '"') {
            result = scanStringBody(json, index + 1)
        }
        return result
    }

    /**
     * Scans one JSON string body from [from] (past the opening quote),
     * resolving escapes into [out]; the closing quote's index, or -1 when
     * the string never closes or an escape is malformed.
     */
    private fun scanStringBody(json: String, from: Int): String? {
        val out = StringBuilder()
        var index = from
        var end = -1
        while (index < json.length && end < 0) {
            val char = json[index]
            if (char == '"') {
                end = index
            } else if (char != '\\') {
                out.append(char)
                index++
            } else {
                index = readEscape(json, index, out)
            }
        }
        return if (end >= 0) out.toString() else null
    }

    /**
     * Resolves the escape at [backslash] into [out]; the index past the
     * escape, or -1 when malformed (which fails the whole string).
     */
    private fun readEscape(json: String, backslash: Int, out: StringBuilder): Int =
        if (backslash + 1 >= json.length) {
            -1
        } else {
            val esc = json[backslash + 1]
            val simple = SIMPLE_ESCAPES[esc]
            if (simple != null) {
                out.append(simple)
                backslash + SIMPLE_ESCAPE_WIDTH
            } else if (esc == 'u' && backslash + UNICODE_ESCAPE_WIDTH <= json.length) {
                val code = json.substring(backslash + 2, backslash + UNICODE_ESCAPE_WIDTH).toIntOrNull(HEXADECIMAL_RADIX)
                if (code != null) {
                    out.append(code.toChar())
                    backslash + UNICODE_ESCAPE_WIDTH
                } else {
                    -1
                }
            } else {
                -1
            }
        }

    /** Streamed reasoning fields carrying plain text (xAI's, then OpenRouter's unified name). */
    private val REASONING_TEXT_FIELDS: List<String> = listOf("reasoning_content", "reasoning")
    private const val REASONING_DETAILS_FIELD: String = "reasoning_details"

    private const val MODEL_NOT_FOUND_STATUS: Int = 404
    private const val SIMPLE_ESCAPE_WIDTH: Int = 2
    private const val UNICODE_ESCAPE_WIDTH: Int = 6
    private const val HEXADECIMAL_RADIX: Int = 16

    private val SIMPLE_ESCAPES: Map<Char, Char> = mapOf(
        '"' to '"',
        '\\' to '\\',
        '/' to '/',
        'n' to '\n',
        'r' to '\r',
        't' to '\t',
        'b' to '\b',
        'f' to '\u000C',
    )

    private val MODEL_UNAVAILABLE_MARKERS: List<String> = listOf(
        "model not found",
        "model_not_found",
        "no endpoints",
        "not supported",
    )
}

/**
 * Provider error metadata from an OpenRouter error payload (ENC-95):
 * the rejection's HTTP-style [code] and its [type] label, either of
 * which the provider may omit. Telemetry only — never user content.
 */
data class ProviderError(
    val code: Int?,
    val type: String?,
)
