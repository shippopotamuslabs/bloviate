package com.shippopotamus.bloviate

import android.app.Application
import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BloviateApplication : Application() {

    @Inject
    lateinit var authSession: AuthSessionPort

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget anonymous sign-in (ENC-151) so the first style
        // call does not pay it. The uid is logged once so Sean can
        // hand-write his Firestore entitlement override doc for dogfooding.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val uid = authSession.ensureSignedIn()
            if (uid != null) {
                Log.d("BloviateAuth", "anonymous uid=" + uid)
            }
        }
    }
}
