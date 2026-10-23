package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import javax.inject.Inject

/**
 * The outcome of one synced custom-style delete (BLO-25 S4): the server
 * and the cache both dropped the style, or the sync failure (the cache
 * is untouched).
 */
sealed interface DeleteSyncedCustomStyleOutcome {

    /** The server deleted the style; the cache and the selection already reflect it. */
    data object Deleted : DeleteSyncedCustomStyleOutcome

    /** The server call failed; the cache and the selection are untouched. */
    data class Failed(val failure: CustomStyleSyncFailure) : DeleteSyncedCustomStyleOutcome
}

/**
 * Deletes a custom style through the server first, then the account
 * cache (BLO-25 S4). The server delete lands before any cache write so
 * a failed delete never loses the local copy; a failure returns the
 * failure with the cache and the selection untouched. When the deleted
 * style was the selected one, the selection resets to the catalog
 * default so the pipeline never points at a missing style.
 */
class DeleteSyncedCustomStyleUseCase @Inject constructor(
    private val sync: CustomStyleSyncPort,
    private val customs: CustomStyleStorePort,
    private val selection: StylePreferencePort,
) {

    /** Deletes [styleId] on the server, then from the cache, resetting the selection when it pointed at it. */
    suspend operator fun invoke(styleId: String): DeleteSyncedCustomStyleOutcome =
        when (val outcome = sync.delete(styleId)) {
            is CustomStyleSyncOutcome.Ok -> {
                customs.delete(styleId)
                if (selection.selectedStyleId.value == styleId) {
                    selection.selectStyle(StyleCatalog.DEFAULT.id)
                }
                DeleteSyncedCustomStyleOutcome.Deleted
            }
            is CustomStyleSyncOutcome.Failed ->
                DeleteSyncedCustomStyleOutcome.Failed(outcome.failure)
        }
}
