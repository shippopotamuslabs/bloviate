package com.shippopotamus.bloviate.testing

import com.shippopotamus.whisper.WhisperEngine
import com.shippopotamus.whisper.WhisperParams

/**
 * Hand-written fake of the library's [WhisperEngine]; counts loads/closes and
 * records transcribe calls so lifecycle tests can assert state, not mocks.
 */
class FakeWhisperEngine : WhisperEngine {

    var loadCount: Int = 0
        private set

    var closeCount: Int = 0
        private set

    var lastLoadedPath: String? = null
        private set

    var lastParams: WhisperParams? = null
        private set

    var transcript: String = "fake transcript"

    val transcribedSampleCounts: MutableList<Int> = mutableListOf()

    private var loaded: Boolean = false

    override suspend fun load(modelPath: String, params: WhisperParams) {
        loadCount++
        loaded = true
        lastLoadedPath = modelPath
        lastParams = params
    }

    override suspend fun transcribe(samples: FloatArray): String {
        check(loaded) { "Whisper context is not initialized." }
        transcribedSampleCounts += samples.size
        return transcript
    }

    override fun isLoaded(): Boolean = loaded

    override suspend fun close() {
        closeCount++
        loaded = false
    }
}
