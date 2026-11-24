package app.kpakam.inference.events

import android.util.Log
import app.kpakam.core.ids.Ulid
import app.kpakam.core.routing.AiEvent
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.RouteTarget
import app.kpakam.di.AppScope
import app.kpakam.platform.firebase.UserPaths
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore `users/{uid}/aiEvents`: create only. */
@Singleton
class FirestoreAiEventRepository
    @Inject
    constructor(
        private val paths: UserPaths,
        @param:AppScope private val scope: CoroutineScope,
    ) : AiEventRepository {
        override fun record(event: AiEvent) {
            scope.launch {
                paths
                    .aiEvents()
                    .document(event.id.value)
                    .set(toMap(event))
                    .addOnFailureListener { Log.w(TAG, "aiEvent write rejected", it) }
            }
        }

        /**
         * Selects events by ULID id instead of `createdAt`: a pending server timestamp is null in
         * local queries, so offline events would not count until they sync. ULIDs sort by time.
         */
        override fun statsSince(start: Instant): Flow<AiStats> =
            paths.forUser { user ->
                user
                    .collection(UserPaths.AI_EVENTS)
                    .whereGreaterThanOrEqualTo(ID, Ulid.lowerBound(start.toEpochMilli()).value)
                    .snapshots()
                    .map { snapshot -> count(snapshot.documents) }
            }

        private fun count(documents: List<DocumentSnapshot>): AiStats {
            val served = documents.filter { it.getString(TARGET) != RouteTarget.QUEUE.wire }
            val onDevice = served.count { it.getString(SOURCE) == EventSource.ON_DEVICE.wire }
            val inCloud = served.count { it.getString(SOURCE) == EventSource.IN_CLOUD.wire }
            val avoided =
                served.count {
                    it.getString(SOURCE) == EventSource.ON_DEVICE.wire && it.getBoolean(VALID) == true
                }
            return AiStats(onDevice + inCloud, onDevice, inCloud, avoided)
        }

        private fun toMap(event: AiEvent): Map<String, Any?> =
            mapOf(
                ID to event.id.value,
                "task" to event.task.wire,
                TARGET to event.target.wire,
                "reason" to event.reason.name,
                SOURCE to event.source.wire,
                "latencyMs" to event.latencyMs,
                VALID to event.valid,
                "attempts" to event.attempts.toLong(),
                "escalated" to event.escalated,
                "queuedMs" to event.queuedMs,
                "promptVersion" to event.promptVersion,
                "createdAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to AiEvent.SCHEMA_VERSION,
            )

        private companion object {
            const val TAG = "AiEvents"
            const val ID = "id"
            const val TARGET = "target"
            const val SOURCE = "source"
            const val VALID = "valid"
        }
    }
