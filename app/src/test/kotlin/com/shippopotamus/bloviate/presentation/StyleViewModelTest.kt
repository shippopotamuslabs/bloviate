package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class StyleViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeStyle = FakeStylePreferencePort()
    private lateinit var viewModel: StyleViewModel

    @BeforeEach
    fun setup() {
        viewModel = StyleViewModel(
            observeCatalog = ObserveStyleCatalogUseCase(),
            observeSelectedStyle = ObserveSelectedStyleUseCase(fakeStyle),
            selectStyle = SelectStyleUseCase(fakeStyle),
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
                assertEquals(StyleCatalog.ALL.map { style -> style.id }.toSet(), initial.sections.flatMap { section -> section.styles }.map { it.id }.toSet())
                assertEquals(listOf(StyleCatalog.DEFAULT.id), initial.sections.flatMap { section -> section.styles }.filter { it.isSelected }.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `llm styles are flagged as requiring ai and mechanical ones are not`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                val byId = initial.sections.flatMap { section -> section.styles }.associateBy { option -> option.id }
                assertEquals(false, byId.getValue("lowercase-casual").requiresAi)
                assertEquals(false, byId.getValue("normal-caps-casual").requiresAi)
                assertEquals(true, byId.getValue("seneca").requiresAi)
                assertEquals(true, byId.getValue("professional").requiresAi)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting an llm style updates the selection and writes through the fake`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onStyleSelected("seneca")
            advanceUntilIdle()

            assertEquals("seneca", fakeStyle.selectedStyleId.value)
            val selected = viewModel.uiState.value.sections.flatMap { section -> section.styles }.filter { option -> option.isSelected }
            assertEquals(listOf("seneca"), selected.map { option -> option.id })
        }

    @Test
    fun `selecting a mechanical style updates the selection and writes through the fake`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            keepUiStateHot()
            viewModel.onStyleSelected("lowercase-casual")
            advanceUntilIdle()

            assertEquals("lowercase-casual", fakeStyle.selectedStyleId.value)
            val selected = viewModel.uiState.value.sections.flatMap { section -> section.styles }.filter { option -> option.isSelected }
            assertEquals(listOf("lowercase-casual"), selected.map { option -> option.id })
        }

    @Test
    fun `an external selection change is reflected in state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertEquals(
                    listOf(StyleCatalog.DEFAULT.id),
                    awaitItem().sections.flatMap { section -> section.styles }.filter { it.isSelected }.map { it.id },
                )

                fakeStyle.setSelected("edgelord")

                val updated = awaitItem()
                assertEquals(listOf("edgelord"), updated.sections.flatMap { section -> section.styles }.filter { it.isSelected }.map { it.id })
                assertTrue(updated.sections.flatMap { section -> section.styles }.size == StyleCatalog.ALL.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sections follow enum order with styles alphabetical inside each`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(
                    listOf(
                        R.string.style_section_serious,
                        R.string.style_section_philosophers,
                        R.string.style_section_writers,
                        R.string.style_section_eras_and_genres,
                        R.string.style_section_personas,
                        R.string.style_section_modern_parody,
                    ),
                    initial.sections.map { section -> section.headerRes },
                )
                assertEquals(listOf(4, 5, 4, 4, 5, 5), initial.sections.map { section -> section.styles.size })
                StyleCategory.entries.forEachIndexed { index, category ->
                    val expectedIds = StyleCatalog.ALL
                        .filter { style -> style.category == category }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { style -> style.displayName })
                        .map { style -> style.id }
                    assertEquals(expectedIds, initial.sections[index].styles.map { style -> style.id })
                }
                // Pin the visible order so a comparator change cannot slip through silently.
                assertEquals(
                    listOf("aristotle", "cicero", "marcus-aurelius", "seneca", "socrates"),
                    initial.sections[1].styles.map { style -> style.id },
                )
                assertEquals(
                    listOf("academic-jargon", "corporate-linkedin", "edgelord", "gen-z-brainrot", "legal-contract"),
                    initial.sections[5].styles.map { style -> style.id },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
