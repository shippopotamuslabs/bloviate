package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

/**
 * Streams bubble window commands derived from field-focus and
 * keyboard-visibility transitions via [BubbleVisibilityReducer] (ENC-125:
 * the bubble follows the keyboard, not field focus alone), and accepts
 * the user's drag-to-dismiss and the recording bar's activity back into
 * the same state machine. The overlay service is the only collector; it
 * owns this use case instance for its lifetime so the reducer state stays
 * coherent.
 *
 * The keyboard signal passes through a hysteresis filter first
 * (ENC-127): `true` is forwarded immediately — showing stays instant —
 * while a `false` is forwarded only once it has held for
 * [KEYBOARD_HIDE_SUSTAIN_MS]. The watcher's IME derivation can flap for
 * a few hundred milliseconds in apps with heavy window churn (the Muse
 * app composer is the observed case); a flap inside the window never
 * reaches the reducer, so it can never hide the bubble. The filter
 * delays ONLY keyboard-driven hides: focus-driven hides (password field,
 * focus loss) travel the unfiltered focus stream, so the mid-recording
 * password cancel stays instantaneous.
 */
class ObserveBubbleVisibilityUseCase @Inject constructor(
    private val fieldFocusPort: FieldFocusPort,
    private val keyboardVisibilityPort: KeyboardVisibilityPort,
    private val reducer: BubbleVisibilityReducer,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<BubbleVisibilityCommand> = merge(
        fieldFocusPort.fieldFocusState.mapNotNull { state -> reducer.onFocusState(state) },
        keyboardVisibilityPort.keyboardVisible
            .sustainedOnly()
            .mapNotNull { visible -> reducer.onKeyboardVisibilityChanged(visible) },
    )

    /** The visible bubble was dragged into the dismiss zone; returns the implied command, if any. */
    fun onBubbleDismissed(): BubbleVisibilityCommand? = reducer.onBubbleDismissed()

    /**
     * Recording/processing bar activity (any non-idle bar state). While
     * the bar is active a keyboard hide cannot hide the window; when
     * activity ends, the deferred hide — if one is owed — is returned
     * here for the service to apply.
     */
    fun onBarActivityChanged(active: Boolean): BubbleVisibilityCommand? =
        reducer.onBarActivityChanged(active)

    /**
     * The overlay service reports the window's actual presence after
     * applying a command (ENC-127 reconciliation): resyncs the state
     * machine's visibility belief to reality and returns a corrective
     * command if one is owed. See [BubbleVisibilityReducer.onWindowPresenceChanged].
     */
    fun onWindowPresenceChanged(present: Boolean): BubbleVisibilityCommand? =
        reducer.onWindowPresenceChanged(present)

    /** Forget reducer state; called when the owning service is destroyed. */
    fun reset() {
        reducer.reset()
    }

    /**
     * Hysteresis for the keyboard signal: a hide must be sustained
     * before it counts. `transformLatest` cancels the pending hide the
     * moment the keyboard is reported visible again, so a flap
     * (true → false → true inside the window) forwards nothing at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<Boolean>.sustainedOnly(): Flow<Boolean> = transformLatest { visible ->
        if (visible) {
            emit(true)
        } else {
            delay(KEYBOARD_HIDE_SUSTAIN_MS)
            emit(false)
        }
    }

    private companion object {
        /**
         * How long the keyboard must stay hidden before a keyboard-driven
         * hide counts (ENC-127). Comfortably longer than the observed
         * window-churn flaps, short enough that a real keyboard dismissal
         * still feels instant.
         */
        const val KEYBOARD_HIDE_SUSTAIN_MS: Long = 500L
    }
}
