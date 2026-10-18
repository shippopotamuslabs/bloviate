package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.TranscriptSinkPort

/** Hand-written fake of [TranscriptSinkPort]; collects every delivered transcript. */
class FakeTranscriptSinkPort : TranscriptSinkPort {

    private val mutableTranscripts = mutableListOf<String>()

    /** Transcripts delivered so far, in order. */
    val transcripts: List<String> get() = mutableTranscripts.toList()

    override fun onTranscript(text: String) {
        mutableTranscripts += text
    }
}
