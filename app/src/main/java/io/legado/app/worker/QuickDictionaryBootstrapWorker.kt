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
        return Result.success()
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
