package io.legado.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.legado.app.utils.NetworkUtils
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class BookSourceHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val processor: BookSourceHealthCheckProcessor by inject()

    override suspend fun doWork(): Result {
        if (!NetworkUtils.isAvailable()) {
            return Result.success(workDataOf(KEY_OFFLINE to true))
        }
        val sourceUrl = inputData.getString(KEY_SOURCE_URL)?.takeIf(String::isNotBlank)
        val profile = inputData.getString(KEY_PROFILE)
            ?.let { runCatching { SourceCheckProfile.valueOf(it) }.getOrNull() }
            ?: SourceCheckProfile.QUICK
        val result = SourceVerificationHelp.withoutInteractivePrompt {
            if (sourceUrl == null) {
                processor.checkAllEnabled(profile)
            } else {
                processor.checkSource(sourceUrl, profile)
            }
        }
        return Result.success(
            workDataOf(
                KEY_HEALTHY_COUNT to result.healthyCount,
                KEY_FAILED_COUNT to result.failedCount,
            )
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "source_health_check"
        const val MANUAL_WORK_NAME = "source_health_check_manual"
        const val KEY_SOURCE_URL = "sourceUrl"
        const val KEY_PROFILE = "profile"
        const val KEY_OFFLINE = "offline"
        const val KEY_HEALTHY_COUNT = "healthyCount"
        const val KEY_FAILED_COUNT = "failedCount"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BookSourceHealthWorker>(
                24,
                TimeUnit.HOURS,
                4,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
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
            runNow(context, null, SourceCheckProfile.QUICK)
        }

        fun runNow(context: Context, sourceUrl: String?) {
            runNow(context, sourceUrl, if (sourceUrl == null) SourceCheckProfile.QUICK else SourceCheckProfile.STANDARD)
        }

        fun runNow(
            context: Context,
            sourceUrl: String?,
            profile: SourceCheckProfile,
        ) {
            val request = OneTimeWorkRequestBuilder<BookSourceHealthWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(buildInputData(sourceUrl, profile))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                manualWorkName(sourceUrl, profile),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        internal fun buildInputData(
            sourceUrl: String?,
            profile: SourceCheckProfile = SourceCheckProfile.QUICK,
        ): Data =
            sourceUrl
                ?.takeIf(String::isNotBlank)
                ?.let { workDataOf(KEY_SOURCE_URL to it, KEY_PROFILE to profile.name) }
                ?: workDataOf(KEY_PROFILE to profile.name)

        internal fun manualWorkName(
            sourceUrl: String?,
            profile: SourceCheckProfile = SourceCheckProfile.QUICK,
        ): String =
            sourceUrl
                ?.takeIf(String::isNotBlank)
                ?.let { "$MANUAL_WORK_NAME:${profile.name}:${it.hashCode()}" }
                ?: "$MANUAL_WORK_NAME:${profile.name}"
    }
}
