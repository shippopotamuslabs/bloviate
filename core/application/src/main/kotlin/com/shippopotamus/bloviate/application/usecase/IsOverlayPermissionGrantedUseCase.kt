package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.BubbleOverlayAccessPort
import javax.inject.Inject

/** Whether the app may draw the bubble over other apps right now. Re-read on resume. */
class IsOverlayPermissionGrantedUseCase @Inject constructor(
    private val bubbleOverlayAccessPort: BubbleOverlayAccessPort,
) {

    operator fun invoke(): Boolean = bubbleOverlayAccessPort.canDrawOverlays()
}
