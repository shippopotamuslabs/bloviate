package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.port.BubbleAppearancePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [BubbleAppearancePort]: an in-memory StateFlow plus a save record. */
class FakeBubbleAppearancePort : BubbleAppearancePort {

    private val mutableAppearance = MutableStateFlow(BubbleAppearance.DEFAULT)

    override val appearance: StateFlow<BubbleAppearance> = mutableAppearance.asStateFlow()

    val savedAppearances = mutableListOf<BubbleAppearance>()

    var saveCallCount: Int = 0
        private set

    override suspend fun save(appearance: BubbleAppearance) {
        saveCallCount++
        savedAppearances += appearance
        mutableAppearance.value = appearance
    }

    /** Publishes an appearance as if another writer (the store) had saved it. */
    fun publish(appearance: BubbleAppearance) {
        mutableAppearance.value = appearance
    }
}
