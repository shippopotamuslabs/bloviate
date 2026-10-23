package com.shippopotamus.bloviate.application.evaluator

import app.cash.turbine.test
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStylesState
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StyleResolverTest {

    private val fakeStore = FakeCustomStyleStorePort()

    private fun buildResolver(scope: CoroutineScope): StyleResolver =
        StyleResolver(store = fakeStore, scope = scope)

    @Test
    fun `the catalog is the built-ins alone when no customs exist`() = runTest {
        buildResolver(scope = backgroundScope).catalog.test {
            assertEquals(StyleCatalog.ALL.map { style -> style.id }, awaitItem().map { style -> style.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customs lead the catalog sorted by display name then id`() = runTest {
        val resolver = buildResolver(scope = backgroundScope)
        resolver.catalog.test {
            assertEquals(StyleCatalog.ALL.size, awaitItem().size)

            fakeStore.replaceSnapshot(listOf(record(id = "custom-b", name = "Beta"), record(id = "custom-a", name = "alpha")))

            val updated = awaitItem()
            assertEquals(
                listOf("custom-a", "custom-b") + StyleCatalog.ALL.map { style -> style.id },
                updated.map { style -> style.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a custom style shadows a built-in with the same id`() = runTest {
        val shadow = record(id = "seneca", name = "My Seneca")
        fakeStore.replaceSnapshot(listOf(shadow))
        val resolver = buildResolver(scope = backgroundScope)

        assertEquals("My Seneca", resolver.resolve("seneca").displayName)
    }

    @Test
    fun `unknown ids fall back to the catalog default`() = runTest {
        val resolver = buildResolver(scope = backgroundScope)

        assertEquals(StyleCatalog.DEFAULT, resolver.resolve("not-a-style"))
        assertEquals(StyleCatalog.DEFAULT, resolver.resolve("custom-unknown"))
    }

    @Test
    fun `built-in ids resolve immediately even before the snapshot loads`() = runTest {
        fakeStore.setState(CustomStylesState(styles = emptyList(), isLoaded = false))
        val resolver = buildResolver(scope = backgroundScope)

        assertEquals("seneca", resolver.resolveAwaitLoaded("seneca").id)
    }

    @Test
    fun `a custom id waits for the snapshot when one arrives`() = runTest {
        fakeStore.setState(CustomStylesState(styles = emptyList(), isLoaded = false))
        val resolver = buildResolver(scope = backgroundScope)
        backgroundScope.launch {
            fakeStore.replaceSnapshot(listOf(record(id = "custom-mine", name = "Mine")))
        }

        assertEquals("custom-mine", resolver.resolveAwaitLoaded("custom-mine").id)
    }

    @Test
    fun `a custom id falls back to the default when the load times out`() = runTest {
        fakeStore.setState(CustomStylesState(styles = emptyList(), isLoaded = false))
        val resolver = buildResolver(scope = backgroundScope)

        assertEquals(StyleCatalog.DEFAULT, resolver.resolveAwaitLoaded("custom-mine"))
    }

    private fun record(id: String, name: String): CustomStyleRecord =
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
