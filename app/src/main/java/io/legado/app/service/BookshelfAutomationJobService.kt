package io.legado.app.service

import android.Manifest
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drducbook.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.help.config.BookshelfAutomationConfig
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BookshelfAutomationJobService : JobService(), KoinComponent {

    private val bookRepository: BookRepository by inject()
    private val refreshTocUseCase: RefreshTocUseCase by inject()
    private val downloadGateway: BookCacheDownloadGateway by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!BookshelfAutomationConfig.enabled) return false
        runningJob = scope.launch {
            var shouldReschedule = false
            try {
                runUpdateCycle()
            } catch (_: CancellationException) {
                shouldReschedule = true
            } catch (_: Throwable) {
                shouldReschedule = true
            } finally {
                jobFinished(params, shouldReschedule)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return BookshelfAutomationConfig.enabled
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runUpdateCycle() {
        val updates = mutableListOf<BookUpdateResult>()
        bookRepository.getHasUpdateBooks().forEach { book ->
            val oldCount = book.totalChapterNum.coerceAtLeast(0)
            var refreshedBook: Book? = null
            val result = refreshTocUseCase.execute(book.bookUrl) { _, refreshed ->
                refreshedBook = refreshed
            }
            if (result.isFailure) return@forEach
            val refreshed = refreshedBook ?: return@forEach
            val newCount = refreshed.totalChapterNum.coerceAtLeast(0)
            val added = (newCount - oldCount).coerceAtLeast(0)
            if (added <= 0) return@forEach
            updates += BookUpdateResult(refreshed, oldCount, newCount, added)
            if (BookshelfAutomationConfig.autoDownloadNewChapters) {
                runCatching {
                    downloadGateway.start(
                        CacheDownloadRequest(
                            bookUrl = refreshed.bookUrl,
                            selection = ChapterSelection.Range(oldCount, newCount - 1),
                            source = CacheDownloadSource.Automation,
                        )
                    )
                }
            }
        }

        BookshelfAutomationConfig.lastCheckAt = System.currentTimeMillis()
        BookshelfAutomationConfig.lastUpdatedBookCount = updates.size
        BookshelfAutomationConfig.lastNewChapterCount = updates.sumOf(BookUpdateResult::added)
        if (updates.isNotEmpty() && BookshelfAutomationConfig.notifyNewChapters) {
            notifyUpdates(updates)
        }
    }

    private fun notifyUpdates(updates: List<BookUpdateResult>) {
        val totalChapters = updates.sumOf(BookUpdateResult::added)
        val detail = updates.joinToString("\n") { result ->
            getString(R.string.bookshelf_automation_book_detail, result.book.name, result.added)
        }
        val notification = NotificationCompat.Builder(this, AppConst.channelIdBookUpdates)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(getString(R.string.bookshelf_automation_notification_title))
            .setContentText(
                getString(R.string.bookshelf_automation_notification_summary, updates.size, totalChapters)
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                activityPendingIntent(MainActivity.createHomeIntent(this), "bookshelfUpdates")
            )
            .build()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NotificationId.BookshelfUpdates, notification)
    }

    private data class BookUpdateResult(
        val book: Book,
        val oldCount: Int,
        val newCount: Int,
        val added: Int,
    )
}
