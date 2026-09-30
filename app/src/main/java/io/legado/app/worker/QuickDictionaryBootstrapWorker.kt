package io.legado.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.legado.app.domain.model.AssetDeliveryCatalogResolver
import io.legado.app.domain.model.AssetDeliveryResolution
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.data.repository.QuickDictionaryPackStore
import io.legado.app.domain.usecase.AccountAuthUseCase
import io.legado.app.domain.usecase.AssetDeliveryUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Downloads the original QT pack once the app account has an authenticated session. */
class QuickDictionaryBootstrapWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val accountAuthUseCase: AccountAuthUseCase by inject()
    private val assetDeliveryUseCase: AssetDeliveryUseCase by inject()
    private val quickDictionaryPackStore: QuickDictionaryPackStore by inject()

    override suspend fun doWork(): Result {
        if (quickDictionaryPackStore.hasOriginalPack()) {
            applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(COMPLETED_KEY, true)
                .apply()
            return Result.success()
        }
        if (!assetDeliveryUseCase.configured || accountAuthUseCase.currentAccessToken().isNullOrBlank()) {
            return Result.success()
        }
        val artifact = when (
            val resolution = AssetDeliveryCatalogResolver.resolve(
                ExternalAssetCatalog.quickTranslationCleanZipUrl,
            )
        ) {
            is AssetDeliveryResolution.Single -> resolution.artifact
            else -> return Result.failure()
        }
        quickDictionaryPackStore.markOriginalDownloading()
        return runCatching {
            val downloaded = assetDeliveryUseCase.downloadArtifact(artifact) { _ -> }
            assetDeliveryUseCase.importArtifact(artifact, downloaded)
            applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(COMPLETED_KEY, true)
                .apply()
            Result.success()
        }.getOrElse { error ->
            quickDictionaryPackStore.markOriginalFailure(error.message ?: "Không thể tải QT gốc")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PREFERENCES = "quick_dictionary_bootstrap"
        private const val COMPLETED_KEY = "original_pack_imported"
        private const val WORK_NAME = "quick_dictionary_bootstrap"
        private const val MAX_RETRIES = 4

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<QuickDictionaryBootstrapWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
