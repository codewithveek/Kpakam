package app.kpakam.inference

import app.kpakam.platform.firebase.ConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/** Cloud Gemini (model name from Remote Config `cloud_model_name`) with `ONLY_IN_CLOUD`. */
@Singleton
class CloudInferenceClient
    @Inject
    constructor(
        private val models: FirebaseModels,
        private val config: ConfigSource,
    ) : InferenceClient {
        override suspend fun generate(request: InferenceRequest): InferenceResult =
            FirebaseModels.run(models.cloud(config.config.value.cloudModelName), request)
    }
