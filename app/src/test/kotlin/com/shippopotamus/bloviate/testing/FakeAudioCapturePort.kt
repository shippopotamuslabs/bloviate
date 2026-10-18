package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.AudioCapturePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

/**
 * Hand-written fake of [AudioCapturePort]. Chunks are emitted by the test via
 * [emit]; emissions are buffered, so a chunk emitted just before collection
 * starts is still delivered.
 */
class FakeAudioCapturePort : AudioCapturePort {

    private val chunkFlow = MutableSharedFlow<FloatArray>(extraBufferCapacity = CHUNK_BUFFER_CAPACITY)

    /** How many times [chunks] collection has started (i.e., capture sessions begun). */
    var collectionCount: Int = 0
        private set

    override fun chunks(): Flow<FloatArray> =
        chunkFlow.onStart { collectionCount++ }

    fun emit(chunk: FloatArray) {
        check(chunkFlow.tryEmit(chunk)) { "Fake capture buffer full; emit fewer chunks per step" }
    }

    private companion object {
        const val CHUNK_BUFFER_CAPACITY: Int = 256
    }
}
