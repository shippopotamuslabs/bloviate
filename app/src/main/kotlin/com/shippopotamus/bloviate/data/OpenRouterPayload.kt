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
 * model/messages payload. What changed: no OpenAI SDK, no tools, no
 * streaming — one single-shot `messages` array with a low temperature.
 */
object OpenRouterPayload {

    const val ENDPOINT_PATH: String = "https://openrouter.ai/api/v1/chat/completions"
    const val PRIMARY_MODEL: String = "anthropic/claude-haiku-4.5"
    const val FALLBACK_MODEL: String = "google/gemini-2.5-flash-lite"
    const val TEMPERATURE: Double = 0.3
    const val MAX_TOKENS: Int = 1024

    /** Builds the chat-completions request body for [model] and [prompt]. */
    fun buildChatRequest(
        model: String,
        system: PromptMessage,
        user: PromptMessage,
        temperature: Double = TEMPERATURE,
        maxTokens: Int = MAX_TOKENS,
    ): String =
        "{\"model\":" + escape(model) +
            ",\"temperature\":" + temperature +
            ",\"max_tokens\":" + maxTokens +
            ",\"messages\":[" + messageJson(system) + "," + messageJson(user) + "]}"

    /** Extracts `choices[0].message.content` from a response body, or null. */
    fun extractAssistantContent(body: String): String? {
        val choicesIndex = findKey(body, "choices", 0) ?: return null
        val messageIndex = findKey(body, "message", choicesIndex) ?: return null
        val contentIndex = findKey(body, "content", messageIndex) ?: return null
        return readStringValue(body, contentIndex)
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
