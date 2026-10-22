package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.application.port.ModelStorePort
import com.shippopotamus.bloviate.application.port.TranscriptionEngine
import com.shippopotamus.whisper.WhisperEngine
import com.shippopotamus.whisper.WhisperParams
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TranscriptionEngine] over the whisper-android library's [WhisperEngine],
 * implementing the warm-context lifecycle of `contracts/TRANSCRIPTION_RULES.md`
 * §3: load lazily once (guarded by a [Mutex]), keep the context warm across
 * dictations — never Enclave's per-utterance init/close — and [release] only
 * on memory pressure or shutdown, after which the next call re-loads lazily.
 *
 * Every entry point serializes on the same mutex, so a [release] can never
 * interleave with an in-flight transcription on the native context.
 */
@Singleton
class OnDeviceWhisperTranscriptionEngine @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val modelStore: ModelStorePort,
) : TranscriptionEngine {

    override val mode: ProcessingMode = ProcessingMode.ON_DEVICE

    private val engineMutex = Mutex()

    override suspend fun warmUp() {
        engineMutex.withLock {
            ensureLoadedLocked()
        }
    }

    override suspend fun transcribe(samples: FloatArray): String =
        engineMutex.withLock {
            ensureLoadedLocked()
            check(whisperEngine.isLoaded()) {
                "Transcription unavailable: no verified model is loaded"
            }
            whisperEngine.transcribe(samples)
        }

    override suspend fun release() {
        engineMutex.withLock {
            if (whisperEngine.isLoaded()) {
                whisperEngine.close()
                Log.d(TAG, "Whisper context released")
            }
        }
    }

    override fun isWarm(): Boolean = whisperEngine.isLoaded()

    /** Loads the verified model once. Caller must hold [engineMutex]. */
    private suspend fun ensureLoadedLocked() {
        if (whisperEngine.isLoaded()) return
        val modelPath = modelStore.verifiedModelPath()
        if (modelPath == null) {
            Log.w(TAG, "Engine load skipped: no verified model available")
            return
        }
        Log.d(TAG, "Loading whisper model: $modelPath")
        whisperEngine.load(modelPath, WhisperParams(language = MODEL_LANGUAGE, translate = false))
        Log.d(TAG, "Whisper model loaded; context is warm")
    }

    private companion object {
        const val TAG: String = "TranscriptionEngine"
        const val MODEL_LANGUAGE: String = "en"
    }
}
