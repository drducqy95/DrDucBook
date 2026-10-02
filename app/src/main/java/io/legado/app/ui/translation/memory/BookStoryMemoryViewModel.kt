package io.legado.app.ui.translation.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.constant.EventBus
import io.legado.app.model.ReadBook
import io.legado.app.utils.postEvent
import io.legado.app.domain.model.AiTranslationStoryEntity
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryMemorySnapshot
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationStoryTimeline
import io.legado.app.domain.model.AiTranslationTimelineCharacter
import io.legado.app.domain.model.AiTranslationWorldEntry
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.usecase.TranslationStoryMemoryUseCase
import io.legado.app.domain.usecase.StoryIllustrationUseCase
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.ui.translation.applyTranslationCaseTransform
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class BookStoryMemoryViewModel(
    private val bookUrl: String,
    private val storyMemoryUseCase: TranslationStoryMemoryUseCase,
    private val storyIllustrationUseCase: StoryIllustrationUseCase,
    private val cachedChapterGateway: CachedChapterGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val translateChapterUseCase: TranslateChapterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookStoryMemoryUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookStoryMemoryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var snapshot = AiTranslationStoryMemorySnapshot()
    private var suggestionJob: Job? = null

    init {
        viewModelScope.launch {
            storyMemoryUseCase.observeBookSnapshot(bookUrl)
                .catch { error ->
                    _uiState.update { it.copy(loading = false, errorMessage = error.localizedMessage) }
                }
                .collect { value ->
                    snapshot = value
                    publishItems()
                }
        }
    }

    fun onIntent(intent: BookStoryMemoryIntent) {
        when (intent) {
            is BookStoryMemoryIntent.SelectKind -> {
                _uiState.update { it.copy(selectedKind = intent.value) }
                publishItems()
            }
            is BookStoryMemoryIntent.Edit -> openEditor(intent.item)
            is BookStoryMemoryIntent.Add -> _uiState.update {
                it.copy(editor = StoryMemoryEditorDraft(kind = intent.kind))
            }
            is BookStoryMemoryIntent.UpdateEditor -> updateEditor(intent.value)
            is BookStoryMemoryIntent.RequestSuggestion -> requestSuggestion(intent.provider)
            is BookStoryMemoryIntent.ApplySuggestion -> _uiState.update { state ->
                state.copy(editor = state.editor?.copy(secondary = intent.value))
            }
            is BookStoryMemoryIntent.ApplyCaseTransform -> _uiState.update { state ->
                state.copy(editor = state.editor?.let { draft ->
                    draft.copy(secondary = applyTranslationCaseTransform(draft.secondary, intent.transform))
                })
            }
            BookStoryMemoryIntent.DismissEditor -> _uiState.update { it.copy(editor = null) }
            BookStoryMemoryIntent.SaveEditor -> saveEditor()
            BookStoryMemoryIntent.DeleteEditor -> deleteEditor()
            BookStoryMemoryIntent.GenerateEditorImage -> generateEditorImage()
            BookStoryMemoryIntent.GenerateWorldMap -> generateWorldMap()
            BookStoryMemoryIntent.RequestImport ->
                _effects.tryEmit(BookStoryMemoryEffect.OpenImportDocument)
            BookStoryMemoryIntent.RequestExport -> exportDocument()
            BookStoryMemoryIntent.RetryPending -> retryPending()
            BookStoryMemoryIntent.BackfillCachedChapters -> backfillCachedChapters()
            is BookStoryMemoryIntent.ImportJson -> importDocument(intent.content)

            BookStoryMemoryIntent.OpenAiBuilderDialog -> openAiBuilderDialog()
            is BookStoryMemoryIntent.UpdateAiBuilderDialog -> _uiState.update { it.copy(builderDialog = intent.value) }
            BookStoryMemoryIntent.DismissAiBuilderDialog -> _uiState.update {
                if (!it.builderDialog.isRunning) it.copy(builderDialog = it.builderDialog.copy(isOpen = false)) else it
            }
            BookStoryMemoryIntent.ExecuteAiBuilder -> executeAiBuilder()

            BookStoryMemoryIntent.OpenRetrofitDialog -> openRetrofitDialog()
            is BookStoryMemoryIntent.UpdateRetrofitDialog -> _uiState.update { it.copy(retrofitDialog = intent.value) }
            BookStoryMemoryIntent.DismissRetrofitDialog -> _uiState.update {
                if (!it.retrofitDialog.isRunning) it.copy(retrofitDialog = it.retrofitDialog.copy(isOpen = false)) else it
            }
            BookStoryMemoryIntent.ExecuteRetrofit -> executeRetrofit()
        }
    }

    private fun updateEditor(value: StoryMemoryEditorDraft) {
        val previous = _uiState.value.editor
        if (previous?.primary?.trim() != value.primary.trim()) {
            suggestionJob?.cancel()
            _uiState.update {
                it.copy(
                    editor = value.copy(suggestions = kotlinx.collections.immutable.persistentListOf(), isSuggesting = false),
                )
            }
        } else {
            _uiState.update { it.copy(editor = value) }
        }
    }

    private fun requestSuggestion(provider: String) {
        val kind = _uiState.value.editor?.kind ?: return
        if (kind !in setOf(AiTranslationStoryMemoryKind.ENTITY, AiTranslationStoryMemoryKind.WORLD_BUILDING)) return
        if (provider !in TranslationConstants.providerValues) return
        val raw = _uiState.value.editor?.primary?.trim().orEmpty()
        if (raw.isBlank()) return
        suggestionJob?.cancel()
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(selectedProvider = provider, isSuggesting = true))
        }
        suggestionJob = viewModelScope.launch(Dispatchers.IO) {
            translateChapterUseCase.executeSuggestion(
                text = raw,
                provider = provider,
                book = cachedChapterGateway.getBook(bookUrl),
            ).onSuccess { translated ->
                _uiState.update { state ->
                    val current = state.editor
                    if (current == null || current.primary.trim() != raw || current.selectedProvider != provider) {
                        state
                    } else {
                        state.copy(
                            editor = current.copy(
                                isSuggesting = false,
                                suggestions = (current.suggestions.filterNot { it.provider == provider } +
                                    TranslationSuggestionUi(provider, providerLabel(provider), translated))
                                    .toImmutableList(),
                            )
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    val current = state.editor
                    if (current == null || current.primary.trim() != raw || current.selectedProvider != provider) state
                    else state.copy(editor = current.copy(isSuggesting = false), errorMessage = error.localizedMessage)
                }
            }
        }
    }

    private fun providerLabel(provider: String): String =
        TranslationConstants.providerValues.zip(TranslationConstants.providerDisplayNames)
            .firstOrNull { it.first == provider }?.second ?: provider

    private fun publishItems() {
        val selectedKind = _uiState.value.selectedKind
        val items = buildList {
            snapshot.entities.forEach { entity ->
                add(
                    StoryMemoryItemUi(
                        id = TranslationStoryMemoryUseCase.entityKey(entity.raw, entity.senseKey),
                        kind = AiTranslationStoryMemoryKind.ENTITY,
                        title = entity.target.ifBlank { entity.raw },
                        subtitle = listOf(entity.raw, entity.type, entity.description)
                            .filter(String::isNotBlank).joinToString(" · "),
                        chapterIndex = entity.firstChapterIndex.takeIf { it >= 0 },
                        imagePath = entity.imagePath.takeIf(String::isNotBlank),
                    )
                )
            }
            snapshot.relationships.forEach { relationship ->
                val typeTag = relationship.relationship.takeIf(String::isNotBlank)
                val relTitle = if (typeTag != null) {
                    "[$typeTag] ${relationship.source} ⇄ ${relationship.target}"
                } else {
                    "${relationship.source} ⇄ ${relationship.target}"
                }
                add(
                    StoryMemoryItemUi(
                        id = TranslationStoryMemoryUseCase.relationshipKey(relationship),
                        kind = AiTranslationStoryMemoryKind.RELATIONSHIP,
                        title = relTitle,
                        subtitle = relationship.description.takeIf(String::isNotBlank).orEmpty(),
                        chapterIndex = relationship.chapterIndex.takeIf { it >= 0 },
                    )
                )
            }
            snapshot.worldBuilding.forEach { world ->
                add(
                    StoryMemoryItemUi(
                        id = TranslationStoryMemoryUseCase.worldKey(world),
                        kind = AiTranslationStoryMemoryKind.WORLD_BUILDING,
                        title = world.target.ifBlank { world.raw },
                        subtitle = listOf(world.raw, world.category, world.description)
                            .filter(String::isNotBlank).joinToString(" · "),
                        chapterIndex = world.chapterIndex.takeIf { it >= 0 },
                        imagePath = world.imagePath.takeIf(String::isNotBlank),
                    )
                )
            }
            snapshot.timelines.forEach { timeline ->
                val details = buildList {
                    if (timeline.summary.isNotBlank()) add(timeline.summary)
                    val charactersDesc = timeline.characters.takeIf { it.isNotEmpty() }?.joinToString(", ") { character ->
                        val name = character.target.ifBlank { character.raw }
                        val statusTag = if (character.status == "new") "mới" else null
                        val roleTag = character.role.takeIf(String::isNotBlank)
                        val extra = listOfNotNull(statusTag, roleTag).joinToString("/")
                        if (extra.isNotBlank()) "$name ($extra)" else name
                    }
                    if (!charactersDesc.isNullOrBlank()) {
                        add("Nhân vật: $charactersDesc")
                    }
                    val discoveriesDesc = timeline.discoveries.takeIf { it.isNotEmpty() }?.joinToString(", ") { disc ->
                        val name = disc.target.ifBlank { disc.raw }
                        val cat = disc.category.takeIf { it.isNotBlank() && it != "other" }
                        if (cat != null) "$name ($cat)" else name
                    }
                    if (!discoveriesDesc.isNullOrBlank()) {
                        add("Khám phá: $discoveriesDesc")
                    }
                    if (timeline.events.isNotEmpty()) {
                        add("${timeline.events.size} sự kiện")
                    }
                }.joinToString(" · ")

                add(
                    StoryMemoryItemUi(
                        id = TranslationStoryMemoryUseCase.timelineKey(timeline.chapterIndex),
                        kind = AiTranslationStoryMemoryKind.TIMELINE,
                        title = timeline.chapterTitle.ifBlank { "Chương ${timeline.chapterIndex + 1}" },
                        subtitle = details.ifBlank { timeline.summary },
                        chapterIndex = timeline.chapterIndex.takeIf { it >= 0 },
                    )
                )
            }
        }.asSequence()
            .filter { selectedKind == null || it.kind == selectedKind }
            .sortedWith(compareBy<StoryMemoryItemUi> { it.chapterIndex ?: Int.MAX_VALUE }.thenBy { it.title })
            .toList()
        _uiState.update {
            it.copy(
                loading = false,
                items = items.toImmutableList(),
                errorMessage = null,
                counts = mapOf(
                    AiTranslationStoryMemoryKind.ENTITY to snapshot.entities.size,
                    AiTranslationStoryMemoryKind.RELATIONSHIP to snapshot.relationships.size,
                    AiTranslationStoryMemoryKind.WORLD_BUILDING to snapshot.worldBuilding.size,
                    AiTranslationStoryMemoryKind.TIMELINE to snapshot.timelines.size,
                ).toImmutableMap(),
                analyzedChapterCount = snapshot.analyzedChapterIndices.size,
                pendingChapterCount = snapshot.pendingChapterIndices.size,
            )
        }
    }

    private fun openEditor(item: StoryMemoryItemUi) {
        val draft = when (item.kind) {
            AiTranslationStoryMemoryKind.ENTITY -> snapshot.entities
                .firstOrNull { TranslationStoryMemoryUseCase.entityKey(it.raw, it.senseKey) == item.id }
                ?.toDraft(item.id)
            AiTranslationStoryMemoryKind.RELATIONSHIP -> snapshot.relationships
                .firstOrNull { TranslationStoryMemoryUseCase.relationshipKey(it) == item.id }
                ?.toDraft(item.id)
            AiTranslationStoryMemoryKind.WORLD_BUILDING -> snapshot.worldBuilding
                .firstOrNull { TranslationStoryMemoryUseCase.worldKey(it) == item.id }
                ?.toDraft(item.id)
            AiTranslationStoryMemoryKind.TIMELINE -> snapshot.timelines
                .firstOrNull { TranslationStoryMemoryUseCase.timelineKey(it.chapterIndex) == item.id }
                ?.toDraft(item.id)
        }
        if (draft != null) _uiState.update { it.copy(editor = draft) }
    }

    private fun saveEditor() = runMutation {
        val draft = _uiState.value.editor ?: return@runMutation
        val newId = when (draft.kind) {
            AiTranslationStoryMemoryKind.ENTITY -> draft.toEntity().also {
                storyMemoryUseCase.upsertEntity(bookUrl, it)
            }.let { TranslationStoryMemoryUseCase.entityKey(it.raw, it.senseKey) }
            AiTranslationStoryMemoryKind.RELATIONSHIP -> draft.toRelationship().also {
                storyMemoryUseCase.upsertRelationship(bookUrl, it)
            }.let(TranslationStoryMemoryUseCase::relationshipKey)
            AiTranslationStoryMemoryKind.WORLD_BUILDING -> draft.toWorldEntry().also {
                storyMemoryUseCase.upsertWorldEntry(bookUrl, it)
            }.let(TranslationStoryMemoryUseCase::worldKey)
            AiTranslationStoryMemoryKind.TIMELINE -> draft.toTimeline().also {
                storyMemoryUseCase.upsertTimeline(bookUrl, it)
            }.let { TranslationStoryMemoryUseCase.timelineKey(it.chapterIndex) }
        }
        draft.originalId?.takeIf { it != newId }?.let { deleteById(draft.kind, it) }
        _uiState.update { it.copy(editor = null) }
        _effects.tryEmit(BookStoryMemoryEffect.ShowMessage(R.string.story_memory_saved))
    }

    private fun deleteEditor() = runMutation {
        val draft = _uiState.value.editor ?: return@runMutation
        draft.originalId?.let { deleteById(draft.kind, it) }
        _uiState.update { it.copy(editor = null) }
    }

    private fun exportDocument() = runMutation {
        val content = storyMemoryUseCase.exportJson(bookUrl)
        _effects.tryEmit(
            BookStoryMemoryEffect.ExportDocument(
                content = content,
                suggestedName = "translation-memory-${bookUrl.hashCode().toUInt()}.json",
            )
        )
    }

    private fun generateEditorImage() = runMutation {
        val draft = _uiState.value.editor ?: return@runMutation
        when (draft.kind) {
            AiTranslationStoryMemoryKind.ENTITY -> {
                val entity = draft.toEntity()
                require(entity.raw.isNotBlank()) { "Vui lòng nhập tên gốc nhân vật" }
                storyMemoryUseCase.upsertEntity(bookUrl, entity)
                val originalId = draft.originalId
                val newId = TranslationStoryMemoryUseCase.entityKey(entity.raw, entity.senseKey)
                if (originalId != null && originalId != newId) {
                    deleteById(draft.kind, originalId)
                }
                storyIllustrationUseCase.generateEntity(bookUrl, entity.raw, force = true)
            }
            AiTranslationStoryMemoryKind.WORLD_BUILDING -> {
                val world = draft.toWorldEntry()
                require(world.raw.isNotBlank()) { "Vui lòng nhập tên mục thế giới" }
                storyMemoryUseCase.upsertWorldEntry(bookUrl, world)
                val originalId = draft.originalId
                val newId = TranslationStoryMemoryUseCase.worldKey(world)
                if (originalId != null && originalId != newId) {
                    deleteById(draft.kind, originalId)
                }
                storyIllustrationUseCase.generateWorldEntry(bookUrl, world, force = true)
            }
            else -> error("Chỉ hỗ trợ tạo ảnh cho nhân vật và mục thế giới")
        }
        _uiState.update { it.copy(editor = null) }
        _effects.tryEmit(BookStoryMemoryEffect.ShowMessage(R.string.story_memory_image_created))
    }

    private fun generateWorldMap() = runMutation {
        storyIllustrationUseCase.generateWorldMap(bookUrl, force = true)
        _effects.tryEmit(BookStoryMemoryEffect.ShowMessage(R.string.story_memory_image_created))
    }

    private fun importDocument(content: String) = runMutation {
        storyMemoryUseCase.importJson(bookUrl, content)
        _effects.tryEmit(BookStoryMemoryEffect.ShowMessage(R.string.story_memory_imported))
    }

    private fun retryPending() = runMutation {
        val count = storyMemoryUseCase.retryPending(bookUrl)
        _effects.tryEmit(BookStoryMemoryEffect.ShowMessageText("Đã ghi lại $count bản ghi story memory đang chờ"))
    }

    private fun backfillCachedChapters() = runMutation {
        val book = cachedChapterGateway.getBook(bookUrl)
            ?: error("Không tìm thấy sách để phân tích memory")
        val chapterCount = cachedChapterGateway.getChapterCount(bookUrl)
        if (chapterCount <= 0) {
            _effects.tryEmit(BookStoryMemoryEffect.ShowMessageText("Chưa có chương đã lưu để phân tích"))
            return@runMutation
        }
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.EXTRACT_STORY_MEMORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
            ?: error("Chưa cấu hình AI tóm tắt/trích xuất chương")
        val completed = storyMemoryUseCase.backfill(book, preset, 0 until chapterCount)
        _effects.tryEmit(
            BookStoryMemoryEffect.ShowMessageText(
                "Đã phân tích lại $completed/$chapterCount chương đã lưu",
            )
        )
    }

    private fun openAiBuilderDialog() {
        viewModelScope.launch {
            val count = cachedChapterGateway.getChapterCount(bookUrl)
            _uiState.update {
                it.copy(
                    builderDialog = AiStoryMemoryBuilderDialogState(
                        isOpen = true,
                        mode = AiStoryMemoryBuilderDialogState.MODE_CHAPTERS,
                        startChapter = 0,
                        endChapter = minOf(10, count.coerceAtLeast(1)),
                        maxChapters = count,
                    )
                )
            }
        }
    }

    private fun executeAiBuilder() {
        val dialog = _uiState.value.builderDialog
        if (dialog.isRunning) return
        viewModelScope.launch {
            val book = cachedChapterGateway.getBook(bookUrl) ?: run {
                _effects.tryEmit(BookStoryMemoryEffect.ShowError("Không tìm thấy sách"))
                return@launch
            }
            _uiState.update {
                it.copy(
                    builderDialog = it.builderDialog.copy(
                        isRunning = true,
                        progressCurrent = 0,
                        progressTotal = if (dialog.mode == AiStoryMemoryBuilderDialogState.MODE_CHAPTERS) {
                            (dialog.endChapter - dialog.startChapter).coerceAtLeast(1)
                        } else 1,
                        progressMessage = "Đang khởi tạo tiến trình AI...",
                    )
                )
            }
            try {
                val preset = aiProfileGateway.getTaskPreset(AiTaskType.EXTRACT_STORY_MEMORY)
                    ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
                    ?: error("Chưa cấu hình AI trích xuất bộ nhớ truyện")
                if (dialog.mode == AiStoryMemoryBuilderDialogState.MODE_CHAPTERS) {
                    val range = dialog.startChapter until dialog.endChapter
                    val result = storyMemoryUseCase.batchAnalyzeChapters(
                        book = book,
                        chapterRange = range,
                        preset = preset,
                        force = true,
                        onProgress = { current, total ->
                            _uiState.update {
                                it.copy(
                                    builderDialog = it.builderDialog.copy(
                                        progressCurrent = current,
                                        progressTotal = total,
                                        progressMessage = "Đang phân tích chương $current / $total...",
                                    )
                                )
                            }
                        }
                    )
                    val count = result.getOrThrow()
                    _uiState.update { it.copy(builderDialog = it.builderDialog.copy(isOpen = false, isRunning = false)) }
                    _effects.tryEmit(BookStoryMemoryEffect.ShowMessageText("Đã phân tích thành công $count chương và cập nhật bộ nhớ dịch!"))
                } else {
                    val result = storyMemoryUseCase.analyzeTextContent(
                        book = book,
                        rawText = dialog.directText,
                        preset = preset,
                    )
                    val analysis = result.getOrThrow()
                    _uiState.update { it.copy(builderDialog = it.builderDialog.copy(isOpen = false, isRunning = false)) }
                    _effects.tryEmit(
                        BookStoryMemoryEffect.ShowMessageText(
                            "Đã trích xuất ${analysis.entities.size} thực thể, ${analysis.relationships.size} quan hệ và ${analysis.worldBuilding.size} thiết lập thế giới!"
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(builderDialog = it.builderDialog.copy(isRunning = false))
                }
                _effects.tryEmit(BookStoryMemoryEffect.ShowError(error.localizedMessage ?: "Lỗi kiến tạo AI"))
            }
        }
    }

    private fun openRetrofitDialog() {
        viewModelScope.launch {
            val count = cachedChapterGateway.getChapterCount(bookUrl)
            _uiState.update {
                it.copy(
                    retrofitDialog = RetrofitCacheDialogState(
                        isOpen = true,
                        startChapter = 0,
                        endChapter = minOf(10, count.coerceAtLeast(1)),
                        maxChapters = count,
                    )
                )
            }
        }
    }

    private fun executeRetrofit() {
        val dialog = _uiState.value.retrofitDialog
        if (dialog.isRunning) return
        viewModelScope.launch {
            val book = cachedChapterGateway.getBook(bookUrl) ?: run {
                _effects.tryEmit(BookStoryMemoryEffect.ShowError("Không tìm thấy sách"))
                return@launch
            }
            val range = (dialog.startChapter until dialog.endChapter).toList()
            val total = range.size
            _uiState.update {
                it.copy(
                    retrofitDialog = it.retrofitDialog.copy(
                        isRunning = true,
                        progressCurrent = 0,
                        progressTotal = total,
                        progressMessage = "Đang chuẩn bị làm mới bản dịch...",
                    )
                )
            }
            try {
                val result = storyMemoryUseCase.retrofitChapterTranslations(
                    book = book,
                    chapterIndices = range,
                    translateChapterUseCase = translateChapterUseCase,
                    onProgress = { current, max ->
                        _uiState.update {
                            it.copy(
                                retrofitDialog = it.retrofitDialog.copy(
                                    progressCurrent = current,
                                    progressTotal = max,
                                    progressMessage = "Đang làm mới chương $current / $max...",
                                )
                            )
                        }
                    }
                )
                val retrofitted = result.getOrThrow()
                if (ReadBook.book?.bookUrl == bookUrl) {
                    ReadBook.clearTextChapter()
                    postEvent(EventBus.REFRESH_BOOK_CONTENT, true)
                }
                _uiState.update { it.copy(retrofitDialog = it.retrofitDialog.copy(isOpen = false, isRunning = false)) }
                _effects.tryEmit(
                    BookStoryMemoryEffect.ShowMessageText(
                        "Đã hoàn thành làm mới $retrofitted/$total chương theo bộ nhớ dịch mới nhất!"
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(retrofitDialog = it.retrofitDialog.copy(isRunning = false))
                }
                _effects.tryEmit(BookStoryMemoryEffect.ShowError(error.localizedMessage ?: "Lỗi làm mới bản dịch"))
            }
        }
    }

    private fun runMutation(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _effects.tryEmit(
                    BookStoryMemoryEffect.ShowError(error.localizedMessage ?: "Translation memory error")
                )
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun deleteById(kind: AiTranslationStoryMemoryKind, id: String) {
        when (kind) {
            AiTranslationStoryMemoryKind.ENTITY -> snapshot.entities
                .firstOrNull { TranslationStoryMemoryUseCase.entityKey(it.raw, it.senseKey) == id }
                ?.let { storyMemoryUseCase.deleteEntity(bookUrl, it.raw) }
            AiTranslationStoryMemoryKind.RELATIONSHIP -> snapshot.relationships
                .firstOrNull { TranslationStoryMemoryUseCase.relationshipKey(it) == id }
                ?.let { storyMemoryUseCase.deleteRelationship(bookUrl, it) }
            AiTranslationStoryMemoryKind.WORLD_BUILDING -> snapshot.worldBuilding
                .firstOrNull { TranslationStoryMemoryUseCase.worldKey(it) == id }
                ?.let { storyMemoryUseCase.deleteWorldEntry(bookUrl, it) }
            AiTranslationStoryMemoryKind.TIMELINE -> snapshot.timelines
                .firstOrNull { TranslationStoryMemoryUseCase.timelineKey(it.chapterIndex) == id }
                ?.let { storyMemoryUseCase.deleteTimeline(bookUrl, it.chapterIndex) }
        }
    }

    private fun AiTranslationStoryEntity.toDraft(id: String) = StoryMemoryEditorDraft(
        originalId = id,
        kind = AiTranslationStoryMemoryKind.ENTITY,
        primary = raw,
        secondary = target,
        senseKey = senseKey,
        type = type,
        description = description,
        chapterIndexText = firstChapterIndex.takeIf { it >= 0 }?.toString().orEmpty(),
        lastChapterIndex = lastChapterIndex,
        aliasesOrRefsText = aliases.joinToString("\n"),
        gender = gender,
        rank = rank,
        imagePath = imagePath,
        imagePrompt = imagePrompt,
        imageUpdatedAt = imageUpdatedAt,
        metadata = metadata,
    )

    private fun AiTranslationStoryRelationship.toDraft(id: String) = StoryMemoryEditorDraft(
        originalId = id,
        kind = AiTranslationStoryMemoryKind.RELATIONSHIP,
        primary = source,
        secondary = target,
        type = relationship,
        description = description,
        chapterIndexText = chapterIndex.takeIf { it >= 0 }?.toString().orEmpty(),
    )

    private fun AiTranslationWorldEntry.toDraft(id: String) = StoryMemoryEditorDraft(
        originalId = id,
        kind = AiTranslationStoryMemoryKind.WORLD_BUILDING,
        primary = raw,
        secondary = target,
        senseKey = senseKey,
        type = category,
        description = description,
        chapterIndexText = chapterIndex.takeIf { it >= 0 }?.toString().orEmpty(),
        lastChapterIndex = lastChapterIndex,
        aliasesOrRefsText = entityRefs.joinToString("\n"),
        imagePath = imagePath,
        imagePrompt = imagePrompt,
        imageUpdatedAt = imageUpdatedAt,
    )

    private fun AiTranslationStoryTimeline.toDraft(id: String) = StoryMemoryEditorDraft(
        originalId = id,
        kind = AiTranslationStoryMemoryKind.TIMELINE,
        primary = chapterTitle,
        description = summary,
        chapterIndexText = chapterIndex.toString(),
        eventsText = events.joinToString("\n"),
        charactersText = characters.joinToString("\n") {
            listOf(it.raw, it.target, it.status, it.role, it.relationships.joinToString("; "))
                .joinToString(" | ")
        },
        discoveriesText = discoveries.joinToString("\n") {
            listOf(it.raw, it.target, it.category, it.description, it.entityRefs.joinToString("; "))
                .joinToString(" | ")
        },
    )

    private fun StoryMemoryEditorDraft.toEntity() = AiTranslationStoryEntity(
        raw = primary.trim().also { require(it.isNotEmpty()) },
        target = secondary.trim().also { require(it.isNotEmpty()) },
        senseKey = senseKey.trim(),
        type = type.trim().ifBlank { "character" },
        description = description.trim(),
        aliases = aliasesOrRefsText.lines().map(String::trim).filter(String::isNotBlank).distinct(),
        gender = gender.trim(),
        rank = rank.trim(),
        firstChapterIndex = chapterIndexText.toIntOrNull() ?: -1,
        lastChapterIndex = lastChapterIndex,
        imagePath = imagePath,
        imagePrompt = imagePrompt,
        imageUpdatedAt = imageUpdatedAt,
        category = type.trim().ifBlank { "character" },
        userEdited = true,
        source = "USER",
        metadata = metadata,
    )

    private fun StoryMemoryEditorDraft.toRelationship() = AiTranslationStoryRelationship(
        source = primary.trim().also { require(it.isNotEmpty()) },
        target = secondary.trim().also { require(it.isNotEmpty()) },
        relationship = type.trim().also { require(it.isNotEmpty()) },
        description = description.trim(),
        chapterIndex = chapterIndexText.toIntOrNull() ?: -1,
    )

    private fun StoryMemoryEditorDraft.toWorldEntry() = AiTranslationWorldEntry(
        raw = primary.trim().also { require(it.isNotEmpty()) },
        target = secondary.trim(),
        senseKey = senseKey.trim(),
        category = type.trim().ifBlank { "other" },
        description = description.trim(),
        entityRefs = aliasesOrRefsText.lines().map(String::trim).filter(String::isNotBlank).distinct(),
        chapterIndex = chapterIndexText.toIntOrNull() ?: -1,
        lastChapterIndex = lastChapterIndex,
        imagePath = imagePath,
        imagePrompt = imagePrompt,
        imageUpdatedAt = imageUpdatedAt,
        userEdited = true,
        source = "USER",
    )

    private fun StoryMemoryEditorDraft.toTimeline(): AiTranslationStoryTimeline {
        val chapterIndex = chapterIndexText.toIntOrNull()
            ?.also { require(it >= 0) }
            ?: error("Chapter index is required")
        return AiTranslationStoryTimeline(
            chapterIndex = chapterIndex,
            chapterTitle = primary.trim(),
            summary = description.trim().also { require(it.isNotEmpty()) },
            events = eventsText.nonBlankLines(),
            characters = charactersText.nonBlankLines().mapNotNull(::parseCharacter),
            discoveries = discoveriesText.nonBlankLines().mapNotNull { parseDiscovery(it, chapterIndex) },
        )
    }

    private fun String.nonBlankLines() = lines().map(String::trim).filter(String::isNotBlank)

    private fun parseCharacter(line: String): AiTranslationTimelineCharacter? {
        val parts = line.split('|').map(String::trim)
        val raw = parts.getOrNull(0).orEmpty()
        if (raw.isBlank()) return null
        return AiTranslationTimelineCharacter(
            raw = raw,
            target = parts.getOrNull(1).orEmpty(),
            status = parts.getOrNull(2).orEmpty().takeIf { it == "new" } ?: "existing",
            role = parts.getOrNull(3).orEmpty(),
            relationships = parts.getOrNull(4).orEmpty().split(';')
                .map(String::trim).filter(String::isNotBlank),
        )
    }

    private fun parseDiscovery(line: String, chapterIndex: Int): AiTranslationWorldEntry? {
        val parts = line.split('|').map(String::trim)
        val raw = parts.getOrNull(0).orEmpty()
        if (raw.isBlank()) return null
        return AiTranslationWorldEntry(
            raw = raw,
            target = parts.getOrNull(1).orEmpty(),
            category = parts.getOrNull(2).orEmpty().ifBlank { "other" },
            description = parts.getOrNull(3).orEmpty(),
            entityRefs = parts.getOrNull(4).orEmpty().split(';')
                .map(String::trim).filter(String::isNotBlank),
            chapterIndex = chapterIndex,
        )
    }
}
