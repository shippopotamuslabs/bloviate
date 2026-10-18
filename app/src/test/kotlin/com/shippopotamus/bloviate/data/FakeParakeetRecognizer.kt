package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.ParakeetModelFiles

/**
 * Hand-written fake of [ParakeetRecognizer] (the sherpa-onnx boundary);
 * records calls so lifecycle tests can assert state, not mocks. Lives in the
 * `data` package because the interface it fakes is internal to it.
 */
class FakeParakeetRecognizer : ParakeetRecognizer {

    var nextTranscript: String = "fake parakeet transcript"

    var transcribeCallCount: Int = 0
        private set

    var closeCount: Int = 0
        private set

    var lastSamplesSize: Int = -1
        private set

    override fun transcribe(samples: FloatArray): String {
        transcribeCallCount++
        lastSamplesSize = samples.size
        return nextTranscript
    }

    override fun close() {
        closeCount++
    }
}

/** Records every model-files set a recognizer was created for, handing out fakes. */
class FakeParakeetRecognizerFactory {

    val createdWith: MutableList<ParakeetModelFiles> = mutableListOf()

    val recognizers: MutableList<FakeParakeetRecognizer> = mutableListOf()

    /** Transcript every created recognizer returns. */
    var nextTranscript: String = "fake parakeet transcript"

    fun create(modelFiles: ParakeetModelFiles): ParakeetRecognizer {
        createdWith += modelFiles
        val recognizer = FakeParakeetRecognizer()
        recognizer.nextTranscript = nextTranscript
        recognizers += recognizer
        return recognizer
    }
}
