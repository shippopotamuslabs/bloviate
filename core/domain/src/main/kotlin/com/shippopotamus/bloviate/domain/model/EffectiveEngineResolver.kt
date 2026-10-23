package com.shippopotamus.bloviate.domain.model

/**
 * The ONE place the **effective** dictation engine is resolved from the
 * user's preference and both engines' model states (ENC-54 default flip).
 *
 * The rule: the preferred engine when its model is [ModelState.Ready];
 * otherwise the other engine when *its* model is Ready; otherwise `null`
 * (nothing can transcribe — the session layer reports Unavailable). An
 * explicit user choice is therefore always honored while its model is on
 * the device, and the fallback engages only while the chosen model is not
 * Ready (not downloaded yet, mid-download, or failed).
 *
 * Every consumer resolves through here so they can never disagree: the
 * bound `TranscriptionEngine`'s call-time delegation, the active model
 * store behind session availability, the status screen's fallback note,
 * and the A/B telemetry log line. Pure and framework-free — the full
 * preference × readiness matrix is unit-tested directly.
 */
object EffectiveEngineResolver {

    /**
     * The engine a dictation will actually use, or null when neither
     * engine's model is [ModelState.Ready].
     */
    fun resolve(
        preference: DictationEngine,
        whisperState: ModelState,
        parakeetState: ModelState,
    ): DictationEngine? =
        when {
            stateOf(preference, whisperState, parakeetState) is ModelState.Ready -> preference
            stateOf(otherOf(preference), whisperState, parakeetState) is ModelState.Ready ->
                otherOf(preference)
            else -> null
        }

    /** The model state of [engine] out of the two engines' states. */
    fun stateOf(
        engine: DictationEngine,
        whisperState: ModelState,
        parakeetState: ModelState,
    ): ModelState =
        when (engine) {
            DictationEngine.WHISPER -> whisperState
            DictationEngine.PARAKEET -> parakeetState
        }

    /** The engine that serves as [engine]'s fallback. */
    fun otherOf(engine: DictationEngine): DictationEngine =
        when (engine) {
            DictationEngine.WHISPER -> DictationEngine.PARAKEET
            DictationEngine.PARAKEET -> DictationEngine.WHISPER
        }
}
