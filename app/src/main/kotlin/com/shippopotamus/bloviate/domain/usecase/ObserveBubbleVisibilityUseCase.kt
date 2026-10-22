package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
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
 * Keyboard transitions are forwarded immediately in both directions.
 * ENC-127 briefly sustained a `false` for 500 ms before forwarding it,
 * insuring against a flapping-keyboard class that was inferred but
 * never actually observed; ENC-128's event-starvation model explains
 * the field reports without it, and the watcher's watchdog
 * re-derivation owns convergence in both directions now — a genuine
 * transient mis-reading self-corrects on the next re-derivation instead
 * of being papered over with latency on every real dismissal. Focus-
 * driven hides (password field, focus loss) were never filtered and
 * stay instantaneous, as does every keyboard hide now.
 */
class ObserveBubbleVisibilityUseCase @Inject constructor(
    private val fieldFocusPort: FieldFocusPort,
    private val keyboardVisibilityPort: KeyboardVisibilityPort,
    private val reducer: BubbleVisibilityReducer,
) {

    operator fun invoke(): Flow<BubbleVisibilityCommand> = merge(
        fieldFocusPort.fieldFocusState.mapNotNull { state -> reducer.onFocusState(state) },
        keyboardVisibilityPort.keyboardVisible
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

    /**
     * Whether the hide the overlay is about to apply comes from a
     * genuine password-field focus (ENC-156). See
     * [BubbleVisibilityReducer.isPasswordFocus].
     */
    fun isPasswordFocus(): Boolean = reducer.isPasswordFocus()

    /** Forget reducer state; called when the owning service is destroyed. */
    fun reset() {
        reducer.reset()
    }
}
