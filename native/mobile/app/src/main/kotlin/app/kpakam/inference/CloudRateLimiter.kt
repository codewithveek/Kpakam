package app.kpakam.inference

import app.kpakam.platform.firebase.ConfigSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Per-user client rate limit for cloud calls (Remote Config `cloud_rate_limit_per_minute`). */
@Singleton
class CloudRateLimiter
    @Inject
    constructor(
        private val config: ConfigSource,
        private val clock: Clock,
    ) {
        private val mutex = Mutex()
        private val calls = ArrayDeque<Long>()

        /** Returns `true` and counts the call when the limit allows one more cloud call now. */
        suspend fun tryAcquire(): Boolean =
            mutex.withLock {
                val now = clock.millis()
                while (calls.isNotEmpty() && now - calls.first() >= WINDOW_MS) calls.removeFirst()
                if (calls.size >= config.config.value.cloudRateLimitPerMinute) return@withLock false
                calls.addLast(now)
                true
            }

        private companion object {
            const val WINDOW_MS = 60_000L
        }
    }
