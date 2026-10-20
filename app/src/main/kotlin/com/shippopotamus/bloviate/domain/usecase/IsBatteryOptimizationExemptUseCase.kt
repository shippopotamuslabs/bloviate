package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.BatteryExemptionPort
import javax.inject.Inject

/** One-shot read of whether battery optimization leaves Bloviate alone (ENC-129). */
class IsBatteryOptimizationExemptUseCase @Inject constructor(
    private val batteryExemptionPort: BatteryExemptionPort,
) {

    operator fun invoke(): Boolean = batteryExemptionPort.isIgnoringBatteryOptimizations()
}
