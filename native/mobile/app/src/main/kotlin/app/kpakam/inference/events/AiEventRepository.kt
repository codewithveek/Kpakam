package app.kpakam.inference.events

import app.kpakam.core.routing.AiEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Counts for the trader's own Stats (F6). */
data class AiStats(val total: Int, val onDevice: Int, val inCloud: Int, val avoidedCloudCalls: Int) {
    /** Share of requests served on the device, 0–100. */
    val onDeviceShare: Int get() = if (total == 0) 0 else onDevice * PERCENT / total

    companion object {
        private const val PERCENT = 100
        val EMPTY = AiStats(0, 0, 0, 0)
    }
}

/** Stores and reads `aiEvents`. Events never hold the trader's text. */
interface AiEventRepository {
    /** Saves [event] in the background. Works offline. */
    fun record(event: AiEvent)

    /** Live counts of events created at or after [start]. */
    fun statsSince(start: Instant): Flow<AiStats>
}
