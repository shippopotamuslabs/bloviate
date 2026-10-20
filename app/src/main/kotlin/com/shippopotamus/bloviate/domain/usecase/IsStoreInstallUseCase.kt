package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.InstallSourcePort
import javax.inject.Inject

/**
 * One-shot read of whether Bloviate was installed by an app store
 * (ENC-67). Restricted settings — the extra "Allow restricted
 * settings" step Android demands before the accessibility and overlay
 * switches will move — applies only to installs that did NOT come
 * from a store, so the wizard keys its blocked-state guidance off
 * this answer. An unreported installer counts as not-a-store: that is
 * what sideloads and test distribution report.
 */
class IsStoreInstallUseCase @Inject constructor(
    private val installSource: InstallSourcePort,
) {

    operator fun invoke(): Boolean =
        installSource.installingPackageName() in STORE_INSTALLER_PACKAGES

    private companion object {
        /** The storefront packages whose installs skip restricted settings. */
        val STORE_INSTALLER_PACKAGES: Set<String> = setOf(
            "com.android.vending", // Google Play
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.amazon.venezia", // Amazon Appstore
        )
    }
}
