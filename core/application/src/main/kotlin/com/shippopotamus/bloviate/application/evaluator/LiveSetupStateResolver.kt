package com.shippopotamus.bloviate.application.evaluator

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.BubbleAppearancePort
import com.shippopotamus.bloviate.application.port.BubbleServiceStatePort
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import com.shippopotamus.bloviate.application.port.LiveSetupState
import com.shippopotamus.bloviate.application.port.ModelStorePort
import com.shippopotamus.bloviate.application.port.OnboardingPort
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import com.shippopotamus.bloviate.application.port.WatcherConnectionPort
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.EffectiveEngineResolver
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines the nine durable streams behind the Setup screen and the
 * first-run wizard into one [LiveSetupState] (BLO-175), so the two
 * screens observe a single state instead of each composing the same
 * streams in the ViewModel (ARCHITECTURE_RULES 3.4). A singleton
 * sharing one [state] flow, mirroring [StyleResolver]: the sharing
 * comes from the singleton, so every consumer sees the same snapshot,
 * and the initial value is the ports' current snapshot — neither
 * screen ever renders a defaults flash. The engine-fallback derivation
 * ([EffectiveEngineResolver]) also lives here, behind the read model;
 * the screens keep only the map to their fallback note.
 */
@Singleton
class LiveSetupStateResolver @Inject constructor(
    private val watcherConnection: WatcherConnectionPort,
    private val bubbleServiceState: BubbleServiceStatePort,
    private val whisperStore: ModelStorePort,
    private val parakeetStore: ParakeetModelStorePort,
    private val enginePreference: EnginePreferencePort,
    private val bubbleAppearance: BubbleAppearancePort,
    private val residentPresence: ResidentPresencePort,
    private val authSession: AuthSessionPort,
    private val onboarding: OnboardingPort,
    scope: CoroutineScope,
) {

    /** Watcher connection + bubble running: the two service-liveness signals. */
    private data class ServiceSignals(
        val watcherConnected: Boolean,
        val bubbleServiceRunning: Boolean,
    )

    /** Engine preference plus both model states: everything the fallback derivation reads. */
    private data class EngineSignals(
        val selectedEngine: DictationEngine,
        val whisperModelState: ModelState,
        val parakeetModelState: ModelState,
    )

    /** Auth gate plus wizard progress: the account-owned durable pair. */
    private data class AccountSignals(
        val authState: AuthState,
        val onboardingProgress: OnboardingProgress,
    )

    private val serviceSignals = combine(
        watcherConnection.isConnected,
        bubbleServiceState.isServiceRunning,
        ::ServiceSignals,
    )

    private val engineSignals = combine(
        enginePreference.selectedEngine,
        whisperStore.state,
        parakeetStore.state,
        ::EngineSignals,
    )

    private val accountSignals = combine(
        authSession.authState,
        onboarding.progress,
        ::AccountSignals,
    )

    /** The combined durable state, current at [snapshot] and live from then on. */
    val state: StateFlow<LiveSetupState> = combine(
        serviceSignals,
        engineSignals,
        bubbleAppearance.appearance,
        residentPresence.state,
        accountSignals,
    ) { services, engines, appearance, presence, account ->
        assemble(services, engines, appearance, presence, account)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = snapshot(),
    )

    /** The ports' current values assembled exactly as the live flow assembles them. */
    private fun snapshot(): LiveSetupState = assemble(
        services = ServiceSignals(
            watcherConnected = watcherConnection.isConnected.value,
            bubbleServiceRunning = bubbleServiceState.isServiceRunning.value,
        ),
        engines = EngineSignals(
            selectedEngine = enginePreference.selectedEngine.value,
            whisperModelState = whisperStore.state.value,
            parakeetModelState = parakeetStore.state.value,
        ),
        appearance = bubbleAppearance.appearance.value,
        presence = residentPresence.state.value,
        account = AccountSignals(
            authState = authSession.authState.value,
            onboardingProgress = onboarding.progress.value,
        ),
    )

    private fun assemble(
        services: ServiceSignals,
        engines: EngineSignals,
        appearance: BubbleAppearance,
        presence: ResidentPresenceState,
        account: AccountSignals,
    ): LiveSetupState = LiveSetupState(
        watcherConnected = services.watcherConnected,
        bubbleServiceRunning = services.bubbleServiceRunning,
        whisperModelState = engines.whisperModelState,
        parakeetModelState = engines.parakeetModelState,
        selectedEngine = engines.selectedEngine,
        effectiveEngine = EffectiveEngineResolver.resolve(
            engines.selectedEngine,
            engines.whisperModelState,
            engines.parakeetModelState,
        ),
        bubbleAppearance = appearance,
        residentPresence = presence,
        authState = account.authState,
        onboardingProgress = account.onboardingProgress,
    )
}
