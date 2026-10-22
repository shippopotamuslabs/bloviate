package com.shippopotamus.bloviate.service

import android.os.SystemClock
import android.util.Log
import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.application.port.TextInsertionPort
import com.shippopotamus.bloviate.application.port.TranscriptSinkPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TranscriptSinkPort] backed by real insertion (ENC-62): delegates to
 * [TextInsertionPort] and reports the outcome back to the recording
 * controller, which shows Done only for [InsertionResult.Inserted].
 * Outcomes are logged (never the transcript text) with the insertion-side
 * latency — one half of the Phase 2 stop→insert metric.
 */
@Singleton
class InsertionTranscriptSink @Inject constructor(
    private val insertion: TextInsertionPort,
) : TranscriptSinkPort {

    override suspend fun onTranscript(text: String): InsertionResult {
        val startedAtMs = SystemClock.elapsedRealtime()
        val result = insertion.insert(text)
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        when (result) {
            is InsertionResult.Inserted ->
                Log.d(TAG, "Transcript inserted via ${result.method} (insertMs=$elapsedMs)")
            is InsertionResult.Refused ->
                Log.w(TAG, "Transcript insertion refused: ${result.reason} (insertMs=$elapsedMs)")
            is InsertionResult.Failed ->
                Log.w(TAG, "Transcript insertion failed: ${result.detail} (insertMs=$elapsedMs)")
        }
        return result
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
    }
}
