package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeCustomStyleSyncPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshCustomStylesUseCaseTest {

    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeStore = FakeCustomStyleStorePort()
    private val useCase = RefreshCustomStylesUseCase(sync = fakeSync, customs = fakeStore)

    @Test
    fun `a successful list replaces the snapshot`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-stale")))
        fakeSync.listResult = CustomStyleSyncOutcome.Ok(listOf(record(id = "custom-fresh", revision = 3L)))

        val outcome = useCase()

        assertEquals(CustomStyleSyncOutcome.Ok(Unit), outcome)
        assertEquals(listOf("custom-fresh"), fakeStore.state.value.styles.map { record -> record.definition.id })
        assertEquals(3L, fakeStore.state.value.styles.single().revision)
    }

    @Test
    fun `a failed list leaves the cache untouched`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-keep")))
        fakeSync.listResult = CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)

        val outcome = useCase()

        assertEquals(CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable), outcome)
        assertEquals(listOf("custom-keep"), fakeStore.state.value.styles.map { record -> record.definition.id })
    }

    @Test
    fun `a signed-out list leaves the cache untouched`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-keep")))
        fakeSync.listResult = CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut)

        assertEquals(CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut), useCase())

        assertEquals(listOf("custom-keep"), fakeStore.state.value.styles.map { record -> record.definition.id })
    }

    private fun record(id: String, revision: Long = 1L): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
                id = id,
                displayName = "Custom",
                description = "A custom style.",
                sample = "Yeah, tomorrow.",
                systemInstructions = "My voice. NEVER change facts.",
                fewShots = listOf(
                    FewShot(raw = "hear this", styled = "Hear this."),
                    FewShot(raw = "try again", styled = "Try again."),
                ),
                kind = StyleKind.LLM,
                category = StyleCategory.CUSTOM,
            ),
            revision = revision,
            updatedAtEpochMillis = 0L,
        )
}
