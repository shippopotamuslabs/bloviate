package com.shippopotamus.bloviate.di

import com.shippopotamus.bloviate.data.AndroidBatteryExemptionAccess
import com.shippopotamus.bloviate.data.AndroidWatcherAccess
import com.shippopotamus.bloviate.data.FieldFocusStateHolder
import com.shippopotamus.bloviate.data.KeyboardVisibilityStateHolder
import com.shippopotamus.bloviate.data.ShadePresenceStateHolder
import com.shippopotamus.bloviate.data.WatcherConnectionStateHolder
import com.shippopotamus.bloviate.domain.port.BatteryExemptionPort
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
import com.shippopotamus.bloviate.domain.port.ShadePresencePort
import com.shippopotamus.bloviate.domain.port.WatcherAccessPort
import com.shippopotamus.bloviate.domain.port.WatcherConnectionPort
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
    abstract fun bindKeyboardVisibilityPort(holder: KeyboardVisibilityStateHolder): KeyboardVisibilityPort

    @Binds
    abstract fun bindShadePresencePort(holder: ShadePresenceStateHolder): ShadePresencePort

    @Binds
    abstract fun bindWatcherAccessPort(access: AndroidWatcherAccess): WatcherAccessPort

    /** Watcher connection state for supervision (ENC-129): the watcher publishes, Setup consumes. */
    @Binds
    abstract fun bindWatcherConnectionPort(holder: WatcherConnectionStateHolder): WatcherConnectionPort

    @Binds
    abstract fun bindBatteryExemptionPort(access: AndroidBatteryExemptionAccess): BatteryExemptionPort
}
