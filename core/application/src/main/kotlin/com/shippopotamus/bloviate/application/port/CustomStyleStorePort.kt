package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's custom writing styles (ENC-199), cached per account. The
 * cache is account-owned: signing out clears it the way the dictionary
 * store clears its own account data, and a later server-sync slice will
 * reconcile [CustomStyleRecord.revision] against the cloud copy.
 *
 * Implementations publish the persisted snapshot on [state] (flipping
 * [CustomStylesState.isLoaded] once it has arrived) and apply [save],
 * [delete], and [replaceSnapshot] to the cache. [save] stores the record
 * as given — validation lives in `SaveCustomStyleUseCase`, not here — and
 * replaces any record sharing the definition id. [replaceSnapshot] swaps
 * the whole cache for a freshly loaded or synced set and marks it loaded.
 */
interface CustomStyleStorePort {

    /** The cached customs plus whether the persisted snapshot has arrived. */
    val state: StateFlow<CustomStylesState>

    /** Inserts or replaces [record] by definition id. */
    suspend fun save(record: CustomStyleRecord)

    /** Removes the custom style with [styleId]; unknown ids are a no-op. */
    suspend fun delete(styleId: String)

    /** Replaces the whole cache with [records] and marks it loaded. */
    suspend fun replaceSnapshot(records: List<CustomStyleRecord>)
}
