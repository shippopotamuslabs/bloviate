package com.shippopotamus.bloviate.domain.model

/**
 * The on-device dictation engines Bloviate can transcribe with, behind the
 * `TranscriptionEngine` seam (`contracts/TRANSCRIPTION_RULES.md` §6). Engine
 * choice is a persisted user preference (see `EnginePreferencePort`), never
 * a call-site branch: the pipeline always talks to the one bound
 * `TranscriptionEngine`, which delegates to the selected implementation.
 *
 * Ship-both design (ENC-54): [PARAKEET] is the recommended default download
 * for its 25 European languages; [WHISPER] covers the languages outside
 * Parakeet's set (99 languages) and is the proven fallback.
 *
 * The persisted default is [PARAKEET] (Sean's on-device A/B verdict,
 * 2026-10-18: Parakeet preserved the proper nouns — "Parakeet",
 * "Nantucket", "Bloviate" — that Whisper mangled). Until Parakeet's
 * ~640 MiB model is Ready on the device, the **effective** engine falls
 * back to [WHISPER] whenever Whisper's model is Ready — see
 * [EffectiveEngineResolver], the one place that resolution happens.
 */
enum class DictationEngine {

    /** whisper.cpp base.en via the shared whisper-android library. */
    WHISPER,

    /** NVIDIA Parakeet TDT 0.6B v3 (INT8 ONNX) via sherpa-onnx. */
    PARAKEET,
}
