package io.legado.app.ui.authoring.ebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.FeatureFlags
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookBlockGeometry
import io.legado.app.domain.model.EbookCodeBlock
import io.legado.app.domain.model.EbookDividerBlock
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookHeadingBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.EbookListBlock
import io.legado.app.domain.model.EbookPageBreakBlock
import io.legado.app.domain.model.EbookPageSize
import io.legado.app.domain.model.EbookParagraphBlock
import io.legado.app.domain.model.EbookQuoteBlock
import io.legado.app.domain.model.legacyContentToBlocks
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.domain.model.toAuthoringChapters
import io.legado.app.domain.model.withGeometry
import io.legado.app.domain.model.withReadingOrder
import io.legado.app.domain.usecase.AuthoringProjectUseCase
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.CloneDownloadedBookRequest
import io.legado.app.domain.usecase.CloneDownloadedBookUseCase
import io.legado.app.domain.usecase.ExportAuthoringProjectUseCase
import io.legado.app.domain.usecase.ValidateEbookProjectUseCase
import io.legado.app.domain.usecase.AccountEntitlementUseCase
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.service.export.EbookExportFormat
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class EbookEditorViewModel(
    private val projectsUseCase: AuthoringProjectUseCase,
    private val aiTextFactoryUseCase: AiTextFactoryUseCase,
    private val cloneDownloadedBookUseCase: CloneDownloadedBookUseCase,
    private val exportUseCase: ExportAuthoringProjectUseCase,
    private val validateEbookProjectUseCase: ValidateEbookProjectUseCase,
    private val accountEntitlementUseCase: AccountEntitlementUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EbookEditorUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<EbookEditorEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private val undoHistory = ArrayDeque<AuthoringProject>()
    private val redoHistory = ArrayDeque<AuthoringProject>()
    private var textHistoryJob: Job? = null
    private var pendingTextHistoryBase: AuthoringProject? = null
    private var aiGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            projectsUseCase.observe(AuthoringProjectKind.EBOOK_EDITOR)
                .catch { emitMessage(it.message ?: "Không thể tải dự án ebook") }
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
        viewModelScope.launch {
            while (isActive) {
                delay(AUTOSAVE_INTERVAL_MS)
                if (_uiState.value.isDirty && !_uiState.value.isLoading) save(silent = true)
            }
        }
    }

    fun onIntent(intent: EbookEditorIntent) {
        when (intent) {
            EbookEditorIntent.BackPressed -> handleBack()
            EbookEditorIntent.CloseProject -> requestPending(EbookEditorPendingAction.CloseProject)
            is EbookEditorIntent.UpdateSearchQuery -> _uiState.update {
                it.copy(searchQuery = intent.value)
            }
            is EbookEditorIntent.OpenProject -> requestPending(
                EbookEditorPendingAction.OpenProject(intent.projectId)
            )
            EbookEditorIntent.ShowCreateProject -> requestPending(EbookEditorPendingAction.CreateProject)
            is EbookEditorIntent.CreateProject -> createProject(intent.title)
            EbookEditorIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
            EbookEditorIntent.RequestDeleteProject -> requestDelete()
            EbookEditorIntent.ConfirmDeleteProject -> confirmDelete()
            EbookEditorIntent.ShowCloneDownloadedBook -> showCloneDownloadedBook()
            EbookEditorIntent.ShowLayers -> _uiState.update { it.copy(sheet = EbookEditorSheet.Layers) }
            EbookEditorIntent.ShowChapterManager -> _uiState.update {
                it.copy(sheet = EbookEditorSheet.ChapterManager)
            }
            EbookEditorIntent.DismissSheet -> _uiState.update { it.copy(sheet = null) }
            is EbookEditorIntent.UpdateSourceBookUrl -> _uiState.update {
                it.copy(sourceBookUrl = intent.value)
            }
            is EbookEditorIntent.UpdateCloneQuery -> updateCloneQuery(intent.value)
            is EbookEditorIntent.UpdateCloneChapterScope -> _uiState.update {
                it.copy(cloneChapterScope = intent.value)
            }
            is EbookEditorIntent.UpdateCloneVariant -> _uiState.update {
                it.copy(cloneVariant = intent.value)
            }
            is EbookEditorIntent.UpdateCloneProvider -> _uiState.update {
                it.copy(cloneProvider = intent.value)
            }
            is EbookEditorIntent.UpdateCloneTargetLanguage -> _uiState.update {
                it.copy(cloneTargetLanguage = intent.value)
            }
            is EbookEditorIntent.SelectCloneCandidate -> _uiState.update {
                it.copy(sourceBookUrl = intent.bookUrl)
            }
            EbookEditorIntent.CloneDownloadedBook -> cloneDownloadedBook()
            is EbookEditorIntent.UpdateTitle -> updateProject { it.copy(title = intent.value) }
            is EbookEditorIntent.UpdateAuthor -> updateProject { it.copy(author = intent.value) }
            is EbookEditorIntent.UpdateDescription -> updateProject {
                it.copy(description = intent.value)
            }
            is EbookEditorIntent.UpdateLanguage -> updateProject { it.copy(language = intent.value) }
            is EbookEditorIntent.SelectChapter -> selectChapter(intent.chapterId)
            is EbookEditorIntent.ToggleChapterSelection -> toggleChapterSelection(intent.chapterId)
            EbookEditorIntent.AddChapter -> addChapter()
            EbookEditorIntent.RequestDeleteChapter -> requestDeleteChapter()
            EbookEditorIntent.RequestDeleteSelectedChapters -> requestDeleteSelectedChapters()
            EbookEditorIntent.ConfirmDeleteChapter -> confirmDeleteChapter()
            is EbookEditorIntent.MoveChapter -> moveChapter(intent.direction)
            is EbookEditorIntent.UpdateChapterTitle -> _uiState.update {
                it.copy(chapterTitle = intent.value, isDirty = true)
            }
            is EbookEditorIntent.UpdateChapterSubtitle -> updateChapterMetadata { chapter ->
                chapter.copy(subtitle = intent.value)
            }
            is EbookEditorIntent.UpdateChapterPageBreakBefore -> updateChapterMetadata { chapter ->
                chapter.copy(pageBreakBefore = intent.value)
            }
            is EbookEditorIntent.SetAutoRenumberChapters -> setAutoRenumberChapters(intent.value)
            is EbookEditorIntent.UpdateChapterContent -> updateLegacyText(intent.value)
            is EbookEditorIntent.UpdateFontFamily -> updateProject {
                it.copy(style = it.style.copy(fontFamily = intent.value))
            }
            is EbookEditorIntent.UpdateFontSize -> updateProject {
                it.copy(style = it.style.copy(fontSizeSp = intent.value.coerceIn(10, 72)))
            }
            is EbookEditorIntent.UpdateLineHeight -> updateProject {
                it.copy(style = it.style.copy(lineHeightPercent = intent.value.coerceIn(100, 250)))
            }
            is EbookEditorIntent.UpdateDropCap -> updateProject {
                it.copy(style = it.style.copy(dropCap = intent.value))
            }
            is EbookEditorIntent.SetLayoutMode -> setLayoutMode(intent.value)
            is EbookEditorIntent.InsertBlock -> insertBlock(intent.kind)
            is EbookEditorIntent.SelectBlock -> selectBlock(intent.blockId)
            is EbookEditorIntent.ToggleBlockSelection -> toggleBlockSelection(intent.blockId)
            is EbookEditorIntent.SelectNextBlock -> selectNextBlock(intent.direction)
            is EbookEditorIntent.UpdateSelectedBlockText -> updateSelectedBlockText(intent.value)
            is EbookEditorIntent.MoveSelectedBlock -> moveSelectedBlock(intent.dx, intent.dy)
            is EbookEditorIntent.ResizeSelectedBlock -> resizeSelectedBlock(intent.dw, intent.dh)
            is EbookEditorIntent.ResizeSelectedBlockFromHandle -> resizeSelectedBlockFromHandle(
                intent.handle,
                intent.dx,
                intent.dy,
            )
            is EbookEditorIntent.RotateSelectedBlock -> rotateSelectedBlock(intent.degrees)
            is EbookEditorIntent.AlignSelectedBlocks -> alignSelectedBlocks(intent.alignment)
            is EbookEditorIntent.DistributeSelectedBlocks -> distributeSelectedBlocks(intent.axis)
            EbookEditorIntent.DuplicateSelectedBlock -> duplicateSelectedBlock()
            EbookEditorIntent.DeleteSelectedBlock -> deleteSelectedBlock()
            EbookEditorIntent.ToggleSelectedBlockLock -> toggleSelectedBlockLock()
            EbookEditorIntent.ToggleSelectedBlockVisibility -> toggleSelectedBlockVisibility()
            is EbookEditorIntent.MoveSelectedBlockLayer -> moveSelectedBlockLayer(intent.direction)
            is EbookEditorIntent.MoveSelectedReadingOrder -> moveSelectedReadingOrder(intent.direction)
            is EbookEditorIntent.ShowRenameBlock -> showRenameBlock(intent.blockId)
            is EbookEditorIntent.RenameBlock -> renameBlock(intent.blockId, intent.value)
            EbookEditorIntent.Undo -> undo()
            EbookEditorIntent.Redo -> redo()
            EbookEditorIntent.Validate -> validateProject()
            EbookEditorIntent.Preview -> preview()
            EbookEditorIntent.SplitChapter -> splitChapter()
            EbookEditorIntent.MergeWithNextChapter -> mergeWithNextChapter()
            is EbookEditorIntent.UpdateAiInstruction -> _uiState.update {
                it.copy(aiInstruction = intent.value)
            }
            EbookEditorIntent.GenerateBlockWithAi -> generateBlockWithAi()
            EbookEditorIntent.ApplyAiSuggestion -> applyAiSuggestion()
            EbookEditorIntent.DismissAiSuggestion -> _uiState.update { it.copy(aiSuggestion = "") }
            EbookEditorIntent.RequestImage -> _effects.tryEmit(EbookEditorEffect.OpenImagePicker)
            is EbookEditorIntent.ImagePicked -> importImage(intent.displayName, intent.bytes)
            is EbookEditorIntent.SelectExportFormat -> _uiState.update {
                it.copy(exportFormat = intent.value)
            }
            EbookEditorIntent.Save -> save()
            EbookEditorIntent.Export -> requestExport()
            EbookEditorIntent.ConfirmExport -> {
                _uiState.update { it.copy(dialog = null) }
                export()
            }
            EbookEditorIntent.SaveAndContinue -> saveAndContinue()
            EbookEditorIntent.DiscardAndContinue -> discardAndContinue()
        }
    }

    private fun handleBack() {
        if (_uiState.value.project == null) {
            _effects.tryEmit(EbookEditorEffect.NavigateBack)
        } else {
            requestPending(EbookEditorPendingAction.CloseProject)
        }
    }

    private fun openProject(projectId: String) {
        _uiState.value.projects.firstOrNull { it.id == projectId }?.let { project ->
            resetHistory()
            _uiState.update { it.open(project) }
        }
    }

    private fun requestPending(action: EbookEditorPendingAction) {
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(dialog = EbookEditorDialog.UnsavedChanges(action)) }
        } else {
            performPending(action)
        }
    }

    private fun performPending(action: EbookEditorPendingAction) {
        when (action) {
            EbookEditorPendingAction.ExitModule -> _effects.tryEmit(EbookEditorEffect.NavigateBack)
            EbookEditorPendingAction.CloseProject -> _uiState.update { it.open(null) }
            EbookEditorPendingAction.CreateProject -> _uiState.update {
                it.copy(dialog = EbookEditorDialog.CreateProject)
            }
            is EbookEditorPendingAction.OpenProject -> openProject(action.projectId)
        }
    }

    private fun createProject(title: String) {
        viewModelScope.launch {
            setLoading(true)
            runCatching { projectsUseCase.create(AuthoringProjectKind.EBOOK_EDITOR, title) }
                .onSuccess { project ->
                    resetHistory()
                    _uiState.update { it.open(project).copy(isLoading = false, dialog = null) }
                }
                .onFailure { error ->
                    setLoading(false)
                    emitMessage(error.message ?: "Không thể tạo ebook")
                }
        }
    }

    private fun cloneDownloadedBook() {
        val state = _uiState.value
        val bookUrl = state.sourceBookUrl.trim()
        if (bookUrl.isBlank()) {
            emitMessage("Hãy nhập URL sách trong giá sách")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            runCatching {
                cloneDownloadedBookUseCase.execute(
                    CloneDownloadedBookRequest(
                        bookUrl = bookUrl,
                        chapterIndices = parseChapterScope(state.cloneChapterScope),
                        variant = state.cloneVariant,
                        targetLanguage = state.cloneTargetLanguage.trim().ifBlank { "vi" },
                        provider = state.cloneProvider.trim(),
                    )
                )
            }
                .onSuccess { project ->
                    resetHistory()
                    _uiState.update {
                        it.open(project).copy(
                            isLoading = false,
                            sourceBookUrl = "",
                            sheet = null,
                        )
                    }
                    emitMessage("Đã sao chép ${project.chapters.size} chương đã tải")
                }
                .onFailure { error ->
                    setLoading(false)
                    emitMessage(error.message ?: "Không thể sao chép sách")
                }
        }
    }

    private fun showCloneDownloadedBook() {
        _uiState.update { it.copy(sheet = EbookEditorSheet.CloneDownloadedBook) }
        updateCloneQuery("")
    }

    private fun updateCloneQuery(value: String) {
        _uiState.update { it.copy(cloneQuery = value) }
        viewModelScope.launch {
            runCatching { cloneDownloadedBookUseCase.candidates(value) }
                .onSuccess { candidates ->
                    _uiState.update { it.copy(cloneCandidates = candidates.toImmutableList()) }
                }
                .onFailure { emitMessage(it.localizedMessage.orEmpty()) }
        }
    }

    private fun parseChapterScope(value: String): Set<Int>? {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.equals("all", ignoreCase = true)) return null
        return buildSet {
            normalized.split(',').map(String::trim).forEach { token ->
                val range = token.split('-').map(String::trim)
                when (range.size) {
                    1 -> range[0].toIntOrNull()?.minus(1)?.takeIf { it >= 0 }?.let(::add)
                    2 -> {
                        val start = range[0].toIntOrNull()?.minus(1)
                        val end = range[1].toIntOrNull()?.minus(1)
                        if (start != null && end != null && start >= 0 && end >= start) {
                            addAll(start..end)
                        }
                    }
                }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun requestDelete() {
        val project = _uiState.value.project ?: return
        _uiState.update {
            it.copy(dialog = EbookEditorDialog.DeleteProject(project.id, project.title))
        }
    }

    private fun confirmDelete() {
        val dialog = _uiState.value.dialog as? EbookEditorDialog.DeleteProject ?: return
        viewModelScope.launch {
            projectsUseCase.delete(dialog.projectId)
            _uiState.update { it.open(null).copy(dialog = null) }
        }
    }

    private fun updateProject(transform: (AuthoringProject) -> AuthoringProject) {
        val before = materializeProject() ?: return
        recordHistory(before)
        _uiState.update { state -> state.copy(project = transform(before), isDirty = true) }
    }

    private fun selectChapter(chapterId: String) {
        val project = materializeProject() ?: return
        val chapter = project.chapters.firstOrNull { it.id == chapterId } ?: return
        _uiState.update {
            it.copy(
                project = project,
                selectedChapterId = chapter.id,
                selectedChapterIds = setOf(chapter.id).toImmutableSet(),
                chapterTitle = chapter.title,
                chapterContent = chapter.content,
                selectedBlockId = null,
                selectedBlockIds = emptySet<String>().toImmutableSet(),
            )
        }
    }

    private fun toggleChapterSelection(chapterId: String) {
        _uiState.update { state ->
            val selected = state.selectedChapterIds.toMutableSet()
            if (!selected.add(chapterId)) selected.remove(chapterId)
            state.copy(
                selectedChapterIds = selected.toImmutableSet(),
                selectedChapterId = state.selectedChapterId,
            )
        }
    }

    private fun addChapter() {
        val project = materializeProject() ?: return
        val selectedId = _uiState.value.selectedChapterId
        val chapter = EbookDocumentChapter(
            id = UUID.randomUUID().toString(),
            title = "Chương ${project.chapters.size + 1}",
        )
        mutateDocument { document ->
            val chapters = document.chapters.toMutableList()
            val selectedIndex = chapters.indexOfFirst { it.id == selectedId }
            chapters.add(if (selectedIndex >= 0) selectedIndex + 1 else chapters.size, chapter)
            applyAutoRenumber(document.copy(chapters = chapters))
        }
        selectChapter(chapter.id)
    }

    private fun importImage(displayName: String, bytes: ByteArray) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            setLoading(true)
            runCatching { projectsUseCase.importImage(project.id, displayName, bytes) }
                .onSuccess { path ->
                    setLoading(false)
                    insertImageBlock(path)
                }
                .onFailure { error ->
                    setLoading(false)
                    emitMessage(error.message ?: "Không thể nhập ảnh")
                }
        }
    }

    private fun updateLegacyText(value: String) {
        if (pendingTextHistoryBase == null) pendingTextHistoryBase = materializeProject()
        _uiState.update { it.copy(chapterContent = value, isDirty = true) }
        scheduleTextHistoryCommit()
    }

    private fun setLayoutMode(mode: EbookLayoutMode) {
        if (mode == EbookLayoutMode.FIXED_PAGE && !FeatureFlags.ebookFixedLayout) {
            _effects.tryEmit(EbookEditorEffect.ShowMessage("Fixed-layout editing is disabled in Lab settings"))
            return
        }
        mutateDocument { document ->
            val fixed = mode == EbookLayoutMode.FIXED_PAGE
            document.copy(
                layoutMode = mode,
                pageSize = if (fixed) document.pageSize ?: EbookPageSize() else null,
                chapters = document.chapters.map { chapter ->
                    chapter.copy(
                        blocks = chapter.blocks.mapIndexed { index, block ->
                            if (!fixed || block.geometry != null) block else block.withGeometry(
                                defaultGeometry(index, block.readingOrder)
                            )
                        }
                    )
                },
            )
        }
    }

    private fun insertBlock(kind: EbookBlockKind) {
        if (kind == EbookBlockKind.IMAGE) {
            _effects.tryEmit(EbookEditorEffect.OpenImagePicker)
            return
        }
        val document = materializeProject()?.resolveEbookDocument() ?: return
        val chapter = document.chapters.firstOrNull { it.id == _uiState.value.selectedChapterId } ?: return
        val order = (chapter.blocks.maxOfOrNull(EbookBlock::readingOrder) ?: -1) + 1
        val geometry = if (document.layoutMode == EbookLayoutMode.FIXED_PAGE) {
            defaultGeometry(chapter.blocks.size, order)
        } else null
        val block: EbookBlock = when (kind) {
            EbookBlockKind.PARAGRAPH -> EbookParagraphBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.HEADING -> EbookHeadingBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.QUOTE -> EbookQuoteBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.DIVIDER -> EbookDividerBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.PAGE_BREAK -> EbookPageBreakBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.CODE -> EbookCodeBlock(readingOrder = order, geometry = geometry)
            EbookBlockKind.LIST -> EbookListBlock(items = listOf(""), readingOrder = order, geometry = geometry)
            EbookBlockKind.IMAGE -> return
        }
        mutateSelectedChapter { blocks -> blocks + block }
        selectBlock(block.id)
    }

    private fun insertImageBlock(path: String) {
        val document = materializeProject()?.resolveEbookDocument() ?: return
        val chapter = document.chapters.firstOrNull { it.id == _uiState.value.selectedChapterId } ?: return
        val order = (chapter.blocks.maxOfOrNull(EbookBlock::readingOrder) ?: -1) + 1
        val block = EbookImageBlock(
            uri = path,
            alt = FileName.alt(path),
            readingOrder = order,
            geometry = if (document.layoutMode == EbookLayoutMode.FIXED_PAGE) {
                defaultGeometry(chapter.blocks.size, order).copy(height = 240f)
            } else null,
        )
        mutateSelectedChapter { it + block }
        selectBlock(block.id)
    }

    private fun selectBlock(blockId: String?) {
        _uiState.update {
            it.copy(
                selectedBlockId = blockId,
                selectedBlockIds = blockId?.let(::setOf).orEmpty().toImmutableSet(),
            )
        }
    }

    private fun toggleBlockSelection(blockId: String) {
        _uiState.update { state ->
            val selected = state.selectedBlockIds.toMutableSet()
            if (!selected.add(blockId)) selected.remove(blockId)
            state.copy(
                selectedBlockId = when {
                    selected.isEmpty() -> null
                    blockId in selected -> blockId
                    state.selectedBlockId in selected -> state.selectedBlockId
                    else -> selected.first()
                },
                selectedBlockIds = selected.toImmutableSet(),
            )
        }
    }

    private fun selectNextBlock(direction: Int) {
        val blocks = selectedChapterBlocks().sortedBy(EbookBlock::readingOrder)
        if (blocks.isEmpty()) return
        val current = blocks.indexOfFirst { it.id == _uiState.value.selectedBlockId }
        val next = if (current < 0) 0 else Math.floorMod(current + direction, blocks.size)
        selectBlock(blocks[next].id)
    }

    private fun updateSelectedBlockText(value: String) {
        if (pendingTextHistoryBase == null) pendingTextHistoryBase = materializeProject()
        mutateSelectedChapter(recordHistory = false) { blocks ->
            blocks.map { block ->
                if (block.id != _uiState.value.selectedBlockId) block else when (block) {
                    is EbookParagraphBlock -> block.copy(text = value)
                    is EbookHeadingBlock -> block.copy(text = value)
                    is EbookQuoteBlock -> block.copy(text = value)
                    is EbookCodeBlock -> block.copy(text = value)
                    is EbookListBlock -> block.copy(items = value.lines())
                    is EbookImageBlock -> block.copy(caption = value)
                    else -> block
                }
            }
        }
        scheduleTextHistoryCommit()
    }

    private fun moveSelectedBlock(dx: Float, dy: Float) = updateSelectedGeometries { geometry, page ->
        geometry.copy(
            x = snap(geometry.x + dx).coerceIn(0f, (page.width - geometry.width).coerceAtLeast(0f)),
            y = snap(geometry.y + dy).coerceIn(0f, (page.height - geometry.height).coerceAtLeast(0f)),
        )
    }

    private fun resizeSelectedBlock(dw: Float, dh: Float) = updatePrimaryGeometry { geometry, page ->
        geometry.copy(
            width = snap(geometry.width + dw).coerceIn(32f, (page.width - geometry.x).coerceAtLeast(32f)),
            height = snap(geometry.height + dh).coerceIn(32f, (page.height - geometry.y).coerceAtLeast(32f)),
        )
    }

    private fun resizeSelectedBlockFromHandle(
        handle: EbookResizeHandle,
        dx: Float,
        dy: Float,
    ) = updatePrimaryGeometry { geometry, page ->
        var left = geometry.x
        var top = geometry.y
        var right = geometry.x + geometry.width
        var bottom = geometry.y + geometry.height
        if (handle in LEFT_RESIZE_HANDLES) left = snap(left + dx).coerceIn(0f, right - MIN_BLOCK_SIZE)
        if (handle in RIGHT_RESIZE_HANDLES) right = snap(right + dx).coerceIn(left + MIN_BLOCK_SIZE, page.width)
        if (handle in TOP_RESIZE_HANDLES) top = snap(top + dy).coerceIn(0f, bottom - MIN_BLOCK_SIZE)
        if (handle in BOTTOM_RESIZE_HANDLES) bottom = snap(bottom + dy).coerceIn(top + MIN_BLOCK_SIZE, page.height)
        geometry.copy(x = left, y = top, width = right - left, height = bottom - top)
    }

    private fun rotateSelectedBlock(degrees: Float) = updateSelectedGeometries { geometry, _ ->
        geometry.copy(rotation = (geometry.rotation + degrees) % 360f)
    }

    private fun alignSelectedBlocks(alignment: EbookBlockAlignment) {
        val selected = selectedChapterBlocks().filter { it.id in selectedIds() && it.geometry != null }
        if (selected.size < 2) return
        val geometries = selected.mapNotNull(EbookBlock::geometry)
        val left = geometries.minOf(EbookBlockGeometry::x)
        val top = geometries.minOf(EbookBlockGeometry::y)
        val right = geometries.maxOf { it.x + it.width }
        val bottom = geometries.maxOf { it.y + it.height }
        updateSelectedGeometries { geometry, page ->
            when (alignment) {
                EbookBlockAlignment.LEFT -> geometry.copy(x = left)
                EbookBlockAlignment.HORIZONTAL_CENTER -> geometry.copy(
                    x = ((left + right - geometry.width) / 2f).coerceIn(0f, page.width - geometry.width)
                )
                EbookBlockAlignment.RIGHT -> geometry.copy(x = (right - geometry.width).coerceAtLeast(0f))
                EbookBlockAlignment.TOP -> geometry.copy(y = top)
                EbookBlockAlignment.VERTICAL_CENTER -> geometry.copy(
                    y = ((top + bottom - geometry.height) / 2f).coerceIn(0f, page.height - geometry.height)
                )
                EbookBlockAlignment.BOTTOM -> geometry.copy(y = (bottom - geometry.height).coerceAtLeast(0f))
            }
        }
    }

    private fun distributeSelectedBlocks(axis: EbookDistributionAxis) {
        val selected = selectedChapterBlocks().filter { it.id in selectedIds() && it.geometry != null }
        if (selected.size < 3) return
        val replacements = when (axis) {
            EbookDistributionAxis.HORIZONTAL -> distributeHorizontally(selected)
            EbookDistributionAxis.VERTICAL -> distributeVertically(selected)
        }
        mutateSelectedChapter { blocks ->
            blocks.map { block -> replacements[block.id]?.let(block::withGeometry) ?: block }
        }
    }

    private fun duplicateSelectedBlock() {
        val selectedIds = selectedIds()
        if (selectedIds.isEmpty()) return
        val duplicateIds = mutableListOf<String>()
        mutateSelectedChapter { blocks ->
            var nextOrder = (blocks.maxOfOrNull(EbookBlock::readingOrder) ?: -1) + 1
            val copies = blocks.filter { it.id in selectedIds }.map { selected ->
                val shiftedGeometry = selected.geometry?.let { geometry ->
                    geometry.copy(x = geometry.x + 16f, y = geometry.y + 16f)
                }
                selected.withId(UUID.randomUUID().toString())
                    .withReadingOrder(nextOrder++)
                    .withGeometry(shiftedGeometry)
                    .also { duplicateIds += it.id }
            }
            blocks + copies
        }
        _uiState.update {
            it.copy(
                selectedBlockId = duplicateIds.firstOrNull(),
                selectedBlockIds = duplicateIds.toImmutableSet(),
            )
        }
    }

    private fun deleteSelectedBlock() {
        val selectedIds = selectedIds()
        if (selectedIds.isEmpty()) return
        mutateSelectedChapter { blocks -> blocks.filterNot { it.id in selectedIds } }
        selectBlock(null)
    }

    private fun toggleSelectedBlockLock() = updateSelectedGeometries(includeLocked = true) { geometry, _ ->
        geometry.copy(isLocked = !geometry.isLocked)
    }

    private fun toggleSelectedBlockVisibility() = updateSelectedGeometries(includeLocked = true) { geometry, _ ->
        geometry.copy(isHidden = !geometry.isHidden)
    }

    private fun moveSelectedBlockLayer(direction: Int) = updateSelectedGeometries { geometry, _ ->
        geometry.copy(zIndex = (geometry.zIndex + direction).coerceAtLeast(0))
    }

    private fun moveSelectedReadingOrder(direction: Int) {
        val selectedId = _uiState.value.selectedBlockId ?: return
        mutateSelectedChapter { blocks ->
            val ordered = blocks.sortedBy(EbookBlock::readingOrder).toMutableList()
            val from = ordered.indexOfFirst { it.id == selectedId }
            val to = (from + direction).coerceIn(ordered.indices)
            if (from < 0 || from == to) return@mutateSelectedChapter blocks
            ordered.add(to, ordered.removeAt(from))
            ordered.mapIndexed { index, block -> block.withReadingOrder(index) }
        }
    }

    private fun showRenameBlock(blockId: String) {
        val block = selectedChapterBlocks().firstOrNull { it.id == blockId } ?: return
        _uiState.update { it.copy(dialog = EbookEditorDialog.RenameBlock(block.id, block.name)) }
    }

    private fun renameBlock(blockId: String, value: String) {
        val name = value.trim().ifBlank { "Block" }
        mutateSelectedChapter { blocks -> blocks.map { block -> if (block.id == blockId) block.withName(name) else block } }
        _uiState.update { it.copy(dialog = null) }
    }

    private fun updatePrimaryGeometry(
        transform: (EbookBlockGeometry, EbookPageSize) -> EbookBlockGeometry,
    ) {
        val selectedId = _uiState.value.selectedBlockId ?: return
        updateGeometries(setOf(selectedId), transform = transform)
    }

    private fun updateSelectedGeometries(
        includeLocked: Boolean = false,
        transform: (EbookBlockGeometry, EbookPageSize) -> EbookBlockGeometry,
    ) = updateGeometries(selectedIds(), includeLocked, transform)

    private fun updateGeometries(
        selectedIds: Set<String>,
        includeLocked: Boolean = false,
        transform: (EbookBlockGeometry, EbookPageSize) -> EbookBlockGeometry,
    ) {
        if (selectedIds.isEmpty()) return
        val document = materializeProject()?.resolveEbookDocument() ?: return
        val page = document.pageSize ?: EbookPageSize()
        mutateSelectedChapter { blocks ->
            blocks.map { block ->
                if (block.id !in selectedIds || (!includeLocked && block.geometry?.isLocked == true)) block
                else block.withGeometry(transform(block.geometry ?: defaultGeometry(0, block.readingOrder), page))
            }
        }
    }

    private fun selectedIds(): Set<String> {
        val selected = _uiState.value.selectedBlockIds
        return if (selected.isNotEmpty()) selected
        else _uiState.value.selectedBlockId?.let(::setOf).orEmpty()
    }

    private fun selectedChapterBlocks(): List<EbookBlock> = materializeProject()?.resolveEbookDocument()
        ?.chapters
        ?.firstOrNull { it.id == _uiState.value.selectedChapterId }
        ?.blocks
        .orEmpty()

    private fun distributeHorizontally(blocks: List<EbookBlock>): Map<String, EbookBlockGeometry> {
        val ordered = blocks.sortedBy { it.geometry?.x }
        val geometries = ordered.mapNotNull(EbookBlock::geometry)
        val left = geometries.first().x
        val right = geometries.last().let { it.x + it.width }
        val gap = ((right - left - geometries.sumOf { it.width.toDouble() }.toFloat()) /
            (geometries.size - 1)).coerceAtLeast(0f)
        var cursor = left
        return ordered.mapNotNull { block ->
            block.geometry?.let { geometry ->
                block.id to geometry.copy(x = cursor).also { cursor += geometry.width + gap }
            }
        }.toMap()
    }

    private fun distributeVertically(blocks: List<EbookBlock>): Map<String, EbookBlockGeometry> {
        val ordered = blocks.sortedBy { it.geometry?.y }
        val geometries = ordered.mapNotNull(EbookBlock::geometry)
        val top = geometries.first().y
        val bottom = geometries.last().let { it.y + it.height }
        val gap = ((bottom - top - geometries.sumOf { it.height.toDouble() }.toFloat()) /
            (geometries.size - 1)).coerceAtLeast(0f)
        var cursor = top
        return ordered.mapNotNull { block ->
            block.geometry?.let { geometry ->
                block.id to geometry.copy(y = cursor).also { cursor += geometry.height + gap }
            }
        }.toMap()
    }

    private fun mutateSelectedChapter(
        recordHistory: Boolean = true,
        transform: (List<EbookBlock>) -> List<EbookBlock>,
    ) = mutateDocument(recordHistory) { document ->
        val chapterId = _uiState.value.selectedChapterId
        document.copy(
            chapters = document.chapters.map { chapter ->
                if (chapter.id == chapterId) chapter.copy(blocks = transform(chapter.blocks)) else chapter
            }
        )
    }

    private fun mutateDocument(
        recordHistory: Boolean = true,
        transform: (EbookDocument) -> EbookDocument,
    ) {
        val before = materializeProject() ?: return
        if (recordHistory) recordHistory(before)
        val now = System.currentTimeMillis()
        val document = transform(before.resolveEbookDocument()).copy(
            metadata = before.resolveEbookDocument().metadata.copy(
                title = before.title,
                author = before.author,
                language = before.language,
            )
        )
        val updated = before.copy(
            document = document,
            chapters = document.toAuthoringChapters(before.chapters, now),
            updatedAt = now,
        )
        val selected = updated.chapters.firstOrNull { it.id == _uiState.value.selectedChapterId }
        _uiState.update {
            it.copy(
                project = updated,
                chapterTitle = selected?.title.orEmpty(),
                chapterContent = selected?.content.orEmpty(),
                isDirty = true,
                canUndo = undoHistory.isNotEmpty(),
                canRedo = redoHistory.isNotEmpty(),
            )
        }
    }

    private fun defaultGeometry(index: Int, order: Int) = EbookBlockGeometry(
        x = 40f + (index % 3) * 16f,
        y = 40f + index * 132f,
        zIndex = order,
    )

    private fun snap(value: Float): Float = (value / GRID_SIZE).roundToInt() * GRID_SIZE

    private fun EbookBlock.withId(value: String): EbookBlock = when (this) {
        is EbookParagraphBlock -> copy(id = value)
        is EbookHeadingBlock -> copy(id = value)
        is EbookQuoteBlock -> copy(id = value)
        is EbookImageBlock -> copy(id = value)
        is EbookDividerBlock -> copy(id = value)
        is EbookPageBreakBlock -> copy(id = value)
        is EbookCodeBlock -> copy(id = value)
        is EbookListBlock -> copy(id = value)
    }

    private fun EbookBlock.withName(value: String): EbookBlock = when (this) {
        is EbookParagraphBlock -> copy(name = value)
        is EbookHeadingBlock -> copy(name = value)
        is EbookQuoteBlock -> copy(name = value)
        is EbookImageBlock -> copy(name = value)
        is EbookDividerBlock -> copy(name = value)
        is EbookPageBreakBlock -> copy(name = value)
        is EbookCodeBlock -> copy(name = value)
        is EbookListBlock -> copy(name = value)
    }

    private object FileName {
        fun alt(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')
    }

    private fun requestDeleteChapter() {
        val state = _uiState.value
        val chapter = state.project?.chapters?.firstOrNull { it.id == state.selectedChapterId }
            ?: return
        _uiState.update {
            it.copy(dialog = EbookEditorDialog.DeleteChapter(chapter.id, state.chapterTitle))
        }
    }

    private fun requestDeleteSelectedChapters() {
        val ids = _uiState.value.selectedChapterIds.toList()
        if (ids.isEmpty()) return
        _uiState.update {
            it.copy(dialog = EbookEditorDialog.DeleteChapters(ids, "${ids.size} chapters"))
        }
    }

    private fun confirmDeleteChapter() {
        val dialog = _uiState.value.dialog
        val deletedIds = when (dialog) {
            is EbookEditorDialog.DeleteChapter -> setOf(dialog.chapterId)
            is EbookEditorDialog.DeleteChapters -> dialog.chapterIds.toSet()
            else -> return
        }
        val document = materializeProject()?.resolveEbookDocument() ?: return
        val deletedIndex = document.chapters.indexOfFirst { it.id in deletedIds }
        if (deletedIndex < 0) return
        val remaining = document.chapters.filterNot { it.id in deletedIds }
        val nextId = remaining.getOrNull(deletedIndex.coerceAtMost(remaining.lastIndex))?.id
            ?: remaining.lastOrNull()?.id
        mutateDocument { current ->
            applyAutoRenumber(current.copy(chapters = current.chapters.filterNot { it.id in deletedIds }))
        }
        _uiState.update { it.copy(dialog = null) }
        if (nextId != null) selectChapter(nextId) else _uiState.update {
            it.copy(
                selectedChapterId = null,
                selectedChapterIds = emptySet<String>().toImmutableSet(),
                chapterTitle = "",
                chapterContent = "",
                selectedBlockId = null,
                selectedBlockIds = emptySet<String>().toImmutableSet(),
            )
        }
    }

    private fun moveChapter(direction: Int) {
        val document = materializeProject()?.resolveEbookDocument() ?: return
        val selectedId = _uiState.value.selectedChapterId ?: return
        val from = document.chapters.indexOfFirst { it.id == selectedId }
        val to = (from + direction).coerceIn(document.chapters.indices)
        if (from < 0 || from == to) return
        mutateDocument { current ->
            val reordered = current.chapters.toMutableList().apply {
                add(to, removeAt(from))
            }
            applyAutoRenumber(current.copy(chapters = reordered))
        }
    }

    private fun updateChapterMetadata(
        transform: (EbookDocumentChapter) -> EbookDocumentChapter,
    ) = mutateDocument { document ->
        val selectedId = _uiState.value.selectedChapterId
        document.copy(
            chapters = document.chapters.map { chapter ->
                if (chapter.id == selectedId) transform(chapter) else chapter
            }
        )
    }

    private fun setAutoRenumberChapters(value: Boolean) {
        _uiState.update { it.copy(autoRenumberChapters = value) }
        if (value) mutateDocument { applyAutoRenumber(it) }
    }

    private fun applyAutoRenumber(document: EbookDocument): EbookDocument {
        if (!_uiState.value.autoRenumberChapters) return document
        return document.copy(
            chapters = document.chapters.mapIndexed { index, chapter ->
                chapter.copy(title = "Chương ${index + 1}")
            }
        )
    }

    private fun splitChapter() {
        val project = materializeProject() ?: return
        val document = project.resolveEbookDocument()
        val chapterId = _uiState.value.selectedChapterId ?: return
        val index = document.chapters.indexOfFirst { it.id == chapterId }
        if (index < 0) return
        val chapter = document.chapters[index]
        val selectedBlockIndex = chapter.blocks.indexOfFirst { it.id == _uiState.value.selectedBlockId }
        val splitAt = if (selectedBlockIndex in 0 until chapter.blocks.lastIndex) {
            selectedBlockIndex + 1
        } else {
            (chapter.blocks.size / 2).coerceAtLeast(1)
        }
        if (splitAt >= chapter.blocks.size) return
        val next = chapter.copy(
            id = UUID.randomUUID().toString(),
            title = "${chapter.title} (2)",
            blocks = chapter.blocks.drop(splitAt).mapIndexed { order, block -> block.withReadingOrder(order) },
        )
        mutateDocument { current ->
            val chapters = current.chapters.toMutableList()
            chapters[index] = chapter.copy(
                blocks = chapter.blocks.take(splitAt).mapIndexed { order, block -> block.withReadingOrder(order) }
            )
            chapters.add(index + 1, next)
            applyAutoRenumber(current.copy(chapters = chapters))
        }
    }

    private fun mergeWithNextChapter() {
        val project = materializeProject() ?: return
        val document = project.resolveEbookDocument()
        val chapterId = _uiState.value.selectedChapterId ?: return
        val index = document.chapters.indexOfFirst { it.id == chapterId }
        if (index !in 0 until document.chapters.lastIndex) return
        mutateDocument { current ->
            val chapters = current.chapters.toMutableList()
            val first = chapters[index]
            val second = chapters.removeAt(index + 1)
            chapters[index] = first.copy(
                blocks = (first.blocks + second.blocks).mapIndexed { order, block ->
                    block.withReadingOrder(order)
                }
            )
            applyAutoRenumber(current.copy(chapters = chapters))
        }
    }

    private fun validateProject() {
        val project = materializeProject() ?: return
        val issues = validateEbookProjectUseCase.execute(project)
        _uiState.update { it.copy(validationIssues = issues.toImmutableList()) }
        emitMessage(if (issues.isEmpty()) "Ebook validation passed" else "Found ${issues.size} validation issues")
    }

    private fun generateBlockWithAi() {
        val state = _uiState.value
        val project = materializeProject() ?: return
        val instruction = state.aiInstruction.trim()
        if (instruction.isBlank()) {
            emitMessage("Enter an AI instruction first")
            return
        }
        val block = project.resolveEbookDocument().chapters
            .firstOrNull { it.id == state.selectedChapterId }
            ?.blocks
            ?.firstOrNull { it.id == state.selectedBlockId }
        val source = block?.let(::blockPlainText)
            ?.takeIf(String::isNotBlank)
            ?: state.chapterContent
        aiGenerationJob?.cancel()
        aiGenerationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, aiSuggestion = "") }
            runCatching {
                aiTextFactoryUseCase.executeStream(
                    AiTextFactoryUseCase.Request(
                        bookUrl = "ebook-editor:${project.id}",
                        chapterTitle = state.chapterTitle,
                        inputText = source,
                        taskType = AiTaskType.TEXT_FACTORY,
                        userInstruction = instruction,
                        referenceText = project.description,
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
                emitMessage(error.localizedMessage.orEmpty())
            }
        }
    }

    private fun applyAiSuggestion() {
        val suggestion = _uiState.value.aiSuggestion.takeIf(String::isNotBlank) ?: return
        if (_uiState.value.selectedBlockId != null) {
            updateSelectedBlockText(suggestion)
        } else {
            updateLegacyText(suggestion)
        }
        _uiState.update { it.copy(aiSuggestion = "") }
    }

    private fun preview() {
        val project = materializeProject() ?: return
        viewModelScope.launch {
            runCatching {
                val chapterId = _uiState.value.selectedChapterId
                if (chapterId != null) {
                    accountEntitlementUseCase.consume(
                        AccountQuotaKind.EDIT_EBOOK_CHAPTER,
                        listOf("${project.id}:$chapterId"),
                    )
                }
                projectsUseCase.save(project)
            }
                .onSuccess {
                    _uiState.update { it.copy(project = project, isDirty = false) }
                    _effects.tryEmit(EbookEditorEffect.NavigatePreview(project.id))
                }
                .onFailure { emitMessage(it.localizedMessage.orEmpty()) }
        }
    }

    private fun recordHistory(project: AuthoringProject) {
        if (undoHistory.lastOrNull() != project) undoHistory.addLast(project)
        while (undoHistory.size > HISTORY_LIMIT) undoHistory.removeFirst()
        redoHistory.clear()
        _uiState.update { it.copy(canUndo = undoHistory.isNotEmpty(), canRedo = false) }
    }

    private fun resetHistory() {
        undoHistory.clear()
        redoHistory.clear()
        pendingTextHistoryBase = null
        textHistoryJob?.cancel()
        textHistoryJob = null
        _uiState.update { it.copy(canUndo = false, canRedo = false) }
    }

    private fun scheduleTextHistoryCommit() {
        textHistoryJob?.cancel()
        textHistoryJob = viewModelScope.launch {
            delay(TEXT_HISTORY_DEBOUNCE_MS)
            pendingTextHistoryBase?.let(::recordHistory)
            pendingTextHistoryBase = null
        }
    }

    private fun commitPendingTextHistory() {
        textHistoryJob?.cancel()
        textHistoryJob = null
        pendingTextHistoryBase?.let(::recordHistory)
        pendingTextHistoryBase = null
    }

    private fun undo() {
        commitPendingTextHistory()
        val previous = undoHistory.removeLastOrNull() ?: return
        materializeProject()?.let(redoHistory::addLast)
        _uiState.update {
            it.open(previous, it.selectedChapterId).copy(
                isDirty = true,
                canUndo = undoHistory.isNotEmpty(),
                canRedo = redoHistory.isNotEmpty(),
            )
        }
    }

    private fun redo() {
        val next = redoHistory.removeLastOrNull() ?: return
        materializeProject()?.let(undoHistory::addLast)
        _uiState.update {
            it.open(next, it.selectedChapterId).copy(
                isDirty = true,
                canUndo = undoHistory.isNotEmpty(),
                canRedo = redoHistory.isNotEmpty(),
            )
        }
    }

    private fun save(onSuccess: (() -> Unit)? = null, silent: Boolean = false) {
        val project = materializeProject() ?: return
        viewModelScope.launch {
            setLoading(true)
            runCatching { projectsUseCase.save(project) }
                .onSuccess {
                    _uiState.update {
                        it.copy(project = project, isLoading = false, isDirty = false, dialog = null)
                    }
                    if (silent) {
                        onSuccess?.invoke()
                        return@onSuccess
                    }
                    emitMessage("Đã lưu ebook")
                    onSuccess?.invoke()
                }
                .onFailure { error ->
                    setLoading(false)
                    emitMessage(error.message ?: "Không thể lưu")
                }
        }
    }

    private fun saveAndContinue() {
        val action = (_uiState.value.dialog as? EbookEditorDialog.UnsavedChanges)?.action ?: return
        save(onSuccess = { performPending(action) })
    }

    private fun discardAndContinue() {
        val action = (_uiState.value.dialog as? EbookEditorDialog.UnsavedChanges)?.action ?: return
        val persisted = _uiState.value.project?.id?.let { currentId ->
            _uiState.value.projects.firstOrNull { it.id == currentId }
        }
        _uiState.update { it.open(persisted).copy(dialog = null) }
        performPending(action)
    }

    private fun export() {
        val project = materializeProject() ?: return
        if (project.chapters.isEmpty()) {
            emitMessage("Ebook chưa có chương")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            runCatching { exportUseCase.execute(project, _uiState.value.exportFormat) }
                .onSuccess { file ->
                    setLoading(false)
                    val mime = when (file.extension.lowercase()) {
                        "epub" -> "application/epub+zip"
                        "pdf" -> "application/pdf"
                        "html" -> "text/html"
                        else -> "text/plain"
                    }
                    _effects.tryEmit(EbookEditorEffect.ShareFile(file.absolutePath, mime))
                }
                .onFailure { error ->
                    setLoading(false)
                    emitMessage(error.message ?: "Không thể xuất ebook")
                }
        }
    }

    private fun requestExport() {
        val project = materializeProject() ?: return
        if (
            _uiState.value.exportFormat == EbookExportFormat.TXT &&
            project.resolveEbookDocument().layoutMode == EbookLayoutMode.FIXED_PAGE
        ) {
            _uiState.update { it.copy(dialog = EbookEditorDialog.ConfirmLossyTextExport) }
        } else {
            export()
        }
    }

    private fun materializeProject(): AuthoringProject? {
        val state = _uiState.value
        val project = state.project ?: return null
        val now = System.currentTimeMillis()
        val chapters = project.chapters.map { chapter ->
                if (chapter.id == state.selectedChapterId) {
                    chapter.copy(
                        title = state.chapterTitle.trim().ifBlank { chapter.title },
                        content = state.chapterContent,
                        updatedAt = now,
                    )
                } else chapter
            }
        val currentDocument = project.resolveEbookDocument()
        val document = currentDocument.copy(
            metadata = currentDocument.metadata.copy(
                title = project.title,
                author = project.author,
                language = project.language,
            ),
            chapters = chapters.map { chapter ->
                val existing = currentDocument.chapters.firstOrNull { it.id == chapter.id }
                    ?: EbookDocumentChapter(chapter.id, chapter.title)
                existing.copy(
                    title = chapter.title,
                    blocks = if (chapter.id == state.selectedChapterId &&
                        currentDocument.layoutMode == EbookLayoutMode.REFLOW
                    ) {
                        legacyContentToBlocks(chapter.content)
                    } else existing.blocks,
                )
            },
        )
        return project.copy(
            chapters = chapters,
            document = document,
            updatedAt = now,
        )
    }

    private fun EbookEditorUiState.open(
        project: AuthoringProject?,
        preferredChapterId: String? = null,
    ): EbookEditorUiState {
        val chapter = project?.chapters?.firstOrNull { it.id == preferredChapterId }
            ?: project?.chapters?.firstOrNull()
        return copy(
            project = project,
            selectedChapterId = chapter?.id,
            selectedChapterIds = chapter?.id?.let(::setOf).orEmpty().toImmutableSet(),
            chapterTitle = chapter?.title.orEmpty(),
            chapterContent = chapter?.content.orEmpty(),
            selectedBlockId = null,
            selectedBlockIds = emptySet<String>().toImmutableSet(),
            isDirty = false,
            dialog = null,
        )
    }

    private fun setLoading(value: Boolean) {
        _uiState.update { it.copy(isLoading = value) }
    }

    private fun emitMessage(message: String) {
        _effects.tryEmit(EbookEditorEffect.ShowMessage(message))
    }

    private companion object {
        const val AUTOSAVE_INTERVAL_MS = 30_000L
        const val TEXT_HISTORY_DEBOUNCE_MS = 500L
        const val HISTORY_LIMIT = 100
        const val GRID_SIZE = 8f
        const val MIN_BLOCK_SIZE = 32f
        val LEFT_RESIZE_HANDLES = setOf(
            EbookResizeHandle.TOP_LEFT,
            EbookResizeHandle.LEFT,
            EbookResizeHandle.BOTTOM_LEFT,
        )
        val RIGHT_RESIZE_HANDLES = setOf(
            EbookResizeHandle.TOP_RIGHT,
            EbookResizeHandle.RIGHT,
            EbookResizeHandle.BOTTOM_RIGHT,
        )
        val TOP_RESIZE_HANDLES = setOf(
            EbookResizeHandle.TOP_LEFT,
            EbookResizeHandle.TOP,
            EbookResizeHandle.TOP_RIGHT,
        )
        val BOTTOM_RESIZE_HANDLES = setOf(
            EbookResizeHandle.BOTTOM_LEFT,
            EbookResizeHandle.BOTTOM,
            EbookResizeHandle.BOTTOM_RIGHT,
        )
    }
}
