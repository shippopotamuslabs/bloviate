package com.shippopotamus.bloviate.application.evaluator

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.application.port.LlmStyleOutcome
import com.shippopotamus.bloviate.application.port.LlmStylePort
import com.shippopotamus.bloviate.application.port.SendProgressPort
import com.shippopotamus.bloviate.application.port.SendStage
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import javax.inject.Inject

/** The send-path styling result: the text plus how it was produced. */
data class StyleProcessResult(
    val text: String,
    val styleId: String,
    val llmMs: Long?,
    val fellBack: Boolean,
    /** Why the LLM pass fell back (ENC-83 telemetry); null when it did not run or succeeded. */
    val fellBackReason: String? = null,
    /** The model that answered the LLM pass (ENC-80 telemetry); null when it did not run. */
    val model: String? = null,
    /** The OpenRouter upstream that served the pass (ENC-80 telemetry); null when unknown. */
    val provider: String? = null,
)

/**
 * The async styling stage of the send path (ENC-73). Runs the local chain
 * first ([TextPostProcessor] — the bound ENC-72 style-aware processor),
 * then, when the selected style is [StyleKind.LLM], sends the local text
 * to the LLM via the Firebase Function (ENC-151; no key exists on device
 * anymore). A mechanical style or an LLM miss returns the local text
 * unchanged.
 *
 * The selection is read from [StylePreferencePort] on every call, never
 * captured at construction, so a Style-tab change applies to the next
 * dictation (the same freshness contract as `StyleAwareTextPostProcessor`).
 */
class StylingTextPostProcessor @Inject constructor(
    private val local: TextPostProcessor,
    private val stylePreference: StylePreferencePort,
    private val llm: LlmStylePort,
) {

    /**
     * Styles [rawTranscript]. When the LLM pass actually runs, [progress]
     * is told the moment the pipeline commits to the call
     * ([SendStage.POLISHING], ENC-76) — before the network wait, so the
     * recording bar's stage label flips at the real handoff; a pass that
     * then falls back still reports it, because the stage genuinely ran.
     * Local-only sends (mechanical style) never report.
     */
    suspend fun process(
        rawTranscript: String,
        progress: SendProgressPort? = null,
    ): StyleProcessResult {
        val localText = local.process(rawTranscript)
        val style = StyleCatalog.byId(stylePreference.selectedStyleId.value)
        if (style.kind != StyleKind.LLM) {
            return StyleProcessResult(
                text = localText,
                styleId = style.id,
                llmMs = null,
                fellBack = false,
            )
        }
        progress?.onStage(SendStage.POLISHING)
        return when (val outcome = llm.style(style, localText)) {
            is LlmStyleOutcome.Styled -> StyleProcessResult(
                text = outcome.text,
                styleId = style.id,
                llmMs = outcome.llmMs,
                fellBack = false,
                model = outcome.model,
                provider = outcome.provider,
            )
            is LlmStyleOutcome.FellBack -> StyleProcessResult(
                text = localText,
                styleId = style.id,
                llmMs = null,
                fellBack = true,
                fellBackReason = outcome.reason,
                model = outcome.model,
                provider = outcome.provider,
            )
        }
    }
}
