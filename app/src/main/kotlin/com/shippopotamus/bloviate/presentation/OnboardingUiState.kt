package com.shippopotamus.bloviate.presentation

import com.shippopotamus.bloviate.domain.model.OnboardingStep

/** Presentation-only mapping of the wizard's steps (ENC-67; battery step added by ENC-129). */
enum class OnboardingStepUi {
    WELCOME,
    WATCHER,
    OVERLAY,
    MICROPHONE,
    BATTERY,
    MODEL,
    TEST,
}

internal fun OnboardingStep.toUi(): OnboardingStepUi =
    when (this) {
        OnboardingStep.WELCOME -> OnboardingStepUi.WELCOME
        OnboardingStep.WATCHER -> OnboardingStepUi.WATCHER
        OnboardingStep.OVERLAY -> OnboardingStepUi.OVERLAY
        OnboardingStep.MICROPHONE -> OnboardingStepUi.MICROPHONE
        OnboardingStep.BATTERY -> OnboardingStepUi.BATTERY
        OnboardingStep.MODEL -> OnboardingStepUi.MODEL
        OnboardingStep.TEST -> OnboardingStepUi.TEST
    }

internal fun OnboardingStepUi.toDomain(): OnboardingStep =
    when (this) {
        OnboardingStepUi.WELCOME -> OnboardingStep.WELCOME
        OnboardingStepUi.WATCHER -> OnboardingStep.WATCHER
        OnboardingStepUi.OVERLAY -> OnboardingStep.OVERLAY
        OnboardingStepUi.MICROPHONE -> OnboardingStep.MICROPHONE
        OnboardingStepUi.BATTERY -> OnboardingStep.BATTERY
        OnboardingStepUi.MODEL -> OnboardingStep.MODEL
        OnboardingStepUi.TEST -> OnboardingStep.TEST
    }

/**
 * The first-run wizard's state (ENC-67): the persisted resume step and
 * visibility, plus the LIVE state of every gate the steps check — the
 * same live reads the Setup screen shows, so a step's chip can never
 * disagree with the dashboard. Carries no domain types and never any
 * field content; [testFieldText] is the wizard's own scratch field,
 * typed (or dictated) by the user in the wizard itself.
 */
data class OnboardingUiState(
    val showWizard: Boolean = true,
    val step: OnboardingStepUi = OnboardingStepUi.WELCOME,
    val watcherEnabled: Boolean = false,

    /** Whether the watcher service is connected right now (ENC-129 supervision). */
    val watcherConnected: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val micPermissionGranted: Boolean = false,

    /** Whether battery optimization leaves Bloviate alone (ENC-129). */
    val batteryExempt: Boolean = false,
    val bubbleServiceRunning: Boolean = false,
    val whisperModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val parakeetModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val selectedEngine: DictationEngineUi = DictationEngineUi.PARAKEET,
    val engineFallback: EngineFallbackUi? = null,
    val testFieldText: String = "",
)

/** The model gate: dictation works when either engine's model is on the device. */
fun OnboardingUiState.modelReady(): Boolean =
    whisperModelStatus is ModelUiStatus.Ready || parakeetModelStatus is ModelUiStatus.Ready

/**
 * Whether [step]'s gate is satisfied by the live state right now. The
 * welcome step has no gate and always passes; the test step's gate is
 * the whole dictation chain working — every dictation gate plus the
 * bubble running — which is what makes its success chip mean
 * something. The watcher gate is supervised (ENC-129): enabled *and*
 * connected, the same verdict the Setup screen shows. The battery
 * step is a presence gate, not a dictation gate — dictation works
 * without it — so it is not part of the test step's chain. Pure
 * functions of view state, like [setupStatus], so the wizard's chips
 * and its navigation derive from one place.
 */
fun OnboardingUiState.stepSatisfied(step: OnboardingStepUi): Boolean =
    when (step) {
        OnboardingStepUi.WELCOME -> true
        OnboardingStepUi.WATCHER -> watcherEnabled && watcherConnected
        OnboardingStepUi.OVERLAY -> overlayPermissionGranted
        OnboardingStepUi.MICROPHONE -> micPermissionGranted
        OnboardingStepUi.BATTERY -> batteryExempt
        OnboardingStepUi.MODEL -> modelReady()
        OnboardingStepUi.TEST ->
            watcherEnabled && watcherConnected && overlayPermissionGranted &&
                micPermissionGranted && modelReady() && bubbleServiceRunning
    }

/** Whether the current step's gate passes live. */
fun OnboardingUiState.currentStepSatisfied(): Boolean = stepSatisfied(step)

/**
 * The guided test's success signal: the user's words landed in the
 * scratch field, typed or dictated. Drives the test step's success
 * chip; finishing never depends on it — the wizard never traps.
 */
fun OnboardingUiState.testDictationLanded(): Boolean = testFieldText.isNotBlank()
