package com.shippopotamus.bloviate

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * App Check for debug builds (BLO-172): tokens are minted by the debug
 * provider. Each device's debug token prints to logcat (FirebaseAppCheck
 * tag) on first launch and must be registered in the Firebase console
 * (App Check, the app's entry, debug tokens) before the enforced
 * callables accept this build's calls. The release source set carries
 * the Play Integrity installer instead.
 */
object AppCheckInstaller {

    fun install(application: Application) {
        // On device the FirebaseInitProvider initializes the default app
        // before Application.onCreate; under Robolectric it does not, so
        // initialize from the processed google-services resources first.
        if (FirebaseApp.getApps(application).isEmpty()) {
            FirebaseApp.initializeApp(application)
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
    }
}
