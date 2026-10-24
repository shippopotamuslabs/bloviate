package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveSyncedCustomStyleUseCaseTest {

    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeStore = FakeCustomStyleStorePort()
    private val fakeSelection = FakeStylePreferencePort()
    private val useCase = SaveSyncedCustomStyleUseCase(
        sync = fakeSync,
        customs = fakeStore,
        selection = fakeSelection,
        validator = CustomStyleValidator(),
    )

    @Test
    fun `an invalid definition never reaches the server or the cache`() = runTest {
        val definition = validDefinition().copy(kind = StyleKind.MECHANICAL, displayName = "")

        val outcome = useCase(definition, expectedRevision = null)

        val invalid = outcome as SaveSyncedCustomStyleOutcome.Invalid
        assertTrue(invalid.violations.contains(CustomStyleViolation.KIND_NOT_LLM))
        assertTrue(invalid.violations.contains(CustomStyleViolation.DISPLAY_NAME_BLANK))
        assertTrue(fakeSync.savedInputs.isEmpty())
        assertTrue(fakeStore.state.value.styles.isEmpty())
        assertEquals(StyleCatalog.DEFAULT.id, fakeSelection.selectedStyleId.value)
    }

    @Test
    fun `a create saves server-first then merges into the cache and selects the new id`() = runTest {
        val serverRecord = record(id = "custom-server-id", revision = 1L)
        fakeSync.saveHandler = { _, _ -> CustomStyleSyncOutcome.Ok(serverRecord) }
        fakeStore.replaceSnapshot(listOf(record(id = "custom-existing")))

        val outcome = useCase(validDefinition().copy(id = ""), expectedRevision = null)

        assertEquals(SaveSyncedCustomStyleOutcome.Saved(serverRecord), outcome)
        assertEquals(
            listOf("custom-existing", "custom-server-id"),
            fakeStore.state.value.styles.map { record -> record.definition.id },
        )
        assertEquals("custom-server-id", fakeSelection.selectedStyleId.value)
        assertEquals(1, fakeSync.savedInputs.size)
    }

    @Test
    fun `an update merges by id and never changes the selection`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-mine", revision = 1L), record(id = "custom-other")))
        fakeSelection.setSelected("custom-other")

        val outcome = useCase(validDefinition(), expectedRevision = 1L)

        val saved = outcome as SaveSyncedCustomStyleOutcome.Saved
        assertEquals(2L, saved.record.revision)
        assertEquals(
            listOf("custom-other", "custom-mine"),
            fakeStore.state.value.styles.map { record -> record.definition.id },
        )
        assertEquals(2L, fakeStore.state.value.styles.single { record -> record.definition.id == "custom-mine" }.revision)
        assertEquals("custom-other", fakeSelection.selectedStyleId.value)
        assertEquals(listOf(validDefinition() to 1L), fakeSync.savedInputs)
    }

    @Test
    fun `a revision conflict leaves the cache and the selection untouched`() = runTest {
        fakeStore.replaceSnapshot(listOf(record(id = "custom-mine", revision = 1L)))
        fakeSync.saveHandler = { _, _ -> CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Conflict) }

        val outcome = useCase(validDefinition(), expectedRevision = 1L)

        assertEquals(SaveSyncedCustomStyleOutcome.Failed(CustomStyleSyncFailure.Conflict), outcome)
        assertEquals(1L, fakeStore.state.value.styles.single().revision)
        assertEquals(StyleCatalog.DEFAULT.id, fakeSelection.selectedStyleId.value)
    }

    @Test
    fun `the first save after a fresh sign-in sticks via replaceSnapshot claim`() = runTest {
        // A fresh sign-in starts the cache empty (the S2 owner gate hides
        // the previous account's records); the merged-list replaceSnapshot
        // after the server save claims the cache for this uid, so the
        // record survives — a plain save() here would be a no-op.
        assertTrue(fakeStore.state.value.styles.isEmpty())
        val serverRecord = record(id = "custom-first", revision = 1L)
        fakeSync.saveHandler = { _, _ -> CustomStyleSyncOutcome.Ok(serverRecord) }

        val outcome = useCase(validDefinition().copy(id = ""), expectedRevision = null)

        assertEquals(SaveSyncedCustomStyleOutcome.Saved(serverRecord), outcome)
        assertEquals(listOf(serverRecord), fakeStore.state.value.styles)
        assertEquals("custom-first", fakeSelection.selectedStyleId.value)
    }

    private fun validDefinition(): StyleDefinition =
        StyleDefinition(
            id = "custom-mine",
            displayName = "Mine",
            description = "A custom style.",
            sample = "Yeah, tomorrow.",
            systemInstructions = "My voice. NEVER change facts.",
            fewShots = listOf(
                FewShot(raw = "hear this", styled = "Hear this."),
                FewShot(raw = "try again", styled = "Try again."),
            ),
            kind = StyleKind.LLM,
            category = StyleCategory.CUSTOM,
        )

    private fun record(id: String, revision: Long = 1L): CustomStyleRecord =
        CustomStyleRecord(
            definition = validDefinition().copy(id = id),
            revision = revision,
            updatedAtEpochMillis = 0L,
        )
}
