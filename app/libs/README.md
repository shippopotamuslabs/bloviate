# Vendored libraries

## sherpa-onnx-1.13.8.aar

- **What:** sherpa-onnx Android AAR (Kotlin API + JNI + bundled onnxruntime),
  the runtime for the Parakeet engine (ENC-54).
- **Version:** 1.13.8 (latest stable at vendoring time).
- **Source:** official GitHub release asset —
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
- **Size:** 50,129,134 bytes.
- **SHA-256:** `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`
  (matches the digest published on the GitHub release; verified at download).
- **Why vendored:** sherpa-onnx is not published to Maven Central under an
  official coordinate (only third-party repackagings exist there), so the
  official release AAR is checked in, exactly as the upstream Android
  examples consume it. The app is arm64-v8a only (`abiFilters` in
  `app/build.gradle.kts`); the AAR's other ABIs are stripped at packaging.
- **License:** Apache-2.0 (sherpa-onnx, © the k2-fsa authors). The bundled
  onnxruntime is MIT (© Microsoft).

## Model attribution (downloaded at runtime, never committed)

- **Parakeet TDT 0.6B v3** (NVIDIA) — CC-BY-4.0. Converted to ONNX for
  sherpa-onnx by the k2-fsa model zoo
  (`csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` on Hugging Face);
  the pinned file set and digests live in `ParakeetModelFiles`.
- **Whisper base.en** (OpenAI, via the whisper.cpp project) — MIT.
