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
     * Style chain primary (ENC-84, Sean's decision 2026-10-19): the
     * floating `~` alias for the latest Grok on OpenRouter — the
     * underlying model rolls forward when xAI ships a new flagship, no
     * app update needed. Pinning to a fixed slug is a one-line revert
     * if a future roll changes styling behavior.
     */
    const val PRIMARY_MODEL: String = "~x-ai/grok-latest"

    /** Style chain fallback #1 (ENC-84): Haiku 5.5, then the local text. */
    const val FALLBACK_MODEL: String = "anthropic/claude-haiku-5.5"
    const val TEMPERATURE: Double = 0.3
    const val MAX_TOKENS: Int = 1024

    /**
     * Provider pin for the primary (ENC-80). Queried from OpenRouter's
     * endpoints API at implementation time (2026-10-19): the
     * `~x-ai/grok-latest` alias exposes no endpoint list of its own (it
     * redirects), and its current resolution `x-ai/grok-4.7` is served
     * ONLY by xAI's own endpoints (slugs `xai`, `xai/zdr`, `xai/zdr/us`,
     * `xai/priority`, `xai/zdr/priority`). Ordering the base slug `xai`
     * therefore pins the whole first-party family and keeps any future
     * third-party endpoint from silently joining the pool.
     */
    val PRIMARY_PROVIDER_ORDER: List<String> = listOf("xai")

    /**
     * Provider pin for the fallback (ENC-80): prefer Anthropic's
     * first-party endpoint (slug `anthropic`) and skip
     * `claude-on-aws` ("Claude Platform on AWS") — the upstream behind
     * the 2026-10-19 dogfood 499 + degraded-retry incident. Fallbacks
     * stay enabled, so Bedrock/Vertex/Azure still serve if Anthropic's
     * own endpoint is down.
     */
    val FALLBACK_PROVIDER_ORDER: List<String> = listOf("anthropic")
    val FALLBACK_PROVIDER_IGNORE: List<String> = listOf("claude-on-aws")

    /**
     * Builds the chat-completions request body for [model] and [prompt].
     * When [providerOrder] is given, a `provider` routing block is
     * appended (ENC-80): try those provider slugs in order,
     * `allow_fallbacks` on, minus any [providerIgnore] slugs. [stream]
     * asks for the SSE form (ENC-81); the client always sets it.
     */
    fun buildChatRequest(
        model: String,
        system: PromptMessage,
        user: PromptMessage,
        temperature: Double = TEMPERATURE,
        maxTokens: Int = MAX_TOKENS,
        providerOrder: List<String>? = null,
        providerIgnore: List<String> = emptyList(),
        stream: Boolean = false,
    ): String =
        "{\"model\":" + escape(model) +
            ",\"temperature\":" + temperature +
            ",\"max_tokens\":" + maxTokens +
            ",\"messages\":[" + messageJson(system) + "," + messageJson(user) + "]" +
            (if (stream) ",\"stream\":true" else "") +
            providerJson(providerOrder, providerIgnore) + "}"

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
     * Whether [status]/[body] means "this model id is not servable" and the
     * fallback model is worth one try: a 404, or an error body naming the
     * model as missing/unsupported with no usable endpoint.
     */
    fun isModelUnavailable(status: Int, body: String): Boolean {
        if (status == MODEL_NOT_FOUND_STATUS) return true
        val lowered = body.lowercase()
        return MODEL_UNAVAILABLE_MARKERS.any { marker -> marker in lowered }
    }

    private fun providerJson(order: List<String>?, ignore: List<String>): String {
        if (order == null) return ""
        val orderJson = order.joinToString(separator = ",") { slug -> escape(slug) }
        val ignoreJson = if (ignore.isEmpty()) {
            ""
        } else {
            ",\"ignore\":[" + ignore.joinToString(separator = ",") { slug -> escape(slug) } + "]"
        }
        return ",\"provider\":{\"order\":[" + orderJson + "],\"allow_fallbacks\":true" + ignoreJson + "}"
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
