package io.legado.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.legado.app.di.RAW_AI_TEXT_GATEWAY
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.utils.NetworkUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.concurrent.TimeUnit

class ModelDiscoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val aiProfileGateway: AiProfileGateway by inject()
    private val rawAiTextGateway: AiTextGateway by inject(named(RAW_AI_TEXT_GATEWAY))

    override suspend fun doWork(): Result {
        if (!NetworkUtils.isAvailable()) {
            return Result.success(workDataOf(KEY_OFFLINE to true))
        }

        var totalDiscovered = 0
        var totalFailed = 0

        return try {
            val providers = aiProfileGateway.getEnabledProviders()
            for (provider in providers) {
                try {
                    val config = aiProfileGateway.toProviderConfig(provider)
                    val result = rawAiTextGateway.fetchModels(config)
                    val models = result.getOrNull()
                    if (models != null && models.isNotEmpty()) {
                        aiProfileGateway.syncDiscoveredModels(provider.id, models)
                        totalDiscovered += models.size
                    }
                } catch (e: Exception) {
                    totalFailed++
                    timber.log.Timber.w(e, "Model discovery failed for provider ${provider.name}")
                }
            }

            aiProfileGateway.deprecateStaleModels()

            Result.success(
                workDataOf(
                    KEY_DISCOVERED_COUNT to totalDiscovered,
                    KEY_FAILED_COUNT to totalFailed,
                )
            )
        } catch (e: Exception) {
            timber.log.Timber.e(e, "ModelDiscoveryWorker encountered error")
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "ai_model_discovery_periodic"
        const val ONE_TIME_WORK_NAME = "ai_model_discovery_one_time"
        const val KEY_OFFLINE = "offline"
        const val KEY_DISCOVERED_COUNT = "discoveredCount"
        const val KEY_FAILED_COUNT = "failedCount"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context, intervalHours: Long) {
            if (intervalHours <= 0) {
                cancel(context)
                return
            }
            val flexHours = (intervalHours / 4).coerceAtLeast(1)
            val request = PeriodicWorkRequestBuilder<ModelDiscoveryWorker>(
                intervalHours,
                TimeUnit.HOURS,
                flexHours,
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ModelDiscoveryWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
