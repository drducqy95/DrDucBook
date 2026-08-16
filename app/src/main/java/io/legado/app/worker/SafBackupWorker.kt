package io.legado.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.legado.app.domain.gateway.SafBackupGateway
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class SafBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val gateway: SafBackupGateway by inject()

    override suspend fun doWork(): Result = runCatching {
        if (gateway.runScheduled()) Result.success() else Result.failure()
    }.getOrElse { cause ->
        if (cause is CancellationException) throw cause
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure()
        else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "saf_backup_periodic"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context, intervalHours: Long) {
            require(intervalHours == 24L || intervalHours == 168L)
            val request = PeriodicWorkRequestBuilder<SafBackupWorker>(
                intervalHours,
                TimeUnit.HOURS,
                if (intervalHours == 24L) 6 else 24,
                TimeUnit.HOURS,
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
