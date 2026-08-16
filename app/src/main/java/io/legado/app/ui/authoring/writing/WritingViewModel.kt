package io.legado.app.ui.authoring.writing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.PreWritingSectionKey
import io.legado.app.domain.model.PreWritingSectionSource
import io.legado.app.domain.model.WritingWorkflowPolicy
import io.legado.app.domain.model.WritingWorkflowStage
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.AuthoringProjectUseCase
import io.legado.app.domain.usecase.AuthoringWorkflowUseCase
import io.legado.app.domain.usecase.AccountEntitlementUseCase
import io.legado.app.domain.model.AccountQuotaKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class WritingViewModel(
    private val projectsUseCase: AuthoringProjectUseCase,
    private val aiTextFactoryUseCase: AiTextFactoryUseCase,
    private val authoringWorkflowUseCase: AuthoringWorkflowUseCase,
    private val accountEntitlementUseCase: AccountEntitlementUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WritingUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<WritingEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var generationJob: Job? = null
    private var autosaveJob: Job? = null
    private var dirtyRevision = 0L
    private val undoStack = ArrayDeque<WritingEditSnapshot>()
    private val redoStack = ArrayDeque<WritingEditSnapshot>()

    init {
        viewModelScope.launch {
            projectsUseCase.observe(AuthoringProjectKind.WRITING)
                .catch { emitMessage(it.message ?: "Không thể tải dự án sáng tác") }
                .collect { projects ->
                    _uiState.update { state ->
                        val selected = state.project?.id?.let { id ->
                            projects.firstOrNull { it.id == id }
                        }
                        if (state.project == null || selected != null) {
                            state.copy(projects = projects.toImmutableList())
                        } else state.copy(projects = projects.toImmutableList()).open(null)
                    }
                }
        }
    }

    fun onIntent(intent: WritingIntent) {
        when (intent) {
            WritingIntent.BackPressed -> handleBack()
            WritingIntent.CloseProject -> requestPending(WritingPendingAction.CloseProject)
            is WritingIntent.UpdateSearchQuery -> _uiState.update {
                it.copy(searchQuery = intent.value)
            }
            is WritingIntent.OpenProject -> requestPending(
                WritingPendingAction.OpenProject(intent.projectId)
            )
            WritingIntent.ShowCreateProject -> requestPending(WritingPendingAction.CreateProject)
            is WritingIntent.CreateProject -> createProject(intent.title)
            WritingIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
            WritingIntent.RequestDeleteProject -> requestDelete()
            WritingIntent.ConfirmDeleteProject -> confirmDelete()
            WritingIntent.DuplicateProject -> duplicateProject()
            is WritingIntent.UpdateProjectTitle -> updateProject { it.copy(title = intent.value) }
            is WritingIntent.UpdateProjectAuthor -> updateProject { it.copy(author = intent.value) }
            is WritingIntent.UpdateProjectDescription -> updateProject {
                it.copy(description = intent.value)
            }
            is WritingIntent.SelectChapter -> selectChapter(intent.chapterId)
            WritingIntent.AddChapter -> addChapter()
            WritingIntent.DuplicateChapter -> duplicateChapter()
            WritingIntent.RequestDeleteChapter -> requestDeleteChapter()
            WritingIntent.ConfirmDeleteChapter -> confirmDeleteChapter()
            is WritingIntent.MoveChapter -> moveChapter(intent.direction)
            is WritingIntent.UpdateChapterTitle -> editState {
                it.copy(chapterTitle = intent.value)
            }
            is WritingIntent.UpdateChapterContent -> editState {
                it.copy(chapterContent = intent.value).withSearchStats()
            }
            is WritingIntent.UpdateReplaceQuery -> _uiState.update {
                it.copy(replaceQuery = intent.value).withSearchStats()
            }
            is WritingIntent.UpdateReplaceWith -> _uiState.update {
                it.copy(replaceWith = intent.value)
            }
            WritingIntent.ReplaceNext -> replaceText(replaceAll = false)
            WritingIntent.ReplaceAll -> replaceText(replaceAll = true)
            WritingIntent.Undo -> undo()
            WritingIntent.Redo -> redo()
            WritingIntent.RequestImage -> _effects.tryEmit(WritingEffect.OpenImagePicker)
            is WritingIntent.ImagePicked -> importImage(intent.displayName, intent.bytes)
            is WritingIntent.SelectWorkspaceMode -> selectWorkspaceMode(intent.value)
            is WritingIntent.SelectPreWritingSection -> selectPreWritingSection(intent.value)
            is WritingIntent.UpdatePreWritingContent -> editState { state ->
                val project = state.project
                if (project == null) {
                    state
                } else {
                    state.copy(
                        project = WritingWorkflowPolicy.invalidateForSectionEdit(
                            project,
                            state.selectedPreWritingSection,
                        ),
                        preWritingContent = intent.value,
                    )
                }
            }
            is WritingIntent.UpdateAiInstruction -> _uiState.update {
                it.copy(aiInstruction = intent.value)
            }
            WritingIntent.GenerateStoryBlueprint -> generateStoryBlueprint()
            WritingIntent.ApproveStoryBlueprint -> approveStoryBlueprint()
            WritingIntent.GenerateNarrativePlan -> generateNarrativePlan()
            WritingIntent.ApproveNarrativePlan -> approveNarrativePlan()
            WritingIntent.Save -> save()
            WritingIntent.FlushAutosave -> flushAutosave()
            WritingIntent.GenerateWithAi -> generateWithAi()
            WritingIntent.ApplyAiSuggestion -> applyAiSuggestion()
            WritingIntent.DismissAiSuggestion -> _uiState.update { it.copy(aiSuggestion = "") }
            WritingIntent.SaveAndContinue -> saveAndContinue()
            WritingIntent.DiscardAndContinue -> discardAndContinue()
        }
    }

    private fun handleBack() {
        if (_uiState.value.project == null) {
            _effects.tryEmit(WritingEffect.NavigateBack)
        } else {
            requestPending(WritingPendingAction.CloseProject)
        }
    }

    private fun openProject(projectId: String) {
        _uiState.value.projects.firstOrNull { it.id == projectId }?.let { project ->
            _uiState.update { it.open(project) }
        }
    }

    private fun requestPending(action: WritingPendingAction) {
        val state = _uiState.value
        if (state.isDirty) {
            _uiState.update { it.copy(dialog = WritingDialog.UnsavedChanges(action)) }
        } else {
            performPending(action)
        }
    }

    private fun performPending(action: WritingPendingAction) {
        when (action) {
            WritingPendingAction.ExitModule -> _effects.tryEmit(WritingEffect.NavigateBack)
            WritingPendingAction.CloseProject -> _uiState.update { it.open(null) }
            WritingPendingAction.CreateProject -> _uiState.update {
                it.copy(dialog = WritingDialog.CreateProject)
            }
            is WritingPendingAction.OpenProject -> openProject(action.projectId)
        }
    }

    private fun createProject(title: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, dialog = null) }
            runCatching { projectsUseCase.create(AuthoringProjectKind.WRITING, title) }
                .onSuccess { project -> _uiState.update { it.open(project).copy(isLoading = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    emitMessage(error.message ?: "Không thể tạo dự án")
                }
        }
    }

    private fun requestDelete() {
        val project = _uiState.value.project ?: return
        _uiState.update {
            it.copy(dialog = WritingDialog.DeleteProject(project.id, project.title))
        }
    }

    private fun confirmDelete() {
        val dialog = _uiState.value.dialog as? WritingDialog.DeleteProject ?: return
        viewModelScope.launch {
            autosaveJob?.cancel()
            projectsUseCase.delete(dialog.projectId)
            _uiState.update { it.open(null).copy(dialog = null) }
            clearHistory()
        }
    }

    private fun updateProject(transform: (AuthoringProject) -> AuthoringProject) {
        editState { state ->
            state.project?.let { state.copy(project = transform(it)) } ?: state
        }
    }

    private fun duplicateProject() {
        val project = materializeProject() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { projectsUseCase.duplicate(project) }
                .onSuccess { duplicate ->
                    autosaveJob?.cancel()
                    clearHistory()
                    _uiState.update { it.open(duplicate).copy(isLoading = false) }
                    emitMessage("Đã nhân bản tác phẩm")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    emitMessage(error.message ?: "Không thể nhân bản tác phẩm")
                }
        }
    }

    private fun selectChapter(chapterId: String) {
        val project = materializeProject() ?: return
        val chapter = project.chapters.firstOrNull { it.id == chapterId } ?: return
        _uiState.update {
            it.copy(
                project = project,
                selectedChapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterContent = chapter.content,
                aiSuggestion = "",
            ).withSearchStats()
        }
    }

    private fun selectWorkspaceMode(mode: WritingWorkspaceMode) {
        val project = materializeProject() ?: return
        if (mode == WritingWorkspaceMode.MANUSCRIPT &&
            !WritingWorkflowPolicy.canWriteManuscript(project)
        ) {
            _uiState.update { it.copy(project = project) }
            emitMessage("Hãy hoàn tất và duyệt đại cương hồi, quyển trước khi bắt đầu viết")
            return
        }
        val section = if (mode == WritingWorkspaceMode.OUTLINE) {
            PreWritingSectionKey.OUTLINE
        } else _uiState.value.selectedPreWritingSection
        _uiState.update {
            it.copy(
                project = project,
                workspaceMode = mode,
                selectedPreWritingSection = section,
                preWritingContent = project.preproduction.section(section).content,
                aiSuggestion = "",
            )
        }
    }

    private fun selectPreWritingSection(key: PreWritingSectionKey) {
        val project = materializeProject() ?: return
        _uiState.update {
            it.copy(
                project = project,
                selectedPreWritingSection = key,
                preWritingContent = project.preproduction.section(key).content,
                aiSuggestion = "",
            )
        }
    }

    private fun applyAiSuggestion() {
        recordUndo()
        if (_uiState.value.workspaceMode in setOf(
                WritingWorkspaceMode.PREWRITING,
                WritingWorkspaceMode.OUTLINE,
            )
        ) {
            val state = _uiState.value
            val project = materializeProject() ?: return
            val updated = project.copy(
                preproduction = project.preproduction.update(
                    key = state.selectedPreWritingSection,
                    content = state.aiSuggestion,
                    source = PreWritingSectionSource.AI_APPLIED,
                    now = System.currentTimeMillis(),
                )
            )
            _uiState.update {
                it.copy(
                    project = updated,
                    preWritingContent = it.aiSuggestion,
                    aiSuggestion = "",
                ).markDirty()
            }
        } else {
            _uiState.update {
                it.copy(chapterContent = it.aiSuggestion, aiSuggestion = "")
                    .markDirty()
                    .withSearchStats()
            }
        }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun generateStoryBlueprint() {
        val project = materializeProject() ?: return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(project = project, isGenerating = true, aiSuggestion = "") }
            runCatching { authoringWorkflowUseCase.generateBlueprint(project) }
                .onSuccess { draft ->
                    val updated = authoringWorkflowUseCase.applyBlueprint(project, draft)
                    applyWorkflowProject(updated, PreWritingSectionKey.DETAILED_OUTLINE)
                    emitMessage("Đã hoàn thiện đề cương chi tiết. Hãy kiểm tra trước khi duyệt")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isGenerating = false) }
                    emitMessage(error.message ?: "AI không thể hoàn thiện đề cương")
                }
        }
    }

    private fun approveStoryBlueprint() {
        val project = materializeProject() ?: return
        runCatching { authoringWorkflowUseCase.approveBlueprint(project) }
            .onSuccess { updated ->
                applyWorkflowProject(updated, PreWritingSectionKey.ARC_VOLUME_OUTLINE)
                emitMessage("Đã duyệt đề cương chi tiết")
            }
            .onFailure { emitMessage(it.message ?: "Đề cương chi tiết chưa đầy đủ") }
    }

    private fun generateNarrativePlan() {
        val project = materializeProject() ?: return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(project = project, isGenerating = true, aiSuggestion = "") }
            runCatching { authoringWorkflowUseCase.generateNarrativePlan(project) }
                .onSuccess { draft ->
                    val updated = authoringWorkflowUseCase.applyNarrativePlan(project, draft)
                    applyWorkflowProject(updated, PreWritingSectionKey.ARC_VOLUME_OUTLINE)
                    emitMessage("Đã tạo đại cương hồi, quyển. Hãy kiểm tra trước khi duyệt")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isGenerating = false) }
                    emitMessage(error.message ?: "AI không thể triển khai đại cương hồi, quyển")
                }
        }
    }

    private fun approveNarrativePlan() {
        val project = materializeProject() ?: return
        runCatching { authoringWorkflowUseCase.approveNarrativePlan(project) }
            .onSuccess { updated ->
                applyWorkflowProject(
                    project = updated,
                    section = PreWritingSectionKey.CHAPTER_ROADMAP,
                    workspaceMode = WritingWorkspaceMode.MANUSCRIPT,
                )
                emitMessage("Đại cương đã hoàn tất. Bạn có thể bắt đầu viết")
            }
            .onFailure { emitMessage(it.message ?: "Đại cương hồi, quyển chưa đầy đủ") }
    }

    private fun applyWorkflowProject(
        project: AuthoringProject,
        section: PreWritingSectionKey,
        workspaceMode: WritingWorkspaceMode = WritingWorkspaceMode.PREWRITING,
    ) {
        recordUndo()
        _uiState.update {
            it.copy(
                project = project,
                selectedPreWritingSection = section,
                preWritingContent = project.preproduction.section(section).content,
                workspaceMode = workspaceMode,
                isGenerating = false,
                aiSuggestion = "",
            ).markDirty()
        }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun addChapter() {
        val project = materializeProject() ?: return
        if (!WritingWorkflowPolicy.canWriteManuscript(project)) {
            _uiState.update { it.copy(project = project) }
            emitMessage("Hãy duyệt đại cương hồi, quyển trước khi thêm chương")
            return
        }
        recordUndo()
        viewModelScope.launch {
            runCatching {
                accountEntitlementUseCase.consume(
                    AccountQuotaKind.AUTHORING_CHAPTER,
                    listOf("${project.id}:new:${UUID.randomUUID()}"),
                )
                projectsUseCase.addChapter(project, "")
            }
                .onSuccess { updated ->
                    _uiState.update { it.open(updated, updated.chapters.lastOrNull()?.id) }
                    syncHistoryFlags()
                }
                .onFailure { emitMessage(it.message ?: "Không thể thêm chương") }
        }
    }

    private fun duplicateChapter() {
        val state = _uiState.value
        val project = materializeProject() ?: return
        val selectedId = state.selectedChapterId ?: return
        viewModelScope.launch {
            runCatching {
                accountEntitlementUseCase.consume(
                    AccountQuotaKind.AUTHORING_CHAPTER,
                    listOf("${project.id}:duplicate:$selectedId:${UUID.randomUUID()}"),
                )
            }.onSuccess {
                recordUndo()
                val now = System.currentTimeMillis()
                duplicateChapterInProject(project, selectedId, now)?.let { (updated, duplicateId) ->
                    val chapter = updated.chapters.first { it.id == duplicateId }
                    _uiState.update {
                        it.copy(
                            project = updated,
                            selectedChapterId = chapter.id,
                            chapterTitle = chapter.title,
                            chapterContent = chapter.content,
                            aiSuggestion = "",
                        ).markDirty().withSearchStats()
                    }
                    scheduleAutosave()
                    syncHistoryFlags()
                }
            }.onFailure { emitMessage(it.message ?: "Không thể nhân bản chương") }
        }
    }

    private fun requestDeleteChapter() {
        val state = _uiState.value
        val chapter = state.project?.chapters?.firstOrNull { it.id == state.selectedChapterId }
            ?: return
        _uiState.update {
            it.copy(dialog = WritingDialog.DeleteChapter(chapter.id, state.chapterTitle))
        }
    }

    private fun confirmDeleteChapter() {
        val dialog = _uiState.value.dialog as? WritingDialog.DeleteChapter ?: return
        val project = materializeProject() ?: return
        val remaining = project.chapters.filterNot { it.id == dialog.chapterId }
        val next = remaining.firstOrNull()
        recordUndo()
        _uiState.update {
            it.copy(
                project = project.copy(chapters = remaining),
                selectedChapterId = next?.id,
                chapterTitle = next?.title.orEmpty(),
                chapterContent = next?.content.orEmpty(),
                dialog = null,
            ).markDirty().withSearchStats()
        }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun moveChapter(direction: Int) {
        val project = materializeProject() ?: return
        val selectedId = _uiState.value.selectedChapterId ?: return
        val from = project.chapters.indexOfFirst { it.id == selectedId }
        val to = (from + direction).coerceIn(project.chapters.indices)
        if (from < 0 || from == to) return
        val reordered = project.chapters.toMutableList().apply {
            add(to, removeAt(from))
        }
        recordUndo()
        _uiState.update {
            it.copy(project = project.copy(chapters = reordered)).markDirty()
        }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun replaceText(replaceAll: Boolean) {
        val state = _uiState.value
        if (state.replaceQuery.isEmpty() || state.selectedChapterId == null) return
        val result = if (replaceAll) {
            replaceAllLiteral(state.chapterContent, state.replaceQuery, state.replaceWith)
        } else {
            replaceFirstLiteral(state.chapterContent, state.replaceQuery, state.replaceWith)
        }
        if (result.replacements == 0) {
            emitMessage("Không tìm thấy nội dung cần thay")
            return
        }
        editState {
            it.copy(chapterContent = result.text).withSearchStats()
        }
        emitMessage("Đã thay ${result.replacements} vị trí")
    }

    private fun importImage(displayName: String, bytes: ByteArray) {
        val state = _uiState.value
        val project = state.project ?: return
        if (state.selectedChapterId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { projectsUseCase.importImage(project.id, displayName, bytes) }
                .onSuccess { path ->
                    _uiState.update { it.copy(isLoading = false) }
                    editState {
                        val marker = "[image:$path]"
                        val content = it.chapterContent.trimEnd()
                        it.copy(
                            chapterContent = if (content.isBlank()) {
                                marker
                            } else {
                                "$content\n\n$marker"
                            }
                        ).withSearchStats()
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    emitMessage(error.message ?: "Không thể chèn ảnh")
                }
        }
    }

    private fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(captureSnapshot())
        restoreSnapshot(snapshot)
    }

    private fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(captureSnapshot())
        restoreSnapshot(snapshot)
    }

    private fun save(silent: Boolean = false, onSuccess: (() -> Unit)? = null) {
        val project = materializeProject() ?: return
        val revisionAtStart = dirtyRevision
        if (!silent) autosaveJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                if (silent) it.copy(autosaveState = WritingAutosaveState.SAVING)
                else it.copy(isLoading = true)
            }
            runCatching { projectsUseCase.save(project) }
                .onSuccess {
                    _uiState.update {
                        val savedCurrentRevision = dirtyRevision == revisionAtStart
                        it.copy(
                            project = if (savedCurrentRevision) project else it.project,
                            isLoading = false,
                            isDirty = if (savedCurrentRevision) false else it.isDirty,
                            autosaveState = if (savedCurrentRevision) {
                                WritingAutosaveState.SAVED
                            } else {
                                WritingAutosaveState.PENDING
                            },
                            dialog = if (silent) it.dialog else null,
                        )
                    }
                    if (!silent) emitMessage("Đã lưu tác phẩm")
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            autosaveState = if (silent) {
                                WritingAutosaveState.ERROR
                            } else {
                                it.autosaveState
                            },
                        )
                    }
                    if (!silent) emitMessage(error.message ?: "Không thể lưu")
                }
        }
    }

    private fun saveAndContinue() {
        val action = (_uiState.value.dialog as? WritingDialog.UnsavedChanges)?.action ?: return
        save { performPending(action) }
    }

    private fun discardAndContinue() {
        val action = (_uiState.value.dialog as? WritingDialog.UnsavedChanges)?.action ?: return
        autosaveJob?.cancel()
        val persisted = _uiState.value.project?.id?.let { currentId ->
            _uiState.value.projects.firstOrNull { it.id == currentId }
        }
        _uiState.update { it.open(persisted).copy(dialog = null) }
        clearHistory()
        performPending(action)
    }

    private fun flushAutosave() {
        if (!_uiState.value.isDirty) return
        autosaveJob?.cancel()
        save(silent = true)
    }

    private fun generateWithAi() {
        val state = _uiState.value
        val project = materializeProject() ?: return
        val instruction = state.aiInstruction.trim()
        if (instruction.isBlank()) {
            emitMessage("Hãy nhập yêu cầu cho AI")
            return
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, aiSuggestion = "") }
            if (state.workspaceMode == WritingWorkspaceMode.MANUSCRIPT &&
                !WritingWorkflowPolicy.canWriteManuscript(project)
            ) {
                _uiState.update { it.copy(isGenerating = false) }
                emitMessage("Đại cương chưa được duyệt")
                return@launch
            }
            val sourceText = (if (state.workspaceMode in setOf(
                    WritingWorkspaceMode.PREWRITING,
                    WritingWorkspaceMode.OUTLINE,
                )
            ) state.preWritingContent else state.chapterContent).ifBlank {
                "Bối cảnh tác phẩm: ${project.description.ifBlank { project.title }}"
            }
            runCatching {
                aiTextFactoryUseCase.executeStream(
                    AiTextFactoryUseCase.Request(
                        bookUrl = "authoring:${project.id}",
                        chapterTitle = state.chapterTitle,
                        inputText = sourceText,
                        taskType = if (state.workspaceMode == WritingWorkspaceMode.MANUSCRIPT) {
                            AiTaskType.AUTHORING_WRITER
                        } else {
                            AiTaskType.TEXT_FACTORY
                        },
                        userInstruction = instruction,
                        referenceText = buildAuthoringReference(project, state.selectedChapterId),
                        skipCache = true,
                    )
                ).collect { event ->
                    when (event) {
                        is AiTextFactoryUseCase.StreamEvent.Content -> _uiState.update {
                            it.copy(aiSuggestion = it.aiSuggestion + event.text)
                        }
                        is AiTextFactoryUseCase.StreamEvent.Done -> _uiState.update {
                            it.copy(aiSuggestion = event.text, isGenerating = false)
                        }
                        is AiTextFactoryUseCase.StreamEvent.Reasoning -> Unit
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isGenerating = false) }
                emitMessage(error.message ?: "AI không thể sinh nội dung")
            }
        }
    }

    private fun materializeProject(): AuthoringProject? {
        val state = _uiState.value
        val project = state.project ?: return null
        val chapterId = state.selectedChapterId
        val now = System.currentTimeMillis()
        val currentSection = project.preproduction.section(state.selectedPreWritingSection)
        val preproduction = if (currentSection.content != state.preWritingContent) {
            project.preproduction.update(
                key = state.selectedPreWritingSection,
                content = state.preWritingContent,
                source = PreWritingSectionSource.USER,
                now = now,
            )
        } else project.preproduction
        val withPreproduction = project.copy(preproduction = preproduction)
        val workflowAwareProject = if (preproduction !== project.preproduction) {
            WritingWorkflowPolicy.invalidateForSectionEdit(
                withPreproduction,
                state.selectedPreWritingSection,
            )
        } else {
            withPreproduction
        }
        return materializeChapterEdit(
            project = workflowAwareProject,
            selectedChapterId = chapterId,
            chapterTitle = state.chapterTitle,
            chapterContent = state.chapterContent,
            now = now,
        )
    }

    private fun WritingUiState.open(
        project: AuthoringProject?,
        preferredChapterId: String? = null,
    ): WritingUiState {
        val chapter = project?.chapters?.firstOrNull { it.id == preferredChapterId }
            ?: project?.chapters?.firstOrNull()
        val initialWorkspaceMode = when {
            project == null -> WritingWorkspaceMode.MANUSCRIPT
            WritingWorkflowPolicy.canWriteManuscript(project) -> WritingWorkspaceMode.MANUSCRIPT
            else -> WritingWorkspaceMode.PREWRITING
        }
        val initialSection = if (initialWorkspaceMode == WritingWorkspaceMode.PREWRITING) {
            PreWritingSectionKey.PREMISE
        } else {
            selectedPreWritingSection
        }
        return copy(
            project = project,
            selectedChapterId = chapter?.id,
            chapterTitle = chapter?.title.orEmpty(),
            chapterContent = chapter?.content.orEmpty(),
            aiSuggestion = "",
            searchResultCount = countLiteralOccurrences(
                chapter?.content.orEmpty(),
                replaceQuery,
            ),
            workspaceMode = initialWorkspaceMode,
            selectedPreWritingSection = initialSection,
            preWritingContent = project?.preproduction
                ?.section(initialSection)
                ?.content
                .orEmpty(),
            isDirty = false,
            dialog = null,
        )
    }

    private fun editState(transform: (WritingUiState) -> WritingUiState) {
        if (_uiState.value.project == null) return
        recordUndo()
        _uiState.update { transform(it).markDirty() }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun WritingUiState.markDirty(): WritingUiState {
        dirtyRevision += 1
        return copy(isDirty = true, autosaveState = WritingAutosaveState.PENDING)
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            save(silent = true)
        }
    }

    private fun WritingUiState.withSearchStats(): WritingUiState =
        copy(searchResultCount = countLiteralOccurrences(chapterContent, replaceQuery))

    private fun recordUndo() {
        undoStack.addLast(captureSnapshot())
        while (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun restoreSnapshot(snapshot: WritingEditSnapshot) {
        _uiState.update {
            snapshot.restoreInto(it).markDirty().withSearchStats()
        }
        scheduleAutosave()
        syncHistoryFlags()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun syncHistoryFlags() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    private fun captureSnapshot(): WritingEditSnapshot {
        val state = _uiState.value
        return WritingEditSnapshot(
            project = state.project,
            selectedChapterId = state.selectedChapterId,
            chapterTitle = state.chapterTitle,
            chapterContent = state.chapterContent,
            workspaceMode = state.workspaceMode,
            selectedPreWritingSection = state.selectedPreWritingSection,
            preWritingContent = state.preWritingContent,
        )
    }

    private fun emitMessage(message: String) {
        _effects.tryEmit(WritingEffect.ShowMessage(message))
    }

    private fun buildAuthoringReference(
        project: AuthoringProject,
        selectedChapterId: String?,
    ): String {
        val preproduction = project.preproduction
        val globalContext = listOf(
            "Đề cương chi tiết" to preproduction.detailedOutline.content,
            "Thế giới quan" to preproduction.worldBible.content,
            "Tuyến truyện chính" to preproduction.plotThreads.content,
            "Nhân vật" to preproduction.characterBible.content,
            "Đại cương hồi và quyển" to preproduction.arcVolumeOutline.content,
            "Mạch truyện" to preproduction.timeline.content,
            "Lộ trình chương" to preproduction.chapterRoadmap.content,
            "Văn phong" to preproduction.styleTone.content,
        ).filter { it.second.isNotBlank() }
            .joinToString("\n\n") { (title, content) -> "[$title]\n$content" }
        val selectedIndex = project.chapters.indexOfFirst { it.id == selectedChapterId }
        val previousChapters = if (selectedIndex >= 0) {
            project.chapters.take(selectedIndex).takeLast(2)
        } else {
            project.chapters.takeLast(2)
        }
        val localContext = previousChapters.joinToString("\n\n") { chapter ->
            "[${chapter.title}]\n${chapter.content.takeLast(2_000)}"
        }
        return listOf(globalContext, localContext)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        generationJob?.cancel()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 2_000L
        const val MAX_HISTORY_SIZE = 50
    }
}

private data class WritingEditSnapshot(
    val project: AuthoringProject?,
    val selectedChapterId: String?,
    val chapterTitle: String,
    val chapterContent: String,
    val workspaceMode: WritingWorkspaceMode,
    val selectedPreWritingSection: PreWritingSectionKey,
    val preWritingContent: String,
) {
    fun restoreInto(state: WritingUiState): WritingUiState = state.copy(
        project = project,
        selectedChapterId = selectedChapterId,
        chapterTitle = chapterTitle,
        chapterContent = chapterContent,
        workspaceMode = workspaceMode,
        selectedPreWritingSection = selectedPreWritingSection,
        preWritingContent = preWritingContent,
        aiSuggestion = "",
    )
}
