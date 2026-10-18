package com.shippopotamus.bloviate.di

import com.shippopotamus.bloviate.data.AndroidWatcherAccess
import com.shippopotamus.bloviate.data.FieldFocusStateHolder
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.WatcherAccessPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the field-watcher domain ports to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FieldWatcherModule {

    @Binds
    abstract fun bindFieldFocusPort(holder: FieldFocusStateHolder): FieldFocusPort

    @Binds
    abstract fun bindWatcherAccessPort(access: AndroidWatcherAccess): WatcherAccessPort
}
