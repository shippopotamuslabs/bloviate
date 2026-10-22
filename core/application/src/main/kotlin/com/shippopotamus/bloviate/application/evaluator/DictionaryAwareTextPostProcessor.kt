package com.shippopotamus.bloviate.application.evaluator

import com.shippopotamus.bloviate.application.port.DictionaryPort
import com.shippopotamus.bloviate.domain.evaluator.BaselineTextPostProcessor
import com.shippopotamus.bloviate.domain.evaluator.DictionaryCorrector
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import javax.inject.Inject

/**
 * The bound [TextPostProcessor] (ENC-74): the full deterministic cleanup
 * chain the send pipeline runs, in order —
 *
 * 1. [DictionaryCorrector] over the **current** dictionary: corrections
 *    ("heard as → should be"), then canonical casing. The dictionary is
 *    read from [DictionaryPort] at send time, never captured at
 *    construction, so an edit in the Dictionary tab applies to the next
 *    dictation without restarting anything. Corrections run on the raw
 *    transcript because "heard as" pairs are written against what the
 *    engine actually emits.
 * 2. [BaselineTextPostProcessor]: filler stripping, punctuation spacing,
 *    sentence-case repair — over the corrected text, so its sentence-case
 *    pass sees the final words.
 *
 * Ordering seam for what comes next: ENC-73's LLM styling appends *after*
 * this whole chain (dictionary → baseline → style), consuming this
 * processor's output; it does not interleave with the steps above.
 */
class DictionaryAwareTextPostProcessor @Inject constructor(
    private val baseline: BaselineTextPostProcessor,
    private val dictionaryPort: DictionaryPort,
    private val corrector: DictionaryCorrector,
) : TextPostProcessor {

    override fun process(rawTranscript: String): String {
        val corrected = corrector.apply(rawTranscript, dictionaryPort.dictionary.value)
        return baseline.process(corrected)
    }
}
