package app.kpakam.inference.queue

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Drains the AI job queue in the background when any connection exists. */
@HiltWorker
class DrainQueueWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val drainer: QueueDrainer,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (drainer.drain()) {
                DrainResult.Done -> Result.success()
                DrainResult.RetryLater -> Result.retry()
            }
    }

/** Schedules [DrainQueueWorker]. Mobile data counts as a connection; the app never waits for Wi-Fi. */
@Singleton
class QueueScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DrainTrigger {
        override fun schedule() {
            val request =
                OneTimeWorkRequestBuilder<DrainQueueWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private companion object {
            const val WORK_NAME = "drain-ai-queue"
            const val BACKOFF_SECONDS = 30L
        }
    }
