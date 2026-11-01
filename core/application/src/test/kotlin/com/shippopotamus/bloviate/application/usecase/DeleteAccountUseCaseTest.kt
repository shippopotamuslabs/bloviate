package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.testing.FakeAccountDeletionPort
import com.shippopotamus.bloviate.testing.FakeAuthSession
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeLastStyledResultPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeleteAccountUseCaseTest {

    private val fakeDeletion = FakeAccountDeletionPort()
    private val fakeAuth = FakeAuthSession()
    private val fakeCustoms = FakeCustomStyleStorePort()
    private val fakeStylePreference = FakeStylePreferencePort()
    private val fakeLastResult = FakeLastStyledResultPort(
        initial = LastStyledResult(
            styleId = "professional",
            sourceTranscript = "raw words",
            styledOutput = "Polished words.",
            model = "model-x",
            provider = "provider-y",
            recordedAtEpochMillis = 42L,
        ),
    )

    private val useCase = DeleteAccountUseCase(
        deletionPort = fakeDeletion,
        authSession = fakeAuth,
        customStyleStore = fakeCustoms,
        stylePreference = fakeStylePreference,
        lastStyledResult = fakeLastResult,
    )

    @Test
    fun `Deleted clears the cache, drops the last result, resets a custom selection, and signs out`() = runTest {
        fakeStylePreference.setSelected("custom-mine")
        fakeDeletion.outcome = AccountDeletionOutcome.Deleted

        val outcome = useCase()

        assertEquals(AccountDeletionOutcome.Deleted, outcome)
        assertEquals(1, fakeCustoms.clearLocalCacheCallCount)
        assertEquals(1, fakeLastResult.clearCallCount)
        assertNull(fakeLastResult.lastResult.value)
        assertEquals(StyleCatalog.DEFAULT.id, fakeStylePreference.selectedStyleId.value)
        assertEquals(1, fakeAuth.signOutCallCount)
    }

    @Test
    fun `Deleted keeps a built-in selection`() = runTest {
        fakeStylePreference.setSelected("professional")
        fakeDeletion.outcome = AccountDeletionOutcome.Deleted

        useCase()

        assertEquals("professional", fakeStylePreference.selectedStyleId.value)
        assertEquals(0, fakeStylePreference.selectCallCount)
    }

    @Test
    fun `Deleted still returns Deleted when the local sign-out fails`() = runTest {
        fakeAuth.stubSignOut(Result.failure(IllegalStateException("credential manager gone")))
        fakeDeletion.outcome = AccountDeletionOutcome.Deleted

        val outcome = useCase()

        assertEquals(AccountDeletionOutcome.Deleted, outcome)
        assertEquals(1, fakeCustoms.clearLocalCacheCallCount)
    }

    @Test
    fun `non-Deleted outcomes clear nothing and never sign out`() = runTest {
        fakeStylePreference.setSelected("custom-mine")
        listOf(
            AccountDeletionOutcome.RequiresRecentLogin,
            AccountDeletionOutcome.SignedOut,
            AccountDeletionOutcome.Unavailable,
        ).forEach { scripted ->
            fakeDeletion.outcome = scripted

            assertEquals(scripted, useCase())
        }

        assertEquals(0, fakeCustoms.clearLocalCacheCallCount)
        assertEquals(0, fakeLastResult.clearCallCount)
        assertEquals("custom-mine", fakeStylePreference.selectedStyleId.value)
        assertEquals(0, fakeAuth.signOutCallCount)
    }
}
