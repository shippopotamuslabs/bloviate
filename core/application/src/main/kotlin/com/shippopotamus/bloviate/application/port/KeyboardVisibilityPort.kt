package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between the accessibility watcher (publisher) and the bubble
 * overlay (collector) for on-screen keyboard visibility (ENC-125).
 *
 * The watcher derives the signal from its accessibility window stream —
 * an input-method window present means the keyboard is up. The signal is
 * a bare visibility fact: no window content, package, or field text is
 * ever read for it. Emissions are deduped: collectors only see actual
 * show/hide transitions.
 */
interface KeyboardVisibilityPort {

    val keyboardVisible: StateFlow<Boolean>

    fun publish(visible: Boolean)
}
