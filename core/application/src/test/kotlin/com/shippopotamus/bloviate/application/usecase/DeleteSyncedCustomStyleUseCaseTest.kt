package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeCustomStyleSyncPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteSyncedCustomStyleUseCaseTest {

    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeStore = FakeCustomStyleStorePort()
    private val fakeSelection = FakeStylePreferencePort()
    private val useCase = DeleteSyncedCustomStyleUseCase(
        sync = fakeSync,
        customs = fakeStore,
        selection = fakeSelection,
    )

    @Test
    fun `a server delete removes the cached copy and keeps an unrelated selection`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-mine"), record(id = "custom-other")))
        fakeSelection.setSelected("custom-other")

        val outcome = useCase("custom-mine")

        assertEquals(DeleteSyncedCustomStyleOutcome.Deleted, outcome)
        assertEquals(listOf("custom-other"), fakeStore.state.value.styles.map { record -> record.definition.id })
        assertEquals("custom-other", fakeSelection.selectedStyleId.value)
        assertEquals(listOf("custom-mine"), fakeSync.deletedIds)
    }

    @Test
    fun `deleting the selected style resets the selection to the default`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-mine"), record(id = "custom-other")))
        fakeSelection.setSelected("custom-mine")

        assertEquals(DeleteSyncedCustomStyleOutcome.Deleted, useCase("custom-mine"))

        assertEquals(listOf("custom-other"), fakeStore.state.value.styles.map { record -> record.definition.id })
        assertEquals(StyleCatalog.DEFAULT.id, fakeSelection.selectedStyleId.value)
    }

    @Test
    fun `a failed server delete leaves the cache and the selection untouched`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-mine")))
        fakeSelection.setSelected("custom-mine")
        fakeSync.deleteResult = CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)

        val outcome = useCase("custom-mine")

        assertEquals(DeleteSyncedCustomStyleOutcome.Failed(CustomStyleSyncFailure.Unavailable), outcome)
        assertEquals(listOf("custom-mine"), fakeStore.state.value.styles.map { record -> record.definition.id })
        assertEquals("custom-mine", fakeSelection.selectedStyleId.value)
    }

    private fun record(id: String): CustomStyleRecord =
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
            revision = 0L,
            updatedAtEpochMillis = 0L,
        )
}
