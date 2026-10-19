package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.port.StylePreferencePort
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
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int plus the style id string. A missing or unknown version, or an
 * unrecognized id, reads as the catalog default (`normal-caps-casual`) — an
 * explicit invalidation, never a silent reinterpretation — and the next save
 * rewrites the record at v1.
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

    override suspend fun selectStyle(styleId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_STYLE_ID] = styleId
        }
    }

    private fun Preferences.toStyleId(): String {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return DEFAULT_STYLE_ID
        val id = this[KEY_STYLE_ID] ?: return DEFAULT_STYLE_ID
        // byId resolves unknown ids to the default — a removed style can
        // never wedge the pipeline; .id normalises back to the stored id.
        return StyleCatalog.byId(id).id
    }

    private companion object {
        /** The style an absent or invalidated record reads as (ENC-72: today's baseline behavior). */
        val DEFAULT_STYLE_ID: String = StyleCatalog.DEFAULT.id
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("style_schema_version")
        val KEY_STYLE_ID = stringPreferencesKey("selected_style_id")
    }
}
