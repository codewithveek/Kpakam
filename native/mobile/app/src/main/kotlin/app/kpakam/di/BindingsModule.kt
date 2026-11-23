package app.kpakam.di

import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.customers.FirestoreCustomerRepository
import app.kpakam.features.ledger.FirestoreLedgerRepository
import app.kpakam.features.ledger.LedgerRepository
import app.kpakam.features.settings.FirestoreSettingsRepository
import app.kpakam.features.settings.SettingsRepository
import app.kpakam.inference.CloudInferenceClient
import app.kpakam.inference.DeviceInferenceClient
import app.kpakam.inference.DeviceModelManager
import app.kpakam.inference.DeviceModelSource
import app.kpakam.inference.InCloud
import app.kpakam.inference.InferenceClient
import app.kpakam.inference.OnDevice
import app.kpakam.inference.SafetyNet
import app.kpakam.inference.SafetyNetInferenceClient
import app.kpakam.inference.events.AiEventRepository
import app.kpakam.inference.events.FirestoreAiEventRepository
import app.kpakam.inference.queue.DrainTrigger
import app.kpakam.inference.queue.QueueScheduler
import app.kpakam.platform.battery.BatteryMonitor
import app.kpakam.platform.battery.BatterySource
import app.kpakam.platform.files.AndroidDocumentWriter
import app.kpakam.platform.files.DocumentWriter
import app.kpakam.platform.firebase.ConfigSource
import app.kpakam.platform.firebase.RemoteConfigSource
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.platform.network.NetworkMonitor
import app.kpakam.platform.speech.AndroidDraftReader
import app.kpakam.platform.speech.AndroidSharedMic
import app.kpakam.platform.speech.AndroidSpeechTranscriber
import app.kpakam.platform.speech.AndroidVoiceRecorder
import app.kpakam.platform.speech.DraftReader
import app.kpakam.platform.speech.LocalSpeechChecker
import app.kpakam.platform.speech.LocalSpeechSource
import app.kpakam.platform.speech.SharedMic
import app.kpakam.platform.speech.SpeechTranscriber
import app.kpakam.platform.speech.VoiceRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds our interfaces to their Android and Firebase implementations. Tests swap these for fakes. */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class BindingsModule {
    @Binds abstract fun ledger(impl: FirestoreLedgerRepository): LedgerRepository

    @Binds abstract fun customers(impl: FirestoreCustomerRepository): CustomerRepository

    @Binds abstract fun settings(impl: FirestoreSettingsRepository): SettingsRepository

    @Binds abstract fun aiEvents(impl: FirestoreAiEventRepository): AiEventRepository

    @Binds abstract fun config(impl: RemoteConfigSource): ConfigSource

    @Binds abstract fun network(impl: NetworkMonitor): ConnectivitySource

    @Binds abstract fun battery(impl: BatteryMonitor): BatterySource

    @Binds abstract fun deviceModel(impl: DeviceModelManager): DeviceModelSource

    @Binds abstract fun localSpeech(impl: LocalSpeechChecker): LocalSpeechSource

    @Binds abstract fun transcriber(impl: AndroidSpeechTranscriber): SpeechTranscriber

    @Binds abstract fun recorder(impl: AndroidVoiceRecorder): VoiceRecorder

    @Binds abstract fun reader(impl: AndroidDraftReader): DraftReader

    @Binds abstract fun sharedMic(impl: AndroidSharedMic): SharedMic

    @Binds abstract fun documentWriter(impl: AndroidDocumentWriter): DocumentWriter

    @Binds abstract fun drainTrigger(impl: QueueScheduler): DrainTrigger

    @Binds @OnDevice
    abstract fun deviceClient(impl: DeviceInferenceClient): InferenceClient

    @Binds @InCloud
    abstract fun cloudClient(impl: CloudInferenceClient): InferenceClient

    @Binds @SafetyNet
    abstract fun safetyNetClient(impl: SafetyNetInferenceClient): InferenceClient
}
