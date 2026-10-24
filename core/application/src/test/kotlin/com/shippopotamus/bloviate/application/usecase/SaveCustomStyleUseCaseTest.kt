package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveCustomStyleUseCaseTest {

    private val fakeStore = FakeCustomStyleStorePort()
    private val useCase = SaveCustomStyleUseCase(customs = fakeStore, validator = CustomStyleValidator())

    @Test
    fun `a valid custom style saves and reports no violations`() = runTest {
        val record = validRecord()

        assertEquals(emptyList<CustomStyleViolation>(), useCase(record))

        assertEquals(listOf(record), fakeStore.state.value.styles)
    }

    @Test
    fun `an invalid custom style reports violations and saves nothing`() = runTest {
        val record = validRecord().copy(
            definition = validRecord().definition.copy(kind = StyleKind.MECHANICAL, displayName = ""),
        )

        val violations = useCase(record)

        assertTrue(violations.contains(CustomStyleViolation.KIND_NOT_LLM))
        assertTrue(violations.contains(CustomStyleViolation.DISPLAY_NAME_BLANK))
        assertTrue(fakeStore.state.value.styles.isEmpty())
    }

    @Test
    fun `saving the same id twice replaces the record`() = runTest {
        useCase(validRecord())
        val edited = validRecord().copy(revision = 2L)

        assertEquals(emptyList<CustomStyleViolation>(), useCase(edited))

        assertEquals(listOf(edited), fakeStore.state.value.styles)
    }

    private fun validRecord(): CustomStyleRecord =
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
