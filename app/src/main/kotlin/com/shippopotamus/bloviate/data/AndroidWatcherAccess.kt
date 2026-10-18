package com.shippopotamus.bloviate.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.shippopotamus.bloviate.domain.port.WatcherAccessPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WatcherAccessPort] backed by [AccessibilityManager]'s enabled-service list.
 * The watcher is matched by flattened component (package + class name) so no
 * compile-time dependency on the service class crosses the layer boundary.
 */
@Singleton
class AndroidWatcherAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatcherAccessPort {

    override fun isFieldWatcherEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == WATCHER_CLASS_NAME
            }
    }

    private companion object {
        const val WATCHER_CLASS_NAME = "com.shippopotamus.bloviate.service.FieldWatcherService"
    }
}
