package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportPort
import com.shippopotamus.bloviate.application.port.OutputReportRequest
import kotlinx.coroutines.CompletableDeferred

/** Hand-written fake of [OutputReportPort]; records every request and scripts the outcome. */
class FakeOutputReportPort(
    var outcome: OutputReportOutcome = OutputReportOutcome.Submitted(duplicate = false),
) : OutputReportPort {

    private val mutableRequests = mutableListOf<OutputReportRequest>()

    /** Every request submitted so far, in order. */
    val requests: List<OutputReportRequest> get() = mutableRequests.toList()

    /** When set, [submit] suspends on it before answering — lets tests observe the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun submit(report: OutputReportRequest): OutputReportOutcome {
        mutableRequests += report
        gate?.await()
        return outcome
    }
}
