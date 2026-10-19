package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.ApiKeyPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-written fake of [ApiKeyPort]. The stored value stands in for the
 * encrypted record — tests assert on presence and round-trip only, and
 * never print it.
 */
class FakeApiKeyPort(
    initialKey: String? = null,
) : ApiKeyPort {

    private var storedKey: String? = initialKey

    private val mutableHasKey = MutableStateFlow(initialKey != null)

    override val hasKey: StateFlow<Boolean> = mutableHasKey.asStateFlow()

    var saveCallCount: Int = 0
        private set

    var clearCallCount: Int = 0
        private set

    override suspend fun saveKey(key: String) {
        saveCallCount++
        storedKey = key
        mutableHasKey.value = true
    }

    override suspend fun clearKey() {
        clearCallCount++
        storedKey = null
        mutableHasKey.value = false
    }

    override suspend fun currentKey(): String? = storedKey

    /** Sets the stored value without counting a save call. */
    fun setKey(key: String?) {
        storedKey = key
        mutableHasKey.value = key != null
    }
}
