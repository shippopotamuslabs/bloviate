package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteCustomStyleUseCaseTest {

    private val fakeStore = FakeCustomStyleStorePort()
    private val useCase = DeleteCustomStyleUseCase(customs = fakeStore)

    @Test
    fun `deleting a saved id removes it`() = runTest {
        fakeStore.save(customRecord(id = "custom-mine"))
        fakeStore.save(customRecord(id = "custom-other"))

        useCase("custom-mine")

        assertEquals(listOf("custom-other"), fakeStore.state.value.styles.map { record -> record.definition.id })
    }

    @Test
    fun `deleting an unknown id is a no-op`() = runTest {
        fakeStore.save(customRecord(id = "custom-mine"))

        useCase("custom-ghost")

        assertEquals(listOf("custom-mine"), fakeStore.state.value.styles.map { record -> record.definition.id })
        assertTrue(fakeStore.state.value.isLoaded)
    }

    private fun customRecord(id: String): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
                id = id,
                displayName = "Custom",
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
