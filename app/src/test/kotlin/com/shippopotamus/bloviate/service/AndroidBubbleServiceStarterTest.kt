package com.shippopotamus.bloviate.service

import android.app.Application
import com.shippopotamus.bloviate.application.port.BubbleServiceStartOutcome
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The bubble-service starter adapter (BLO-189): requestable origins
 * map onto their service intent actions, and the system-origin
 * classification is refused without touching the platform.
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
        assertEquals(BubbleServiceStartOutcome.Started, starter.startBubble(BubbleServiceStartOrigin.BOOT))

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(BubbleOverlayService.ACTION_BOOT_START, started?.action)
        assertEquals(BubbleOverlayService::class.java.name, started?.component?.className)
    }

    @Test
    fun `a user request starts the service tagged as a user start`() {
        assertEquals(BubbleServiceStartOutcome.Started, starter.startBubble(BubbleServiceStartOrigin.USER))

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(BubbleOverlayService.ACTION_USER_START, started?.action)
        assertEquals(BubbleOverlayService::class.java.name, started?.component?.className)
    }

    @Test
    fun `a restart classification is refused without starting the service`() {
        assertEquals(BubbleServiceStartOutcome.Refused, starter.startBubble(BubbleServiceStartOrigin.RESTART))

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }
}
