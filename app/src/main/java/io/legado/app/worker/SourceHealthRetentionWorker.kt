package io.legado.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SourceHealthRetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repository: SourceCheckRepository by inject()

    override suspend fun doWork(): Result {
        val result = repository.cleanup()
        return Result.success(
            workDataOf(
                KEY_INSPECTED_RUN_COUNT to result.inspectedRunCount,
                KEY_DELETED_RUN_COUNT to result.deletedRunCount,
                KEY_REMAINING_RUN_COUNT to result.remainingRunCount,
            )
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "source_health_retention"
        const val KEY_INSPECTED_RUN_COUNT = "inspectedRunCount"
        const val KEY_DELETED_RUN_COUNT = "deletedRunCount"
        const val KEY_REMAINING_RUN_COUNT = "remainingRunCount"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SourceHealthRetentionWorker>(
                24,
                TimeUnit.HOURS,
                6,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SourceHealthRetentionWorker>().build()
            )
        }
    }
}
