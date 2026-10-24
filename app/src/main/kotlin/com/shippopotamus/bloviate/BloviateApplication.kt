package com.shippopotamus.bloviate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.shippopotamus.bloviate.data.SignInHostContext
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BloviateApplication : Application() {

    /**
     * Credential Manager needs a foreground Activity context (ENC-197).
     * The composition root owns that wiring — not the activity — so
     * `presentation/` never imports `data/`: lifecycle callbacks attach
     * the resumed activity as sign-in host and release it on destroy.
     *
     * Startup performs no sign-in of any kind: the launcher UI gates on
     * the live auth state, and anonymous sessions are never created.
     */
    @Inject
    lateinit var signInHostContext: SignInHostContext

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SignInHostCallbacks(signInHostContext))
    }

    private class SignInHostCallbacks(
        private val hostContext: SignInHostContext,
    ) : ActivityLifecycleCallbacks {

        override fun onActivityResumed(activity: Activity) {
            hostContext.attach(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            hostContext.detach(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
