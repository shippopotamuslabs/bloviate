package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.TextPostProcessor

/**
 * Hand-written fake of [TextPostProcessor]. [transform] defaults to the
 * identity so pipeline tests see the raw transcript unless they script a
 * cleanup; every input is recorded in [inputs].
 */
class FakeTextPostProcessor : TextPostProcessor {

    var transform: (String) -> String = { it }

    private val mutableInputs = mutableListOf<String>()

    /** Raw transcripts passed to [process] so far, in order. */
    val inputs: List<String> get() = mutableInputs.toList()

    override fun process(rawTranscript: String): String {
        mutableInputs += rawTranscript
        return transform(rawTranscript)
    }
}
