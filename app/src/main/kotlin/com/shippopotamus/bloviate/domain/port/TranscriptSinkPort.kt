package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.InsertionResult

/**
 * The insertion boundary: where a finished, post-processed transcript
 * leaves the dictation pipeline. The implementation delivers the text into
 * the focused field paste-first (`contracts/ARCHITECTURE_RULES.md` §5.3)
 * and reports the [InsertionResult] back, so the recording bar shows Done
 * only when the text actually landed — a refused or failed insertion
 * surfaces the Error state instead.
 */
interface TranscriptSinkPort {

    /** Hands one finished transcript to insertion and reports the outcome. */
    suspend fun onTranscript(text: String): InsertionResult
}
