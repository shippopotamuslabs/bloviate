package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStylesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CustomStyleStorePort] backed by the shared preferences DataStore
 * (BLO-27 S2) — the same injected instance the dictionary and style
 * preference stores share, with `custom_style*` namespaced keys, so no
 * existing prefs file is orphaned by a new DataStore file.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2):
 * a version int, the owning account uid, the index as one JSON array of
 * ids, one JSON record per id (`custom_style:<id>`), and the last sync
 * timestamp (epoch millis, written by [replaceSnapshot] only).
 *
 * Account ownership: records are exposed only while the cache owner
 * matches the current signed-in uid. A mismatch — including signed-out
 * or a cache that was never claimed — exposes an empty list (loaded
 * once the first snapshot arrives). [replaceSnapshot] claims the cache
 * for the current uid; while signed out it writes nothing. [save] and
 * [delete] only touch a cache owned by the current uid, so one account
 * can never rewrite another's cached styles.
 *
 * There is no sign-out wipe hook: the dictionary store (the named
 * precedent) is device-scoped with no clearing either, so sign-out
 * hiding falls out of the owner gate — the previous owner's records
 * stay on disk and reappear if that account signs back in. A later
 * server-sync slice owns cross-account reconciliation via
 * [CustomStyleRecord.revision].
 *
 * Corruption never crashes: an unknown schema version or an unparsable
 * index reads as empty (loaded); a single unparsable record is dropped
 * while its siblings survive. A later [replaceSnapshot] heals any of it.
 */
@Singleton
class DataStoreCustomStyleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val auth: AuthSessionPort,
    scope: CoroutineScope,
) : CustomStyleStorePort {

    override val state: StateFlow<CustomStylesState> = combine(
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            },
        auth.authState,
    ) { preferences, authState ->
        preferences.toCustomStylesState(
            currentUid = (authState as? AuthState.Authenticated)?.user?.uid,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CustomStylesState(styles = emptyList(), isLoaded = false),
    )

    override suspend fun save(record: CustomStyleRecord) {
        val uid = auth.currentUid() ?: return
        dataStore.edit { preferences ->
            if (preferences[KEY_OWNER_UID] != uid) return@edit
            preferences[stringPreferencesKey(RECORD_KEY_PREFIX + record.definition.id)] =
                json.encodeToString(CustomStyleDto.from(record))
            val ids = preferences.decodeIndex().filterNot { id -> id == record.definition.id } +
                record.definition.id
            preferences.writeIndex(ids)
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
        }
    }

    override suspend fun delete(styleId: String) {
        val uid = auth.currentUid() ?: return
        dataStore.edit { preferences ->
            if (preferences[KEY_OWNER_UID] != uid) return@edit
            preferences.remove(stringPreferencesKey(RECORD_KEY_PREFIX + styleId))
            preferences.writeIndex(preferences.decodeIndex().filterNot { id -> id == styleId })
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
        }
    }

    override suspend fun replaceSnapshot(records: List<CustomStyleRecord>) {
        val uid = auth.currentUid() ?: return
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_OWNER_UID] = uid
            preferences.writeIndex(records.map { record -> record.definition.id })
            val wanted = records.map { record -> RECORD_KEY_PREFIX + record.definition.id }.toSet()
            preferences.asMap().keys
                .map { key -> key.name }
                .filter { name -> name.startsWith(RECORD_KEY_PREFIX) && name !in wanted }
                .forEach { name -> preferences.remove(stringPreferencesKey(name)) }
            records.forEach { record ->
                preferences[stringPreferencesKey(RECORD_KEY_PREFIX + record.definition.id)] =
                    json.encodeToString(CustomStyleDto.from(record))
            }
            preferences[KEY_LAST_SYNCED_AT] = System.currentTimeMillis()
        }
    }

    private fun Preferences.toCustomStylesState(currentUid: String?): CustomStylesState {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return emptyLoaded()
        val owner = this[KEY_OWNER_UID]
        if (owner == null || owner != currentUid) return emptyLoaded()
        val records = decodeIndex().mapNotNull { id ->
            this[stringPreferencesKey(RECORD_KEY_PREFIX + id)]?.decodeRecord()
        }
        return CustomStylesState(styles = records, isLoaded = true)
    }

    /** The persisted index, or empty when it is missing or unparsable — never null, never a crash. */
    private fun Preferences.decodeIndex(): List<String> {
        val raw = this[KEY_STYLE_IDS] ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrNull()
            ?.filter { id -> id.isNotBlank() }
            ?: emptyList()
    }

    /** Decodes one persisted record, or null when it is malformed (dropped, siblings survive). */
    private fun String.decodeRecord(): CustomStyleRecord? =
        runCatching { json.decodeFromString<CustomStyleDto>(this) }
            .getOrNull()
            ?.toRecord()

    private fun MutablePreferences.writeIndex(ids: List<String>) {
        this[KEY_STYLE_IDS] = json.encodeToString(ids)
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        const val RECORD_KEY_PREFIX: String = "custom_style:"
        val KEY_SCHEMA_VERSION = intPreferencesKey("custom_styles_schema_version")
        val KEY_OWNER_UID = stringPreferencesKey("custom_styles_owner_uid")
        val KEY_STYLE_IDS = stringPreferencesKey("custom_style_ids")
        val KEY_LAST_SYNCED_AT = longPreferencesKey("custom_styles_last_synced_at")
        val json: Json = Json { ignoreUnknownKeys = true }
    }
}

/** The loaded-but-empty snapshot every gated, corrupt, or versioned-out read collapses to. */
private fun emptyLoaded(): CustomStylesState = CustomStylesState(styles = emptyList(), isLoaded = true)
