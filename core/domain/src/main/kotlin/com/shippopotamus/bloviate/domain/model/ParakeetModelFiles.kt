package com.shippopotamus.bloviate.domain.model

/**
 * The verified Parakeet model set on disk: absolute paths of the four files
 * the sherpa-onnx NeMo transducer recognizer loads. Produced only by
 * `ParakeetModelStorePort.verifiedModelFiles()` after every file has passed
 * its pinned size + SHA-256 check — a partial or unverified set can never
 * reach the engine (`contracts/DATA_LAYER_RULES.md` §4).
 */
data class ParakeetModelFiles(
    val encoderPath: String,
    val decoderPath: String,
    val joinerPath: String,
    val tokensPath: String,
)
