package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.LastStyledResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryLastStyledResultHolderTest {

    private fun result(text: String): LastStyledResult = LastStyledResult(
        styleId = "seneca",
        sourceTranscript = "words",
        styledOutput = text,
        model = null,
        provider = null,
        recordedAtEpochMillis = 1L,
    )

    @Test
    fun `record replaces and clear drops, in memory only`() {
        val holder = InMemoryLastStyledResultHolder()

        assertNull(holder.lastResult.value)

        holder.record(result("First."))
        assertEquals("First.", holder.lastResult.value?.styledOutput)

        holder.record(result("Second."))
        assertEquals("Second.", holder.lastResult.value?.styledOutput)

        holder.clear()
        assertNull(holder.lastResult.value)
    }
}
