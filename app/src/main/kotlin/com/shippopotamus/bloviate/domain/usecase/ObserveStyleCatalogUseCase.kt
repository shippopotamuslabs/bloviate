package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import javax.inject.Inject

/** Exposes the style catalog (static typed data in `domain/` — no persistence, no flow). */
class ObserveStyleCatalogUseCase @Inject constructor() {

    /** Every style Bloviate offers, in display order. */
    operator fun invoke(): List<StyleDefinition> = StyleCatalog.ALL
}
