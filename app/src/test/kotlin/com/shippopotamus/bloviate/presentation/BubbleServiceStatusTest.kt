package com.shippopotamus.bloviate.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bubble's presentation states (BLO-189): [MainUiState] carries
 * the user's gate ([MainUiState.bubbleEnabled]) apart from the
 * service's liveness ([MainUiState.bubbleServiceRunning]), and the
 * three pure derivations below read exactly that split.
 */
class BubbleServiceStatusTest {

    private fun state(enabled: Boolean, running: Boolean) = MainUiState(
        bubbleEnabled = enabled,
        bubbleServiceRunning = running,
    )

    @Test
    fun `running service reads RUNNING even while wanted`() {
        assertEquals(BubbleStatusUi.RUNNING, state(enabled = true, running = true).bubbleServiceStatus())
    }

    @Test
    fun `running service reads RUNNING even after an explicit stop`() {
        // A stop in flight has not landed yet: liveness wins, so the
        // row never flashes down before the service actually stops.
        assertEquals(BubbleStatusUi.RUNNING, state(enabled = false, running = true).bubbleServiceStatus())
    }

    @Test
    fun `wanted-but-down reads NOT_RUNNING`() {
        assertEquals(BubbleStatusUi.NOT_RUNNING, state(enabled = true, running = false).bubbleServiceStatus())
    }

    @Test
    fun `switched-off reads OFF`() {
        assertEquals(BubbleStatusUi.OFF, state(enabled = false, running = false).bubbleServiceStatus())
    }

    @Test
    fun `re-arm is due while wanted, allowed, and down`() {
        assertTrue(state(enabled = true, running = false).copy(autoStartEnabled = true).bubbleAutoStartDue())
    }

    @Test
    fun `re-arm is not due while running`() {
        assertFalse(state(enabled = true, running = true).bubbleAutoStartDue())
    }

    @Test
    fun `re-arm is not due after an explicit stop`() {
        assertFalse(state(enabled = false, running = false).bubbleAutoStartDue())
    }

    @Test
    fun `re-arm is not due while auto-start is off`() {
        assertFalse(
            state(enabled = true, running = false).copy(autoStartEnabled = false).bubbleAutoStartDue(),
        )
    }
}
