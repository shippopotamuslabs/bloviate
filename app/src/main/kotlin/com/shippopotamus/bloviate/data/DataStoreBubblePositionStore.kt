package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.port.BubblePositionPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BubblePositionPort] backed by preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int plus the two normalized fractions. A missing or unknown version
 * is treated as "no saved position" — an explicit invalidation, never a
 * silent reinterpretation — and the next save rewrites the record at v1.
 */
@Singleton
class DataStoreBubblePositionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BubblePositionPort {

    override val position: Flow<BubblePosition?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toBubblePosition() }

    override suspend fun save(position: BubblePosition) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_X_FRACTION] = position.xFraction
            preferences[KEY_Y_FRACTION] = position.yFraction
        }
    }

    private fun Preferences.toBubblePosition(): BubblePosition? {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return null
        val xFraction = this[KEY_X_FRACTION] ?: return null
        val yFraction = this[KEY_Y_FRACTION] ?: return null
        return BubblePosition(xFraction = xFraction, yFraction = yFraction)
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("bubble_position_schema_version")
        val KEY_X_FRACTION = floatPreferencesKey("bubble_position_x_fraction")
        val KEY_Y_FRACTION = floatPreferencesKey("bubble_position_y_fraction")
    }
}
