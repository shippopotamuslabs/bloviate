package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
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
 * [EnginePreferencePort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int plus the engine enum name. A missing or unknown version, or
 * an unrecognized value, reads as the default [DictationEngine.PARAKEET] —
 * an explicit invalidation, never a silent reinterpretation — and the next
 * save rewrites the record at v1.
 */
@Singleton
class DataStoreEnginePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : EnginePreferencePort {

    override val selectedEngine: StateFlow<DictationEngine> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toDictationEngine() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_ENGINE,
        )

    override suspend fun selectEngine(engine: DictationEngine) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_ENGINE] = engine.name
        }
    }

    private fun Preferences.toDictationEngine(): DictationEngine {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return DEFAULT_ENGINE
        val name = this[KEY_ENGINE] ?: return DEFAULT_ENGINE
        return runCatching { DictationEngine.valueOf(name) }.getOrDefault(DEFAULT_ENGINE)
    }

    private companion object {
        /** The engine an absent or invalidated record reads as (ENC-54: Parakeet won the A/B). */
        val DEFAULT_ENGINE: DictationEngine = DictationEngine.PARAKEET
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("engine_preference_schema_version")
        val KEY_ENGINE = stringPreferencesKey("dictation_engine")
    }
}
