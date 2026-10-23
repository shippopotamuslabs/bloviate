package com.shippopotamus.bloviate.application.evaluator

import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.evaluator.LowercaseCasualTransformer
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import javax.inject.Inject

/**
 * The bound [TextPostProcessor] (ENC-72): the full cleanup chain plus the
 * user's writing style, in order —
 *
 * 1. [DictionaryAwareTextPostProcessor]: dictionary corrections + casing,
 *    then the baseline — the delegate's output, unchanged.
 * 2. Style: the selection is read from [StylePreferencePort] at send time,
 *    never captured at construction, so a Style-tab change applies to the
 *    next dictation — and resolved through [StyleResolver] (BLO-25 S4), so
 *    custom styles apply here too. `lowercase-casual` runs
 *    [LowercaseCasualTransformer]; `normal-caps-casual` (the default) is
 *    the baseline output unchanged; every [StyleKind.LLM] style passes
 *    through unchanged in this ticket — the ENC-73 AI setup owns their
 *    transforms behind this same seam. Unknown ids fall back to the
 *    catalog default, exactly as before.
 */
class StyleAwareTextPostProcessor @Inject constructor(
    private val delegate: DictionaryAwareTextPostProcessor,
    private val stylePreference: StylePreferencePort,
    private val lowercaseCasual: LowercaseCasualTransformer,
    private val resolver: StyleResolver,
) : TextPostProcessor {

    override fun process(rawTranscript: String): String {
        val cleaned = delegate.process(rawTranscript)
        val style = resolver.resolve(stylePreference.selectedStyleId.value)
        return when (style.kind) {
            StyleKind.MECHANICAL ->
                if (style.id == LOWERCASE_CASUAL_ID) lowercaseCasual.transform(cleaned) else cleaned
            StyleKind.LLM -> cleaned
        }
    }

    private companion object {
        const val LOWERCASE_CASUAL_ID: String = "lowercase-casual"
    }
}
