package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The effective-engine resolution matrix (ENC-54 default flip): the
 * preference wins while its model is Ready, the other engine serves as
 * the fallback while *its* model is Ready, and nothing resolves when
 * neither model is Ready — for every non-Ready flavor (Absent,
 * Downloading, Failed), in both preference directions.
 */
class EffectiveEngineResolverTest {

    private val notReadyStates: List<ModelState> = listOf(
        ModelState.Absent,
        ModelState.Downloading(progressFraction = 0.5f),
        ModelState.Failed("boom"),
    )

    @Test
    fun `preferred engine resolves while its model is ready, whatever the other state`() {
        for (preference in DictationEngine.entries) {
            for (otherState in notReadyStates + ModelState.Ready) {
                val resolved = resolveWith(preference, otherState)
                assertEquals(preference, resolved, "preference=$preference otherState=$otherState")
            }
        }
    }

    @Test
    fun `fallback engine resolves while the preference is not ready and the other is`() {
        for (preference in DictationEngine.entries) {
            for (preferenceState in notReadyStates) {
                val resolved = resolveWithStates(
                    preference = preference,
                    preferenceState = preferenceState,
                    otherState = ModelState.Ready,
                )
                assertEquals(
                    EffectiveEngineResolver.otherOf(preference),
                    resolved,
                    "preference=$preference preferenceState=$preferenceState",
                )
            }
        }
    }

    @Test
    fun `nothing resolves while neither model is ready`() {
        for (preference in DictationEngine.entries) {
            for (whisperState in notReadyStates) {
                for (parakeetState in notReadyStates) {
                    assertNull(
                        EffectiveEngineResolver.resolve(preference, whisperState, parakeetState),
                        "preference=$preference whisper=$whisperState parakeet=$parakeetState",
                    )
                }
            }
        }
    }

    @Test
    fun `both models ready resolves to the preference in both directions`() {
        assertEquals(
            DictationEngine.WHISPER,
            EffectiveEngineResolver.resolve(
                DictationEngine.WHISPER,
                ModelState.Ready,
                ModelState.Ready,
            ),
        )
        assertEquals(
            DictationEngine.PARAKEET,
            EffectiveEngineResolver.resolve(
                DictationEngine.PARAKEET,
                ModelState.Ready,
                ModelState.Ready,
            ),
        )
    }

    @Test
    fun `stateOf reads each engine's own state`() {
        val whisperState = ModelState.Downloading(progressFraction = 0.25f)
        val parakeetState = ModelState.Failed("nope")

        assertEquals(
            whisperState,
            EffectiveEngineResolver.stateOf(DictationEngine.WHISPER, whisperState, parakeetState),
        )
        assertEquals(
            parakeetState,
            EffectiveEngineResolver.stateOf(DictationEngine.PARAKEET, whisperState, parakeetState),
        )
    }

    @Test
    fun `otherOf swaps the two engines`() {
        assertEquals(DictationEngine.PARAKEET, EffectiveEngineResolver.otherOf(DictationEngine.WHISPER))
        assertEquals(DictationEngine.WHISPER, EffectiveEngineResolver.otherOf(DictationEngine.PARAKEET))
    }

    /** Resolves with [preference]'s model Ready and the other engine's model in [otherState]. */
    private fun resolveWith(preference: DictationEngine, otherState: ModelState): DictationEngine? =
        resolveWithStates(preference, preferenceState = ModelState.Ready, otherState = otherState)

    private fun resolveWithStates(
        preference: DictationEngine,
        preferenceState: ModelState,
        otherState: ModelState,
    ): DictationEngine? {
        val (whisperState, parakeetState) = when (preference) {
            DictationEngine.WHISPER -> preferenceState to otherState
            DictationEngine.PARAKEET -> otherState to preferenceState
        }
        return EffectiveEngineResolver.resolve(preference, whisperState, parakeetState)
    }
}
