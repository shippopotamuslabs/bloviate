package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleForegroundType
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bubble service's start decisions (ENC-129): every start begins
 * as specialUse, promotion follows the start's origin, boot start is
 * gated on the resident-presence record, and stickiness follows the
 * auto-start switch alone.
 */
class BubbleServiceStartPolicyTest {

    @Test
    fun `every start begins as special use, the only type legal in every context`() {
        assertEquals(BubbleForegroundType.SPECIAL_USE, BubbleServiceStartPolicy.initialType())
    }

    @Test
    fun `a user start promotes to microphone when the permission is granted`() {
        assertEquals(
            BubbleForegroundType.MICROPHONE,
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.USER, micPermissionGranted = true),
        )
    }

    @Test
    fun `a user start without the microphone permission does not promote`() {
        assertNull(
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.USER, micPermissionGranted = false),
        )
    }

    @Test
    fun `a boot start never promotes, permission or not`() {
        // Android 15 refuses a microphone-typed start from a boot
        // receiver outright; the policy never asks for one.
        assertNull(
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.BOOT, micPermissionGranted = true),
        )
        assertNull(
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.BOOT, micPermissionGranted = false),
        )
    }

    @Test
    fun `a sticky restart attempts the microphone promotion under its guard`() {
        assertEquals(
            BubbleForegroundType.MICROPHONE,
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.RESTART, micPermissionGranted = true),
        )
        assertNull(
            BubbleServiceStartPolicy.promotionType(BubbleServiceStartOrigin.RESTART, micPermissionGranted = false),
        )
    }

    @Test
    fun `auto start runs when presence is untouched`() {
        assertTrue(
            BubbleServiceStartPolicy.shouldAutoStart(
                ResidentPresenceState(autoStartEnabled = true, userStopped = false),
            ),
        )
    }

    @Test
    fun `the setup off switch blocks auto start`() {
        assertFalse(
            BubbleServiceStartPolicy.shouldAutoStart(
                ResidentPresenceState(autoStartEnabled = false),
            ),
        )
    }

    @Test
    fun `a user stop is honored across reboots until the next in-app start`() {
        assertFalse(
            BubbleServiceStartPolicy.shouldAutoStart(
                ResidentPresenceState(autoStartEnabled = true, userStopped = true),
            ),
        )
    }

    @Test
    fun `stickiness follows the auto-start switch alone`() {
        assertTrue(
            BubbleServiceStartPolicy.restartIsSticky(ResidentPresenceState(autoStartEnabled = true)),
        )
        assertTrue(
            BubbleServiceStartPolicy.restartIsSticky(
                ResidentPresenceState(autoStartEnabled = true, userStopped = true),
            ),
        )
        assertFalse(
            BubbleServiceStartPolicy.restartIsSticky(ResidentPresenceState(autoStartEnabled = false)),
        )
    }
}
