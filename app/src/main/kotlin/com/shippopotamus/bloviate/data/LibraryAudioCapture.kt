package com.shippopotamus.bloviate.data

import android.content.Context
import com.shippopotamus.bloviate.application.port.AudioCapturePort
import com.shippopotamus.whisper.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioCapturePort] over the whisper-android library's [AudioRecorder]
 * (16 kHz mono PCM16 normalized to floats — Enclave's capture lineage). A
 * fresh recorder is created per collection; the recorder owns the
 * `AudioRecord` lifecycle and releases it when collection is cancelled.
 */
@Singleton
class LibraryAudioCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioCapturePort {

    override fun chunks(): Flow<FloatArray> = AudioRecorder(context).audioChunks()
}
