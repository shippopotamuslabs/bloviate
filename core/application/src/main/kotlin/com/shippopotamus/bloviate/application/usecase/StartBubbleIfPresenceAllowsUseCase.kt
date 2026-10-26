package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.application.port.BubbleServiceStarterPort
import com.shippopotamus.bloviate.application.port.BubbleStartRequest
import com.shippopotamus.bloviate.application.port.OnboardingPort
import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import com.shippopotamus.bloviate.domain.evaluator.BubbleServiceStartPolicy
import javax.inject.Inject

/**
 * The outcome of one gated bubble-start attempt (BLO-189), mirroring
 * the port-outcome precedent: the refusal is a terminal outcome,
 * never a throw — the bubble simply stays down until the next
 * request.
 */
sealed interface BubbleAutoStartResult {

    /** The start was requested. */
    data object Started : BubbleAutoStartResult

    /** The setup gate or the presence invariant did not allow a start. */
    data object NotAllowed : BubbleAutoStartResult

    /** The start was allowed but the platform refused it. */
    data object StartRefused : BubbleAutoStartResult
}

/**
 * The gated bubble start (BLO-189): starts the bubble when setup is
 * finished and the resident-presence record allows it, passing the
 * caller's [request] straight to the starter. The setup gate is this
 * use case's precondition — a fresh install must meet the wizard
 * first; the presence invariant (the switch on, no explicit stop) is
 * the domain policy's ([BubbleServiceStartPolicy.shouldAutoStart]).
 * An explicit stop and an opted-out switch never resurrect: they
 * answer [BubbleAutoStartResult.NotAllowed] without touching the
 * starter. Both the boot receiver ([BubbleStartRequest.BOOT]) and the
 * in-app convergence trigger ([BubbleStartRequest.USER]) decide
 * nothing themselves — this use case is the one decision point.
 */
class StartBubbleIfPresenceAllowsUseCase @Inject constructor(
    private val onboarding: OnboardingPort,
    private val residentPresence: ResidentPresencePort,
    private val bubbleStarter: BubbleServiceStarterPort,
) {

    operator fun invoke(request: BubbleStartRequest): BubbleAutoStartResult {
        if (!onboarding.progress.value.finished) return BubbleAutoStartResult.NotAllowed
        if (!BubbleServiceStartPolicy.shouldAutoStart(residentPresence.state.value)) {
            return BubbleAutoStartResult.NotAllowed
        }
        return when (bubbleStarter.startBubble(request)) {
            BubbleServiceStartOutcome.Started -> BubbleAutoStartResult.Started
            BubbleServiceStartOutcome.Refused -> BubbleAutoStartResult.StartRefused
        }
    }
}
