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
        assertNull(RecordingBarState.Done().processingLabelRes())
        assertNull(
            RecordingBarState.NotStyled(
                RecordingBarState.StyleFallbackNotice.SUBSCRIPTION_REQUIRED,
            ).processingLabelRes(),
        )
        assertNull(RecordingBarState.Error.processingLabelRes())
    }

    @Test
    fun `subscription notice maps to the subscription pill text`() {
        assertEquals(
            R.string.bubble_not_styled_subscription,
            RecordingBarState.NotStyled(
                RecordingBarState.StyleFallbackNotice.SUBSCRIPTION_REQUIRED,
            ).notStyledTextRes(),
        )
        assertEquals(
            R.string.bubble_not_styled_subscription_description,
            RecordingBarState.NotStyled(
                RecordingBarState.StyleFallbackNotice.SUBSCRIPTION_REQUIRED,
            ).notStyledDescriptionRes(),
        )
    }

    @Test
    fun `sign in notice maps to the sign in pill text`() {
        assertEquals(
            R.string.bubble_not_styled_sign_in,
            RecordingBarState.NotStyled(
                RecordingBarState.StyleFallbackNotice.SIGN_IN_REQUIRED,
            ).notStyledTextRes(),
        )
        assertEquals(
            R.string.bubble_not_styled_sign_in_description,
            RecordingBarState.NotStyled(
                RecordingBarState.StyleFallbackNotice.SIGN_IN_REQUIRED,
            ).notStyledDescriptionRes(),
        )
    }

    @Test
    fun `other states have no not styled text`() {
        assertNull(RecordingBarState.Idle.notStyledTextRes())
        assertNull(RecordingBarState.Recording(elapsedMs = 1_000L).notStyledTextRes())
        assertNull(RecordingBarState.Transcribing.notStyledTextRes())
        assertNull(RecordingBarState.Polishing.notStyledTextRes())
        assertNull(RecordingBarState.Done().notStyledTextRes())
        assertNull(RecordingBarState.Error.notStyledTextRes())
        assertNull(RecordingBarState.Idle.notStyledDescriptionRes())
        assertNull(RecordingBarState.Done().notStyledDescriptionRes())
        assertNull(RecordingBarState.Error.notStyledDescriptionRes())
    }
}
