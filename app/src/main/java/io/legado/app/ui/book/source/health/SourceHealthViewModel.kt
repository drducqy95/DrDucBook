package io.legado.app.ui.book.source.health

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.repository.BookSourceHealthRepository
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import io.legado.app.domain.model.BookSourceHealthRow
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.worker.BookSourceHealthWorker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceHealthViewModel(
    private val application: Application,
    private val repository: BookSourceHealthRepository,
    private val sourceCheckRepository: SourceCheckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceHealthUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SourceHealthEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val selectedSourceUrl = MutableStateFlow<String?>(null)
    private val selectedRunId = MutableStateFlow<String?>(null)

    private var sourceRows: List<BookSourceHealthRow> = emptyList()
    private var latestRuns: List<SourceCheckRun> = emptyList()
    private var selectedRuns: List<SourceCheckRun> = emptyList()
    private var selectedStages: List<SourceCheckStageResult> = emptyList()
    private var rowsJob: Job? = null
    private var latestRunsJob: Job? = null
    private var initialized = false

    init {
        observeRows()
        observeLatestRuns()
        observeSelectedSourceRuns()
        observeSelectedStages()
    }

    fun onIntent(intent: SourceHealthIntent) {
        when (intent) {
            is SourceHealthIntent.Initialize -> initialize(intent.sourceUrl)
            SourceHealthIntent.Refresh -> refresh()
            SourceHealthIntent.CheckNow -> checkNow(sourceUrl = null)
            is SourceHealthIntent.CheckSource -> checkNow(sourceUrl = intent.sourceUrl)
            is SourceHealthIntent.SetFilter -> {
                _uiState.update { it.copy(filter = intent.filter) }
                publishDashboard()
            }
            is SourceHealthIntent.ChangeQuery -> {
                _uiState.update { it.copy(query = intent.query) }
                publishDashboard()
            }
            is SourceHealthIntent.SelectSource -> selectSource(intent.sourceUrl, toggle = true)
            is SourceHealthIntent.SelectRun -> selectRun(intent.runId)
            is SourceHealthIntent.OpenBrowser -> _effects.tryEmit(
                SourceHealthEffect.OpenBrowser(
                    sourceUrl = intent.sourceUrl,
                    initialUrl = intent.initialUrl,
                )
            )
            is SourceHealthIntent.OpenEdit -> _effects.tryEmit(
                SourceHealthEffect.OpenEdit(
                    sourceUrl = intent.sourceUrl,
                    sourceType = intent.sourceType,
                )
            )
        }
    }

    private fun initialize(sourceUrl: String?) {
        if (initialized) return
        initialized = true
        sourceUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { selectSource(it, toggle = false) }
    }

    private fun refresh() {
        observeRows()
        observeLatestRuns()
    }

    private fun observeRows() {
        rowsJob?.cancel()
        _uiState.update { it.copy(loading = true) }
        rowsJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeRows()
                .catch { error ->
                    _uiState.update { it.copy(loading = false) }
                    showError(error)
                }
                .collect { rows ->
                    sourceRows = rows
                    publishDashboard(loading = false)
                }
        }
    }

    private fun observeLatestRuns() {
        latestRunsJob?.cancel()
        latestRunsJob = viewModelScope.launch(Dispatchers.IO) {
            sourceCheckRepository.observeLatestRuns(RECENT_RUN_LIMIT)
                .catch { error -> showError(error) }
                .collect { runs ->
                    latestRuns = runs
                    publishDashboard()
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedSourceRuns() {
        viewModelScope.launch(Dispatchers.IO) {
            selectedSourceUrl
                .flatMapLatest { sourceUrl ->
                    if (sourceUrl.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        sourceCheckRepository.observeRunsBySourceUrl(sourceUrl)
                    }
                }
                .catch { error ->
                    selectedRuns = emptyList()
                    publishDashboard()
                    showError(error)
                }
                .collect { runs ->
                    selectedRuns = runs.take(SELECTED_RUN_LIMIT)
                    reconcileSelectedRun()
                    publishDashboard()
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedStages() {
        viewModelScope.launch(Dispatchers.IO) {
            selectedRunId
                .flatMapLatest { runId ->
                    if (runId.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        sourceCheckRepository.observeStages(runId)
                    }
                }
                .catch { error ->
                    selectedStages = emptyList()
                    publishDashboard()
                    showError(error)
                }
                .collect { stages ->
                    selectedStages = stages
                    publishDashboard()
                }
        }
    }

    private fun selectSource(sourceUrl: String?, toggle: Boolean) {
        val current = _uiState.value.selectedSourceUrl
        val next = sourceUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { toggle && it == current }
        selectedSourceUrl.value = next
        selectedRunId.value = null
        selectedRuns = emptyList()
        selectedStages = emptyList()
        _uiState.update {
            it.copy(
                selectedSourceUrl = next,
                selectedSource = null,
                selectedRunId = null,
                selectedRuns = emptyList<SourceHealthRunUi>().toImmutableList(),
                selectedStages = emptyList<SourceHealthStageUi>().toImmutableList(),
            )
        }
        publishDashboard()
    }

    private fun selectRun(runId: String?) {
        val next = runId?.takeIf { id -> selectedRuns.any { it.id == id } }
        selectedRunId.value = next
        selectedStages = emptyList()
        _uiState.update {
            it.copy(
                selectedRunId = next,
                selectedStages = emptyList<SourceHealthStageUi>().toImmutableList(),
            )
        }
        publishDashboard()
    }

    private fun reconcileSelectedRun() {
        val currentRunId = selectedRunId.value
        val nextRunId = currentRunId
            ?.takeIf { runId -> selectedRuns.any { it.id == runId } }
            ?: selectedRuns.firstOrNull()?.id
        if (currentRunId != nextRunId) {
            selectedRunId.value = nextRunId
            _uiState.update { it.copy(selectedRunId = nextRunId) }
        }
    }

    private fun checkNow(sourceUrl: String?) {
        if (_uiState.value.checking) return
        _uiState.update { it.copy(checking = true) }
        BookSourceHealthWorker.runNow(application, sourceUrl)
        viewModelScope.launch {
            delay(750)
            _uiState.update { it.copy(checking = false) }
            _effects.tryEmit(
                SourceHealthEffect.ShowMessage(
                    application.getString(R.string.source_health_check_started)
                )
            )
        }
    }

    private fun publishDashboard(loading: Boolean? = null) {
        _uiState.update { current ->
            buildSourceHealthUiState(
                rows = sourceRows,
                recentRuns = latestRuns,
                selectedRuns = selectedRuns,
                selectedStages = selectedStages,
                current = if (loading == null) current else current.copy(loading = loading),
            )
        }
    }

    private fun showError(error: Throwable) {
        _effects.tryEmit(
            SourceHealthEffect.ShowMessage(
                error.localizedMessage ?: application.getString(R.string.error)
            )
        )
    }

    private companion object {
        const val RECENT_RUN_LIMIT = 12
        const val SELECTED_RUN_LIMIT = 16
    }
}

internal fun buildSourceHealthUiState(
    rows: List<BookSourceHealthRow>,
    recentRuns: List<SourceCheckRun>,
    selectedRuns: List<SourceCheckRun>,
    selectedStages: List<SourceCheckStageResult>,
    current: SourceHealthUiState,
): SourceHealthUiState {
    val query = current.query.trim()
    val items = rows
        .asSequence()
        .filter { row -> row.matchesQuery(query) }
        .filter { row -> row.matchesFilter(current.filter) }
        .map { row -> row.toSourceHealthItemUi() }
        .toList()
        .toImmutableList()
    val selectedSource = current.selectedSourceUrl
        ?.let { sourceUrl -> rows.firstOrNull { row -> row.sourceUrl == sourceUrl } }
        ?.toSourceHealthItemUi()
    return current.copy(
        summary = rows.toSummaryUi(),
        items = items,
        selectedSource = selectedSource,
        recentRuns = recentRuns.map { it.toSourceHealthRunUi() }.toImmutableList(),
        selectedRuns = selectedRuns.map { it.toSourceHealthRunUi() }.toImmutableList(),
        selectedStages = selectedStages.map { it.toSourceHealthStageUi() }.toImmutableList(),
    )
}

private fun BookSourceHealthRow.toSourceHealthItemUi(): SourceHealthItemUi {
    val healthStatus = health?.statusValue ?: BookSourceHealthStatus.UNKNOWN_OFFLINE
    return SourceHealthItemUi(
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceGroup = sourceGroup,
        sourceType = sourceType,
        homeUrl = homeUrl,
        loginUrl = loginUrl,
        iconPath = iconPath,
        isVbook = isVbook,
        enabled = enabled,
        status = healthStatus,
        lastChecked = health?.lastChecked ?: 0L,
        latencyMs = health?.latencyMs,
        message = health?.messageRedacted,
        hasLoginUrl = hasLoginUrl,
    )
}

private fun SourceCheckRun.toSourceHealthRunUi(): SourceHealthRunUi = SourceHealthRunUi(
    id = id,
    sourceUrl = sourceUrl,
    sourceName = sourceName,
    sourceGroup = sourceGroup,
    profile = profile,
    runStatus = status,
    healthStatus = healthStatus,
    startedAt = startedAt,
    finishedAt = finishedAt,
    latencyMs = latencyMs,
    stageCount = stageCount,
    passedStageCount = passedStageCount,
    failedStageCount = failedStageCount,
    skippedStageCount = skippedStageCount,
    message = messageRedacted,
)

private fun SourceCheckStageResult.toSourceHealthStageUi(): SourceHealthStageUi =
    SourceHealthStageUi(
        stageKey = stageKey,
        stageOrder = stageOrder,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = failureStep,
        message = messageRedacted,
    )

private fun List<BookSourceHealthRow>.toSummaryUi(): SourceHealthSummaryUi {
    val statuses = map { row -> row.health?.statusValue ?: BookSourceHealthStatus.UNKNOWN_OFFLINE }
    return SourceHealthSummaryUi(
        total = size,
        checked = count { row -> (row.health?.lastChecked ?: 0L) > 0L },
        healthy = statuses.count { it == BookSourceHealthStatus.HEALTHY },
        needsAttention = statuses.count(BookSourceHealthStatus::needsAttention),
        authRequired = statuses.count { it == BookSourceHealthStatus.AUTH_REQUIRED },
        captchaRequired = statuses.count { it == BookSourceHealthStatus.CAPTCHA_REQUIRED },
    )
}

private fun BookSourceHealthRow.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return sourceName.contains(query, ignoreCase = true) ||
        sourceUrl.contains(query, ignoreCase = true) ||
        sourceGroup.orEmpty().contains(query, ignoreCase = true) ||
        sourceType.name.contains(query, ignoreCase = true) ||
        health?.statusValue?.name.orEmpty().contains(query, ignoreCase = true)
}

private fun BookSourceHealthRow.matchesFilter(filter: SourceHealthFilter): Boolean {
    val status = health?.statusValue ?: BookSourceHealthStatus.UNKNOWN_OFFLINE
    return when (filter) {
        SourceHealthFilter.ALL -> true
        SourceHealthFilter.HEALTHY -> status == BookSourceHealthStatus.HEALTHY
        SourceHealthFilter.ERROR -> status.needsAttention()
        SourceHealthFilter.AUTH_REQUIRED -> status == BookSourceHealthStatus.AUTH_REQUIRED
        SourceHealthFilter.CAPTCHA_REQUIRED -> status == BookSourceHealthStatus.CAPTCHA_REQUIRED
    }
}

private fun BookSourceHealthStatus.needsAttention(): Boolean = this !in setOf(
    BookSourceHealthStatus.HEALTHY,
    BookSourceHealthStatus.DEGRADED,
    BookSourceHealthStatus.UNKNOWN_OFFLINE,
    BookSourceHealthStatus.STALE,
)
