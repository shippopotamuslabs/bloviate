package com.shippopotamus.bloviate.di

import android.content.Context
import com.shippopotamus.bloviate.data.AndroidMicPermissionAccess
import com.shippopotamus.bloviate.data.DataStoreDictionaryStore
import com.shippopotamus.bloviate.data.DataStoreEnginePreferenceStore
import com.shippopotamus.bloviate.data.HttpModelFileDownloader
import com.shippopotamus.bloviate.data.LibraryAudioCapture
import com.shippopotamus.bloviate.data.ModelFileDownloader
import com.shippopotamus.bloviate.data.ParakeetModelFileSet
import com.shippopotamus.bloviate.data.ParakeetModelStore
import com.shippopotamus.bloviate.data.RecordingSessionManager
import com.shippopotamus.bloviate.data.SelectedModelStore
import com.shippopotamus.bloviate.data.SelectingTranscriptionEngine
import com.shippopotamus.bloviate.data.WhisperModelStore
import com.shippopotamus.bloviate.data.DataStoreStylePreferenceStore
import com.shippopotamus.bloviate.data.FirebaseStyleClient
import com.shippopotamus.bloviate.data.FirebaseStyleFunctionGateway
import com.shippopotamus.bloviate.data.StyleFunctionGateway
import com.shippopotamus.bloviate.application.evaluator.StyleAwareTextPostProcessor
import com.shippopotamus.bloviate.application.port.ActiveModelStorePort
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AudioCapturePort
import com.shippopotamus.bloviate.application.port.DictionaryPort
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import com.shippopotamus.bloviate.application.port.LlmStylePort
import com.shippopotamus.bloviate.application.port.MicPermissionPort
import com.shippopotamus.bloviate.application.port.ModelStorePort
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import com.shippopotamus.bloviate.application.port.RecordingSessionPort
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.port.TextPostProcessor
import com.shippopotamus.bloviate.application.port.TranscriptionEngine
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

/**
 * Binds the voice-plumbing ports (model store, capture, session, engine)
 * to their implementations. The style pipeline's LLM stage (ENC-73) lives
 * here too — not in BubbleModule, which only provides the shared DataStore
 * file — because VoiceModule owns the cleanup pipeline and everything the
 * send path runs through.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    /** The bound engine is the selector: it delegates to Whisper or Parakeet per the persisted preference (ENC-54). */
    @Binds
    abstract fun bindTranscriptionEngine(engine: SelectingTranscriptionEngine): TranscriptionEngine

    @Binds
    abstract fun bindEnginePreferencePort(store: DataStoreEnginePreferenceStore): EnginePreferencePort

    @Binds
    abstract fun bindActiveModelStorePort(store: SelectedModelStore): ActiveModelStorePort

    @Binds
    abstract fun bindAudioCapturePort(capture: LibraryAudioCapture): AudioCapturePort

    @Binds
    abstract fun bindMicPermissionPort(access: AndroidMicPermissionAccess): MicPermissionPort

    @Binds
    abstract fun bindRecordingSessionPort(session: RecordingSessionManager): RecordingSessionPort

    /**
     * The bound cleanup chain (ENC-72): dictionary corrections + casing
     * over the live dictionary, then the baseline, then the user's style —
     * see [StyleAwareTextPostProcessor] for the ordering contract. The
     * dictionary-aware processor stays injectable as the style processor's
     * delegate; it is simply no longer the bound seam.
     */
    @Binds
    abstract fun bindTextPostProcessor(processor: StyleAwareTextPostProcessor): TextPostProcessor

    @Binds
    abstract fun bindDictionaryPort(store: DataStoreDictionaryStore): DictionaryPort

    @Binds
    abstract fun bindStylePreferencePort(store: DataStoreStylePreferenceStore): StylePreferencePort

    @Binds
    abstract fun bindStyleFunctionGateway(gateway: FirebaseStyleFunctionGateway): StyleFunctionGateway

    companion object {

        /**
         * The Function-backed styling client (ENC-151): provided rather
         * than bound so the clock stays a defaulted constructor parameter.
         */
        @Provides
        @Singleton
        fun provideLlmStylePort(
            gateway: StyleFunctionGateway,
            auth: AuthSessionPort,
        ): LlmStylePort =
            FirebaseStyleClient(
                gateway = gateway,
                auth = auth,
            )

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

        /** Parakeet's store downloads four files from four URLs, so it gets a downloader factory. */
        @Provides
        @Singleton
        fun provideParakeetModelStorePort(
            @ApplicationContext context: Context,
        ): ParakeetModelStorePort =
            ParakeetModelStore(
                modelDir = File(File(context.filesDir, MODEL_DIR_NAME), ParakeetModelFileSet.MODEL_NAME),
                downloaderFactory = { url -> HttpModelFileDownloader(url = url) },
            )

        /** Scope owning the recording session's capture/availability coroutines. */
        @Provides
        fun provideRecordingSessionScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private const val MODEL_DIR_NAME: String = "models"
    }
}
