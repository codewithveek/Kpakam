package app.kpakam.platform.firebase

import android.content.Context
import app.kpakam.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Starts the Firebase SDKs that must run before anything else touches Firebase. */
object FirebaseSetup {
    /** `true` when google-services.json was present at build time, so a default FirebaseApp exists. */
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Installs App Check first, then sets crash reporting. Call once from Application.onCreate. */
    fun start(context: Context) {
        if (!isConfigured(context)) return
        AppCheckInstaller.install()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }
}
