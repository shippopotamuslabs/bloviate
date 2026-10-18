package com.shippopotamus.bloviate.di

import android.content.Context
import com.shippopotamus.bloviate.data.AndroidMicPermissionAccess
import com.shippopotamus.bloviate.data.HttpModelFileDownloader
import com.shippopotamus.bloviate.data.LibraryAudioCapture
import com.shippopotamus.bloviate.data.ModelFileDownloader
import com.shippopotamus.bloviate.data.OnDeviceWhisperTranscriptionEngine
import com.shippopotamus.bloviate.data.RecordingSessionManager
import com.shippopotamus.bloviate.data.WhisperModelStore
import com.shippopotamus.bloviate.domain.evaluator.BaselineTextPostProcessor
import com.shippopotamus.bloviate.domain.port.AudioCapturePort
import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import com.shippopotamus.bloviate.domain.port.RecordingSessionPort
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import com.shippopotamus.bloviate.domain.port.TranscriptionEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

/** Binds the voice-plumbing ports (model store, capture, session, engine) to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    abstract fun bindTranscriptionEngine(engine: OnDeviceWhisperTranscriptionEngine): TranscriptionEngine

    @Binds
    abstract fun bindAudioCapturePort(capture: LibraryAudioCapture): AudioCapturePort

    @Binds
    abstract fun bindMicPermissionPort(access: AndroidMicPermissionAccess): MicPermissionPort

    @Binds
    abstract fun bindRecordingSessionPort(session: RecordingSessionManager): RecordingSessionPort

    @Binds
    abstract fun bindTextPostProcessor(processor: BaselineTextPostProcessor): TextPostProcessor

    companion object {

        @Provides
        @Singleton
        fun provideModelFileDownloader(): ModelFileDownloader = HttpModelFileDownloader()

        @Provides
        @Singleton
        fun provideModelStorePort(
            @ApplicationContext context: Context,
            downloader: ModelFileDownloader,
        ): ModelStorePort =
            WhisperModelStore(
                modelDir = File(context.filesDir, MODEL_DIR_NAME),
                downloader = downloader,
            )

        /** Scope owning the recording session's capture/availability coroutines. */
        @Provides
        fun provideRecordingSessionScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private const val MODEL_DIR_NAME: String = "models"
    }
}
