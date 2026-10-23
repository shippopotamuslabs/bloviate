package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStylesState
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeAuthSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreCustomStyleStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var fakeAuth: FakeAuthSession
    private lateinit var store: DataStoreCustomStyleStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("custom-style-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        fakeAuth = FakeAuthSession(FakeAuthSession.authenticated(UID_A))
        store = DataStoreCustomStyleStore(dataStore, fakeAuth, storeScope)
    }

    @Test
    fun `state starts unloaded before the first snapshot arrives`() = runTest {
        val gatedScope = TestScope(StandardTestDispatcher(testScheduler))
        val gated = DataStoreCustomStyleStore(dataStore, fakeAuth, gatedScope)

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = false), gated.state.value)

        advanceUntilIdle()

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), gated.state.value)
    }

    @Test
    fun `a fresh cache reads as loaded and empty`() = runTest {
        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)
    }

    @Test
    fun `replaceSnapshot claims the cache and persists across store instances`() = runTest {
        val records = listOf(record(id = "custom-alpha"), record(id = "custom-beta"))
        store.replaceSnapshot(records)

        assertEquals(records, store.state.value.styles)

        val reread = DataStoreCustomStyleStore(dataStore, fakeAuth, storeScope)
        assertEquals(records, reread.state.value.styles)
    }

    @Test
    fun `replaceSnapshot stamps the owner and the sync time`() = runTest {
        val before = System.currentTimeMillis()
        store.replaceSnapshot(listOf(record(id = "custom-alpha")))
        val after = System.currentTimeMillis()

        val snapshot = dataStore.data.first()
        assertEquals(UID_A, snapshot[stringPreferencesKey("custom_styles_owner_uid")])
        val syncedAt = snapshot[longPreferencesKey("custom_styles_last_synced_at")]
        assertTrue(syncedAt != null && syncedAt in before..after)
    }

    @Test
    fun `save upserts by id and delete removes the entry`() = runTest {
        store.replaceSnapshot(emptyList())
        val first = record(id = "custom-alpha", name = "Alpha")
        val second = record(id = "custom-beta", name = "Beta")
        store.save(first)
        store.save(second)

        assertEquals(listOf(first, second), store.state.value.styles)

        val renamed = record(id = "custom-alpha", name = "Alpha Renamed")
        store.save(renamed)

        assertEquals(listOf(second, renamed), store.state.value.styles)

        store.delete("custom-beta")

        assertEquals(listOf(renamed), store.state.value.styles)

        val reread = DataStoreCustomStyleStore(dataStore, fakeAuth, storeScope)
        assertEquals(listOf(renamed), reread.state.value.styles)
    }

    @Test
    fun `deleting an unknown id is a no-op`() = runTest {
        val records = listOf(record(id = "custom-alpha"))
        store.replaceSnapshot(records)
        store.delete("custom-unknown")

        assertEquals(records, store.state.value.styles)
    }

    @Test
    fun `save on an unclaimed cache is a no-op until replaceSnapshot claims it`() = runTest {
        // Strict owner rule: a null owner never matches the current uid, so
        // the cache must be claimed by a signed-in replaceSnapshot first.
        store.save(record(id = "custom-alpha"))

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)

        store.replaceSnapshot(emptyList())
        store.save(record(id = "custom-alpha"))

        assertEquals(listOf("custom-alpha"), store.state.value.styles.map { it.definition.id })
    }

    @Test
    fun `replaceSnapshot removes stale per-style keys`() = runTest {
        store.replaceSnapshot(listOf(record(id = "custom-alpha"), record(id = "custom-beta")))
        store.replaceSnapshot(listOf(record(id = "custom-gamma")))

        assertEquals(listOf("custom-gamma"), store.state.value.styles.map { it.definition.id })

        val snapshot = dataStore.data.first()
        assertNull(snapshot[stringPreferencesKey("custom_style:custom-alpha")])
        assertNull(snapshot[stringPreferencesKey("custom_style:custom-beta")])
    }

    @Test
    fun `another account sees an empty cache and cannot rewrite it`() = runTest {
        val records = listOf(record(id = "custom-alpha"))
        store.replaceSnapshot(records)

        fakeAuth.publish(FakeAuthSession.authenticated(UID_B))

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)

        store.save(record(id = "custom-intruder"))
        store.delete("custom-alpha")

        fakeAuth.publish(FakeAuthSession.authenticated(UID_A))

        assertEquals(records, store.state.value.styles)
    }

    @Test
    fun `replaceSnapshot claims the cache for the current account`() = runTest {
        store.replaceSnapshot(listOf(record(id = "custom-alpha")))

        fakeAuth.publish(FakeAuthSession.authenticated(UID_B))
        val intruder = listOf(record(id = "custom-intruder"))
        store.replaceSnapshot(intruder)

        assertEquals(intruder, store.state.value.styles)

        fakeAuth.publish(FakeAuthSession.authenticated(UID_A))

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)
    }

    @Test
    fun `signing out hides the cache and writes nothing`() = runTest {
        val records = listOf(record(id = "custom-alpha"))
        store.replaceSnapshot(records)

        fakeAuth.publish(AuthState.Unauthenticated)

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)

        store.save(record(id = "custom-intruder"))
        store.replaceSnapshot(listOf(record(id = "custom-intruder")))

        fakeAuth.publish(FakeAuthSession.authenticated(UID_A))

        assertEquals(records, store.state.value.styles)
    }

    @Test
    fun `an unknown schema version exposes empty instead of crashing`() = runTest {
        store.replaceSnapshot(listOf(record(id = "custom-alpha")))
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("custom_styles_schema_version")] = 99
        }

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)
    }

    @Test
    fun `an unparsable index exposes empty instead of crashing`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("custom_styles_schema_version")] = 1
            preferences[stringPreferencesKey("custom_styles_owner_uid")] = UID_A
            preferences[stringPreferencesKey("custom_style_ids")] = "[not json"
        }

        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)
    }

    @Test
    fun `a malformed record is dropped while its siblings survive`() = runTest {
        store.replaceSnapshot(listOf(record(id = "custom-alpha"), record(id = "custom-beta")))
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("custom_style:custom-beta")] = "{broken json"
        }

        assertEquals(listOf("custom-alpha"), store.state.value.styles.map { it.definition.id })
    }

    @Test
    fun `a later replaceSnapshot heals corruption`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("custom_styles_schema_version")] = 1
            preferences[stringPreferencesKey("custom_styles_owner_uid")] = UID_A
            preferences[stringPreferencesKey("custom_style_ids")] = "[not json"
        }
        assertEquals(CustomStylesState(styles = emptyList(), isLoaded = true), store.state.value)

        val healed = listOf(record(id = "custom-alpha"))
        store.replaceSnapshot(healed)

        assertEquals(healed, store.state.value.styles)
    }

    private fun record(id: String, name: String = "Custom"): CustomStyleRecord =
        CustomStyleRecord(
            definition = StyleDefinition(
                id = id,
                displayName = name,
                description = "A custom style.",
                sample = "Yeah, tomorrow.",
                systemInstructions = "My voice. NEVER change facts.",
                fewShots = listOf(
                    FewShot(raw = "hear this", styled = "Hear this."),
                    FewShot(raw = "try again", styled = "Try again."),
                ),
                kind = StyleKind.LLM,
                category = StyleCategory.CUSTOM,
            ),
            revision = 0L,
            updatedAtEpochMillis = 0L,
        )

    private companion object {
        const val UID_A: String = "uid-a"
        const val UID_B: String = "uid-b"
    }
}
