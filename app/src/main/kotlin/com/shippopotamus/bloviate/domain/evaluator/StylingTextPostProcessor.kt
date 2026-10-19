package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.domain.port.ApiKeyPort
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.domain.port.LlmStylePort
import com.shippopotamus.bloviate.domain.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import javax.inject.Inject

/** The send-path styling result: the text plus how it was produced. */
data class StyleProcessResult(
    val text: String,
    val styleId: String,
    val llmMs: Long?,
    val fellBack: Boolean,
)

/**
 * The async styling stage of the send path (ENC-73). Runs the local chain
 * first ([TextPostProcessor] — the bound ENC-72 style-aware processor),
 * then, only when the selected style is [StyleKind.LLM] AND a key is
 * stored, sends the local text to the LLM. Anything else — a mechanical
 * style, no key, an LLM miss — returns the local text unchanged.
 *
 * The selection is read from [StylePreferencePort] on every call, never
 * captured at construction, so a Style-tab change applies to the next
 * dictation (the same freshness contract as `StyleAwareTextPostProcessor`).
 */
class StylingTextPostProcessor @Inject constructor(
    private val local: TextPostProcessor,
    private val stylePreference: StylePreferencePort,
    private val llm: LlmStylePort,
    private val apiKeys: ApiKeyPort,
) {

    suspend fun process(rawTranscript: String): StyleProcessResult {
        val localText = local.process(rawTranscript)
        val style = StyleCatalog.byId(stylePreference.selectedStyleId.value)
        if (style.kind != StyleKind.LLM || !apiKeys.hasKey.value) {
            return StyleProcessResult(
                text = localText,
                styleId = style.id,
                llmMs = null,
                fellBack = false,
            )
        }
        return when (val outcome = llm.style(style, localText)) {
            is LlmStyleOutcome.Styled -> StyleProcessResult(
                text = outcome.text,
                styleId = style.id,
                llmMs = outcome.llmMs,
                fellBack = false,
            )
            is LlmStyleOutcome.FellBack -> StyleProcessResult(
                text = localText,
                styleId = style.id,
                llmMs = null,
                fellBack = true,
            )
        }
    }
}
