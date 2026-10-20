package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.WatcherSupervisionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The watcher supervision mapping (ENC-129): the full two-signal
 * truth table. Enablement dominates — a watcher that was never turned
 * on is DISABLED whatever the connection bookkeeping says.
 */
class WatcherSupervisionEvaluatorTest {

    @Test
    fun `enabled and connected is connected`() {
        assertEquals(
            WatcherSupervisionState.CONNECTED,
            WatcherSupervisionEvaluator.resolve(watcherEnabled = true, watcherConnected = true),
        )
    }

    @Test
    fun `enabled but not connected is the supervised failure`() {
        assertEquals(
            WatcherSupervisionState.DISCONNECTED,
            WatcherSupervisionEvaluator.resolve(watcherEnabled = true, watcherConnected = false),
        )
    }

    @Test
    fun `disabled is disabled regardless of connection`() {
        assertEquals(
            WatcherSupervisionState.DISABLED,
            WatcherSupervisionEvaluator.resolve(watcherEnabled = false, watcherConnected = false),
        )
        assertEquals(
            WatcherSupervisionState.DISABLED,
            WatcherSupervisionEvaluator.resolve(watcherEnabled = false, watcherConnected = true),
        )
    }
}
