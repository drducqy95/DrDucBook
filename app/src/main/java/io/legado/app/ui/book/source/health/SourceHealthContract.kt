package io.legado.app.ui.book.source.health

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class SourceHealthItemUi(
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val sourceType: SourceKeyType,
    val homeUrl: String?,
    val loginUrl: String?,
    val iconPath: String?,
    val isVbook: Boolean,
    val enabled: Boolean,
    val status: BookSourceHealthStatus,
    val lastChecked: Long,
    val latencyMs: Long?,
    val message: String?,
    val hasLoginUrl: Boolean,
)

@Stable
data class SourceHealthSummaryUi(
    val total: Int = 0,
    val checked: Int = 0,
    val healthy: Int = 0,
    val needsAttention: Int = 0,
    val authRequired: Int = 0,
    val captchaRequired: Int = 0,
)

@Stable
data class SourceHealthRunUi(
    val id: String,
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val profile: SourceCheckProfile,
    val runStatus: SourceCheckRunStatus,
    val healthStatus: BookSourceHealthStatus,
    val startedAt: Long,
    val finishedAt: Long?,
    val latencyMs: Long?,
    val stageCount: Int,
    val passedStageCount: Int,
    val failedStageCount: Int,
    val skippedStageCount: Int,
    val message: String?,
)

@Stable
data class SourceHealthStageUi(
    val stageKey: String,
    val stageOrder: Int,
    val status: SourceCheckStageStatus,
    val startedAt: Long,
    val finishedAt: Long?,
    val latencyMs: Long?,
    val httpStatus: Int?,
    val failureStep: String?,
    val message: String?,
)

enum class SourceHealthFilter {
    ALL,
    HEALTHY,
    ERROR,
    AUTH_REQUIRED,
    CAPTCHA_REQUIRED,
}

@Stable
data class SourceHealthUiState(
    val loading: Boolean = true,
    val checking: Boolean = false,
    val query: String = "",
    val filter: SourceHealthFilter = SourceHealthFilter.ALL,
    val summary: SourceHealthSummaryUi = SourceHealthSummaryUi(),
    val items: ImmutableList<SourceHealthItemUi> = persistentListOf(),
    val recentRuns: ImmutableList<SourceHealthRunUi> = persistentListOf(),
    val selectedSourceUrl: String? = null,
    val selectedSource: SourceHealthItemUi? = null,
    val selectedRunId: String? = null,
    val selectedRuns: ImmutableList<SourceHealthRunUi> = persistentListOf(),
    val selectedStages: ImmutableList<SourceHealthStageUi> = persistentListOf(),
)

sealed interface SourceHealthIntent {
    data class Initialize(val sourceUrl: String?) : SourceHealthIntent
    data object Refresh : SourceHealthIntent
    data object CheckNow : SourceHealthIntent
    data class CheckSource(val sourceUrl: String) : SourceHealthIntent
    data class SetFilter(val filter: SourceHealthFilter) : SourceHealthIntent
    data class ChangeQuery(val query: String) : SourceHealthIntent
    data class SelectSource(val sourceUrl: String?) : SourceHealthIntent
    data class SelectRun(val runId: String?) : SourceHealthIntent
    data class OpenBrowser(
        val sourceUrl: String,
        val initialUrl: String?,
    ) : SourceHealthIntent
    data class OpenEdit(
        val sourceUrl: String,
        val sourceType: SourceKeyType,
    ) : SourceHealthIntent
}

sealed interface SourceHealthEffect {
    data class ShowMessage(val message: String) : SourceHealthEffect
    data class OpenBrowser(
        val sourceUrl: String,
        val initialUrl: String?,
    ) : SourceHealthEffect
    data class OpenEdit(
        val sourceUrl: String,
        val sourceType: SourceKeyType,
    ) : SourceHealthEffect
}
