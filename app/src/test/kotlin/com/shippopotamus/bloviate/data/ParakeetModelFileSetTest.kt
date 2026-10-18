package com.shippopotamus.bloviate.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pinned Parakeet file set is the integrity root for a ~640 MiB
 * download: names, sizes, digests, and URLs must stay exactly the values
 * verified against the k2-fsa model zoo (ENC-54, 2026-10-18). If upstream
 * re-publishes the model, this test fails on purpose — re-verify every
 * value against Hugging Face before updating a single constant.
 */
class ParakeetModelFileSetTest {

    @Test
    fun `the set is exactly the four recognizer files`() {
        assertEquals(
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            ParakeetModelFileSet.FILES.map { file -> file.fileName },
        )
    }

    @Test
    fun `pinned sizes and digests match the verified upstream values`() {
        val expected = mapOf(
            "encoder.int8.onnx" to (652_184_281L to "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
            "decoder.int8.onnx" to (11_845_275L to "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            "joiner.int8.onnx" to (6_355_277L to "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            "tokens.txt" to (93_939L to "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
        )
        for (file in ParakeetModelFileSet.FILES) {
            val (size, sha256) = expected.getValue(file.fileName)
            assertEquals(size, file.sizeBytes, "size of ${file.fileName}")
            assertEquals(sha256, file.sha256, "SHA-256 of ${file.fileName}")
        }
        assertEquals(670_478_772L, ParakeetModelFileSet.TOTAL_SIZE_BYTES)
    }

    @Test
    fun `every digest is a well-formed sha-256 and every url is the hf resolve url`() {
        val sha256Pattern = Regex("[0-9a-f]{64}")
        for (file in ParakeetModelFileSet.FILES) {
            assertTrue(sha256Pattern.matches(file.sha256), "digest of ${file.fileName}")
            assertEquals(
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8" +
                    "/resolve/main/${file.fileName}",
                file.downloadUrl,
            )
            assertTrue(file.sizeBytes > 0, "size of ${file.fileName}")
        }
    }
}
