package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StylePreferencePort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v2, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int plus the style id string. A record with no version key has
 * never been written and reads as the catalog default (`normal-caps-
 * casual`). A v1 record keeps its exact v1 meaning: ids resolve through
 * the static catalog ([StyleCatalog.byId]), so a removed or custom id
 * stored at v1 still reads as the default — an explicit invalidation,
 * never a silent reinterpretation. A v2 record preserves its raw
 * non-blank id verbatim, so custom-style ids survive the read; a blank
 * or missing id still reads as the default. Any other version (> 2 or
 * otherwise invalid) invalidates the whole record to the default, and
 * the next save rewrites it at v2.
 */
@Singleton
class DataStoreStylePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : StylePreferencePort {

    override val selectedStyleId: StateFlow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toStyleId() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_STYLE_ID,
        )

    /**
     * Persists [styleId] verbatim at schema v2: no static-catalog
     * normalization on write, so custom ids round-trip. Resolution
     * fallback for unknown ids stays the pipeline's job.
     */
    override suspend fun selectStyle(styleId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION_V2
            preferences[KEY_STYLE_ID] = styleId
        }
    }

    private fun Preferences.toStyleId(): String =
        when (this[KEY_SCHEMA_VERSION]) {
            SCHEMA_VERSION_V1 -> {
                val id = this[KEY_STYLE_ID] ?: return DEFAULT_STYLE_ID
                // byId resolves unknown ids to the default — a removed style can
                // never wedge the pipeline; .id normalises back to the stored id.
                StyleCatalog.byId(id).id
            }
            SCHEMA_VERSION_V2 -> {
                val id = this[KEY_STYLE_ID]
                if (id.isNullOrBlank()) DEFAULT_STYLE_ID else id
            }
            else -> DEFAULT_STYLE_ID
        }

    private companion object {
        /** The style an absent or invalidated record reads as (ENC-72: today's baseline behavior). */
        val DEFAULT_STYLE_ID: String = StyleCatalog.DEFAULT.id
        const val SCHEMA_VERSION_V1: Int = 1
        const val SCHEMA_VERSION_V2: Int = 2
        val KEY_SCHEMA_VERSION = intPreferencesKey("style_schema_version")
        val KEY_STYLE_ID = stringPreferencesKey("selected_style_id")
    }
}
