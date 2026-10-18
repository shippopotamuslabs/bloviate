package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.DictationEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted dictation-engine choice. The pipeline resolves the
 * engine from this preference at send time (`contracts/TRANSCRIPTION_RULES.md`
 * §6: engine choice is a model-manager decision, never a call-site branch).
 *
 * The default is [DictationEngine.PARAKEET] (Sean's A/B verdict, ENC-54).
 * The preference names the engine the user *wants*; the engine a dictation
 * actually uses is the **effective** engine resolved by
 * `EffectiveEngineResolver` — the preference while its model is Ready, with
 * a graceful fallback to the other engine until then.
 */
interface EnginePreferencePort {

    /** The currently selected engine; [DictationEngine.PARAKEET] until changed. */
    val selectedEngine: StateFlow<DictationEngine>

    /** Persists [engine] as the selected dictation engine. */
    suspend fun selectEngine(engine: DictationEngine)
}
