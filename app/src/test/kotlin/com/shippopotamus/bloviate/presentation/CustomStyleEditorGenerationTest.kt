package com.shippopotamus.bloviate.presentation

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.port.GeneratedStyleDraft
import com.shippopotamus.bloviate.application.usecase.GenerateCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.SaveSyncedCustomStyleUseCase
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.testing.FakeCustomStyleGenerationPort
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

class CustomStyleEditorGenerationTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeCustoms = FakeCustomStyleStorePort()
    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeStyle = FakeStylePreferencePort()
    private val fakeGeneration = FakeCustomStyleGenerationPort()
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
            generate = GenerateCustomStyleUseCase(fakeGeneration),
            validator = CustomStyleValidator(),
        )
    }

    private fun TestScope.keepUiStateHot() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun defaultDraft(): GeneratedStyleDraft =
        (fakeGeneration.result as CustomStyleGenerationOutcome.Ok).draft

    private fun TestScope.openGenerated(sourceDescription: String = "writes like noir") {
        keepUiStateHot()
        viewModel.loadGenerated(defaultDraft(), sourceDescription)
        advanceUntilIdle()
    }

    @Test
    fun `a generated load prefills every field and reads clean`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated()

            val state = viewModel.uiState.value
            assertTrue(state.isCreate)
            assertTrue(state.isGenerated)
            assertFalse(state.isDirty)
            assertTrue(state.isValid)
            val draft = defaultDraft()
            assertEquals(draft.displayName, state.displayName)
            assertEquals(draft.description, state.description)
            assertEquals(draft.sample, state.sample)
            assertEquals(draft.systemInstructions, state.systemInstructions)
            assertEquals(
                draft.fewShots.map { shot -> shot.raw to shot.styled },
                state.pairs.map { pair -> pair.raw to pair.styled },
            )
        }

    @Test
    fun `a generated edit reads dirty against the generated baseline`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated()

            viewModel.onDisplayNameChange("Noir Detective, Retired")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.isGenerated)
        }

    @Test
    fun `a manual load resets a stale generated basis`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated("writes like noir")
            viewModel.onDisplayNameChange("edited")

            viewModel.load(null)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isGenerated)
            assertFalse(state.isDirty)
            assertEquals("", state.displayName)
            assertTrue(fakeGeneration.descriptions.isEmpty())
        }

    @Test
    fun `a clean regenerate calls the port with the retained description and replaces the draft`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated("writes like noir")
            val fresh = GeneratedStyleDraft(
                displayName = "Cozy Mystery",
                description = "Tea, cats, and gentle peril.",
                sample = "The kettle whistled, and so did I.",
                systemInstructions = "Warm sentences. Never rush.",
                fewShots = listOf(
                    FewShot(raw = "the cat sat down", styled = "The cat sat down, prim as you please."),
                    FewShot(raw = "tea is ready", styled = "Tea is ready, and the afternoon exhales."),
                    FewShot(raw = "a clue appeared", styled = "A clue appeared, soft as snowfall."),
                ),
            )
            fakeGeneration.result = CustomStyleGenerationOutcome.Ok(fresh)

            viewModel.onRegenerateClick()
            advanceUntilIdle()

            assertEquals(listOf("writes like noir"), fakeGeneration.descriptions)
            val state = viewModel.uiState.value
            assertEquals("Cozy Mystery", state.displayName)
            assertEquals("Tea, cats, and gentle peril.", state.description)
            assertEquals(3, state.pairs.size)
            assertFalse(state.isDirty)
            assertTrue(state.isGenerated)
            assertFalse(state.isRegenerating)
        }

    @Test
    fun `a dirty regenerate raises confirmation first and waits`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated()
            viewModel.onDisplayNameChange("edited")
            advanceUntilIdle()

            viewModel.onRegenerateClick()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.regenerateConfirmPending)
            assertTrue(fakeGeneration.descriptions.isEmpty())

            viewModel.onRegenerateDismissConfirm()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.regenerateConfirmPending)
            assertTrue(fakeGeneration.descriptions.isEmpty())
            assertEquals("edited", viewModel.uiState.value.displayName)
        }

    @Test
    fun `confirming a dirty regenerate replaces the draft and reads clean`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated("writes like noir")
            viewModel.onDisplayNameChange("edited")
            advanceUntilIdle()

            viewModel.onRegenerateClick()
            advanceUntilIdle()
            viewModel.onRegenerateConfirm()
            advanceUntilIdle()

            assertEquals(listOf("writes like noir"), fakeGeneration.descriptions)
            val state = viewModel.uiState.value
            assertFalse(state.regenerateConfirmPending)
            assertEquals(defaultDraft().displayName, state.displayName)
            assertFalse(state.isDirty)
            assertTrue(state.isGenerated)
        }

    @Test
    fun `back-to-back regenerates reuse the retained description`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated("writes like noir")

            viewModel.onRegenerateClick()
            advanceUntilIdle()
            viewModel.onRegenerateClick()
            advanceUntilIdle()

            assertEquals(listOf("writes like noir", "writes like noir"), fakeGeneration.descriptions)
        }

    @Test
    fun `a failed regenerate keeps the draft and reports`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            openGenerated()
            val expectedName = defaultDraft().displayName
            fakeGeneration.result =
                CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.QuotaReached)

            viewModel.onRegenerateClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CustomStyleGenerationError.QUOTA_REACHED, state.regenerateError)
            assertEquals(expectedName, state.displayName)
            assertFalse(state.isRegenerating)

            viewModel.onSaveErrorDismiss()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.regenerateError)
        }

    @Test
    fun `regenerate is a no-op outside generated mode`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.load(null)
            advanceUntilIdle()

            viewModel.onRegenerateClick()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isGenerated)
            assertFalse(viewModel.uiState.value.regenerateConfirmPending)
            assertTrue(fakeGeneration.descriptions.isEmpty())
        }
}
