package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.domain.port.TranscriptSinkPort

/**
 * Hand-written fake of [TranscriptSinkPort]; collects every delivered
 * transcript and reports [nextResult] (or the head of [resultQueue], when
 * scripted) back to the caller.
 */
class FakeTranscriptSinkPort : TranscriptSinkPort {

    private val mutableTranscripts = mutableListOf<String>()

    /** Transcripts delivered so far, in order. */
    val transcripts: List<String> get() = mutableTranscripts.toList()

    /** Result reported when [resultQueue] is empty. Defaults to a successful paste. */
    var nextResult: InsertionResult =
        InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE)

    /** Scripted results, consumed one per delivery before [nextResult] is used. */
    val resultQueue: ArrayDeque<InsertionResult> = ArrayDeque()

    override suspend fun onTranscript(text: String): InsertionResult {
        mutableTranscripts += text
        return if (resultQueue.isEmpty()) nextResult else resultQueue.removeFirst()
    }
}
