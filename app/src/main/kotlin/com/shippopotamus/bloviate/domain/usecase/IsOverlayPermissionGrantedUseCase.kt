package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.BubbleOverlayAccessPort
import javax.inject.Inject

/** Whether the app may draw the bubble over other apps right now. Re-read on resume. */
class IsOverlayPermissionGrantedUseCase @Inject constructor(
    private val bubbleOverlayAccessPort: BubbleOverlayAccessPort,
) {

    operator fun invoke(): Boolean = bubbleOverlayAccessPort.canDrawOverlays()
}
