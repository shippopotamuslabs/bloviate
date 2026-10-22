package com.shippopotamus.bloviate.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class WatcherConnectionStateHolderTest {

    private val holder = WatcherConnectionStateHolder()

    @Test
    fun `initial state is disconnected - a fresh process has no connected watcher`() {
        assertFalse(holder.isConnected.value)
    }

    @Test
    fun `duplicate states are not re-emitted and transitions emit in order`() = runTest {
        holder.isConnected.test {
            assertEquals(false, awaitItem())

            holder.publishConnected(true)
            assertEquals(true, awaitItem())

            holder.publishConnected(true)
            expectNoEvents()

            holder.publishConnected(false)
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
