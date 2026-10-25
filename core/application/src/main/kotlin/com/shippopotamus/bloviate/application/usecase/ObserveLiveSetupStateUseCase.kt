package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.evaluator.LiveSetupStateResolver
import com.shippopotamus.bloviate.application.port.LiveSetupState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Streams the durable live state behind the Setup screen and the
 * first-run wizard (BLO-175): the single [StateFlow] passthrough over
 * [LiveSetupStateResolver.state], mirroring
 * [ObserveStyleCatalogUseCase]. Both ViewModels observe this one use
 * case and fold in only their local transient state (ARCHITECTURE_RULES
 * 3.4).
 */
class ObserveLiveSetupStateUseCase @Inject constructor(
    private val resolver: LiveSetupStateResolver,
) {

    /** The combined durable state, live from the current snapshot on. */
    operator fun invoke(): StateFlow<LiveSetupState> = resolver.state
}
