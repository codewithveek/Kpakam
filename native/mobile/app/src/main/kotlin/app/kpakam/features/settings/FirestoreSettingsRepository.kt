package app.kpakam.features.settings

import android.util.Log
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.di.AppScope
import app.kpakam.platform.firebase.UserPaths
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore `users/{uid}`: `{ settings, createdAt, updatedAt, schemaVersion }`. */
@Singleton
class FirestoreSettingsRepository
    @Inject
    constructor(
        private val paths: UserPaths,
        @AppScope scope: CoroutineScope,
    ) : SettingsRepository {
        override val settings: StateFlow<UserSettings> =
            paths
                .forUser { user -> user.snapshots().map(::parse) }
                .stateIn(scope, SharingStarted.Eagerly, UserSettings())

        override suspend fun ensureProfile() {
            val user = paths.user()
            val exists =
                try {
                    user.get().await().exists()
                } catch (e: FirebaseFirestoreException) {
                    Log.i(TAG, "Profile check failed; will try on next launch", e)
                    return
                }
            if (exists) return
            user.set(
                mapOf(
                    SETTINGS to toMap(UserSettings()),
                    "createdAt" to FieldValue.serverTimestamp(),
                    UPDATED_AT to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1,
                ),
            )
        }

        override suspend fun save(settings: UserSettings) {
            paths
                .user()
                .update(mapOf(SETTINGS to toMap(settings), UPDATED_AT to FieldValue.serverTimestamp()))
                .addOnFailureListener { Log.w(TAG, "Settings write rejected", it) }
        }

        private fun parse(doc: DocumentSnapshot): UserSettings {
            val map = doc.get(SETTINGS) as? Map<*, *> ?: return UserSettings()
            return UserSettings(
                dataSaver = map[DATA_SAVER] as? Boolean ?: false,
                voiceLanguage = (map[VOICE_LANGUAGE] as? String)?.let(VoiceLanguage::fromWire) ?: VoiceLanguage.AUTO,
                analyticsOptIn = map[ANALYTICS] as? Boolean ?: false,
            )
        }

        private fun toMap(settings: UserSettings): Map<String, Any> =
            mapOf(
                DATA_SAVER to settings.dataSaver,
                VOICE_LANGUAGE to settings.voiceLanguage.wire,
                ANALYTICS to settings.analyticsOptIn,
            )

        private companion object {
            const val TAG = "Settings"
            const val SETTINGS = "settings"
            const val UPDATED_AT = "updatedAt"
            const val DATA_SAVER = "dataSaver"
            const val VOICE_LANGUAGE = "voiceLanguage"
            const val ANALYTICS = "analyticsOptIn"
        }
    }
