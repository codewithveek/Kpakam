package app.kpakam.core.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixedSpeechTest {
    @Test
    fun `each rule alone makes speech mixed`() {
        assertTrue(MixedSpeech.isMixedSpeech("", null, 0.0, 0.35))
        assertTrue(MixedSpeech.isMixedSpeech("rice", null, 0.36, 0.35))
        assertTrue(MixedSpeech.isMixedSpeech("rice", 0.49, 0.0, 0.35))
        assertFalse(MixedSpeech.isMixedSpeech("rice", 0.5, 0.35, 0.35))
    }

    @Test
    fun `auto is the default language and has a wire value`() {
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.fromWire("auto"))
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.entries.first())
    }

    @Test
    fun `pidgin mode never listens on the phone`() {
        val decision =
            SpeechRouter.route(
                SpeechRouteSignals(VoiceLanguage.PCM_YO, ModelState.AVAILABLE, online = true, cloudEnabled = true),
            )
        assertEquals(ReasonCode.SPEECH_CLOUD, decision.reason)
    }
}
