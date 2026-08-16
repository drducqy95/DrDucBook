package io.legado.app.worker

import io.legado.app.constant.AppConst
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.sourcehealth.SourceCheckEngine
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.net.URL

data class BookSourceHealthCheckResult(
    val healthyCount: Int,
    val failedCount: Int,
)

class BookSourceHealthCheckProcessor(
    private val bookSourceDao: BookSourceDao,
    private val sourceCheckEngine: SourceCheckEngine,
) {
    private val threadCount: Int = AppConst.MAX_THREAD

    suspend fun checkAllEnabled(
        profile: SourceCheckProfile = SourceCheckProfile.QUICK,
    ): BookSourceHealthCheckResult = checkSources(bookSourceDao.allEnabled, profile)

    suspend fun checkSource(
        sourceUrl: String,
        profile: SourceCheckProfile = SourceCheckProfile.STANDARD,
    ): BookSourceHealthCheckResult {
        val source = bookSourceDao.getBookSource(sourceUrl)
            ?: return BookSourceHealthCheckResult(
                healthyCount = 0,
                failedCount = 0,
            )
        return checkSources(listOf(source), profile)
    }

    private suspend fun checkSources(
        sources: List<BookSource>,
        profile: SourceCheckProfile,
    ): BookSourceHealthCheckResult = coroutineScope {
        val healthy = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val domainSemaphore = Semaphore(threadCount)
        sources
            .groupBy { source -> domainKey(source.bookSourceUrl) }
            .values
            .map { group ->
                async {
                    domainSemaphore.withPermit {
                        group.forEachIndexed { index, source ->
                            currentCoroutineContext().ensureActive()
                            if (index > 0) {
                                delay(SAME_DOMAIN_BACKOFF_MS)
                            }
                            val run = sourceCheckEngine.checkBookSource(
                                source = source,
                                profile = profile,
                                persistSummary = true,
                            )
                            if (run.healthStatus in SUCCESS_STATUSES) {
                                healthy.incrementAndGet()
                            } else {
                                failed.incrementAndGet()
                            }
                        }
                    }
                }
            }
            .awaitAll()
        BookSourceHealthCheckResult(
            healthyCount = healthy.get(),
            failedCount = failed.get(),
        )
    }

    private fun domainKey(sourceUrl: String): String =
        runCatching { URL(sourceUrl).host.lowercase() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { host ->
                if (NetworkUtils.isIPAddress(host)) {
                    host
                } else {
                    val labels = host.trim('.').split('.').filter(String::isNotBlank)
                    when {
                        labels.size <= 2 -> host
                        labels.lastOrNull()?.length == 2 && labels.size >= 3 ->
                            labels.takeLast(3).joinToString(".")

                        else -> labels.takeLast(2).joinToString(".")
                    }
                }
            }
            ?: sourceUrl

    private companion object {
        val SUCCESS_STATUSES = setOf(
            BookSourceHealthStatus.HEALTHY,
            BookSourceHealthStatus.DEGRADED,
            BookSourceHealthStatus.STALE,
        )

        const val SAME_DOMAIN_BACKOFF_MS = 120L
    }
}
