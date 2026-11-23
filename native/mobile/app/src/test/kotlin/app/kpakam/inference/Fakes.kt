package app.kpakam.inference

import app.kpakam.core.config.AppConfig
import app.kpakam.core.draft.DraftValidator
import app.kpakam.core.prompts.PromptAssembler
import app.kpakam.core.prompts.PromptSet
import app.kpakam.core.prompts.PromptTemplate
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.ModelState
import app.kpakam.features.settings.SettingsRepository
import app.kpakam.features.settings.UserSettings
import app.kpakam.platform.firebase.ConfigSource
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.platform.network.NetworkState
import app.kpakam.platform.speech.LocalSpeechSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Reads the shared contract (Gradle passes `contractDir`). */
object Contract {
    private val dir = File(requireNotNull(System.getProperty("contractDir")) { "contractDir not set" })

    fun text(path: String): String = dir.resolve(path).readText()

    val config: AppConfig by lazy { AppConfig.fromJsonText(text("remote-config.defaults.json")) }

    val prompts: PromptAssembler by lazy {
        fun load(id: String) = PromptTemplate.parse(text("prompts/$id.md"))
        PromptAssembler(
            PromptSet(
                load("parse-v1"),
                load("parse-retry-v1"),
                load("voice-v1"),
                load("receipt-v1"),
                load("summary-v1"),
                load("insights-v1"),
            ),
        )
    }

    val validator: DraftValidator by lazy {
        DraftValidator(Json.parseToJsonElement(text("schemas/draft.schema.json")).jsonObject)
    }
}

/** A model that replies with queued results, one per call, and records every prompt. */
class ScriptedClient(private val source: EventSource, vararg replies: InferenceResult) : InferenceClient {
    private val queue = ArrayDeque(replies.toList())
    val prompts = mutableListOf<String>()

    override suspend fun generate(request: InferenceRequest): InferenceResult {
        prompts += request.prompt
        return queue.removeFirstOrNull() ?: error("Unexpected call to the $source model")
    }

    companion object {
        fun ok(text: String, source: EventSource) = InferenceResult.Success(text, source, latencyMs = 10)

        fun fail(error: InferenceError) = InferenceResult.Failure(error, latencyMs = 5)
    }
}

class FakeConfig(config: AppConfig = Contract.config) : ConfigSource {
    override val config = MutableStateFlow(config)
}

class FakeNetwork(online: Boolean) : ConnectivitySource {
    override val state = MutableStateFlow(NetworkState(online = online, metered = true))
}

class FakeDeviceModel(state: ModelState = ModelState.AVAILABLE) : DeviceModelSource {
    override val state = MutableStateFlow(state)

    override suspend fun refresh(): ModelState = state.value
}

class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(UserSettings())

    override suspend fun ensureProfile() = Unit

    override suspend fun save(settings: UserSettings) {
        this.settings.value = settings
    }
}

object FakeSpeech : LocalSpeechSource {
    override suspend fun englishState(): ModelState = ModelState.UNAVAILABLE

    override suspend fun installAtIdle() = Unit
}

object FakeSharedMic : app.kpakam.platform.speech.SharedMic {
    override val supported: Boolean = true

    override fun open(name: String): app.kpakam.platform.speech.SharedCapture? = null
}

val fixedClock: Clock = Clock.fixed(Instant.parse("2026-11-24T08:41:00Z"), ZoneOffset.UTC)
