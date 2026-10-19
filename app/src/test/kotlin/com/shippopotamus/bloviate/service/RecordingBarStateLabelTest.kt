package com.shippopotamus.bloviate.service

import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ENC-76: the processing bar's stage word is state-driven. The two
 * pipeline states map to their stage labels; every other state has no
 * label (it renders its own treatment instead).
 */
class RecordingBarStateLabelTest {

    @Test
    fun `transcribing maps to the transcribing label`() {
        assertEquals(
            R.string.bubble_stage_transcribing,
            RecordingBarState.Transcribing.processingLabelRes(),
        )
    }

    @Test
    fun `polishing maps to the polishing label`() {
        assertEquals(
            R.string.bubble_stage_polishing,
            RecordingBarState.Polishing.processingLabelRes(),
        )
    }

    @Test
    fun `non pipeline states have no stage label`() {
        assertNull(RecordingBarState.Idle.processingLabelRes())
        assertNull(RecordingBarState.Recording(elapsedMs = 1_000L).processingLabelRes())
        assertNull(RecordingBarState.Done.processingLabelRes())
        assertNull(RecordingBarState.Error.processingLabelRes())
    }
}
