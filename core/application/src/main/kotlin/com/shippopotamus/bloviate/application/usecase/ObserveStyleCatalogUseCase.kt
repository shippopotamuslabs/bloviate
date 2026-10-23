package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.evaluator.StyleResolver
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Streams the merged style catalog (ENC-199): the user's custom styles
 * first, then the static built-ins — the single [StateFlow] passthrough
 * over [StyleResolver.catalog], mirroring [ObserveSelectedStyleUseCase].
 */
class ObserveStyleCatalogUseCase @Inject constructor(
    private val resolver: StyleResolver,
) {

    /** Every style Bloviate offers, customs first, in display order. */
    operator fun invoke(): StateFlow<List<StyleDefinition>> = resolver.catalog
}
