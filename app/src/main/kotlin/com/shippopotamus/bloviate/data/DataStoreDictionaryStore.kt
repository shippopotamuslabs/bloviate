package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.application.port.DictionaryPort
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
 * [DictionaryPort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2): a
 * version int, the words as one entry per line, and the corrections as
 * one `heardAs ␉ replacement` line each. The line/tab encoding is safe
 * by construction: [PersonalDictionary.normalizeEntry] collapses every
 * whitespace run in a stored entry to a single space, so no entry can
 * contain a line break or a tab.
 *
 * Seeding and invalidation: a record with **no version key** has never
 * been written — it reads as [PersonalDictionary.SEED], and the first
 * edit persists the full (seed-derived) lists at v1. A record at an
 * **unknown version** invalidates to the seed set rather than being
 * silently reinterpreted. A v1 record whose lists are empty is the user
 * having deleted everything — it stays empty; seeds never resurrect.
 * Malformed correction lines (no tab, a blank side) are dropped on read.
 *
 * Edits run their read-modify-write inside a single `DataStore.edit`
 * transaction, so the outcome is always computed against the persisted
 * snapshot, never a stale in-memory copy.
 */
@Singleton
class DataStoreDictionaryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : DictionaryPort {

    override val dictionary: StateFlow<PersonalDictionary> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toPersonalDictionary() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = PersonalDictionary.SEED,
        )

    override suspend fun addWord(word: String): DictionaryEditResult =
        editDictionary { current -> current.withWordAdded(word) }

    override suspend fun removeWord(word: String): DictionaryEditResult =
        editDictionary { current -> current.withWordRemoved(word) }

    override suspend fun addCorrection(heardAs: String, replacement: String): DictionaryEditResult =
        editDictionary { current -> current.withCorrectionAdded(heardAs, replacement) }

    override suspend fun removeCorrection(heardAs: String): DictionaryEditResult =
        editDictionary { current -> current.withCorrectionRemoved(heardAs) }

    /** Applies [edit] against the persisted dictionary inside one transaction; persists only on APPLIED. */
    private suspend fun editDictionary(
        edit: (PersonalDictionary) -> Pair<DictionaryEditResult, PersonalDictionary>,
    ): DictionaryEditResult {
        var result = DictionaryEditResult.NOT_FOUND
        dataStore.edit { preferences ->
            val (outcome, updated) = edit(preferences.toPersonalDictionary())
            result = outcome
            if (outcome == DictionaryEditResult.APPLIED) {
                preferences.writePersonalDictionary(updated)
            }
        }
        return result
    }

    private fun Preferences.toPersonalDictionary(): PersonalDictionary {
        val version = this[KEY_SCHEMA_VERSION] ?: return PersonalDictionary.SEED
        if (version != SCHEMA_VERSION) return PersonalDictionary.SEED
        val words = this[KEY_WORDS]
            ?.split(ENTRY_SEPARATOR)
            ?.map { entry -> entry.trim() }
            ?.filter { entry -> entry.isNotEmpty() }
            ?: emptyList()
        val corrections = this[KEY_CORRECTIONS]
            ?.split(ENTRY_SEPARATOR)
            ?.mapNotNull { line -> line.toCorrection() }
            ?: emptyList()
        return PersonalDictionary(words = words, corrections = corrections)
    }

    /** A persisted correction line: `heardAs ␉ replacement`; anything malformed parses to null and is dropped. */
    private fun String.toCorrection(): PersonalDictionary.Correction? {
        val separatorIndex = indexOf(FIELD_SEPARATOR)
        if (separatorIndex < 0) return null
        val heardAs = substring(0, separatorIndex).trim()
        val replacement = substring(separatorIndex + 1).trim()
        if (heardAs.isEmpty() || replacement.isEmpty()) return null
        return PersonalDictionary.Correction(heardAs = heardAs, replacement = replacement)
    }

    private fun MutablePreferences.writePersonalDictionary(dictionary: PersonalDictionary) {
        this[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
        this[KEY_WORDS] = dictionary.words.joinToString(ENTRY_SEPARATOR)
        this[KEY_CORRECTIONS] = dictionary.corrections.joinToString(ENTRY_SEPARATOR) { correction ->
            "${correction.heardAs}$FIELD_SEPARATOR${correction.replacement}"
        }
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        const val ENTRY_SEPARATOR: String = "\n"
        const val FIELD_SEPARATOR: String = "\t"
        val KEY_SCHEMA_VERSION = intPreferencesKey("dictionary_schema_version")
        val KEY_WORDS = stringPreferencesKey("dictionary_words")
        val KEY_CORRECTIONS = stringPreferencesKey("dictionary_corrections")
    }
}
