package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.testing.FakeCustomStyleGenerationPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateCustomStyleUseCaseTest {

    @Test
    fun `a blank description fails without a port call`() = runTest {
        val port = FakeCustomStyleGenerationPort()

        listOf("", "   ", "\n\t ").forEach { description ->
            assertEquals(
                CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.InvalidInput),
                GenerateCustomStyleUseCase(port)(description),
            )
        }

        assertTrue(port.descriptions.isEmpty())
    }

    @Test
    fun `an over-cap description fails without a port call`() = runTest {
        val port = FakeCustomStyleGenerationPort()

        val outcome = GenerateCustomStyleUseCase(port)("x".repeat(MAX_GENERATE_DESCRIPTION_CHARS + 1))

        assertEquals(
            CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.InvalidInput),
            outcome,
        )
        assertTrue(port.descriptions.isEmpty())
    }

    @Test
    fun `a description at the cap delegates with the trimmed text`() = runTest {
        val port = FakeCustomStyleGenerationPort()
        val padded = "  " + "x".repeat(MAX_GENERATE_DESCRIPTION_CHARS - 4) + "  "

        val outcome = GenerateCustomStyleUseCase(port)(padded)

        assertTrue(outcome is CustomStyleGenerationOutcome.Ok)
        assertEquals(listOf(padded.trim()), port.descriptions)
    }

    @Test
    fun `a valid description delegates and returns the draft`() = runTest {
        val port = FakeCustomStyleGenerationPort()

        val outcome = GenerateCustomStyleUseCase(port)("  writes like noir  ")

        assertEquals(port.result, outcome)
        assertEquals(listOf("writes like noir"), port.descriptions)
    }

    @Test
    fun `a port failure passes through untouched`() = runTest {
        val port = FakeCustomStyleGenerationPort(
            result = CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.QuotaReached),
        )

        assertEquals(
            CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.QuotaReached),
            GenerateCustomStyleUseCase(port)("writes like noir"),
        )
    }
}
