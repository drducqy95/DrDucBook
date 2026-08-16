package io.legado.app.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.usecase.AuthoringProjectUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val projectsUseCase: AuthoringProjectUseCase,
    private val aiAgentGateway: AiAgentGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState(modules = workspaceModules()))
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkspaceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var loadJob: Job? = null

    init {
        observeWorkspace()
    }

    fun onIntent(intent: WorkspaceIntent) {
        when (intent) {
            WorkspaceIntent.Refresh -> observeWorkspace()
            is WorkspaceIntent.OpenModule -> {
                val available = _uiState.value.modules
                    .firstOrNull { it.module == intent.module }
                    ?.available == true
                if (available) {
                    _effects.tryEmit(WorkspaceEffect.OpenModule(intent.module))
                }
            }
        }
    }

    private fun observeWorkspace() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        loadJob = viewModelScope.launch {
            combine(
                projectsUseCase.observe(AuthoringProjectKind.WRITING),
                projectsUseCase.observe(AuthoringProjectKind.EBOOK_EDITOR),
                aiAgentGateway.observeRecentRuns(RECENT_AGENT_RUN_LIMIT),
            ) { writingProjects, ebookProjects, agentRuns ->
                buildWorkspaceUiState(writingProjects, ebookProjects, agentRuns)
            }.catch {
                _uiState.update { state -> state.copy(isLoading = false, hasError = true) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private companion object {
        const val RECENT_AGENT_RUN_LIMIT = 6
    }
}

internal fun buildWorkspaceUiState(
    writingProjects: List<AuthoringProject>,
    ebookProjects: List<AuthoringProject>,
    agentRuns: List<AiAgentRun>,
): WorkspaceUiState {
    val recentItems = buildList {
        writingProjects.forEach { project ->
            add(project.toRecentUi(WorkspaceModule.WRITING))
        }
        ebookProjects.forEach { project ->
            add(project.toRecentUi(WorkspaceModule.EBOOK_EDITOR))
        }
        agentRuns.forEach { run ->
            add(
                WorkspaceRecentUi(
                    id = "agent:${run.id}",
                    module = WorkspaceModule.AGENT,
                    title = run.taskType.orEmpty().ifBlank { run.finalTextPreview },
                    updatedAt = run.updatedAt,
                )
            )
        }
    }.sortedByDescending(WorkspaceRecentUi::updatedAt)
        .take(RECENT_ITEM_LIMIT)
        .toImmutableList()

    return WorkspaceUiState(
        isLoading = false,
        modules = workspaceModules(
            writingCount = writingProjects.size,
            ebookCount = ebookProjects.size,
            agentCount = agentRuns.size,
        ),
        recentItems = recentItems,
    )
}

private fun workspaceModules(
    writingCount: Int = 0,
    ebookCount: Int = 0,
    agentCount: Int = 0,
) = listOf(
    WorkspaceModuleUi(WorkspaceModule.WRITING, badgeCount = writingCount.takeIf { it > 0 }),
    WorkspaceModuleUi(WorkspaceModule.EBOOK_EDITOR, badgeCount = ebookCount.takeIf { it > 0 }),
    WorkspaceModuleUi(WorkspaceModule.AGENT, badgeCount = agentCount.takeIf { it > 0 }),
    WorkspaceModuleUi(WorkspaceModule.RSS),
    WorkspaceModuleUi(WorkspaceModule.STORY_WIKI),
).toImmutableList()

private fun AuthoringProject.toRecentUi(module: WorkspaceModule) = WorkspaceRecentUi(
    id = "${module.name.lowercase()}:$id",
    module = module,
    title = title,
    updatedAt = updatedAt,
)

private const val RECENT_ITEM_LIMIT = 8
