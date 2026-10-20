package com.shippopotamus.bloviate.domain.port

/**
 * Where this install of Bloviate came from (ENC-67). The onboarding
 * wizard uses it for one judgment: Android's restricted settings block
 * the accessibility and overlay switches for apps that did NOT come
 * from an app store (sideloaded APKs, Firebase App Distribution test
 * builds) until the user allows them once — so a non-store install
 * with a gate still off gets the "Allow restricted settings" steps,
 * and a store install never sees them.
 */
interface InstallSourcePort {

    /**
     * The package name of the app that installed Bloviate (e.g. the
     * Play Store's `com.android.vending`), or null when the platform
     * reports no installer — the usual answer for sideloads and test
     * distribution.
     */
    fun installingPackageName(): String?
}
