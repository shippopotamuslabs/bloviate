package com.shippopotamus.bloviate.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BubbleServiceStateHolderTest {

    private val holder = BubbleServiceStateHolder()

    @Test
    fun `initial state is not running`() {
        assertFalse(holder.isServiceRunning.value)
    }

    @Test
    fun `duplicate states are not re-emitted and transitions emit in order`() = runTest {
        holder.isServiceRunning.test {
            assertEquals(false, awaitItem())

            holder.publishServiceRunning(true)
            assertEquals(true, awaitItem())

            holder.publishServiceRunning(true)
            expectNoEvents()

            holder.publishServiceRunning(false)
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
