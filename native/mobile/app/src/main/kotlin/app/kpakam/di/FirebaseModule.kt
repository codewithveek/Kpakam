package app.kpakam.di

import app.kpakam.inference.FirebaseModels
import app.kpakam.inference.FirebaseSchemaFactory
import app.kpakam.platform.assets.ContractAssets
import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Firebase SDK instances. Only platform adapters, repositories, and model adapters receive these. */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private const val CACHE_BYTES = 100L * 1024 * 1024

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore =
        Firebase.firestore.apply {
            firestoreSettings =
                firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_BYTES) })
                }
        }

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun remoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig

    /** Gemini Developer API backend, with limited-use App Check tokens (replay protection). */
    @Provides
    @Singleton
    fun firebaseAi(): FirebaseAI =
        Firebase.ai(
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true,
        )

    @Provides
    @Singleton
    fun firebaseModels(ai: FirebaseAI, assets: ContractAssets): FirebaseModels =
        FirebaseModels(ai, FirebaseSchemaFactory.fromJsonSchema(assets.draftSchema))
}
