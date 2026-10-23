package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.WatcherAccessPort
import javax.inject.Inject

/** One-shot read of whether the accessibility watcher is enabled in system settings. */
class IsFieldWatcherEnabledUseCase @Inject constructor(
    private val watcherAccessPort: WatcherAccessPort,
) {

    operator fun invoke(): Boolean = watcherAccessPort.isFieldWatcherEnabled()
}
