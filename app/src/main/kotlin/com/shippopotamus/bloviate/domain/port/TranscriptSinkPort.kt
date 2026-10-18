package com.shippopotamus.bloviate.domain.port

/**
 * The insertion boundary: where a finished transcript leaves the dictation
 * pipeline. ENC-61 ships a logging implementation so transcription can be
 * verified in logcat; ENC-62 replaces it with paste-first insertion into the
 * focused field (`contracts/ARCHITECTURE_RULES.md` §5.3) behind this same
 * port.
 */
interface TranscriptSinkPort {

    /** Hands one finished transcript to insertion. */
    fun onTranscript(text: String)
}
