package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.InstallSourcePort

/**
 * Hand-written fake of [InstallSourcePort]; set [installingPackageName]
 * to simulate an install source (null = the sideload answer).
 */
class FakeInstallSourcePort : InstallSourcePort {

    var installingPackageName: String? = null

    override fun installingPackageName(): String? = installingPackageName
}
