package com.shippopotamus.bloviate.service

import android.util.Log
import com.shippopotamus.bloviate.domain.port.TranscriptSinkPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ENC-61 [TranscriptSinkPort]: the transcript is logged so Sean can verify
 * transcription end to end in logcat. ENC-62 replaces this binding with
 * paste-first insertion into the focused field.
 */
@Singleton
class LoggingTranscriptSink @Inject constructor() : TranscriptSinkPort {

    override fun onTranscript(text: String) {
        Log.d(TAG, "Transcript ready (insertion lands with ENC-62): \"$text\"")
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
    }
}
