package com.shippopotamus.bloviate.domain.model

/**
 * Framework-free snapshot of one window in the accessibility window
 * stream, copied by the watcher service from `AccessibilityWindowInfo`.
 *
 * Only the window's type classification and whether it is the active
 * window (the source of input events) are captured — never its title,
 * content, or owning package. That is all the shade-freeze policy
 * (ENC-132) is allowed to see.
 */
data class WatcherWindowSnapshot(
    val kind: Kind,
    val isActive: Boolean,
) {
    /** Window type classification, mirroring `AccessibilityWindowInfo`'s types. */
    enum class Kind {
        APPLICATION,
        INPUT_METHOD,
        SYSTEM,
        ACCESSIBILITY_OVERLAY,
        SPLIT_SCREEN_DIVIDER,
        OTHER,
    }
}
