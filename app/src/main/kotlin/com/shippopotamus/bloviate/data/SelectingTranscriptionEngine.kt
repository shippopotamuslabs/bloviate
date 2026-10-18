package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one bound [TranscriptionEngine]: delegates every call to the engine
 * the user selected in [EnginePreferencePort], resolved **at call time**, so
 * flipping the status-screen toggle takes effect on the next dictation with
 * no re-wiring (`contracts/TRANSCRIPTION_RULES.md` §6 — engine choice is a
 * model-manager decision, never a call-site branch).
 *
 * [release] releases **both** engines: under memory pressure, whichever
 * context is warm must go, even if it is not the selected one. [isWarm]
 * reports the selected engine — that is the one the overlay service warms.
 */
@Singleton
class SelectingTranscriptionEngine @Inject constructor(
    private val whisperEngine: OnDeviceWhisperTranscriptionEngine,
    private val parakeetEngine: ParakeetTranscriptionEngine,
    private val enginePreference: EnginePreferencePort,
) : TranscriptionEngine {

    override val mode: ProcessingMode = ProcessingMode.ON_DEVICE

    private fun selected(): TranscriptionEngine =
        when (enginePreference.selectedEngine.value) {
            DictationEngine.WHISPER -> whisperEngine
            DictationEngine.PARAKEET -> parakeetEngine
        }

    override suspend fun warmUp() {
        selected().warmUp()
    }

    override suspend fun transcribe(samples: FloatArray): String = selected().transcribe(samples)

    override suspend fun release() {
        whisperEngine.release()
        parakeetEngine.release()
    }

    override fun isWarm(): Boolean = selected().isWarm()
}
