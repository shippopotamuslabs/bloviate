package com.shippopotamus.bloviate.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Setup header pill's verdict (ENC-75): [MainUiState.setupStatus]
 * must name the FIRST blocker in gate order, so the pill always agrees
 * with the rows beneath it and the user fixes things in the order the
 * app actually needs them.
 */
class SetupStatusTest {

    private fun readyState() = MainUiState(
        watcherEnabled = true,
        watcherConnected = true,
        overlayPermissionGranted = true,
        micPermissionGranted = true,
        whisperModelStatus = ModelUiStatus.Ready,
        parakeetModelStatus = ModelUiStatus.Ready,
        bubbleServiceRunning = true,
    )

    @Test
    fun `all gates satisfied is Ready`() {
        assertEquals(SetupStatusUi.Ready, readyState().setupStatus())
    }

    @Test
    fun `watcher off blocks first, ahead of every other failure`() {
        val state = MainUiState()
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.WATCHER), state.setupStatus())
    }

    @Test
    fun `watcher enabled but disconnected blocks ahead of overlay and microphone`() {
        val state = readyState().copy(
            watcherConnected = false,
            overlayPermissionGranted = false,
            micPermissionGranted = false,
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.WATCHER_DISCONNECTED), state.setupStatus())
    }

    @Test
    fun `overlay missing blocks ahead of microphone and model`() {
        val state = readyState().copy(
            overlayPermissionGranted = false,
            micPermissionGranted = false,
            whisperModelStatus = ModelUiStatus.Absent,
            parakeetModelStatus = ModelUiStatus.Absent,
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.OVERLAY), state.setupStatus())
    }

    @Test
    fun `microphone missing blocks ahead of model`() {
        val state = readyState().copy(
            micPermissionGranted = false,
            whisperModelStatus = ModelUiStatus.Absent,
            parakeetModelStatus = ModelUiStatus.Absent,
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.MICROPHONE), state.setupStatus())
    }

    @Test
    fun `no ready model blocks when both engines are absent`() {
        val state = readyState().copy(
            whisperModelStatus = ModelUiStatus.Absent,
            parakeetModelStatus = ModelUiStatus.Absent,
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.MODEL), state.setupStatus())
    }

    @Test
    fun `downloading and failed models do not count as ready`() {
        val state = readyState().copy(
            whisperModelStatus = ModelUiStatus.Downloading(percent = 42),
            parakeetModelStatus = ModelUiStatus.Failed(reason = "network"),
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.MODEL), state.setupStatus())
    }

    @Test
    fun `one ready engine is enough for the model gate`() {
        val state = readyState().copy(
            whisperModelStatus = ModelUiStatus.Absent,
            parakeetModelStatus = ModelUiStatus.Ready,
        )
        assertEquals(SetupStatusUi.Ready, state.setupStatus())
    }

    @Test
    fun `model gate blocks ahead of the bubble switch`() {
        val state = readyState().copy(
            whisperModelStatus = ModelUiStatus.Absent,
            parakeetModelStatus = ModelUiStatus.Absent,
            bubbleServiceRunning = false,
        )
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.MODEL), state.setupStatus())
    }

    @Test
    fun `wanted-but-down bubble blocks as not running, after the model gate`() {
        val state = readyState().copy(bubbleEnabled = true, bubbleServiceRunning = false)
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.BUBBLE_NOT_RUNNING), state.setupStatus())
    }

    @Test
    fun `switched-off bubble is the last blocker`() {
        val state = readyState().copy(bubbleEnabled = false, bubbleServiceRunning = false)
        assertEquals(SetupStatusUi.Blocked(SetupBlocker.BUBBLE), state.setupStatus())
    }

    @Test
    fun `running bubble with the gate on is ready`() {
        val state = readyState().copy(bubbleEnabled = true, bubbleServiceRunning = true)
        assertEquals(SetupStatusUi.Ready, state.setupStatus())
    }
}
