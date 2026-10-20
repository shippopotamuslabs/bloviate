package com.shippopotamus.bloviate.data

import android.content.Context
import com.shippopotamus.bloviate.domain.port.InstallSourcePort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InstallSourcePort] backed by the platform's install-source info.
 * A failed query reads as no installer — the sideload answer — so the
 * wizard can only over-show the restricted-settings guidance on an odd
 * device, never hide it from an install that needs it.
 */
@Singleton
class AndroidInstallSourceAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstallSourcePort {

    override fun installingPackageName(): String? =
        runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
}
