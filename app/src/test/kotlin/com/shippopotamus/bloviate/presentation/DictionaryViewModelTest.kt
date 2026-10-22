package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.application.usecase.AddDictionaryCorrectionUseCase
import com.shippopotamus.bloviate.application.usecase.AddDictionaryWordUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveDictionaryUseCase
import com.shippopotamus.bloviate.application.usecase.RemoveDictionaryCorrectionUseCase
import com.shippopotamus.bloviate.application.usecase.RemoveDictionaryWordUseCase
import com.shippopotamus.bloviate.testing.FakeDictionaryPort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DictionaryViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeDictionary = FakeDictionaryPort()
    private lateinit var viewModel: DictionaryViewModel

    @BeforeEach
    fun setup() {
        viewModel = DictionaryViewModel(
            observeDictionary = ObserveDictionaryUseCase(fakeDictionary),
            addWord = AddDictionaryWordUseCase(fakeDictionary),
            removeWord = RemoveDictionaryWordUseCase(fakeDictionary),
            addCorrection = AddDictionaryCorrectionUseCase(fakeDictionary),
            removeCorrection = RemoveDictionaryCorrectionUseCase(fakeDictionary),
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

    @Test
    fun `initial state shows the seeded dictionary mapped to presentation models`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(PersonalDictionary.SEED.words, initial.words)
                assertEquals(
                    PersonalDictionary.SEED.corrections.map { correction ->
                        CorrectionUi(heardAs = correction.heardAs, replacement = correction.replacement)
                    },
                    initial.corrections,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `adding a word appends it and clears the draft`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onWordDraftChange("Tensor")
            viewModel.onAddWordClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PersonalDictionary.SEED.words + "Tensor", state.words)
            assertEquals("", state.wordDraft)
            assertNull(state.wordError)
        }

    @Test
    fun `adding a duplicate word keeps the draft and shows the duplicate error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onWordDraftChange("bloviate")
            viewModel.onAddWordClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PersonalDictionary.SEED.words, state.words)
            assertEquals("bloviate", state.wordDraft)
            assertEquals(DictionaryEditError.DUPLICATE_WORD, state.wordError)
        }

    @Test
    fun `adding a blank word shows the blank error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onAddWordClick()
            advanceUntilIdle()

            assertEquals(DictionaryEditError.BLANK_WORD, viewModel.uiState.value.wordError)
        }

    @Test
    fun `typing again clears the word error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onAddWordClick()
            advanceUntilIdle()
            assertEquals(DictionaryEditError.BLANK_WORD, viewModel.uiState.value.wordError)

            viewModel.onWordDraftChange("T")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.wordError)
        }

    @Test
    fun `removing a word drops its row`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onRemoveWordClick("Parakeet")
            advanceUntilIdle()

            assertEquals(listOf("Bloviate", "Shippopotamus"), viewModel.uiState.value.words)
        }

    @Test
    fun `adding a correction appends it and clears both drafts`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onHeardAsDraftChange("tensor flow")
            viewModel.onReplacementDraftChange("TensorFlow")
            viewModel.onAddCorrectionClick()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                CorrectionUi(heardAs = "tensor flow", replacement = "TensorFlow"),
                state.corrections.last(),
            )
            assertEquals("", state.heardAsDraft)
            assertEquals("", state.replacementDraft)
            assertNull(state.correctionError)
        }

    @Test
    fun `adding a correction with a blank side shows the blank error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onHeardAsDraftChange("perikit")
            viewModel.onAddCorrectionClick()
            advanceUntilIdle()

            assertEquals(DictionaryEditError.BLANK_CORRECTION, viewModel.uiState.value.correctionError)
        }

    @Test
    fun `adding a self-correction shows the self-correction error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onHeardAsDraftChange("same")
            viewModel.onReplacementDraftChange("Same")
            viewModel.onAddCorrectionClick()
            advanceUntilIdle()

            assertEquals(DictionaryEditError.SELF_CORRECTION, viewModel.uiState.value.correctionError)
        }

    @Test
    fun `adding a correction for an already-corrected hearing shows the duplicate error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onHeardAsDraftChange("PERIKIT")
            viewModel.onReplacementDraftChange("Something")
            viewModel.onAddCorrectionClick()
            advanceUntilIdle()

            assertEquals(DictionaryEditError.DUPLICATE_CORRECTION, viewModel.uiState.value.correctionError)
        }

    @Test
    fun `removing a correction drops its row`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onRemoveCorrectionClick("below v8")
            advanceUntilIdle()

            assertEquals(
                listOf("perikit", "blow-viate", "blow viate"),
                viewModel.uiState.value.corrections.map { correction -> correction.heardAs },
            )
        }
}
