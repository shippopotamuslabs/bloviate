package com.shippopotamus.bloviate.data

/** One pinned file of the Parakeet model set: identity, integrity, and source. */
data class ParakeetModelFile(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
)

/**
 * The pinned Parakeet TDT 0.6B v3 INT8 model set (ENC-54), verified against
 * the k2-fsa model zoo on 2026-10-18: sizes and SHA-256 digests are the
 * Hugging Face LFS values for
 * `csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (the zoo lives
 * under the sherpa-onnx author's account; the official docs link there).
 * `tokens.txt` is not LFS-hosted, so its digest was computed from the
 * downloaded file itself. Total set size: 670,478,772 bytes (~640 MiB).
 *
 * The model is NVIDIA Parakeet TDT 0.6B v3, CC-BY-4.0 (attribution in
 * `app/libs/README.md` and on the status screen's engines card).
 */
object ParakeetModelFileSet {

    const val MODEL_NAME: String = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"

    private const val RESOLVE_BASE: String =
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"

    val ENCODER: ParakeetModelFile = ParakeetModelFile(
        fileName = "encoder.int8.onnx",
        sizeBytes = 652_184_281L,
        sha256 = "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247",
        downloadUrl = "$RESOLVE_BASE/encoder.int8.onnx",
    )

    val DECODER: ParakeetModelFile = ParakeetModelFile(
        fileName = "decoder.int8.onnx",
        sizeBytes = 11_845_275L,
        sha256 = "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
        downloadUrl = "$RESOLVE_BASE/decoder.int8.onnx",
    )

    val JOINER: ParakeetModelFile = ParakeetModelFile(
        fileName = "joiner.int8.onnx",
        sizeBytes = 6_355_277L,
        sha256 = "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
        downloadUrl = "$RESOLVE_BASE/joiner.int8.onnx",
    )

    val TOKENS: ParakeetModelFile = ParakeetModelFile(
        fileName = "tokens.txt",
        sizeBytes = 93_939L,
        sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
        downloadUrl = "$RESOLVE_BASE/tokens.txt",
    )

    /** The full set, in download order. */
    val FILES: List<ParakeetModelFile> = listOf(ENCODER, DECODER, JOINER, TOKENS)

    /** Sum of every file's pinned size — the download's total and space floor. */
    val TOTAL_SIZE_BYTES: Long = FILES.sumOf { file -> file.sizeBytes }
}
