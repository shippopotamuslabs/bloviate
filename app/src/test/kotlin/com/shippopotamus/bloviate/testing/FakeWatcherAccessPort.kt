package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.WatcherAccessPort

/** Hand-written fake of [WatcherAccessPort]; flip [enabled] to simulate the system setting. */
class FakeWatcherAccessPort : WatcherAccessPort {

    var enabled: Boolean = false

    override fun isFieldWatcherEnabled(): Boolean = enabled
}
