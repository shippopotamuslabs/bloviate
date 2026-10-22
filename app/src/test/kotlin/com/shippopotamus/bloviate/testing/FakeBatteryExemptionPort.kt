package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.BatteryExemptionPort

/** Hand-written fake of [BatteryExemptionPort]; flip [exempt] to simulate the system list. */
class FakeBatteryExemptionPort : BatteryExemptionPort {

    var exempt: Boolean = false

    override fun isIgnoringBatteryOptimizations(): Boolean = exempt
}
