package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.BatteryExemptionPort
import javax.inject.Inject

/** One-shot read of whether battery optimization leaves Bloviate alone (ENC-129). */
class IsBatteryOptimizationExemptUseCase @Inject constructor(
    private val batteryExemptionPort: BatteryExemptionPort,
) {

    operator fun invoke(): Boolean = batteryExemptionPort.isIgnoringBatteryOptimizations()
}
