package com.shippopotamus.bloviate.service

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.IntSize
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * ENC-93: bar motion is per-transition. The recording-start expansion
 * (bubble → Recording) must SNAP — Sean's acceptance test measured
 * ~3 s on ENC-89's calm spring, which the expansion had inherited
 * from the shared root — while the Listening → Processing settle and
 * the transient bubbles keep that calm spring. These pins assert the
 * state → spec mapping and the spring constants, plus the expansion's
 * settle envelope: for a no-bounce spring released from rest, the
 * remaining-displacement fraction after t seconds is exactly
 * (1 + ωt)·e^(−ωt) with ω = √stiffness — the standard settle proxy.
 */
class BarSizeAnimationSpecTest {

    @Test
    fun `the recording expansion uses the snap spring`() {
        val spec = RecordingBarState.Recording(elapsedMs = 0L).sizeSpec()

        assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio, EXACT)
        assertEquals(Spring.StiffnessHigh, spec.stiffness, EXACT)
    }

    @Test
    fun `the expansion spring is settled well inside half a second`() {
        val spec = RecordingBarState.Recording(elapsedMs = 0L).sizeSpec()

        assertTrue(
            noBounceEnvelope(spec.stiffness, QUARTER_SECOND) < SETTLED_FRACTION,
            "expansion still travelling at 250ms: ${noBounceEnvelope(spec.stiffness, QUARTER_SECOND)}",
        )
        assertTrue(noBounceEnvelope(spec.stiffness, HALF_SECOND) < SETTLED_FRACTION)
    }

    @Test
    fun `the processing states keep the calm settle spring`() {
        listOf(RecordingBarState.Transcribing, RecordingBarState.Polishing).forEach { state ->
            val spec = state.sizeSpec()

            assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio, EXACT)
            assertEquals(Spring.StiffnessMediumLow, spec.stiffness, EXACT)
        }
    }

    @Test
    fun `idle done and error keep the calm spring`() {
        listOf(RecordingBarState.Idle, RecordingBarState.Done, RecordingBarState.Error).forEach { state ->
            assertEquals(Spring.StiffnessMediumLow, state.sizeSpec().stiffness, EXACT)
        }
    }

    @Test
    fun `the calm spring is still travelling at a quarter second`() {
        // The discriminator between the two specs: where the snap is
        // already settled, the calm settle (ENC-89's feel) is not —
        // if this ever flips, the two transitions have traded places.
        val calm = RecordingBarState.Transcribing.sizeSpec()

        assertTrue(noBounceEnvelope(calm.stiffness, QUARTER_SECOND) > SETTLED_FRACTION)
    }

    @Test
    fun `exactly two specs cover every state`() {
        // The whole motion story is two springs: the snap and the
        // calm settle. A third spec appearing means a transition has
        // drifted from the ENC-93 split.
        val states = listOf(
            RecordingBarState.Idle,
            RecordingBarState.Recording(elapsedMs = 0L),
            RecordingBarState.Transcribing,
            RecordingBarState.Polishing,
            RecordingBarState.Done,
            RecordingBarState.Error,
        )
        val distinctSpecs = states.map { state -> state.sizeSpec() }.distinctBy { spec -> spec.stiffness }

        assertEquals(2, distinctSpecs.size)
    }

    private fun RecordingBarState.sizeSpec(): SpringSpec<IntSize> =
        sizeAnimationSpec() as SpringSpec<IntSize>

    private companion object {
        const val EXACT: Float = 0f
        const val QUARTER_SECOND: Double = 0.25
        const val HALF_SECOND: Double = 0.5

        /** Under 1% of the travel remaining counts as settled. */
        const val SETTLED_FRACTION: Double = 0.01

        /** Remaining-displacement fraction of a no-bounce (ζ = 1) spring from rest. */
        fun noBounceEnvelope(stiffness: Float, seconds: Double): Double {
            val omega = sqrt(stiffness.toDouble())
            return (1.0 + omega * seconds) * exp(-omega * seconds)
        }
    }
}
