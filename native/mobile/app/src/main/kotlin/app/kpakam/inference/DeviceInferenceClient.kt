package app.kpakam.inference

import app.kpakam.platform.firebase.ConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/** Gemini Nano through ML Kit Prompt API, via Firebase AI Logic with `ONLY_ON_DEVICE`. */
@Singleton
class DeviceInferenceClient
    @Inject
    constructor(
        private val models: FirebaseModels,
        private val config: ConfigSource,
    ) : InferenceClient {
        override suspend fun generate(request: InferenceRequest): InferenceResult =
            FirebaseModels.run(models.device(config.config.value.cloudModelName), request)
    }
