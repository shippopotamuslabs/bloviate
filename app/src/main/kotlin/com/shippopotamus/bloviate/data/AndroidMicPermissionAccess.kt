package com.shippopotamus.bloviate.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MicPermissionPort] backed by the platform's live `RECORD_AUDIO` grant.
 * Android has no permission-change callback, so the published state is a
 * cache refreshed at the moments the grant can change (see the port's KDoc).
 */
@Singleton
class AndroidMicPermissionAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : MicPermissionPort {

    private val mutableGranted = MutableStateFlow(readLiveGrant())

    override val isGranted: StateFlow<Boolean> = mutableGranted.asStateFlow()

    override fun isGrantedNow(): Boolean = readLiveGrant()

    override fun refresh() {
        val live = readLiveGrant()
        mutableGranted.update { live }
    }

    private fun readLiveGrant(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
