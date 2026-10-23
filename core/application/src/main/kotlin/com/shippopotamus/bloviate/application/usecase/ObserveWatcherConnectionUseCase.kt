package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.WatcherConnectionPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams whether the field watcher service is connected right now (ENC-129 supervision). */
class ObserveWatcherConnectionUseCase @Inject constructor(
    private val watcherConnectionPort: WatcherConnectionPort,
) {

    operator fun invoke(): StateFlow<Boolean> = watcherConnectionPort.isConnected
}
