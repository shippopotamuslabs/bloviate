package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.usecase.GenerateCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.MAX_GENERATE_DESCRIPTION_CHARS
import com.shippopotamus.bloviate.testing.FakeCustomStyleGenerationPort
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

class CustomStyleGenerationViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeGeneration = FakeCustomStyleGenerationPort()
    private lateinit var viewModel: CustomStyleGenerationViewModel

    @BeforeEach
    fun setup() {
        viewModel = CustomStyleGenerationViewModel(
            generate = GenerateCustomStyleUseCase(fakeGeneration),
        )
    }

    /**
     * uiState is stateIn(WhileSubscribed): without a collector it never
     * leaves its initial value. A background collector keeps it hot so
     * .value assertions see live state.
     */
    private fun TestScope.keepUiStateHot() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun `a blank description disables generate and never calls the port`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            advanceUntilIdle()

            listOf("", "   ").forEach { blank ->
                viewModel.onDescriptionChange(blank)
                advanceUntilIdle()

                assertFalse(viewModel.uiState.value.canGenerate)
                viewModel.onGenerateClick()
                advanceUntilIdle()
            }

            assertTrue(fakeGeneration.descriptions.isEmpty())
            assertFalse(viewModel.uiState.value.isGenerating)
        }

    @Test
    fun `an over-cap description disables generate and never calls the port`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onDescriptionChange("x".repeat(MAX_GENERATE_DESCRIPTION_CHARS + 1))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canGenerate)
            viewModel.onGenerateClick()
            advanceUntilIdle()

            assertTrue(fakeGeneration.descriptions.isEmpty())
        }

    @Test
    fun `a valid description enables generate`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onDescriptionChange("writes like noir")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.canGenerate)
        }

    @Test
    fun `a successful generate emits the handoff with the retained description`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()

            viewModel.generateSucceeded.test {
                viewModel.onDescriptionChange("  writes like noir  ")
                advanceUntilIdle()
                viewModel.onGenerateClick()
                advanceUntilIdle()

                val handoff = awaitItem()
                assertEquals("writes like noir", handoff.sourceDescription)
                assertEquals("Noir Detective", handoff.draft.displayName)
                assertEquals(listOf("writes like noir"), fakeGeneration.descriptions)
                cancelAndIgnoreRemainingEvents()
            }

            assertFalse(viewModel.uiState.value.isGenerating)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `failures map onto their documented errors`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            val cases = listOf(
                CustomStyleGenerationFailure.NotAStyleDescription to CustomStyleGenerationError.NOT_A_STYLE,
                CustomStyleGenerationFailure.QuotaReached to CustomStyleGenerationError.QUOTA_REACHED,
                CustomStyleGenerationFailure.NotEntitled to CustomStyleGenerationError.NOT_ENTITLED,
                CustomStyleGenerationFailure.SignedOut to CustomStyleGenerationError.SIGNED_OUT,
                CustomStyleGenerationFailure.GenerationInvalid to CustomStyleGenerationError.INVALID_GENERATION,
                CustomStyleGenerationFailure.ClassifierUnavailable to CustomStyleGenerationError.INVALID_GENERATION,
                CustomStyleGenerationFailure.Unavailable to CustomStyleGenerationError.UNAVAILABLE,
                CustomStyleGenerationFailure.InvalidInput to CustomStyleGenerationError.UNAVAILABLE,
            )

            cases.forEach { (failure, error) ->
                fakeGeneration.result = CustomStyleGenerationOutcome.Failed(failure)
                viewModel.onDescriptionChange("writes like noir")
                viewModel.onGenerateClick()
                advanceUntilIdle()

                assertEquals(error, viewModel.uiState.value.error, "failure: $failure")
                assertFalse(viewModel.uiState.value.isGenerating)
            }
        }

    @Test
    fun `dismissing the error clears it and keeps the description`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            fakeGeneration.result =
                CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.Unavailable)
            viewModel.onDescriptionChange("writes like noir")
            viewModel.onGenerateClick()
            advanceUntilIdle()

            viewModel.onErrorDismiss()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertEquals("writes like noir", viewModel.uiState.value.description)
        }

    @Test
    fun `cancelling mid-generation emits nothing and resets`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()

            viewModel.generateSucceeded.test {
                viewModel.onDescriptionChange("writes like noir")
                viewModel.onGenerateClick()
                viewModel.onCancelInFlight()
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(fakeGeneration.descriptions.isEmpty())
            assertFalse(viewModel.uiState.value.isGenerating)
        }
}
