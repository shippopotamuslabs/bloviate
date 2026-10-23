package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import javax.inject.Inject

/**
 * Pulls the server's custom-styles truth into the account cache (BLO-25
 * S4). A successful list replaces the whole snapshot via
 * [CustomStyleStorePort.replaceSnapshot] — which also claims the cache
 * for the current uid, so a fresh sign-in heals an unclaimed cache here.
 * Any failure leaves the cache exactly as it was and returns the
 * failure; the caller (a ViewModel refresh trigger) swallows it.
 */
class RefreshCustomStylesUseCase @Inject constructor(
    private val sync: CustomStyleSyncPort,
    private val customs: CustomStyleStorePort,
) {

    /** Refreshes the cache from the server; the failure, if any. */
    suspend operator fun invoke(): CustomStyleSyncOutcome<Unit> =
        when (val outcome = sync.list()) {
            is CustomStyleSyncOutcome.Ok -> {
                customs.replaceSnapshot(outcome.value)
                CustomStyleSyncOutcome.Ok(Unit)
            }
            is CustomStyleSyncOutcome.Failed -> outcome
        }
}
