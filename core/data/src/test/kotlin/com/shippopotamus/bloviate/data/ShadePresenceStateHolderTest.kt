package com.shippopotamus.bloviate.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShadePresenceStateHolderTest {

    private val holder = ShadePresenceStateHolder()

    @Test
    fun `initial state is shade absent`() {
        assertEquals(false, holder.shadePresent.value)
    }

    @Test
    fun `duplicate reports are not re-emitted and transitions emit in order`() = runTest {
        holder.shadePresent.test {
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
