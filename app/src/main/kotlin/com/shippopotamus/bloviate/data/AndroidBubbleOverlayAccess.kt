package com.shippopotamus.bloviate.data

import android.content.Context
import android.provider.Settings
import com.shippopotamus.bloviate.domain.port.BubbleOverlayAccessPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [BubbleOverlayAccessPort] backed by [Settings.canDrawOverlays]. */
@Singleton
class AndroidBubbleOverlayAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : BubbleOverlayAccessPort {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}
