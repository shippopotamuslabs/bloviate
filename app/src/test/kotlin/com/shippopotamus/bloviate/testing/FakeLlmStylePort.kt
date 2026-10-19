package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.domain.port.LlmStylePort
import kotlinx.coroutines.CompletableDeferred

/**
 * Hand-written fake of [LlmStylePort]. [outcome] scripts the result;
 * every (style id, text) call is recorded in [inputs]. Defaults to a
 * fallback so pipeline tests see the local text unless they opt into AI.
 */
class FakeLlmStylePort(
    var outcome: LlmStyleOutcome = LlmStyleOutcome.FellBack(reason = "test-unset"),
) : LlmStylePort {

    private val mutableInputs = mutableListOf<Pair<String, String>>()

    /** (style id, text) pairs passed to [style] so far, in order. */
    val inputs: List<Pair<String, String>> get() = mutableInputs.toList()

    /** Number of [style] calls so far. */
    val callCount: Int get() = mutableInputs.size

    /** When set, [style] suspends on this gate before returning — for in-flight stage tests. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun style(style: StyleDefinition, text: String): LlmStyleOutcome {
        mutableInputs += style.id to text
        gate?.await()
        return outcome
    }
}
