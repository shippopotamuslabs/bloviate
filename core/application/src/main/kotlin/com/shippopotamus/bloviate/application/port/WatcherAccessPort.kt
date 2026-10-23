package com.shippopotamus.bloviate.application.port

/** Reports whether the user has enabled Bloviate's accessibility watcher in system settings. */
interface WatcherAccessPort {

    fun isFieldWatcherEnabled(): Boolean
}
