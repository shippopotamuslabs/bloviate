package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.application.port.BubbleStartRequest
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.OnboardingStep
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStarterPort
import com.shippopotamus.bloviate.testing.FakeOnboardingPort
import com.shippopotamus.bloviate.testing.FakeResidentPresencePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The gated bubble start (BLO-189): setup gates the start, the
 * presence record gates it next, and only an allowed start reaches
 * the starter — with the caller's request passed through untouched.
 */
class StartBubbleIfPresenceAllowsUseCaseTest {

    private lateinit var onboarding: FakeOnboardingPort
    private lateinit var residentPresence: FakeResidentPresencePort
    private lateinit var starter: FakeBubbleServiceStarterPort
    private lateinit var useCase: StartBubbleIfPresenceAllowsUseCase

    @BeforeEach
    fun setup() {
        onboarding = FakeOnboardingPort()
        residentPresence = FakeResidentPresencePort()
        starter = FakeBubbleServiceStarterPort()
        onboarding.publish(OnboardingProgress(step = OnboardingStep.WELCOME, finished = true))
        useCase = StartBubbleIfPresenceAllowsUseCase(
            onboarding = onboarding,
            residentPresence = residentPresence,
            bubbleStarter = starter,
        )
    }

    @Test
    fun `unfinished setup answers NotAllowed without touching the starter`() {
        onboarding.publish(OnboardingProgress(step = OnboardingStep.WELCOME, finished = false))

        assertEquals(BubbleAutoStartResult.NotAllowed, useCase(BubbleStartRequest.BOOT))

        assertTrue(starter.requestedRequests.isEmpty())
    }

    @Test
    fun `a user stop answers NotAllowed without touching the starter`() {
        residentPresence.publish(ResidentPresenceState(autoStartEnabled = true, userStopped = true))

        assertEquals(BubbleAutoStartResult.NotAllowed, useCase(BubbleStartRequest.BOOT))

        assertTrue(starter.requestedRequests.isEmpty())
    }

    @Test
    fun `an opted-out switch answers NotAllowed without touching the starter`() {
        residentPresence.publish(ResidentPresenceState(autoStartEnabled = false, userStopped = false))

        assertEquals(BubbleAutoStartResult.NotAllowed, useCase(BubbleStartRequest.USER))

        assertTrue(starter.requestedRequests.isEmpty())
    }

    @Test
    fun `an allowed boot re-arm requests the boot start and answers Started`() {
        assertEquals(BubbleAutoStartResult.Started, useCase(BubbleStartRequest.BOOT))

        assertEquals(listOf(BubbleStartRequest.BOOT), starter.requestedRequests)
    }

    @Test
    fun `an allowed user start passes the user request through untouched`() {
        assertEquals(BubbleAutoStartResult.Started, useCase(BubbleStartRequest.USER))

        assertEquals(listOf(BubbleStartRequest.USER), starter.requestedRequests)
    }

    @Test
    fun `a refused platform start answers StartRefused`() {
        starter.outcome = BubbleServiceStartOutcome.Refused

        assertEquals(BubbleAutoStartResult.StartRefused, useCase(BubbleStartRequest.BOOT))

        assertEquals(listOf(BubbleStartRequest.BOOT), starter.requestedRequests)
    }
}
