package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState

/**
 * The durable live state the Setup screen and the first-run wizard both
 * read (BLO-175): one snapshot of every long-lived stream behind the
 * two screens, combined in the application layer by the
 * `LiveSetupStateResolver` and observed through
 * `ObserveLiveSetupStateUseCase` — the single observing use case both
 * ViewModels fold their local transient state into (ARCHITECTURE_RULES
 * 3.4). Permission grants are absent on purpose: they are one-shot
 * settings reads refreshed on resume, so they stay ViewModel-local.
 *
 * [effectiveEngine] is the domain `EffectiveEngineResolver`'s verdict
 * over [selectedEngine] and the two model states — the engine a
 * dictation will actually use, or null while neither model is ready.
 * Each screen maps it to its fallback note; the resolver itself stays
 * in the domain.
 */
data class LiveSetupState(
    val watcherConnected: Boolean,
    val bubbleServiceRunning: Boolean,
    val whisperModelState: ModelState,
    val parakeetModelState: ModelState,
    val selectedEngine: DictationEngine,
    val effectiveEngine: DictationEngine?,
    val bubbleAppearance: BubbleAppearance,
    val residentPresence: ResidentPresenceState,
    val authState: AuthState,
    val onboardingProgress: OnboardingProgress,
)
