package com.shippopotamus.bloviate.data

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.shippopotamus.bloviate.domain.model.ParakeetModelFiles
import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.application.port.TranscriptionEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The native recognizer behind [ParakeetTranscriptionEngine]: creates a
 * sherpa-onnx offline recognizer over the verified model files, transcribes
 * one utterance, and frees the native handles on [close]. An interface so
 * the engine's warm lifecycle is unit-testable with a fake — the ONNX
 * runtime itself has no JVM oracle and is device-verified only.
 */
interface ParakeetRecognizer {

    /** Transcribes one whole utterance of 16 kHz mono PCM floats in a single pass. */
    fun transcribe(samples: FloatArray): String

    /** Frees the native recognizer. Safe to call more than once. */
    fun close()
}

/**
 * [ParakeetRecognizer] over sherpa-onnx's [OfflineRecognizer] with the NeMo
 * transducer config for Parakeet TDT (`modelType = "nemo_transducer"`, per
 * the upstream model-zoo docs). Input is the same 16 kHz FloatArray the
 * session layer already produces — the spike transcribes whole utterances
 * in one pass, exactly like the whisper engine; sherpa's streaming
 * recognizer is future headroom, not wired here.
 */
class SherpaParakeetRecognizer(
    modelFiles: ParakeetModelFiles,
    numThreads: Int,
) : ParakeetRecognizer {

    private val recognizer: OfflineRecognizer = OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE_HZ,
                featureDim = FEATURE_DIM,
            ),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = modelFiles.encoderPath,
                    decoder = modelFiles.decoderPath,
                    joiner = modelFiles.joinerPath,
                ),
                tokens = modelFiles.tokensPath,
                numThreads = numThreads,
                modelType = MODEL_TYPE_NEMO_TRANSDUCER,
            ),
        ),
    )

    @Volatile
    private var closed: Boolean = false

    override fun transcribe(samples: FloatArray): String {
        check(!closed) { "Recognizer is closed" }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE_HZ)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            recognizer.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ: Int = 16_000
        const val FEATURE_DIM: Int = 80
        const val MODEL_TYPE_NEMO_TRANSDUCER: String = "nemo_transducer"
    }
}

/**
 * [TranscriptionEngine] over sherpa-onnx running Parakeet TDT 0.6B v3,
 * mirroring the whisper engine's warm lifecycle
 * (`contracts/TRANSCRIPTION_RULES.md` §3): the recognizer is created lazily
 * once behind a [Mutex], stays warm across dictations, and [release] frees
 * it under memory pressure, after which the next call re-creates it lazily.
 * Every entry point serializes on the same mutex — the native recognizer is
 * not thread-safe.
 */
@Singleton
class ParakeetTranscriptionEngine @Inject constructor(
    private val modelStore: ParakeetModelStorePort,
) : TranscriptionEngine {

    override val mode: ProcessingMode = ProcessingMode.ON_DEVICE

    private val engineMutex = Mutex()

    private var recognizer: ParakeetRecognizer? = null

    /** Test seam: how recognizers are created. Production uses sherpa-onnx. */
    internal var recognizerFactory: (ParakeetModelFiles) -> ParakeetRecognizer =
        { modelFiles -> SherpaParakeetRecognizer(modelFiles, numThreads = defaultNumThreads()) }

    override suspend fun warmUp() {
        engineMutex.withLock {
            ensureLoadedLocked()
        }
    }

    override suspend fun transcribe(samples: FloatArray): String =
        engineMutex.withLock {
            ensureLoadedLocked()
            val active = recognizer
            check(active != null) {
                "Transcription unavailable: no verified Parakeet model is loaded"
            }
            active.transcribe(samples)
        }

    override suspend fun release() {
        engineMutex.withLock {
            recognizer?.close()
            recognizer = null
            Log.d(TAG, "Parakeet recognizer released")
        }
    }

    override fun isWarm(): Boolean = recognizer != null

    /** Creates the recognizer from the verified model set once. Caller must hold [engineMutex]. */
    private suspend fun ensureLoadedLocked() {
        if (recognizer != null) return
        val modelFiles = modelStore.verifiedModelFiles()
        if (modelFiles == null) {
            Log.w(TAG, "Engine load skipped: no verified Parakeet model set available")
            return
        }
        Log.d(TAG, "Creating Parakeet recognizer from ${modelFiles.encoderPath.substringBeforeLast('/')}")
        recognizer = recognizerFactory(modelFiles)
        if (!startupLogged) {
            startupLogged = true
            Log.i(
                TAG,
                "Parakeet engine ready: sherpa-onnx $SHERPA_ONNX_VERSION, " +
                    "model ${ParakeetModelFileSet.MODEL_NAME} (NVIDIA Parakeet TDT 0.6B v3, CC-BY-4.0)",
            )
        }
        Log.d(TAG, "Parakeet recognizer created; context is warm")
    }

    companion object {
        const val TAG: String = "ParakeetEngine"

        /** Vendored AAR version (see `app/libs/README.md`); named in the one-time startup log line. */
        const val SHERPA_ONNX_VERSION: String = "1.13.8"

        @Volatile
        private var startupLogged: Boolean = false

        /** Same thread budget as the whisper library default: processors/2, capped at 4. */
        internal fun defaultNumThreads(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    }
}
