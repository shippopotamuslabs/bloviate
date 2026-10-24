package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.usecase.ObserveCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.SaveSyncedCustomStyleUseCase
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeCustomStyleSyncPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CustomStyleEditorViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeCustoms = FakeCustomStyleStorePort()
    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeStyle = FakeStylePreferencePort()
    private lateinit var viewModel: CustomStyleEditorViewModel

    @BeforeEach
    fun setup() {
        viewModel = CustomStyleEditorViewModel(
            observeCustoms = ObserveCustomStylesUseCase(fakeCustoms),
            saveSynced = SaveSyncedCustomStyleUseCase(
                sync = fakeSync,
                customs = fakeCustoms,
                selection = fakeStyle,
                validator = CustomStyleValidator(),
            ),
            validator = CustomStyleValidator(),
        )
    }

    /**
     * uiState is stateIn(WhileSubscribed): without a collector it never
     * leaves its initial value. A background collector keeps it hot so
     * .value assertions see live state (the Turbine test subscribes
     * itself and does not need this).
     */
    private fun TestScope.keepUiStateHot() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun TestScope.openCreate() {
        keepUiStateHot()
        viewModel.load(null)
        advanceUntilIdle()
    }

    private fun TestScope.openEdit(styleId: String) {
        keepUiStateHot()
        viewModel.load(styleId)
        advanceUntilIdle()
    }

    /** Fills every field with a valid voice, leaving a valid draft. */
    private fun fillValidDraft() {
        viewModel.onDisplayNameChange("Noir Detective")
        viewModel.onDescriptionChange("Rain, neon, and regret.")
        viewModel.onSampleChange("The rain kept falling, and so did I.")
        viewModel.onInstructionsChange("Short sentences. Rain everywhere. NEVER change facts.")
        viewModel.onPairRawChange(0, "i walked home late")
        viewModel.onPairStyledChange(0, "I walked home late, the rain keeping time.")
        viewModel.onPairRawChange(1, "the meeting is at noon")
        viewModel.onPairStyledChange(1, "The meeting is at noon, high noon.")
    }

    @Test
    fun `a blank create draft is invalid with a message on every field`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()

            val state = viewModel.uiState.value
            assertTrue(state.isCreate)
            assertFalse(state.isValid)
            assertFalse(state.isDirty)
            assertEquals(
                setOf(
                    CustomStyleEditorField.DISPLAY_NAME,
                    CustomStyleEditorField.DESCRIPTION,
                    CustomStyleEditorField.SAMPLE,
                    CustomStyleEditorField.INSTRUCTIONS,
                ),
                state.fieldErrors.keys,
            )
            assertEquals(2, state.pairs.size)
            assertTrue(state.pairs.all { pair -> pair.rawErrorRes != null && pair.styledErrorRes != null })
        }

    @Test
    fun `a filled draft validates and enables save`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isValid)
            assertTrue(state.isDirty)
            assertTrue(state.fieldErrors.isEmpty())
            assertTrue(state.pairs.all { pair -> pair.rawErrorRes == null && pair.styledErrorRes == null })
            assertNull(state.saveError)
        }

    @Test
    fun `a too-long name maps to the name length message`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()
            viewModel.onDisplayNameChange("n".repeat(MAX_CUSTOM_STYLE_NAME_CHARS + 1))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isValid)
            assertEquals(
                R.string.style_editor_error_name_too_long,
                state.fieldErrors[CustomStyleEditorField.DISPLAY_NAME],
            )
        }

    @Test
    fun `adding pairs stops at five and removing stops at two`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()

            viewModel.onAddPair()
            viewModel.onAddPair()
            viewModel.onAddPair()
            advanceUntilIdle()
            assertEquals(5, viewModel.uiState.value.pairs.size)
            assertFalse(viewModel.uiState.value.canAddPair)
            assertTrue(viewModel.uiState.value.canRemovePair)

            viewModel.onAddPair()
            advanceUntilIdle()
            assertEquals(5, viewModel.uiState.value.pairs.size)

            viewModel.onRemovePair(4)
            viewModel.onRemovePair(3)
            viewModel.onRemovePair(2)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.pairs.size)
            assertTrue(viewModel.uiState.value.canAddPair)
            assertFalse(viewModel.uiState.value.canRemovePair)

            viewModel.onRemovePair(1)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.pairs.size)
        }

    @Test
    fun `removing an out-of-range pair is a no-op`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()

            viewModel.onRemovePair(7)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.pairs.size)
            assertTrue(viewModel.uiState.value.isValid)
        }

    @Test
    fun `an edit loads the record fields and starts clean`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(validRecord()))
            openEdit("custom-noir")

            val state = viewModel.uiState.value
            assertFalse(state.isCreate)
            assertFalse(state.loadFailed)
            assertEquals("Noir Detective", state.displayName)
            assertEquals("Rain, neon, and regret.", state.description)
            assertEquals("The rain kept falling, and so did I.", state.sample)
            assertEquals(
                listOf(
                    "i walked home late" to "I walked home late, the rain keeping time.",
                    "the meeting is at noon" to "The meeting is at noon, high noon.",
                ),
                state.pairs.map { pair -> pair.raw to pair.styled },
            )
            assertTrue(state.isValid)
            assertFalse(state.isDirty)
        }

    @Test
    fun `editing a field marks the edit dirty`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(validRecord()))
            openEdit("custom-noir")

            viewModel.onSampleChange("The rain kept falling, and so did I, always.")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.isValid)
        }

    @Test
    fun `loading a missing style reports load failure`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openEdit("custom-gone")

            assertTrue(viewModel.uiState.value.loadFailed)
        }

    @Test
    fun `saving a valid create sends a blank id with a null revision and emits success`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()
            advanceUntilIdle()

            viewModel.saveSucceeded.test {
                viewModel.onSaveClick()
                advanceUntilIdle()

                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, fakeSync.savedInputs.size)
            val (definition, expectedRevision) = fakeSync.savedInputs.single()
            // The create placeholder id is validation-only: the server
            // allocates the real id, so the write path sends a blank one.
            assertEquals("", definition.id)
            assertFalse(definition.id.startsWith("custom-"))
            assertNull(expectedRevision)
            assertEquals("Noir Detective", definition.displayName)
            assertEquals(StyleKind.LLM, definition.kind)
            assertEquals(StyleCategory.CUSTOM, definition.category)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `saving an edit sends the real id with the loaded revision`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(validRecord(revision = 7L)))
            openEdit("custom-noir")

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertEquals(1, fakeSync.savedInputs.size)
            val (definition, expectedRevision) = fakeSync.savedInputs.single()
            assertEquals("custom-noir", definition.id)
            assertEquals(7L, expectedRevision)
        }

    @Test
    fun `saving while invalid sends nothing`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()

            viewModel.saveSucceeded.test {
                viewModel.onSaveClick()
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(fakeSync.savedInputs.isEmpty())
        }

    @Test
    fun `a save conflict keeps the draft and reports until edited`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(validRecord()))
            fakeSync.saveHandler = { _, _ ->
                CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Conflict)
            }
            openEdit("custom-noir")
            viewModel.onDisplayNameChange("Noir Detective, Revised")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isDirty)

            viewModel.saveSucceeded.test {
                viewModel.onSaveClick()
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            val failed = viewModel.uiState.value
            assertEquals(CustomStyleSaveError.CONFLICT, failed.saveError)
            assertEquals("Noir Detective, Revised", failed.displayName)
            assertTrue(failed.isDirty)

            viewModel.onDisplayNameChange("Noir Detective, Revised Again")
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.saveError)
        }

    @Test
    fun `every sync failure maps to its save error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()
            advanceUntilIdle()

            val cases = listOf(
                CustomStyleSyncFailure.SignedOut to CustomStyleSaveError.SIGNED_OUT,
                CustomStyleSyncFailure.NotEntitled to CustomStyleSaveError.NOT_ENTITLED,
                CustomStyleSyncFailure.Invalid to CustomStyleSaveError.INVALID,
                CustomStyleSyncFailure.Conflict to CustomStyleSaveError.CONFLICT,
                CustomStyleSyncFailure.LimitReached to CustomStyleSaveError.LIMIT_REACHED,
                CustomStyleSyncFailure.Unavailable to CustomStyleSaveError.UNAVAILABLE,
            )
            cases.forEach { (failure, expected) ->
                fakeSync.saveHandler = { _, _ -> CustomStyleSyncOutcome.Failed(failure) }
                viewModel.onSaveClick()
                advanceUntilIdle()
                assertEquals(expected, viewModel.uiState.value.saveError)
                viewModel.onSaveErrorDismiss()
                advanceUntilIdle()
                assertNull(viewModel.uiState.value.saveError)
            }
        }

    @Test
    fun `a dismissed save error stays dismissed until the next save`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openCreate()
            fillValidDraft()
            fakeSync.saveHandler = { _, _ ->
                CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)
            }
            advanceUntilIdle()

            viewModel.onSaveClick()
            advanceUntilIdle()
            assertEquals(CustomStyleSaveError.UNAVAILABLE, viewModel.uiState.value.saveError)

            viewModel.onSaveErrorDismiss()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.saveError)

            viewModel.onSaveClick()
            advanceUntilIdle()
            assertEquals(CustomStyleSaveError.UNAVAILABLE, viewModel.uiState.value.saveError)
        }

    private fun validRecord(revision: Long = 0L): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
                id = "custom-noir",
                displayName = "Noir Detective",
                description = "Rain, neon, and regret.",
                sample = "The rain kept falling, and so did I.",
                systemInstructions = "Short sentences. Rain everywhere. NEVER change facts.",
                fewShots = listOf(
                    FewShot(raw = "i walked home late", styled = "I walked home late, the rain keeping time."),
                    FewShot(raw = "the meeting is at noon", styled = "The meeting is at noon, high noon."),
                ),
                kind = StyleKind.LLM,
                category = StyleCategory.CUSTOM,
            ),
            revision = revision,
            updatedAtEpochMillis = 0L,
        )
}
