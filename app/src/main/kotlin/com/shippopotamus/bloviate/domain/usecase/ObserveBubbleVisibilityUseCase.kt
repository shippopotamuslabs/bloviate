package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/**
 * Streams bubble window commands derived from field-focus transitions via
 * [BubbleVisibilityReducer], and accepts the user's drag-to-dismiss back into
 * the same state machine. The overlay service is the only collector; it owns
 * this use case instance for its lifetime so the reducer state stays coherent.
 */
class ObserveBubbleVisibilityUseCase @Inject constructor(
    private val fieldFocusPort: FieldFocusPort,
    private val reducer: BubbleVisibilityReducer,
) {

    operator fun invoke(): Flow<BubbleVisibilityCommand> =
        fieldFocusPort.fieldFocusState.mapNotNull { state -> reducer.onFocusState(state) }

    /** The visible bubble was dragged into the dismiss zone; returns the implied command, if any. */
    fun onBubbleDismissed(): BubbleVisibilityCommand? = reducer.onBubbleDismissed()

    /** Forget reducer state; called when the owning service is destroyed. */
    fun reset() {
        reducer.reset()
    }
}
