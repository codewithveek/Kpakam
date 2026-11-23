package app.kpakam.features.settings

import app.kpakam.core.routing.VoiceLanguage
import kotlinx.coroutines.flow.StateFlow

/** The trader's settings, stored in `users/{uid}.settings`. */
data class UserSettings(
    val dataSaver: Boolean = false,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.EN,
    val analyticsOptIn: Boolean = false,
)

/** Reads and writes the trader's profile and settings. */
interface SettingsRepository {
    /** The current settings; defaults until the profile loads. */
    val settings: StateFlow<UserSettings>

    /** Creates `users/{uid}` on first launch. Does nothing when it exists. */
    suspend fun ensureProfile()

    /** Saves new settings. Works offline; syncs later. */
    suspend fun save(settings: UserSettings)
}
