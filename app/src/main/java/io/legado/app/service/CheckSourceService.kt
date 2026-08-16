package io.legado.app.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.sourcehealth.SourceCheckEngine
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.help.IntentData
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.Executors
import java.net.URL
import kotlin.math.min

/**
 * 校验书源.
 */
class CheckSourceService : BaseService(), KoinComponent {

    private val sourceCheckEngine: SourceCheckEngine by inject()
    private val sourceCheckRepository: SourceCheckRepository by inject()
    private val threadCount: Int
        get() = OtherConfig.threadCount.coerceIn(1, AppConst.MAX_THREAD)
    private lateinit var checkDispatcher: ExecutorCoroutineDispatcher
    private val commandMutex = Mutex()
    private val progressMutex = Mutex()
    private var notificationMsg = ""
    private var checkJob: Job? = null
    private var activeSession: CheckSourceSession? = null
    private var originSize = 0
    private var finishCount = 0
    private var sessionRunning = false
    private var cancelRequested = false
    private var donePosted = false

    override fun onCreate() {
        notificationMsg = appCtx.getString(R.string.service_starting)
        checkDispatcher =
            Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var startResult = START_REDELIVER_INTENT
        when (intent?.action) {
            IntentAction.start -> enqueueCommand {
                val session = buildSessionFromStartIntent(
                    intent = intent,
                    defaultProfile = CheckSource.profile(),
                    defaultTimeoutMs = CheckSource.currentTimeoutMs(),
                    checkSearch = CheckSource.checkSearch,
                    checkDiscovery = CheckSource.checkDiscovery,
                    checkInfo = CheckSource.checkInfo,
                    checkCategory = CheckSource.checkCategory,
                    checkContent = CheckSource.checkContent,
                )
                if (session == null) {
                    stopSelf()
                } else {
                    startOrResumeSession(session)
                }
            }

            IntentAction.pause -> enqueueCommand {
                pauseSession()
            }

            IntentAction.resume -> enqueueCommand {
                resumeStoredSession()
            }

            IntentAction.stop -> {
                startResult = START_NOT_STICKY
                enqueueCommand {
                    cancelSession()
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        return startResult
    }

    override fun onDestroy() {
        checkJob?.cancel()
        if (::checkDispatcher.isInitialized) {
            checkDispatcher.close()
        }
        sessionRunning = false
        Debug.finishChecking()
        postDoneOnce()
        notificationManager.cancel(NotificationId.CheckSourceService)
        super.onDestroy()
    }

    private fun enqueueCommand(block: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            commandMutex.withLock {
                block()
            }
        }
    }

    private suspend fun startOrResumeSession(requested: CheckSourceSession) {
        if (checkJob?.isActive == true) {
            toastOnUi(getString(R.string.source_already_checking))
            return
        }
        val stored = CheckSourceSessionStore.load(this)
        if (
            stored != null &&
            activeSession == null &&
            stored.sourceUrls == requested.sourceUrls &&
            stored.pendingSourceUrls.isNotEmpty()
        ) {
            resumeSession(stored, markOrphanRunsInterrupted = true)
            return
        }
        startSession(requested)
    }

    private suspend fun startSession(session: CheckSourceSession) {
        cancelRequested = false
        donePosted = false
        sessionRunning = true
        val started = session.copy(
            paused = false,
            startedAt = session.startedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        activeSession = started
        CheckSourceSessionStore.save(this, started)
        Debug.isChecking = true
        launchSession(started, markOrphanRunsInterrupted = false)
        updateNotificationFromSession(started, "")
    }

    private suspend fun resumeStoredSession() {
        if (checkJob?.isActive == true) {
            upNotification()
            return
        }
        val session = activeSession ?: CheckSourceSessionStore.load(this)
        if (session == null || session.pendingSourceUrls.isEmpty()) {
            finishSession()
            return
        }
        resumeSession(
            session = session,
            markOrphanRunsInterrupted = activeSession == null,
        )
    }

    private suspend fun resumeSession(
        session: CheckSourceSession,
        markOrphanRunsInterrupted: Boolean,
    ) {
        cancelRequested = false
        donePosted = false
        sessionRunning = true
        val resumed = session.copy(
            paused = false,
            updatedAt = System.currentTimeMillis(),
        )
        activeSession = resumed
        CheckSourceSessionStore.save(this, resumed)
        Debug.isChecking = true
        launchSession(resumed, markOrphanRunsInterrupted)
        updateNotificationFromSession(resumed, "")
    }

    private suspend fun pauseSession() {
        val session = activeSession ?: CheckSourceSessionStore.load(this) ?: return
        val paused = session.copy(
            paused = true,
            updatedAt = System.currentTimeMillis(),
        )
        activeSession = paused
        CheckSourceSessionStore.save(this, paused)
        Debug.finishChecking()
        sessionRunning = false
        updateNotificationFromSession(paused, getString(R.string.pause))
        checkJob?.cancel()
    }

    private fun cancelSession() {
        cancelRequested = true
        sessionRunning = false
        checkJob?.cancel()
        activeSession = null
        CheckSourceSessionStore.clear(this)
        Debug.finishChecking()
        postDoneOnce()
        stopSelf()
    }

    private fun launchSession(
        session: CheckSourceSession,
        markOrphanRunsInterrupted: Boolean,
    ) {
        checkJob = lifecycleScope.launch(checkDispatcher) {
            try {
                if (markOrphanRunsInterrupted) {
                    markInterruptedRuns(session.pendingSourceUrls)
                }
                runSession(session)
                val current = activeSession
                if (current != null && current.pendingSourceUrls.isEmpty() && !current.paused) {
                    finishSession()
                }
            } catch (_: CancellationException) {
                if (!cancelRequested) {
                    activeSession?.let { updateNotificationFromSession(it, getString(R.string.pause)) }
                }
            }
        }
    }

    private suspend fun runSession(session: CheckSourceSession) = coroutineScope {
        val sources = loadPendingSources(session.pendingSourceUrls)
        markMissingSourcesCompleted(session.pendingSourceUrls, sources)
        if (sources.isEmpty()) {
            return@coroutineScope
        }
        val domainGroups = sources.groupBy { source -> domainKey(source.bookSourceUrl) }.values
        val domainSemaphore = Semaphore(threadCount)
        domainGroups.map { group ->
            async {
                domainSemaphore.withPermit {
                    group.forEachIndexed { index, source ->
                        currentCoroutineContext().ensureActive()
                        if (index > 0) {
                            delay(SAME_DOMAIN_BACKOFF_MS)
                        }
                        val run = checkSource(source, session)
                        updateCompletedSource(source, run)
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun loadPendingSources(sourceUrls: List<String>): List<BookSource> =
        sourceUrls.mapNotNull { sourceUrl ->
            appDb.bookSourceDao.getBookSource(sourceUrl)
        }

    private suspend fun markMissingSourcesCompleted(
        pendingSourceUrls: List<String>,
        loadedSources: List<BookSource>,
    ) {
        val loadedUrls = loadedSources.mapTo(hashSetOf()) { it.bookSourceUrl }
        pendingSourceUrls
            .filterNot { it in loadedUrls }
            .forEach { sourceUrl ->
                progressMutex.withLock {
                    val current = activeSession ?: return@withLock
                    val updated = current.copy(
                        pendingSourceUrls = current.pendingSourceUrls - sourceUrl,
                        failedCount = current.failedCount + 1,
                        updatedAt = System.currentTimeMillis(),
                    )
                    activeSession = updated
                    CheckSourceSessionStore.save(this, updated)
                    updateNotificationFromSession(updated, sourceUrl)
                }
            }
    }

    private suspend fun checkSource(
        source: BookSource,
        session: CheckSourceSession,
    ): SourceCheckRun {
        val checkSource = source.copy().apply {
            if (!session.checkSearch) {
                searchUrl = null
                ruleSearch = null
            }
            if (!session.checkDiscovery) {
                enabledExplore = false
                exploreUrl = null
                ruleExplore = null
            }
            if (!session.checkInfo) {
                ruleBookInfo = null
                ruleToc = null
                ruleContent = null
            } else {
                if (!session.checkCategory) {
                    ruleToc = null
                    ruleContent = null
                } else if (!session.checkContent) {
                    ruleContent = null
                }
            }
        }
        Debug.startChecking(checkSource)
        val run = sourceCheckEngine.checkBookSource(
            source = checkSource,
            profile = session.profile,
            persistSummary = true,
            timeoutMs = session.timeoutMs,
        )
        if (run.healthStatus in SUCCESS_STATUSES) {
            Debug.updateFinalMessage(checkSource.bookSourceUrl, "校验成功")
        } else {
            Debug.updateFinalMessage(
                checkSource.bookSourceUrl,
                "校验失败:${run.messageRedacted ?: run.healthStatus.name}"
            )
        }
        return run
    }

    private suspend fun updateCompletedSource(
        source: BookSource,
        run: SourceCheckRun,
    ) {
        progressMutex.withLock {
            val current = activeSession ?: return@withLock
            val success = run.healthStatus in SUCCESS_STATUSES
            val updated = current.copy(
                pendingSourceUrls = current.pendingSourceUrls - source.bookSourceUrl,
                healthyCount = current.healthyCount + if (success) 1 else 0,
                failedCount = current.failedCount + if (success) 0 else 1,
                updatedAt = System.currentTimeMillis(),
            )
            activeSession = updated
            CheckSourceSessionStore.save(this, updated)
            updateNotificationFromSession(updated, source.bookSourceName)
        }
    }

    private suspend fun markInterruptedRuns(sourceUrls: List<String>) {
        val finishedAt = System.currentTimeMillis()
        sourceUrls.distinct().forEach { sourceUrl ->
            val run = appDb.sourceCheckDao.getLatestRunForSource(sourceUrl)
            if (run?.statusValue == SourceCheckRunStatus.RUNNING) {
                sourceCheckRepository.markInterrupted(run.id, finishedAt)
            }
        }
    }

    private suspend fun finishSession() {
        progressMutex.withLock {
            val total = activeSession?.totalCount ?: finishCount
            activeSession = null
            CheckSourceSessionStore.clear(this)
            originSize = total
            finishCount = total
            notificationMsg = getString(R.string.progress_show, "", total, total)
        }
        sessionRunning = false
        Debug.finishChecking()
        postDoneOnce()
        stopSelf()
    }

    private fun updateNotificationFromSession(
        session: CheckSourceSession,
        sourceName: String,
    ) {
        originSize = session.totalCount
        finishCount = session.totalCount - session.pendingSourceUrls.size
        notificationMsg = getString(
            R.string.progress_show,
            sourceName,
            finishCount,
            originSize,
        )
        upNotification()
    }

    private fun upNotification() {
        postEvent(EventBus.CHECK_SOURCE, notificationMsg)
        notificationManager.notify(NotificationId.CheckSourceService, buildNotification())
    }

    override fun startForegroundNotification() {
        startForeground(NotificationId.CheckSourceService, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
        .setSmallIcon(R.drawable.ic_network_check)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentTitle(getString(R.string.check_book_source))
        .setContentText(notificationMsg)
        .setProgress(originSize, finishCount, false)
        .setContentIntent(
            activityPendingIntent<BookSourceActivity>("activity")
        )
        .apply {
            val session = activeSession
            if (session != null) {
                if (session.paused || !sessionRunning) {
                    addAction(
                        R.drawable.ic_play,
                        getString(R.string.resume),
                        servicePendingIntent<CheckSourceService>(
                            IntentAction.resume,
                            ACTION_RESUME_REQUEST,
                        )
                    )
                } else {
                    addAction(
                        R.drawable.ic_pause,
                        getString(R.string.pause),
                        servicePendingIntent<CheckSourceService>(
                            IntentAction.pause,
                            ACTION_PAUSE_REQUEST,
                        )
                    )
                }
            }
            addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<CheckSourceService>(
                    IntentAction.stop,
                    ACTION_STOP_REQUEST,
                )
            )
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
        .build()

    private fun postDoneOnce() {
        if (donePosted) return
        donePosted = true
        postEvent(EventBus.CHECK_SOURCE_DONE, 0)
    }

    companion object {
        const val EXTRA_SELECTED_IDS = "selectedSourceIds"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_TIMEOUT_MS = "timeoutMs"

        private const val LEGACY_SELECTED_IDS_KEY = "checkSourceSelectedIds"
        private const val DEFAULT_TIMEOUT_MS = 180_000L
        private const val SAME_DOMAIN_BACKOFF_MS = 150L
        private const val ACTION_PAUSE_REQUEST = 1
        private const val ACTION_RESUME_REQUEST = 2
        private const val ACTION_STOP_REQUEST = 3

        fun hasSession(context: Context): Boolean =
            CheckSourceSessionStore.hasSession(context)

        internal fun buildSessionFromStartIntent(
            intent: Intent,
            now: Long = System.currentTimeMillis(),
            defaultProfile: SourceCheckProfile = SourceCheckProfile.FULL,
            defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
            checkSearch: Boolean = true,
            checkDiscovery: Boolean = true,
            checkInfo: Boolean = true,
            checkCategory: Boolean = true,
            checkContent: Boolean = true,
        ): CheckSourceSession? {
            val sourceUrls = intent.selectedSourceUrls().distinct()
            if (sourceUrls.isEmpty()) return null
            val profile = intent.getStringExtra(EXTRA_PROFILE)
                ?.let { runCatching { SourceCheckProfile.valueOf(it) }.getOrNull() }
                ?: defaultProfile
            val timeoutMs = intent
                .getLongExtra(EXTRA_TIMEOUT_MS, defaultTimeoutMs)
                .coerceAtLeast(0L)
            return CheckSourceSession(
                sourceUrls = sourceUrls,
                pendingSourceUrls = sourceUrls,
                profile = profile,
                timeoutMs = timeoutMs,
                checkSearch = checkSearch,
                checkDiscovery = checkDiscovery,
                checkInfo = checkInfo,
                checkCategory = checkCategory,
                checkContent = checkContent,
                startedAt = now,
                updatedAt = now,
            )
        }

        internal fun domainKey(sourceUrl: String): String =
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

        private fun Intent.selectedSourceUrls(): List<String> =
            getStringArrayListExtra(EXTRA_SELECTED_IDS)
                ?.filter(String::isNotBlank)
                ?: IntentData.get<List<String>>(LEGACY_SELECTED_IDS_KEY)
                    ?.filter(String::isNotBlank)
                ?: emptyList()

        private val SUCCESS_STATUSES = setOf(
            BookSourceHealthStatus.HEALTHY,
            BookSourceHealthStatus.DEGRADED,
            BookSourceHealthStatus.STALE,
        )
    }
}
