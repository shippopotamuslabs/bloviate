package com.shippopotamus.bloviate.application.evaluator

import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.domain.model.CUSTOM_STYLE_ID_PREFIX
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves style ids over the merged catalog (ENC-199): the user's custom
 * styles first (sorted by display name, then id), then the built-in
 * [StyleCatalog.ALL]. Customs shadow built-ins on id collision, and
 * unknown ids fall back to the catalog default — the same never-null
 * promise `StyleCatalog.byId` makes.
 *
 * A singleton sharing one [catalog] flow, mirroring the DataStore stores:
 * the sharing comes from the singleton store underneath, so every
 * consumer sees the same snapshot. The send-path processors
 * (`StyleAwareTextPostProcessor`, `StylingTextPostProcessor`) switch to
 * this resolver in S4; until then it serves the catalog UI.
 */
@Singleton
class StyleResolver @Inject constructor(
    private val store: CustomStyleStorePort,
    scope: CoroutineScope,
) {

    /** Customs first, then every built-in style, in display order. */
    val catalog: StateFlow<List<StyleDefinition>> = store.state
        .map { state -> merge(customs = state.styles.map { record -> record.definition }) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = merge(customs = store.state.value.styles.map { record -> record.definition }),
        )

    /** Resolves [id] against the latest snapshot: customs first, then the built-in catalog. */
    fun resolve(id: String): StyleDefinition =
        store.state.value.styles.firstOrNull { record -> record.definition.id == id }?.definition
            ?: StyleCatalog.byId(id)

    /**
     * Like [resolve], but a `custom-` id waits (up to [LOAD_TIMEOUT_MS])
     * for the persisted snapshot before resolving, so a cold start does
     * not read a pre-load empty cache as "no such custom style".
     * Built-in ids resolve immediately; on timeout the latest snapshot
     * decides, exactly as [resolve].
     */
    suspend fun resolveAwaitLoaded(id: String): StyleDefinition {
        if (id.startsWith(CUSTOM_STYLE_ID_PREFIX) && !store.state.value.isLoaded) {
            // A null return means the load timed out: resolve against whatever has arrived.
            withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                store.state.first { state -> state.isLoaded }
            }
        }
        return resolve(id = id)
    }

    private fun merge(customs: List<StyleDefinition>): List<StyleDefinition> =
        customs.sortedWith(CUSTOM_ORDER) + StyleCatalog.ALL

    private companion object {
        const val LOAD_TIMEOUT_MS: Long = 1_000L
        val CUSTOM_ORDER: Comparator<StyleDefinition> =
            compareBy(String.CASE_INSENSITIVE_ORDER, StyleDefinition::displayName)
                .thenBy(StyleDefinition::id)
    }
}
