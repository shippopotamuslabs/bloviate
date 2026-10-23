package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.application.port.ResidentPresencePort
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
 * [ResidentPresencePort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2):
 * a version int, the auto-start flag, and the user-stopped flag. A
 * missing or unknown version reads as [ResidentPresenceState.DEFAULT]
 * — an explicit invalidation, never a silent reinterpretation. Within
 * a v1 record each field falls back independently, and each fallback
 * is the reading that cannot strand the user: a missing auto-start
 * flag reads as on (presence is the product's default posture; the
 * Setup switch is an explicit opt-out, and only an explicit write
 * opts out), and a missing stopped flag reads as not stopped (only an
 * explicit Stop may keep the bubble down). The next save rewrites the
 * record at v1.
 */
@Singleton
class DataStoreResidentPresenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ResidentPresencePort {

    override val state: StateFlow<ResidentPresenceState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toResidentPresenceState() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ResidentPresenceState.DEFAULT,
        )

    override suspend fun save(state: ResidentPresenceState) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_AUTO_START_ENABLED] = state.autoStartEnabled
            preferences[KEY_USER_STOPPED] = state.userStopped
        }
    }

    private fun Preferences.toResidentPresenceState(): ResidentPresenceState {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return ResidentPresenceState.DEFAULT
        return ResidentPresenceState(
            autoStartEnabled = this[KEY_AUTO_START_ENABLED] ?: true,
            userStopped = this[KEY_USER_STOPPED] ?: false,
        )
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("resident_presence_schema_version")
        val KEY_AUTO_START_ENABLED = booleanPreferencesKey("resident_presence_auto_start_enabled")
        val KEY_USER_STOPPED = booleanPreferencesKey("resident_presence_user_stopped")
    }
}
