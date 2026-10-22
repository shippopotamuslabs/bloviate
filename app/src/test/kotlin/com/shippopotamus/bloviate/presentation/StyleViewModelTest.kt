package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.application.usecase.ClearApiKeyUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveHasApiKeyUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.application.usecase.SaveApiKeyUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class StyleViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeStyle = FakeStylePreferencePort()
    private val fakeKeys = FakeApiKeyPort()
    private lateinit var viewModel: StyleViewModel

    @BeforeEach
    fun setup() {
        viewModel = StyleViewModel(
            observeCatalog = ObserveStyleCatalogUseCase(),
            observeSelectedStyle = ObserveSelectedStyleUseCase(fakeStyle),
            selectStyle = SelectStyleUseCase(fakeStyle),
            observeHasApiKey = ObserveHasApiKeyUseCase(fakeKeys),
            saveApiKey = SaveApiKeyUseCase(fakeKeys),
            clearApiKey = ClearApiKeyUseCase(fakeKeys),
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
    fun `initial state lists the whole catalog with the default selected`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(StyleCatalog.ALL.map { style -> style.id }, initial.options.map { it.id })
                assertEquals(listOf(StyleCatalog.DEFAULT.id), initial.options.filter { it.isSelected }.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `llm styles are flagged as requiring ai and mechanical ones are not`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                val byId = initial.options.associateBy { option -> option.id }
                assertEquals(false, byId.getValue("lowercase-casual").requiresAi)
                assertEquals(false, byId.getValue("normal-caps-casual").requiresAi)
                assertEquals(true, byId.getValue("seneca").requiresAi)
                assertEquals(true, byId.getValue("professional").requiresAi)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting a style moves the selection and writes through the fake`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            fakeKeys.setKey("test-key")
            advanceUntilIdle()
            viewModel.onStyleSelected("seneca")
            advanceUntilIdle()

            assertEquals("seneca", fakeStyle.selectedStyleId.value)
            val selected = viewModel.uiState.value.options.filter { option -> option.isSelected }
            assertEquals(listOf("seneca"), selected.map { option -> option.id })
        }

    @Test
    fun `an external selection change is reflected in state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertEquals(
                    listOf(StyleCatalog.DEFAULT.id),
                    awaitItem().options.filter { it.isSelected }.map { it.id },
                )

                fakeStyle.setSelected("edgelord")

                val updated = awaitItem()
                assertEquals(listOf("edgelord"), updated.options.filter { it.isSelected }.map { it.id })
                assertTrue(updated.options.size == StyleCatalog.ALL.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `llm rows are disabled until a key is stored`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                val byId = initial.options.associateBy { option -> option.id }
                assertFalse(byId.getValue("seneca").isEnabled)
                assertTrue(byId.getValue("lowercase-casual").isEnabled)
                assertFalse(initial.hasApiKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting an llm style without a key is ignored`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()

            viewModel.onStyleSelected("seneca")
            advanceUntilIdle()

            assertEquals(StyleCatalog.DEFAULT.id, fakeStyle.selectedStyleId.value)
        }

    @Test
    fun `saving a key enables llm rows and clears the draft`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onApiKeyDraftChange("test-key")
            advanceUntilIdle()
            assertEquals("test-key", viewModel.uiState.value.apiKeyDraft)

            viewModel.onSaveApiKeyClick()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.apiKeyDraft)
            assertTrue(viewModel.uiState.value.hasApiKey)
            assertTrue(viewModel.uiState.value.options.first { it.id == "seneca" }.isEnabled)

            viewModel.onStyleSelected("seneca")
            advanceUntilIdle()
            assertEquals("seneca", fakeStyle.selectedStyleId.value)
        }

    @Test
    fun `clearing the key disables llm rows again`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onApiKeyDraftChange("test-key")
            viewModel.onSaveApiKeyClick()
            advanceUntilIdle()

            viewModel.onClearApiKeyClick()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasApiKey)
            assertFalse(viewModel.uiState.value.options.first { it.id == "seneca" }.isEnabled)
        }

    @Test
    fun `a blank draft never reaches the store`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onApiKeyDraftChange("   ")
            viewModel.onSaveApiKeyClick()
            advanceUntilIdle()

            assertEquals(0, fakeKeys.saveCallCount)
            assertFalse(viewModel.uiState.value.hasApiKey)
        }
}
