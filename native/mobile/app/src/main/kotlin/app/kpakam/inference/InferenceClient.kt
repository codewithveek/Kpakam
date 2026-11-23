package app.kpakam.inference

import app.kpakam.core.routing.EventSource

/** Inline media sent after the text part (audio clip or receipt photo). */
class Attachment(val bytes: ByteArray, val mimeType: String)

/** One model call. [prompt] is fully assembled by the core prompt assembler. */
data class InferenceRequest(
    val prompt: String,
    val attachment: Attachment? = null,
)

/** Why a model call failed. Expected failures only; bugs throw. */
sealed interface InferenceError {
    /** The on-device model is missing or not ready. */
    data object DeviceUnavailable : InferenceError

    /** The cloud quota or rate limit was hit. */
    data object QuotaExceeded : InferenceError

    /** The request could not reach the service. */
    data object Network : InferenceError

    /** The model refused or stopped (safety, prompt blocked). */
    data object Blocked : InferenceError

    /** Anything else the SDK reported. */
    data class Unknown(val message: String?) : InferenceError
}

/** The outcome of one model call. */
sealed interface InferenceResult {
    val latencyMs: Long

    data class Success(val text: String, val source: EventSource, override val latencyMs: Long) : InferenceResult

    data class Failure(val error: InferenceError, override val latencyMs: Long) : InferenceResult
}

/**
 * Our own interface over a generative model. Services depend on this, never on
 * Firebase types, so tests use fakes and the SDK can change in one place.
 */
fun interface InferenceClient {
    /** Runs one request. Never throws for expected failures; returns [InferenceResult.Failure]. */
    suspend fun generate(request: InferenceRequest): InferenceResult
}
