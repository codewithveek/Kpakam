package app.kpakam.inference

import app.kpakam.platform.firebase.ConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/** The safety net: one `PREFER_ON_DEVICE` call when the router itself fails (`ROUTER_FALLBACK`). */
@Singleton
class SafetyNetInferenceClient
    @Inject
    constructor(
        private val models: FirebaseModels,
        private val config: ConfigSource,
    ) : InferenceClient {
        override suspend fun generate(request: InferenceRequest): InferenceResult =
            FirebaseModels.run(models.safetyNet(config.config.value.cloudModelName), request)
    }
