package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreDictionaryStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreDictionaryStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("dictionary-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreDictionaryStore(dataStore, storeScope)
    }

    @Test
    fun `a fresh store reads as the seeded starter dictionary`() = runTest {
        assertEquals(PersonalDictionary.SEED, store.dictionary.value)
    }

    @Test
    fun `an added word persists across store instances`() = runTest {
        assertEquals(DictionaryEditResult.APPLIED, store.addWord("Tensor"))

        assertEquals(PersonalDictionary.SEED.words + "Tensor", store.dictionary.value.words)

        val reread = DataStoreDictionaryStore(dataStore, storeScope)
        assertEquals(PersonalDictionary.SEED.words + "Tensor", reread.dictionary.value.words)
    }

    @Test
    fun `deleting every seed persists as empty instead of resurrecting the seeds`() = runTest {
        PersonalDictionary.SEED.words.forEach { word -> store.removeWord(word) }
        PersonalDictionary.SEED.corrections.forEach { correction ->
            store.removeCorrection(correction.heardAs)
        }

        assertEquals(PersonalDictionary.EMPTY, store.dictionary.value)

        val reread = DataStoreDictionaryStore(dataStore, storeScope)
        assertEquals(PersonalDictionary.EMPTY, reread.dictionary.value)
    }

    @Test
    fun `a rejected edit writes nothing`() = runTest {
        assertEquals(DictionaryEditResult.DUPLICATE, store.addWord("bloviate"))
        assertEquals(
            DictionaryEditResult.SELF_CORRECTION,
            store.addCorrection("same", "same"),
        )

        // No record was ever written: a fresh instance still reads the seeds.
        val reread = DataStoreDictionaryStore(dataStore, storeScope)
        assertEquals(PersonalDictionary.SEED, reread.dictionary.value)
    }

    @Test
    fun `corrections round trip with phrases and casing intact`() = runTest {
        assertEquals(
            DictionaryEditResult.APPLIED,
            store.addCorrection("my kebab place", "Kebabistan"),
        )

        val reread = DataStoreDictionaryStore(dataStore, storeScope)
        assertEquals(
            PersonalDictionary.Correction(heardAs = "my kebab place", replacement = "Kebabistan"),
            reread.dictionary.value.corrections.last(),
        )
    }

    @Test
    fun `an unknown schema version invalidates the record to the seed set`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("dictionary_schema_version")] = 99
            preferences[stringPreferencesKey("dictionary_words")] = "ShouldNotSurvive"
        }

        // The stored word is not reinterpreted: an unknown version reads as seeds.
        assertEquals(PersonalDictionary.SEED, store.dictionary.value)
    }

    @Test
    fun `malformed correction lines are dropped on read while valid lines survive`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("dictionary_schema_version")] = 1
            preferences[stringPreferencesKey("dictionary_words")] = "Kept"
            preferences[stringPreferencesKey("dictionary_corrections")] =
                "no-tab-on-this-line\ngood pair\tGood Pair\n\tblank heard side"
        }

        val parsed = store.dictionary.value
        assertEquals(listOf("Kept"), parsed.words)
        assertEquals(
            listOf(PersonalDictionary.Correction(heardAs = "good pair", replacement = "Good Pair")),
            parsed.corrections,
        )
    }
}
