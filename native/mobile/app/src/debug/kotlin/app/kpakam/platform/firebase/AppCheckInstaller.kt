package app.kpakam.platform.firebase

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. The SDK prints a debug token to Logcat;
 * register it in the Firebase console. A stable token can come from local.properties
 * (APP_CHECK_DEBUG_TOKEN) through the manifest placeholder. Never commit a token.
 */
object AppCheckInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
