package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint
import com.shippopotamus.bloviate.domain.port.BubbleAppearancePort
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
 * [BubbleAppearancePort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int plus opacity (float) and the size/shape/tint enum names. A
 * missing or unknown version reads as [BubbleAppearance.DEFAULT] — an
 * explicit invalidation, never a silent reinterpretation. Within a v1
 * record each field invalidates independently: an unrecognized enum name
 * falls back to that field's default (a removed silhouette can never
 * wedge the bubble), and opacity is coerced into its legal range on read
 * so a corrupt value can never render the bubble invisible. One rename
 * is migrated explicitly rather than left to that fallback: a stored
 * CIRCLE shape (removed in ENC-122, where it was one shape offered
 * twice) reads as its surviving twin PILL. The next save rewrites the
 * record at v1.
 */
@Singleton
class DataStoreBubbleAppearanceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : BubbleAppearancePort {

    override val appearance: StateFlow<BubbleAppearance> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toBubbleAppearance() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BubbleAppearance.DEFAULT,
        )

    override suspend fun save(appearance: BubbleAppearance) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_OPACITY] = appearance.opacity.coerceIn(
                BubbleAppearance.MIN_OPACITY,
                BubbleAppearance.MAX_OPACITY,
            )
            preferences[KEY_SIZE] = appearance.size.name
            preferences[KEY_SHAPE] = appearance.shape.name
            preferences[KEY_TINT] = appearance.tint.name
        }
    }

    private fun Preferences.toBubbleAppearance(): BubbleAppearance {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return BubbleAppearance.DEFAULT
        return BubbleAppearance(
            opacity = (this[KEY_OPACITY] ?: BubbleAppearance.MAX_OPACITY).coerceIn(
                BubbleAppearance.MIN_OPACITY,
                BubbleAppearance.MAX_OPACITY,
            ),
            size = enumOrDefault(this[KEY_SIZE], BubbleSize.MEDIUM),
            shape = shapeOrDefault(this[KEY_SHAPE]),
            tint = enumOrDefault(this[KEY_TINT], BubbleTint.INDIGO),
        )
    }

    /**
     * The stored shape name, migrated: CIRCLE — a real v1 value until
     * ENC-122 removed it as a duplicate of PILL — reads as PILL by
     * explicit rename, not by falling into the unknown-name fallback.
     * Any other absent or unrecognized name takes the field default.
     */
    private fun shapeOrDefault(name: String?): BubbleShape =
        when (name) {
            LEGACY_SHAPE_CIRCLE -> BubbleShape.PILL
            else -> enumOrDefault(name, BubbleShape.PILL)
        }

    private companion object {
        const val SCHEMA_VERSION: Int = 1

        /** The shape name stored by pre-ENC-122 builds for what is now [BubbleShape.PILL]. */
        const val LEGACY_SHAPE_CIRCLE: String = "CIRCLE"
        val KEY_SCHEMA_VERSION = intPreferencesKey("bubble_appearance_schema_version")
        val KEY_OPACITY = floatPreferencesKey("bubble_opacity")
        val KEY_SIZE = stringPreferencesKey("bubble_size")
        val KEY_SHAPE = stringPreferencesKey("bubble_shape")
        val KEY_TINT = stringPreferencesKey("bubble_tint")
    }
}

/** The enum named [name], or [default] when the name is absent or no longer a value. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() } ?: default
