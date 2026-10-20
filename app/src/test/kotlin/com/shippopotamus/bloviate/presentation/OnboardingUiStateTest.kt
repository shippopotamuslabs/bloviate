package com.shippopotamus.bloviate.presentation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The wizard's step-gating derivations (ENC-67): pure functions of the
 * UI state, tested directly — each gate step passes on exactly its own
 * live condition, the model step on either engine, and the test step
 * only on the whole chain at once.
 */
class OnboardingUiStateTest {

    @Test
    fun `the welcome step is always satisfied`() {
        assertTrue(OnboardingUiState().stepSatisfied(OnboardingStepUi.WELCOME))
        assertTrue(allGatesOn().stepSatisfied(OnboardingStepUi.WELCOME))
    }

    @Test
    fun `each permission step is satisfied by exactly its own gate`() {
        val state = OnboardingUiState()

        assertFalse(state.stepSatisfied(OnboardingStepUi.WATCHER))
        assertTrue(
            state.copy(watcherEnabled = true).stepSatisfied(OnboardingStepUi.WATCHER),
        )

        assertFalse(state.stepSatisfied(OnboardingStepUi.OVERLAY))
        assertTrue(
            state.copy(overlayPermissionGranted = true)
                .stepSatisfied(OnboardingStepUi.OVERLAY),
        )

        assertFalse(state.stepSatisfied(OnboardingStepUi.MICROPHONE))
        assertTrue(
            state.copy(micPermissionGranted = true)
                .stepSatisfied(OnboardingStepUi.MICROPHONE),
        )
    }

    @Test
    fun `the model step is satisfied by either engine ready and nothing less`() {
        val state = OnboardingUiState()

        assertTrue(
            state.copy(whisperModelStatus = ModelUiStatus.Ready)
                .stepSatisfied(OnboardingStepUi.MODEL),
        )
        assertTrue(
            state.copy(parakeetModelStatus = ModelUiStatus.Ready)
                .stepSatisfied(OnboardingStepUi.MODEL),
        )
        assertFalse(
            state.copy(parakeetModelStatus = ModelUiStatus.Downloading(percent = 90))
                .stepSatisfied(OnboardingStepUi.MODEL),
        )
        assertFalse(
            state.copy(whisperModelStatus = ModelUiStatus.Failed(reason = "offline"))
                .stepSatisfied(OnboardingStepUi.MODEL),
        )
    }

    @Test
    fun `the test step needs every gate and the bubble at once`() {
        assertTrue(allGatesOn().stepSatisfied(OnboardingStepUi.TEST))

        assertFalse(allGatesOn().copy(watcherEnabled = false).stepSatisfied(OnboardingStepUi.TEST))
        assertFalse(
            allGatesOn().copy(overlayPermissionGranted = false)
                .stepSatisfied(OnboardingStepUi.TEST),
        )
        assertFalse(
            allGatesOn().copy(micPermissionGranted = false)
                .stepSatisfied(OnboardingStepUi.TEST),
        )
        assertFalse(
            allGatesOn().copy(parakeetModelStatus = ModelUiStatus.Absent)
                .stepSatisfied(OnboardingStepUi.TEST),
        )
        assertFalse(
            allGatesOn().copy(bubbleServiceRunning = false)
                .stepSatisfied(OnboardingStepUi.TEST),
        )
    }

    @Test
    fun `the landed signal is any non-blank scratch text`() {
        assertFalse(OnboardingUiState().testDictationLanded())
        assertFalse(OnboardingUiState(testFieldText = "   ").testDictationLanded())
        assertTrue(OnboardingUiState(testFieldText = "hello").testDictationLanded())
    }

    private fun allGatesOn(): OnboardingUiState = OnboardingUiState(
        watcherEnabled = true,
        overlayPermissionGranted = true,
        micPermissionGranted = true,
        bubbleServiceRunning = true,
        parakeetModelStatus = ModelUiStatus.Ready,
    )
}
