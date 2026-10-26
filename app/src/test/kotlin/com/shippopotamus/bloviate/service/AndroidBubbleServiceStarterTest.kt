package com.shippopotamus.bloviate.service

import android.app.Application
import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.application.port.BubbleStartRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The bubble-service starter adapter (BLO-189): each requestable
 * start maps onto its service intent action.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AndroidBubbleServiceStarterTest {

    private lateinit var starter: AndroidBubbleServiceStarter

    @BeforeEach
    fun setup() {
        val context: Application = RuntimeEnvironment.getApplication()
        starter = AndroidBubbleServiceStarter(context = context)
    }

    @Test
    fun `a boot request starts the service tagged as a boot start`() {
        assertEquals(BubbleServiceStartOutcome.Started, starter.startBubble(BubbleStartRequest.BOOT))

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(BubbleOverlayService.ACTION_BOOT_START, started?.action)
        assertEquals(BubbleOverlayService::class.java.name, started?.component?.className)
    }

    @Test
    fun `a user request starts the service tagged as a user start`() {
        assertEquals(BubbleServiceStartOutcome.Started, starter.startBubble(BubbleStartRequest.USER))

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(BubbleOverlayService.ACTION_USER_START, started?.action)
        assertEquals(BubbleOverlayService::class.java.name, started?.component?.className)
    }
}
