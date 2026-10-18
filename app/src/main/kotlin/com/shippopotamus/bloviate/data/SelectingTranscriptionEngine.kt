package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import com.shippopotamus.bloviate.domain.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one bound [TranscriptionEngine]: delegates every call to the
 * **effective** engine, resolved **at call time** through
 * [EffectiveEngineResolver] — the preference while its model is Ready,
 * otherwise the other engine while *its* model is Ready — so flipping the
 * status-screen toggle (or a model finishing its download) takes effect on
 * the next dictation with no re-wiring (`contracts/TRANSCRIPTION_RULES.md`
 * §6 — engine choice is a model-manager decision, never a call-site
 * branch). The session layer gates dictation on the same resolution (via
 * [SelectedModelStore]), so the engine delegated to here is always the one
 * availability promised.
 *
 * When neither model is Ready the session is Unavailable and no call
 * should arrive; delegation then targets the preferred engine, whose own
 * load gate produces the failure — the pre-fallback behavior.
 *
 * [release] releases **both** engines: under memory pressure, whichever
 * context is warm must go, even if it is not the effective one. [isWarm]
 * reports the effective engine — that is the one the overlay service warms.
 */
@Singleton
class SelectingTranscriptionEngine @Inject constructor(
    private val whisperEngine: OnDeviceWhisperTranscriptionEngine,
    private val parakeetEngine: ParakeetTranscriptionEngine,
    private val enginePreference: EnginePreferencePort,
    private val whisperStore: ModelStorePort,
    private val parakeetStore: ParakeetModelStorePort,
) : TranscriptionEngine {

    override val mode: ProcessingMode = ProcessingMode.ON_DEVICE

    private fun selected(): TranscriptionEngine =
        when (effectiveEngine()) {
            DictationEngine.WHISPER -> whisperEngine
            DictationEngine.PARAKEET -> parakeetEngine
        }

    /** The engine the next call delegates to: effective, or the preference while nothing is Ready. */
    private fun effectiveEngine(): DictationEngine =
        EffectiveEngineResolver.resolve(
            preference = enginePreference.selectedEngine.value,
            whisperState = whisperStore.state.value,
            parakeetState = parakeetStore.state.value,
        ) ?: enginePreference.selectedEngine.value

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
