package com.shippopotamus.bloviate.domain.model

/**
 * The field watcher's supervised health (ENC-129), derived from two
 * independent signals: whether the user has enabled the watcher in
 * system settings, and whether the watcher service is actually
 * connected right now. Android can leave an accessibility service
 * enabled-but-disconnected (an OEM kill, a botched rebind); before
 * supervision existed that state was silent — Setup said "On" while
 * nothing was watching. The derivation lives in
 * `WatcherSupervisionEvaluator`; this enum is only the verdict.
 */
enum class WatcherSupervisionState {

    /** Not enabled in system settings — nothing to supervise. */
    DISABLED,

    /** Enabled and connected: fields are being watched. */
    CONNECTED,

    /** Enabled in settings but the service is not connected: dictation is silently dead. */
    DISCONNECTED,
}
