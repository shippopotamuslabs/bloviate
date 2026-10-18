package com.shippopotamus.bloviate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shippopotamus.bloviate.data.AndroidBubbleOverlayAccess
import com.shippopotamus.bloviate.data.BubbleServiceStateHolder
import com.shippopotamus.bloviate.data.DataStoreBubblePositionStore
import com.shippopotamus.bloviate.domain.port.BubbleActionsPort
import com.shippopotamus.bloviate.domain.port.BubbleOverlayAccessPort
import com.shippopotamus.bloviate.domain.port.BubblePositionPort
import com.shippopotamus.bloviate.domain.port.BubbleServiceStatePort
import com.shippopotamus.bloviate.service.LoggingBubbleActions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the bubble-overlay domain ports to their data/service implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BubbleModule {

    @Binds
    abstract fun bindBubblePositionPort(store: DataStoreBubblePositionStore): BubblePositionPort

    @Binds
    abstract fun bindBubbleServiceStatePort(holder: BubbleServiceStateHolder): BubbleServiceStatePort

    @Binds
    abstract fun bindBubbleOverlayAccessPort(access: AndroidBubbleOverlayAccess): BubbleOverlayAccessPort

    @Binds
    abstract fun bindBubbleActionsPort(actions: LoggingBubbleActions): BubbleActionsPort

    companion object {

        @Provides
        @Singleton
        fun provideBubblePositionDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(BUBBLE_POSITION_DATASTORE_FILE)
            }

        private const val BUBBLE_POSITION_DATASTORE_FILE: String = "bubble_position"
    }
}
