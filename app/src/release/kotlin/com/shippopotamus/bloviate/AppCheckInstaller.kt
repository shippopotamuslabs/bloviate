package com.shippopotamus.bloviate

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * App Check for release builds (BLO-172): tokens are minted by Play
 * Integrity, so the style functions only answer the genuine
 * Play-distributed binary. The debug provider is not on the release
 * classpath; the debug source set carries its own installer.
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
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
