package com.shippopotamus.bloviate.domain.port

/**
 * The stages of the send pipeline after the recording stops (ENC-76). The
 * recording bar names the stage it is in — the two stages have very
 * different latencies, and a bare spinner cannot tell them apart.
 */
enum class SendStage {

    /** Stage 1: the on-device engine is transcribing the utterance. */
    TRANSCRIBING,

    /**
     * Stage 2: the LLM style pass (ENC-73) is running. Only ever reported
     * when the pass actually runs — an LLM style is selected and a key is
     * stored; local-only sends never leave [TRANSCRIBING].
     */
    POLISHING,
}

/**
 * Progress sink for send-pipeline stage transitions, owned by the pipeline
 * and consumed by the overlay service: the styling stage reports
 * [SendStage.POLISHING] through this port at the moment it commits to the
 * LLM call, and the recording controller flips the bar's label to match.
 * Insertion itself is instant and reports no stage.
 */
fun interface SendProgressPort {

    /** The pipeline has entered [stage]. */
    fun onStage(stage: SendStage)
}
