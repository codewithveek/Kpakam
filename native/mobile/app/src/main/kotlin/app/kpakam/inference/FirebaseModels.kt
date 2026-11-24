package app.kpakam.inference

import app.kpakam.core.routing.EventSource
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Builds Firebase AI models and runs requests. The router owns the decision, so each model
 * is fixed to one place: [InferenceMode.ONLY_ON_DEVICE] or [InferenceMode.ONLY_IN_CLOUD].
 */
@OptIn(PublicPreviewAPI::class)
class FirebaseModels(private val ai: FirebaseAI, private val responseSchema: Schema) {
    private val cache = mutableMapOf<Pair<String, Boolean>, GenerativeModel>()
    private val fallbackCache = mutableMapOf<String, GenerativeModel>()

    /** Returns a model that runs only on the device, created once per [modelName]. */
    @Synchronized
    fun device(modelName: String): GenerativeModel =
        cache.getOrPut(modelName to true) {
            ai.generativeModel(
                modelName = modelName,
                generationConfig = jsonConfig(),
                onDeviceConfig =
                    OnDeviceConfig(
                        mode = InferenceMode.ONLY_ON_DEVICE,
                        maxOutputTokens = MAX_OUTPUT_TOKENS,
                        temperature = 0f,
                    ),
            )
        }

    /** Returns a model that runs only in the cloud, created once per [modelName]. */
    @Synchronized
    fun cloud(modelName: String): GenerativeModel =
        cache.getOrPut(modelName to false) {
            ai.generativeModel(
                modelName = modelName,
                generationConfig = jsonConfig(),
                onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
            )
        }

    /**
     * Returns the safety-net model (`PREFER_ON_DEVICE`), used only when reading signals or
     * routing throws (routing.md "Safety net"). The response says where it ran.
     */
    @Synchronized
    fun safetyNet(modelName: String): GenerativeModel =
        fallbackCache.getOrPut(modelName) {
            ai.generativeModel(
                modelName = modelName,
                generationConfig = jsonConfig(),
                onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_ON_DEVICE),
            )
        }

    private fun jsonConfig() =
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = this@FirebaseModels.responseSchema
            temperature = 0f
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 1024

        /** Runs [request] on [model] and maps SDK exceptions to [InferenceError]. */
        suspend fun run(model: GenerativeModel, request: InferenceRequest): InferenceResult {
            val started = System.nanoTime()

            fun elapsed() = (System.nanoTime() - started) / NANOS_PER_MS
            return try {
                val response =
                    model.generateContent(
                        content {
                            text(request.prompt)
                            request.attachment?.let { inlineData(it.bytes, it.mimeType) }
                        },
                    )
                val source =
                    if (response.inferenceSource == InferenceSource.ON_DEVICE) {
                        EventSource.ON_DEVICE
                    } else {
                        EventSource.IN_CLOUD
                    }
                InferenceResult.Success(response.text.orEmpty(), source, elapsed())
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseAIException) {
                InferenceResult.Failure(map(e), elapsed())
            } catch (e: IOException) {
                InferenceResult.Failure(InferenceError.Network, elapsed())
            }
        }

        private fun map(e: FirebaseAIException): InferenceError =
            when (e) {
                is FirebaseAIOnDeviceNotAvailableException -> InferenceError.DeviceUnavailable
                is QuotaExceededException -> InferenceError.QuotaExceeded
                is ServiceConnectionHandshakeFailedException, is RequestTimeoutException -> InferenceError.Network
                is PromptBlockedException,
                is ContentBlockedException,
                is ResponseStoppedException,
                -> InferenceError.Blocked
                else -> if (e.cause is IOException) InferenceError.Network else InferenceError.Unknown(e.message)
            }

        private const val NANOS_PER_MS = 1_000_000L
    }
}
