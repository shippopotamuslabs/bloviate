package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.application.evaluator.StyleResolver
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.application.usecase.DeleteSyncedCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class StyleViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeStyle = FakeStylePreferencePort()
    private val fakeCustoms = FakeCustomStyleStorePort()
    private val fakeSync = FakeCustomStyleSyncPort()
    private val resolverScope = TestScope(mainDispatcherExtension.testDispatcher)
    private lateinit var viewModel: StyleViewModel

    @BeforeEach
    fun setup() {
        viewModel = StyleViewModel(
            observeCatalog = ObserveStyleCatalogUseCase(
                StyleResolver(store = fakeCustoms, scope = resolverScope),
            ),
            observeSelectedStyle = ObserveSelectedStyleUseCase(fakeStyle),
            selectStyle = SelectStyleUseCase(fakeStyle),
            deleteCustomStyle = DeleteSyncedCustomStyleUseCase(
                sync = fakeSync,
                customs = fakeCustoms,
                selection = fakeStyle,
            ),
            refreshCustomStyles = RefreshCustomStylesUseCase(sync = fakeSync, customs = fakeCustoms),
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
    fun `init refreshes the custom-styles cache without blocking the tab`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            advanceUntilIdle()

            assertEquals(1, fakeSync.listCallCount)
            viewModel.uiState.test {
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with no customs the my styles section is present but empty so the create entry survives`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                val custom = initial.sections.first()
                assertEquals(R.string.style_section_custom, custom.headerRes)
                assertTrue(custom.styles.isEmpty())
                assertEquals(StyleCatalog.ALL.size, initial.sections.sumOf { section -> section.styles.size })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with customs the my styles section leads with customs alphabetical`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(
                listOf(
                    customRecord(id = "custom-bravo", name = "Bravo"),
                    customRecord(id = "custom-alpha", name = "alpha"),
                ),
            )

            viewModel.uiState.test {
                // The first emission predates the resolver's catalog update;
                // the customs arrive on the next one.
                awaitItem()
                val updated = awaitItem()
                assertEquals(R.string.style_section_custom, updated.sections.first().headerRes)
                assertEquals(
                    listOf("custom-alpha", "custom-bravo"),
                    updated.sections.first().styles.map { option -> option.id },
                )
                assertEquals(
                    StyleCatalog.ALL.size + 2,
                    updated.sections.sumOf { section -> section.styles.size },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `custom options are flagged custom and built-ins are not`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            keepUiStateHot()
            advanceUntilIdle()

            val byId = viewModel.uiState.value.sections
                .flatMap { section -> section.styles }
                .associateBy { option -> option.id }
            assertEquals(true, byId.getValue("custom-mine").isCustom)
            assertEquals(false, byId.getValue("seneca").isCustom)
            assertEquals(false, byId.getValue("normal-caps-casual").isCustom)
        }

    @Test
    fun `delete opens the confirmation naming the style and dismiss closes it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            keepUiStateHot()
            advanceUntilIdle()

            viewModel.onDeleteCustomClick("custom-mine")
            advanceUntilIdle()
            assertEquals(
                PendingStyleDelete(styleId = "custom-mine", displayName = "Mine"),
                viewModel.uiState.value.pendingDelete,
            )

            viewModel.onDeleteDismiss()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.pendingDelete)
        }

    @Test
    fun `confirming the delete removes the style from the cache and closes the dialog`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            keepUiStateHot()
            advanceUntilIdle()

            viewModel.onDeleteCustomClick("custom-mine")
            advanceUntilIdle()
            viewModel.onDeleteConfirm()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingDelete)
            assertNull(viewModel.uiState.value.deleteError)
            assertTrue(fakeCustoms.state.value.styles.none { record -> record.definition.id == "custom-mine" })
            assertEquals(listOf("custom-mine"), fakeSync.deletedIds)
        }

    @Test
    fun `a failed delete closes the dialog and surfaces the mapped error until dismissed`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            fakeSync.deleteResult = CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)
            keepUiStateHot()
            advanceUntilIdle()

            viewModel.onDeleteCustomClick("custom-mine")
            advanceUntilIdle()
            viewModel.onDeleteConfirm()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingDelete)
            assertEquals(CustomStyleDeleteError.UNAVAILABLE, viewModel.uiState.value.deleteError)
            // The failed delete touches nothing: the cache still holds the style.
            assertEquals(1, fakeCustoms.state.value.styles.size)

            viewModel.onDeleteErrorDismiss()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.deleteError)
        }

    @Test
    fun `every sync failure maps to its delete error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            keepUiStateHot()
            advanceUntilIdle()

            val cases = listOf(
                CustomStyleSyncFailure.SignedOut to CustomStyleDeleteError.SIGNED_OUT,
                CustomStyleSyncFailure.NotEntitled to CustomStyleDeleteError.NOT_ENTITLED,
                CustomStyleSyncFailure.Invalid to CustomStyleDeleteError.INVALID,
                CustomStyleSyncFailure.Conflict to CustomStyleDeleteError.CONFLICT,
                CustomStyleSyncFailure.LimitReached to CustomStyleDeleteError.LIMIT_REACHED,
                CustomStyleSyncFailure.Unavailable to CustomStyleDeleteError.UNAVAILABLE,
            )
            cases.forEach { (failure, expected) ->
                fakeSync.deleteResult = CustomStyleSyncOutcome.Failed(failure)
                viewModel.onDeleteCustomClick("custom-mine")
                advanceUntilIdle()
                viewModel.onDeleteConfirm()
                advanceUntilIdle()
                assertEquals(expected, viewModel.uiState.value.deleteError)
                viewModel.onDeleteErrorDismiss()
                advanceUntilIdle()
            }
            // Every failure left the cache untouched.
            assertEquals(1, fakeCustoms.state.value.styles.size)
        }

    @Test
    fun `a selected custom style is marked selected and survives`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))
            keepUiStateHot()
            viewModel.onStyleSelected("custom-mine")
            advanceUntilIdle()

            assertEquals("custom-mine", fakeStyle.selectedStyleId.value)
            val selected = viewModel.uiState.value.sections.flatMap { section -> section.styles }.filter { option -> option.isSelected }
            assertEquals(listOf("custom-mine"), selected.map { option -> option.id })
        }

    @Test
    fun `an external customs change is reflected in state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertTrue(awaitItem().sections.first().styles.isEmpty())

                fakeCustoms.replaceSnapshot(listOf(customRecord(id = "custom-mine", name = "Mine")))

                val updated = awaitItem()
                assertEquals(R.string.style_section_custom, updated.sections.first().headerRes)
                assertEquals(listOf("custom-mine"), updated.sections.first().styles.map { option -> option.id })
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
    fun `sections follow enum order with the my styles section first and styles alphabetical inside each`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(
                    listOf(
                        R.string.style_section_custom,
                        R.string.style_section_serious,
                        R.string.style_section_philosophers,
                        R.string.style_section_writers,
                        R.string.style_section_eras_and_genres,
                        R.string.style_section_personas,
                        R.string.style_section_modern_parody,
                    ),
                    initial.sections.map { section -> section.headerRes },
                )
                assertEquals(listOf(0, 5, 8, 4, 4, 5, 8), initial.sections.map { section -> section.styles.size })
                StyleCategory.entries.filter { category -> category != StyleCategory.CUSTOM }.forEachIndexed { index, category ->
                    val expectedIds = StyleCatalog.ALL
                        .filter { style -> style.category == category }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { style -> style.displayName })
                        .map { style -> style.id }
                    assertEquals(expectedIds, initial.sections[index + 1].styles.map { style -> style.id })
                }
                // Pin the visible order so a comparator change cannot slip through silently.
                assertEquals(
                    listOf("aquinas", "aristotle", "augustine", "cicero", "marcus-aurelius", "plato", "seneca", "socrates"),
                    initial.sections[2].styles.map { style -> style.id },
                )
                assertEquals(
                    listOf("academic-jargon", "ai-psychosis", "corporate-linkedin", "edgelord", "gen-z-brainrot", "legal-contract", "silicon-valley-bro", "ultra-hr"),
                    initial.sections[6].styles.map { style -> style.id },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun customRecord(id: String, name: String): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
                id = id,
                displayName = name,
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
