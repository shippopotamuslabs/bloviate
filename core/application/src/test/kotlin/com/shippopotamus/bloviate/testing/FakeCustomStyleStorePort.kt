package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStylesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hand-written fake of [CustomStyleStorePort]: a plain in-memory cache.
 * Starts loaded-but-empty (the steady state); pass another snapshot to
 * start elsewhere, e.g. unloaded for cold-start scenarios. Unlike the
 * real store, `save` stores as given — validation is the use case's job.
 */
class FakeCustomStyleStorePort(
    initial: CustomStylesState = CustomStylesState(styles = emptyList(), isLoaded = true),
) : CustomStyleStorePort {

    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<CustomStylesState> = mutableState.asStateFlow()

    override suspend fun save(record: CustomStyleRecord) {
        mutableState.update { current ->
            current.copy(styles = current.styles.filterNot { existing -> existing.definition.id == record.definition.id } + record)
        }
    }

    override suspend fun delete(styleId: String) {
        mutableState.update { current ->
            current.copy(styles = current.styles.filterNot { existing -> existing.definition.id == styleId })
        }
    }

    override suspend fun replaceSnapshot(records: List<CustomStyleRecord>) {
        mutableState.value = CustomStylesState(styles = records, isLoaded = true)
    }

    /** Publishes [state] directly — for arranging loaded/unloaded snapshots without edit semantics. */
    fun setState(state: CustomStylesState) {
        mutableState.value = state
    }
}
