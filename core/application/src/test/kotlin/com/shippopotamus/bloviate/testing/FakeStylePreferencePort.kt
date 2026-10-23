package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-written fake of [StylePreferencePort]; drive the selection
 * directly. The default initial value mirrors the real store's default
 * (the catalog default); pass another id to start elsewhere.
 */
class FakeStylePreferencePort(
    initial: String = StyleCatalog.DEFAULT.id,
) : StylePreferencePort {

    private val mutableSelected = MutableStateFlow(initial)

    override val selectedStyleId: StateFlow<String> = mutableSelected.asStateFlow()

    var selectCallCount: Int = 0
        private set

    override suspend fun selectStyle(styleId: String) {
        selectCallCount++
        mutableSelected.value = styleId
    }

    /** Sets the selection without counting a select call. */
    fun setSelected(styleId: String) {
        mutableSelected.value = styleId
    }
}
