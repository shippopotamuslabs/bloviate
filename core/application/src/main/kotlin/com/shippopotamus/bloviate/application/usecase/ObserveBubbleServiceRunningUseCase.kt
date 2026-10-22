package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.BubbleServiceStatePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams whether the bubble overlay service is currently running. */
class ObserveBubbleServiceRunningUseCase @Inject constructor(
    private val bubbleServiceStatePort: BubbleServiceStatePort,
) {

    operator fun invoke(): StateFlow<Boolean> = bubbleServiceStatePort.isServiceRunning
}
