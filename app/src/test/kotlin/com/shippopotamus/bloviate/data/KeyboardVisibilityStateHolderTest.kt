package com.shippopotamus.bloviate.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyboardVisibilityStateHolderTest {

    private val holder = KeyboardVisibilityStateHolder()

    @Test
    fun `initial state is keyboard hidden`() {
        assertEquals(false, holder.keyboardVisible.value)
    }

    @Test
    fun `duplicate reports are not re-emitted and transitions emit in order`() = runTest {
        holder.keyboardVisible.test {
            assertEquals(false, awaitItem())

            holder.publish(true)
            assertEquals(true, awaitItem())

            holder.publish(true)
            expectNoEvents()

            holder.publish(false)
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
