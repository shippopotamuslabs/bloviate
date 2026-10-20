package com.shippopotamus.bloviate.data

import android.content.Context
import android.os.PowerManager
import com.shippopotamus.bloviate.domain.port.BatteryExemptionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [BatteryExemptionPort] backed by [PowerManager.isIgnoringBatteryOptimizations]. */
@Singleton
class AndroidBatteryExemptionAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : BatteryExemptionPort {

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
