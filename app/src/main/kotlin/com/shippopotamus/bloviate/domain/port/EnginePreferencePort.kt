package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.DictationEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted dictation-engine choice. The pipeline resolves the
 * engine from this preference at send time (`contracts/TRANSCRIPTION_RULES.md`
 * §6: engine choice is a model-manager decision, never a call-site branch).
 *
 * The default is [DictationEngine.WHISPER]: Parakeet is the recommended
 * download, but its ~640 MiB model must be fetched explicitly from the status
 * screen — selecting it there is what flips this preference.
 */
interface EnginePreferencePort {

    /** The currently selected engine; [DictationEngine.WHISPER] until changed. */
    val selectedEngine: StateFlow<DictationEngine>

    /** Persists [engine] as the selected dictation engine. */
    suspend fun selectEngine(engine: DictationEngine)
}
