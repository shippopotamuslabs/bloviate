package com.shippopotamus.bloviate.application.evaluator

import app.cash.turbine.test
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.OnboardingStep
import com.shippopotamus.bloviate.testing.FakeAuthSession
import com.shippopotamus.bloviate.testing.FakeBubbleAppearancePort
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStatePort
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeOnboardingPort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeResidentPresencePort
import com.shippopotamus.bloviate.testing.FakeWatcherConnectionPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveSetupStateResolverTest {

    private val fakeWatcherConnection = FakeWatcherConnectionPort()
    private val fakeBubbleServiceState = FakeBubbleServiceStatePort()
    private val fakeWhisperStore = FakeModelStorePort()
    private val fakeParakeetStore = FakeParakeetModelStorePort()
    private val fakeEnginePreference = FakeEnginePreferencePort()
    private val fakeBubbleAppearance = FakeBubbleAppearancePort()
    private val fakeResidentPresence = FakeResidentPresencePort()
    private val fakeAuthSession = FakeAuthSession()
    private val fakeOnboarding = FakeOnboardingPort()

    private fun buildResolver(scope: CoroutineScope): LiveSetupStateResolver =
        LiveSetupStateResolver(
            watcherConnection = fakeWatcherConnection,
            bubbleServiceState = fakeBubbleServiceState,
            whisperStore = fakeWhisperStore,
            parakeetStore = fakeParakeetStore,
            enginePreference = fakeEnginePreference,
            bubbleAppearance = fakeBubbleAppearance,
            residentPresence = fakeResidentPresence,
            authSession = fakeAuthSession,
            onboarding = fakeOnboarding,
            scope = scope,
        )

    @Test
    fun `the initial snapshot mirrors the ports current values`() = runTest {
        fakeWatcherConnection.publishConnected(true)
        fakeParakeetStore.setReady()
        fakeAuthSession.publish(
            AuthState.Authenticated(
                UserProfile(uid = "uid-1", displayName = "Sam", email = null, photoUrl = null),
            ),
        )
        fakeOnboarding.publish(OnboardingProgress(step = OnboardingStep.MODEL, finished = false))

        val state = buildResolver(scope = backgroundScope).state.value

        assertTrue(state.watcherConnected)
        assertFalse(state.bubbleServiceRunning)
        assertEquals(ModelState.Absent, state.whisperModelState)
        assertEquals(ModelState.Ready, state.parakeetModelState)
        assertEquals(DictationEngine.PARAKEET, state.selectedEngine)
        assertEquals(DictationEngine.PARAKEET, state.effectiveEngine)
        assertTrue(state.authState is AuthState.Authenticated)
        assertEquals(OnboardingStep.MODEL, state.onboardingProgress.step)
    }

    @Test
    fun `published changes flow into the combined state`() = runTest {
        val resolver = buildResolver(scope = backgroundScope)

        resolver.state.test {
            assertFalse(awaitItem().bubbleServiceRunning)

            fakeBubbleServiceState.publishServiceRunning(true)
            assertTrue(awaitItem().bubbleServiceRunning)

            fakeWatcherConnection.publishConnected(true)
            assertTrue(awaitItem().watcherConnected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the effective engine falls back to the ready engine and clears when neither is ready`() = runTest {
        // Selected Parakeet (the fake's default) with no models ready:
        // no effective engine. Whisper becoming ready makes it the
        // effective fallback; Parakeet becoming ready restores it.
        val resolver = buildResolver(scope = backgroundScope)

        resolver.state.test {
            assertNull(awaitItem().effectiveEngine)

            fakeWhisperStore.setReady()
            assertEquals(DictationEngine.WHISPER, awaitItem().effectiveEngine)

            fakeParakeetStore.setReady()
            assertEquals(DictationEngine.PARAKEET, awaitItem().effectiveEngine)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an engine reselection re-derives the effective engine`() = runTest {
        fakeWhisperStore.setReady()
        val resolver = buildResolver(scope = backgroundScope)

        resolver.state.test {
            // Parakeet selected but absent, Whisper ready: fallback.
            assertEquals(DictationEngine.WHISPER, awaitItem().effectiveEngine)

            fakeEnginePreference.setSelected(DictationEngine.WHISPER)
            val state = awaitItem()
            assertEquals(DictationEngine.WHISPER, state.selectedEngine)
            assertEquals(DictationEngine.WHISPER, state.effectiveEngine)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
