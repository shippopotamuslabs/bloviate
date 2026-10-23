package com.shippopotamus.bloviate.application.usecase

import app.cash.turbine.test
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStylesState
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObserveCustomStylesUseCaseTest {

    private val fakeStore = FakeCustomStyleStorePort(
        initial = CustomStylesState(styles = emptyList(), isLoaded = false),
    )
    private val useCase = ObserveCustomStylesUseCase(customs = fakeStore)

    @Test
    fun `the unloaded empty cache streams first, then the snapshot`() = runTest {
        useCase().test {
            val loading = awaitItem()
            assertTrue(loading.styles.isEmpty())
            assertEquals(false, loading.isLoaded)

            fakeStore.replaceSnapshot(listOf(customRecord()))

            val loaded = awaitItem()
            assertEquals(listOf("custom-mine"), loaded.styles.map { record -> record.definition.id })
            assertEquals(true, loaded.isLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun customRecord(): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
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
            ),
            revision = 0L,
            updatedAtEpochMillis = 0L,
        )
}
