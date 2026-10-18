package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [EnginePreferencePort]; drive the selection directly. */
class FakeEnginePreferencePort : EnginePreferencePort {

    private val mutableSelected = MutableStateFlow(DictationEngine.WHISPER)

    override val selectedEngine: StateFlow<DictationEngine> = mutableSelected.asStateFlow()

    var selectCallCount: Int = 0
        private set

    override suspend fun selectEngine(engine: DictationEngine) {
        selectCallCount++
        mutableSelected.value = engine
    }

    /** Sets the selection without counting a select call. */
    fun setSelected(engine: DictationEngine) {
        mutableSelected.value = engine
    }
}
