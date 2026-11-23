package app.kpakam

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.kpakam.platform.firebase.FirebaseSetup
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KpakamApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var startup: Lazy<AppStartup>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // App Check goes first, before any Firebase AI or Firestore call.
        FirebaseSetup.start(this)
        if (FirebaseSetup.isConfigured(this)) startup.get().start()
    }
}
