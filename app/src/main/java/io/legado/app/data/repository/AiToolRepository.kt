package io.legado.app.data.repository

import android.net.Uri
import com.google.gson.JsonObject
import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.data.repository.sourcehealth.SourceCheckEngine
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.EventBus
import io.legado.app.domain.agent.AgentActionApproval
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentSkillDraft
import io.legado.app.domain.agent.AgentSkillSnapshot
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTranslationStoryEntity
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationWorldEntry
import io.legado.app.domain.model.StoryChroniclePeriod
import io.legado.app.domain.usecase.containsCjk
import io.legado.app.domain.usecase.StoryIllustrationUseCase
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.domain.usecase.TranslationStoryMemoryUseCase
import io.legado.app.utils.postEvent
import io.legado.app.domain.agent.withCanonicalToolName
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.CustomAgentToolGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.MatchMode
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.usecase.AddBookUseCase
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.BookshelfAutomationConfig
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.vbook.VbookPluginImporter
import io.legado.app.model.CacheBook
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.service.BookshelfAutomationScheduler
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AiToolRepository(
    private val bookDao: BookDao,
    private val bookSourceDao: BookSourceDao,
    private val bookChapterDao: BookChapterDao,
    private val searchBookDao: SearchBookDao,
    private val bookmarkDao: BookmarkDao,
    private val readRecordDao: ReadRecordDao,
    private val aiArtifactDao: AiArtifactDao,
    private val aiMemoryGateway: AiMemoryGateway,
    private val refreshTocUseCase: RefreshTocUseCase,
    private val bookCacheDownloadGateway: BookCacheDownloadGateway,
    private val searchBooksUseCase: SearchBooksUseCase,
    private val addBookUseCase: AddBookUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val dictionaryGateway: DictionaryGateway,
    private val agentPermissionBroker: AgentPermissionBroker,
    private val aiProfileGateway: AiProfileGateway,
    private val aiSkillGateway: AiSkillGateway,
    private val authoringProjectGateway: AuthoringProjectGateway,
    private val sourceCheckEngine: SourceCheckEngine,
    private val sourceCheckRepository: SourceCheckRepository,
    private val customAgentToolGateway: CustomAgentToolGateway? = null,
    private val translationStoryMemoryUseCase: TranslationStoryMemoryUseCase? = null,
    private val aiTextGateway: AiTextGateway? = null,
    private val translateChapterUseCase: TranslateChapterUseCase? = null,
    private val storyIllustrationUseCase: StoryIllustrationUseCase? = null,
) : AiToolGateway {

    override fun registeredTools(): List<AiToolDefinition> =
        toolDefinitions + customAgentToolGateway.registeredCustomTools()

    override fun availableTools(): List<AiToolDefinition> =
        toolDefinitions.filter { agentPermissionBroker.isToolEnabled(it.name) } +
            customAgentToolGateway.availableCustomTools()

    override fun requiresConfirmation(toolName: String): Boolean {
        return agentPermissionBroker.requiresApproval(toolName)
    }

    override suspend fun execute(
        call: AiToolCall,
        approval: AgentActionApproval?,
        conversationId: String?,
    ): AiToolResult = withContext(Dispatchers.IO) {
        val canonicalCall = call.withCanonicalToolName()
        val definition = toolDefinitions.firstOrNull { it.name == canonicalCall.name }
            ?: return@withContext customAgentToolGateway?.execute(call) ?: AiToolResult(
                callId = canonicalCall.id,
                name = canonicalCall.name,
                content = GSON.toJson(
                    mapOf(
                        "error" to "Unknown tool",
                        "tool" to canonicalCall.name,
                    )
                )
            )
        agentPermissionBroker.requireCanExecute(canonicalCall, approval, conversationId)
        val args = validateToolArguments(definition, canonicalCall.arguments).getOrElse { error ->
            return@withContext AiToolResult(
                callId = canonicalCall.id,
                name = canonicalCall.name,
                content = GSON.toJson(
                    mapOf(
                        "error" to "Invalid tool arguments",
                        "tool" to canonicalCall.name,
                        "message" to error.message.orEmpty(),
                    )
                )
            )
        }
        val content = when (canonicalCall.name) {
            TOOL_SEARCH_INTERNET -> searchInternet(args)
            TOOL_FETCH_INTERNET_PAGE -> fetchInternetPage(args)
            TOOL_SEARCH_BOOKS -> searchBooks(args)
            TOOL_SEARCH_BOOK_SOURCES -> searchBookSources(args)
            TOOL_SEARCH_ONLINE_BOOKS -> searchOnlineBooks(args)
            TOOL_GET_AI_RUNTIME_STATUS -> getAiRuntimeStatus(args)
            TOOL_GET_AI_QUOTA_STATUS -> getAiQuotaStatus(args)
            TOOL_DIAGNOSE_BOOK_SOURCE -> diagnoseBookSource(args)
            TOOL_REPAIR_BOOK_SOURCE -> repairBookSource(args)
            TOOL_ADD_BOOK_TO_BOOKSHELF -> addBookToBookshelf(args)
            TOOL_CREATE_VBOOK_PLUGIN_DRAFT -> createVbookPluginDraft(args)
            TOOL_INSTALL_VBOOK_PLUGIN -> installVbookPlugin(args)
            TOOL_CREATE_LEGADO_BOOK_SOURCE_DRAFT -> createLegadoBookSourceDraft(args)
            TOOL_INSTALL_LEGADO_BOOK_SOURCE -> installLegadoBookSource(args)
            TOOL_LIST_AGENT_SKILLS -> listAgentSkills()
            TOOL_CREATE_AGENT_SKILL_DRAFT -> createAgentSkillDraft(args)
            TOOL_SET_AGENT_SKILL_ENABLED -> setAgentSkillEnabled(args)
            TOOL_ACTIVATE_AGENT_SKILL_VERSION -> activateAgentSkillVersion(args)
            TOOL_ROLLBACK_AGENT_SKILL -> rollbackAgentSkill(args)
            TOOL_GET_BOOK_DETAIL -> getBookDetail(args)
            TOOL_LIST_BOOK_CHAPTERS -> listBookChapters(args)
            TOOL_GET_CHAPTER_CONTENT -> getChapterContent(args)
            TOOL_GET_CHAPTER_WINDOW -> getChapterWindow(args)
            TOOL_SEARCH_CHAPTER_CONTENT -> searchChapterContent(args)
            TOOL_SEARCH_BOOKMARKS -> searchBookmarks(args)
            TOOL_LIST_AUTHORING_PROJECTS -> listAuthoringProjects(args)
            TOOL_GET_AUTHORING_PROJECT -> getAuthoringProject(args)
            TOOL_SAVE_AUTHORING_PROJECT -> saveAuthoringProject(args)
            TOOL_DELETE_AUTHORING_PROJECT -> deleteAuthoringProject(args)
            TOOL_GET_READING_STATS -> getReadingStats(args)
            TOOL_GET_AI_ARTIFACTS -> getAiArtifacts(args)
            TOOL_SAVE_AI_ARTIFACT -> saveAiArtifact(args)
            TOOL_SAVE_MEMORY -> saveMemory(args)
            TOOL_RECALL_MEMORY -> recallMemory(args)
            TOOL_DELETE_MEMORY -> deleteMemory(args)
            TOOL_UPDATE_BOOK -> updateBook(args)
            TOOL_DOWNLOAD_BOOK_CHAPTERS -> downloadBookChapters(args)
            TOOL_GET_DOWNLOAD_STATUS -> getDownloadStatus(args)
            TOOL_LIST_BOOK_DICTIONARY_TERMS -> listBookDictionaryTerms(args)
            TOOL_SAVE_BOOK_DICTIONARY_TERM -> saveBookDictionaryTerm(args)
            TOOL_DELETE_BOOK_DICTIONARY_TERM -> deleteBookDictionaryTerm(args)
            TOOL_CLEAR_BOOK_DICTIONARY -> clearBookDictionary(args)
            TOOL_LIST_DICTIONARY_ENTRIES -> listDictionaryEntries(args)
            TOOL_SAVE_DICTIONARY_ENTRY -> saveDictionaryEntry(args)
            TOOL_DELETE_DICTIONARY_ENTRY -> deleteDictionaryEntry(args)
            TOOL_GET_BOOKSHELF_AUTOMATION -> getBookshelfAutomation()
            TOOL_SET_BOOKSHELF_AUTOMATION -> setBookshelfAutomation(args)
            TOOL_CREATE_STORY_MEMORY -> createStoryMemory(args)
            TOOL_GET_STORY_MEMORY -> getStoryMemory(args)
            TOOL_RETROFIT_STORY_TRANSLATIONS -> retrofitStoryTranslations(args)
            TOOL_GET_STORY_CHRONICLE -> getStoryChronicle(args)
            TOOL_SYNTHESIZE_STORY_CHRONICLE -> synthesizeStoryChronicle(args)
            TOOL_SAVE_STORY_CHRONICLE -> saveStoryChronicle(args)
            TOOL_UPSERT_STORY_WIKI_ENTITY -> upsertStoryWikiEntity(args)
            TOOL_DELETE_STORY_WIKI_ENTITY -> deleteStoryWikiEntity(args)
            TOOL_UPSERT_STORY_WIKI_WORLD -> upsertStoryWikiWorld(args)
            TOOL_DELETE_STORY_WIKI_WORLD -> deleteStoryWikiWorld(args)
            TOOL_UPSERT_STORY_WIKI_RELATIONSHIP -> upsertStoryWikiRelationship(args)
            TOOL_DELETE_STORY_WIKI_RELATIONSHIP -> deleteStoryWikiRelationship(args)
            TOOL_GET_STORY_WIKI -> getStoryWiki(args)
            TOOL_SAVE_BOOK_SOURCE -> saveBookSource(args)
            TOOL_TEST_BOOK_SOURCE_RULE -> testBookSourceRule(args)
            TOOL_GENERATE_CHARACTER_IMAGE -> generateCharacterImage(args)
            TOOL_GENERATE_BOOK_COVER -> generateBookCover(args)
            TOOL_GENERATE_WORLD_IMAGE -> generateWorldImage(args)
            else -> error("Unhandled registered tool: ${canonicalCall.name}")
        }
        AiToolResult(
            callId = canonicalCall.id,
            name = canonicalCall.name,
            content = content
        )
    }

    private fun CustomAgentToolGateway?.registeredCustomTools(): List<AiToolDefinition> =
        this?.registeredToolDefinitions().orEmpty()

    private fun CustomAgentToolGateway?.availableCustomTools(): List<AiToolDefinition> =
        this?.availableToolDefinitions().orEmpty()

    private suspend fun fetchInternetPage(args: JsonObject): String {
        return try {
            val rawUrl = args.string("url").orEmpty().trim()
            if (rawUrl.isBlank()) return """{"error":"url is required"}"""
            val validatedUrl = validateInternetFetchUrl(rawUrl).getOrElse { error ->
                return GSON.toJson(
                    mapOf(
                        "error" to "Internet page fetch blocked",
                        "message" to error.message.orEmpty(),
                    )
                )
            }
            val maxChars = args.int("maxChars", 8_000)
                .coerceIn(INTERNET_FETCH_MIN_CHARS, INTERNET_FETCH_MAX_CHARS)
            val timeoutMs = args.int("timeoutMs", 20_000).coerceIn(5_000, 45_000)
            val maxBytes = (maxChars * 4)
                .coerceAtLeast(INTERNET_FETCH_MIN_RESPONSE_BYTES)
                .coerceAtMost(INTERNET_FETCH_MAX_RESPONSE_BYTES)
            val response = withTimeoutOrNull(timeoutMs.toLong()) {
                okHttpClient.newCallResponse {
                    url(validatedUrl)
                    header("User-Agent", TOOL_USER_AGENT)
                    header("Accept", "text/html,text/plain,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.1")
                }
            } ?: return """{"error":"Internet page fetch timed out"}"""
            response.use { result ->
                val finalUrl = result.request.url.toString()
                validateInternetFetchUrl(finalUrl).exceptionOrNull()?.let { error ->
                    return GSON.toJson(
                        mapOf(
                            "error" to "Internet page fetch blocked",
                            "message" to "Redirect target is not allowed: ${error.message.orEmpty()}",
                            "url" to validatedUrl,
                            "finalUrl" to finalUrl,
                        )
                    )
                }
                if (!result.isSuccessful) {
                    return GSON.toJson(
                        mapOf(
                            "error" to "Internet page fetch failed",
                            "statusCode" to result.code,
                            "message" to result.message,
                            "url" to validatedUrl,
                            "finalUrl" to finalUrl,
                        )
                    )
                }
                val body = result.body
                val contentType = body.contentType()?.toString()
                if (!isReadableInternetContentType(contentType)) {
                    return GSON.toJson(
                        mapOf(
                            "error" to "Internet page content is not text",
                            "contentType" to contentType.orEmpty(),
                            "url" to validatedUrl,
                            "finalUrl" to finalUrl,
                        )
                    )
                }
                val bodyPreview = readInternetBodyPreview(body, maxBytes)
                val page = extractInternetPageContent(
                    raw = bodyPreview.text,
                    baseUrl = finalUrl,
                    contentType = contentType,
                    maxChars = maxChars,
                )
                GSON.toJson(
                    mapOf(
                        "url" to validatedUrl,
                        "finalUrl" to finalUrl,
                        "title" to page.title,
                        "description" to page.description,
                        "contentType" to contentType.orEmpty(),
                        "text" to page.text,
                        "truncated" to (bodyPreview.truncated || page.truncated),
                    )
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Internet page fetch failed", error)
        }
    }

    private suspend fun searchInternet(args: JsonObject): String {
        return try {
            val query = args.string("query").orEmpty().trim()
            if (query.isBlank()) return """{"error":"query is required"}"""
            val limit = args.int("limit", 5).coerceIn(1, 10)
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val searchUrl = "https://duckduckgo.com/html/?q=$encoded"
            val response = withTimeoutOrNull(args.int("timeoutMs", 20_000).coerceIn(5_000, 45_000).toLong()) {
                okHttpClient.newCallStrResponse {
                    url(searchUrl)
                    header("User-Agent", TOOL_USER_AGENT)
                    header("Accept", "text/html,application/xhtml+xml")
                }
            } ?: return """{"error":"Internet search timed out"}"""
            if (!response.isSuccessful()) {
                return GSON.toJson(
                    mapOf(
                        "error" to "Internet search failed",
                        "statusCode" to response.code(),
                        "message" to response.message(),
                    )
                )
            }
            val doc = Jsoup.parse(response.body.orEmpty(), "https://duckduckgo.com")
            val results = doc.select(".result").asSequence()
                .mapNotNull { result ->
                    val title = result.selectFirst("a.result__a") ?: return@mapNotNull null
                    val titleText = title.text().trim().takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val href = normalizeDuckDuckGoUrl(title.attr("href"))
                        .takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    mapOf(
                        "title" to titleText,
                        "url" to href,
                        "snippet" to result.select(".result__snippet").text().trim(),
                        "source" to result.select(".result__url").text().trim(),
                    )
                }
                .distinctBy { it["url"] }
                .take(limit)
                .toList()
            GSON.toJson(
                mapOf(
                    "query" to query,
                    "engine" to "duckduckgo_html",
                    "results" to results,
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Internet search failed", error)
        }
    }

    private fun searchBooks(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 8).coerceIn(1, 20)
        val books = bookDao.all
            .asSequence()
            .filter { book ->
                query.isBlank() ||
                    book.name.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.originName.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.durChapterTime }
            .take(limit)
            .map { it.toSummaryMap() }
            .toList()
        return GSON.toJson(mapOf("books" to books))
    }

    private fun searchBookSources(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 20).coerceIn(1, 100)
        val enabledOnly = args.boolean("enabledOnly", true)
        val sourceType = args.string("sourceType")?.toIntOrNull()
        val requireSearch = args.boolean("requireSearch", false)
        val sources = (if (query.isBlank()) bookSourceDao.all else bookSourceDao.search(query))
            .asSequence()
            .filter { !enabledOnly || it.enabled }
            .filter { sourceType == null || it.bookSourceType == sourceType }
            .filter { !requireSearch || !it.searchUrl.isNullOrBlank() }
            .take(limit)
            .map { it.toToolMap() }
            .toList()
        return GSON.toJson(
            mapOf(
                "query" to query,
                "sources" to sources,
            )
        )
    }

    private suspend fun searchOnlineBooks(args: JsonObject): String {
        return try {
            val keyword = args.string("query").orEmpty().trim()
            if (keyword.isBlank()) return """{"error":"query is required"}"""
            val limit = args.int("limit", 10).coerceIn(1, 20)
            val sourceLimit = args.int("sourceLimit", 8).coerceIn(1, 20)
            val timeoutMs = args.int("timeoutMs", 45_000).coerceIn(10_000, 90_000)
            val books = linkedMapOf<String, SearchBook>()
            var processedSources = 0
            var totalSources = 0
            var hasMore = false
            val scope = buildBookSearchScope(args, sourceLimit)
            val finished = withTimeoutOrNull(timeoutMs.toLong()) {
                searchBooksUseCase.execute(
                    BookSearchRequest(
                        keyword = keyword,
                        page = args.int("page", 1).coerceAtLeast(1),
                        scope = scope,
                        matchMode = if (args.boolean("exact", false)) MatchMode.EXACT else MatchMode.DEFAULT,
                        concurrency = args.int("concurrency", 4).coerceIn(1, 8),
                        types = args.string("sourceType")?.toIntOrNull()?.let(::setOf),
                    ),
                    BookSearchControl(),
                ).collect { event ->
                    when (event) {
                        SearchRunEvent.Started -> Unit
                        is SearchRunEvent.Progress -> {
                            processedSources = event.processedSources
                            totalSources = event.totalSources
                            event.upsertBooks.forEach { book ->
                                if (books.size < limit) {
                                    books.putIfAbsent(book.bookUrl, book)
                                }
                            }
                        }
                        is SearchRunEvent.Finished -> {
                            hasMore = event.hasMore
                        }
                    }
                }
                true
            } ?: false
            GSON.toJson(
                mapOf(
                    "query" to keyword,
                    "finished" to finished,
                    "timedOut" to !finished,
                    "processedSources" to processedSources,
                    "totalSources" to totalSources,
                    "hasMore" to hasMore,
                    "books" to books.values.map { it.toToolMap() },
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Online book search failed", error)
        }
    }

    private suspend fun getAiRuntimeStatus(args: JsonObject): String {
        val taskType = args.aiTaskType(defaultValue = AiTaskType.CHAT)
        val selectedPreset = aiProfileGateway.getTaskPreset(taskType)
        val chatPreset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
        val translationPreset = aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
        val builtInRegistered = toolDefinitions.size
        val builtInAvailable = toolDefinitions.count { agentPermissionBroker.isToolEnabled(it.name) }
        val registered = registeredTools()
        val available = availableTools()
        return GSON.toJson(
            mapOf(
                "taskType" to taskType,
                "configured" to (selectedPreset != null),
                "selectedPreset" to selectedPreset?.toAiPresetToolMap(),
                "chatPreset" to chatPreset?.toAiPresetToolMap(),
                "translationPreset" to translationPreset?.toAiPresetToolMap(),
                "tools" to mapOf(
                    "builtInRegistered" to builtInRegistered,
                    "builtInAvailable" to builtInAvailable,
                    "registeredTotal" to registered.size,
                    "availableTotal" to available.size,
                    "availableBuiltInTools" to toolDefinitions
                        .filter { agentPermissionBroker.isToolEnabled(it.name) }
                        .map(AiToolDefinition::name),
                    "customRegistered" to customAgentToolGateway.registeredCustomTools().size,
                    "customAvailable" to customAgentToolGateway.availableCustomTools().size,
                ),
            )
        )
    }

    private suspend fun getAiQuotaStatus(args: JsonObject): String {
        val taskType = args.aiTaskType(defaultValue = AiTaskType.CHAT)
        val preset = aiProfileGateway.getTaskPreset(taskType)
            ?: return GSON.toJson(
                mapOf(
                    "taskType" to taskType,
                    "configured" to false,
                    "quotaKnown" to false,
                    "message" to "Chưa cấu hình model mặc định cho tác vụ này.",
                )
            )
        val protocol = preset.model.provider.protocol
        val quota = when (protocol) {
            AiProtocol.LOCAL_GGUF -> mapOf(
                "quotaKnown" to true,
                "remaining" to null,
                "unit" to "local_compute",
                "message" to "Model local không có hạn mức API; tốc độ phụ thuộc vào thiết bị.",
            )
            AiProtocol.CODEX_SUBSCRIPTION,
            AiProtocol.GROK_CLI_SUBSCRIPTION,
            AiProtocol.CLAUDE_SUBSCRIPTION,
            AiProtocol.ANTIGRAVITY -> mapOf(
                "quotaKnown" to false,
                "remaining" to null,
                "unit" to "provider_subscription",
                "message" to "Hạn mức gói đăng ký do provider quản lý; app không có endpoint chung để đọc số còn lại.",
            )
            else -> mapOf(
                "quotaKnown" to false,
                "remaining" to null,
                "unit" to "provider_api",
                "message" to "Provider/API hiện tại không cung cấp endpoint hạn mức chung trong app. Khi provider trả lời quota/rate-limit, app sẽ hiện lỗi từ request gần nhất.",
            )
        }
        return GSON.toJson(
            mapOf(
                "taskType" to taskType,
                "configured" to true,
                "preset" to preset.toAiPresetToolMap(),
                "quota" to quota,
                "safeToReport" to "Do not guess exact remaining quota when quotaKnown is false.",
            )
        )
    }

    private suspend fun diagnoseBookSource(args: JsonObject): String {
        return try {
            val source = resolveBookSource(args)
                ?: return """{"error":"sourceUrl, sourceName, or query is required"}"""
            val profile = args.sourceCheckProfile(defaultValue = SourceCheckProfile.STANDARD)
            val timeoutMs = args.int("timeoutMs", 90_000)
                .coerceIn(5_000, 180_000)
                .toLong()
            val run = sourceCheckEngine.checkBookSource(
                source = source,
                profile = profile,
                persistSummary = args.boolean("persist", true),
                timeoutMs = timeoutMs,
            )
            val stages = sourceCheckRepository.observeStages(run.id).first()
            GSON.toJson(
                mapOf(
                    "source" to source.toToolMap(),
                    "run" to run.toToolMap(),
                    "stages" to stages.map { it.toToolMap() },
                    "repairHints" to repairHints(source, run, stages),
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Book source diagnosis failed", error)
        }
    }

    private suspend fun repairBookSource(args: JsonObject): String {
        return try {
            val source = resolveBookSource(args)
                ?: return """{"error":"sourceUrl, sourceName, or query is required"}"""
            val before = source.toToolMap()
            val changes = mutableListOf<String>()

            args.booleanOrNull("enabled")?.let { enabled ->
                if (source.enabled != enabled) {
                    source.enabled = enabled
                    changes += "enabled"
                }
            }
            args.booleanOrNull("enabledExplore")?.let { enabledExplore ->
                if (source.enabledExplore != enabledExplore) {
                    source.enabledExplore = enabledExplore
                    changes += "enabledExplore"
                }
            }

            args.string("bookSourceName")?.takeIf { it.isNotBlank() }?.let {
                if (source.bookSourceName != it) {
                    source.bookSourceName = it
                    changes += "bookSourceName"
                }
            }
            args.string("bookSourceGroup")?.let {
                if (source.bookSourceGroup != it) {
                    source.bookSourceGroup = it
                    changes += "bookSourceGroup"
                }
            }
            args.string("bookSourceUrl")?.takeIf { it.isNotBlank() }?.let {
                if (source.bookSourceUrl != it) {
                    source.bookSourceUrl = it
                    changes += "bookSourceUrl"
                }
            }
            args.string("searchUrl")?.let {
                if (source.searchUrl != it) {
                    source.searchUrl = it
                    changes += "searchUrl"
                }
            }
            args.string("exploreUrl")?.let {
                if (source.exploreUrl != it) {
                    source.exploreUrl = it
                    changes += "exploreUrl"
                }
            }
            args.string("header")?.let {
                if (source.header != it) {
                    source.header = it
                    changes += "header"
                }
            }
            args.string("loginUrl")?.let {
                if (source.loginUrl != it) {
                    source.loginUrl = it
                    changes += "loginUrl"
                }
            }
            args.intOrNull("weight")?.let {
                if (source.weight != it) {
                    source.weight = it
                    changes += "weight"
                }
            }

            if (args.has("ruleSearch")) {
                val elem = args.get("ruleSearch")
                val rule = if (elem.isJsonObject) GSON.fromJson(elem, SearchRule::class.java) else GSON.fromJson(elem.asString, SearchRule::class.java)
                source.ruleSearch = rule
                changes += "ruleSearch"
            }
            if (args.has("ruleBookInfo")) {
                val elem = args.get("ruleBookInfo")
                val rule = if (elem.isJsonObject) GSON.fromJson(elem, BookInfoRule::class.java) else GSON.fromJson(elem.asString, BookInfoRule::class.java)
                source.ruleBookInfo = rule
                changes += "ruleBookInfo"
            }
            if (args.has("ruleToc")) {
                val elem = args.get("ruleToc")
                val rule = if (elem.isJsonObject) GSON.fromJson(elem, TocRule::class.java) else GSON.fromJson(elem.asString, TocRule::class.java)
                source.ruleToc = rule
                changes += "ruleToc"
            }
            if (args.has("ruleContent")) {
                val elem = args.get("ruleContent")
                val rule = if (elem.isJsonObject) GSON.fromJson(elem, ContentRule::class.java) else GSON.fromJson(elem.asString, ContentRule::class.java)
                source.ruleContent = rule
                changes += "ruleContent"
            }
            if (args.has("ruleExplore")) {
                val elem = args.get("ruleExplore")
                val rule = if (elem.isJsonObject) GSON.fromJson(elem, ExploreRule::class.java) else GSON.fromJson(elem.asString, ExploreRule::class.java)
                source.ruleExplore = rule
                changes += "ruleExplore"
            }

            if (args.boolean("repairVbookCompatibility", true) && source.isVbookInstalledSource()) {
                if (VbookPluginImporter.reconcileInstalledSourceType(appCtx, source)) {
                    changes += "vbookSourceType"
                }
                if (source.searchUrl.isNullOrBlank()) {
                    source.searchUrl = "vbook://search"
                    changes += "searchUrl"
                }
                if (source.exploreUrl.isNullOrBlank()) {
                    source.exploreUrl = "vbook://home"
                    changes += "exploreUrl"
                }
                val contentRule = source.ruleContent ?: ContentRule().also {
                    source.ruleContent = it
                }
                if (contentRule.content.isNullOrBlank()) {
                    contentRule.content = "vbook"
                    changes += "ruleContent.content"
                }
                if (source.bookSourceGroup.isNullOrBlank()) {
                    source.bookSourceGroup = "VBook"
                    changes += "bookSourceGroup"
                }
            }

            if (changes.isNotEmpty()) {
                source.lastUpdateTime = System.currentTimeMillis()
                bookSourceDao.update(source)
            }

            val run = if (args.boolean("runHealthCheck", true)) {
                val profile = args.sourceCheckProfile(defaultValue = SourceCheckProfile.STANDARD)
                sourceCheckEngine.checkBookSource(
                    source = source,
                    profile = profile,
                    persistSummary = true,
                    timeoutMs = args.int("timeoutMs", 90_000)
                        .coerceIn(5_000, 180_000)
                        .toLong(),
                )
            } else {
                null
            }
            val stages = run?.let { sourceCheckRepository.observeStages(it.id).first() }.orEmpty()
            GSON.toJson(
                mapOf(
                    "repaired" to changes.isNotEmpty(),
                    "changes" to changes,
                    "before" to before,
                    "after" to source.toToolMap(),
                    "run" to run?.toToolMap(),
                    "stages" to stages.map { it.toToolMap() },
                    "note" to "Source rules and metadata updated successfully.",
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Book source repair failed", error)
        }
    }

    private fun saveBookSource(args: JsonObject): String {
        return try {
            val sourceJson = args.string("sourceJson")
            val source = if (!sourceJson.isNullOrBlank()) {
                parseAgentLegadoBookSourceJson(sourceJson)
            } else {
                val url = args.string("bookSourceUrl")?.trim().orEmpty()
                val name = args.string("bookSourceName")?.trim().orEmpty()
                require(url.isNotBlank()) { "bookSourceUrl is required" }
                require(name.isNotBlank()) { "bookSourceName is required" }
                val existing = bookSourceDao.getBookSource(url) ?: BookSource(bookSourceUrl = url, bookSourceName = name)
                existing.bookSourceName = name
                args.string("bookSourceGroup")?.let { existing.bookSourceGroup = it }
                args.string("searchUrl")?.let { existing.searchUrl = it }
                args.string("exploreUrl")?.let { existing.exploreUrl = it }
                args.string("header")?.let { existing.header = it }
                args.string("loginUrl")?.let { existing.loginUrl = it }
                args.intOrNull("weight")?.let { existing.weight = it }
                existing
            }
            val enableAfterSave = args.boolean("enableAfterSave", true)
            source.enabled = enableAfterSave
            if (enableAfterSave) source.enabledExplore = true
            SourceHelp.insertBookSource(source)
            GSON.toJson(
                mapOf(
                    "saved" to true,
                    "bookSourceName" to source.bookSourceName,
                    "bookSourceUrl" to source.bookSourceUrl,
                    "enabled" to source.enabled,
                    "enabledExplore" to source.enabledExplore,
                )
            )
        } catch (error: Throwable) {
            toolError("Save book source failed", error)
        }
    }

    private suspend fun testBookSourceRule(args: JsonObject): String {
        return try {
            val source = resolveBookSource(args)
                ?: if (!args.string("sourceJson").isNullOrBlank()) {
                    parseAgentLegadoBookSourceJson(args.string("sourceJson").orEmpty())
                } else {
                    return """{"error":"sourceUrl, sourceName, or sourceJson is required"}"""
                }
            val testType = args.string("testType")?.uppercase() ?: "SEARCH"
            val targetUrl = args.string("targetUrl").orEmpty().trim()
            val keyword = args.string("keyword")?.trim().orEmpty().ifBlank { "test" }

            when (testType) {
                "SEARCH" -> {
                    val results = io.legado.app.model.webBook.WebBook.searchBookAwait(
                        bookSource = source,
                        key = keyword,
                        page = 1,
                    )
                    GSON.toJson(
                        mapOf(
                            "success" to true,
                            "testType" to "SEARCH",
                            "keyword" to keyword,
                            "resultsCount" to results.size,
                            "sampleResults" to results.take(3).map {
                                mapOf(
                                    "name" to it.name,
                                    "author" to it.author,
                                    "bookUrl" to it.bookUrl,
                                    "latestChapterTitle" to it.latestChapterTitle,
                                    "intro" to it.intro?.take(100),
                                )
                            }
                        )
                    )
                }
                "TOC" -> {
                    val sampleBook = Book(
                        bookUrl = targetUrl.ifBlank { source.bookSourceUrl },
                        origin = source.bookSourceUrl,
                        originName = source.bookSourceName,
                        name = "Test Book",
                    )
                    val chapters = io.legado.app.model.webBook.WebBook.getChapterListAwait(
                        bookSource = source,
                        book = sampleBook,
                    ).getOrThrow()
                    GSON.toJson(
                        mapOf(
                            "success" to true,
                            "testType" to "TOC",
                            "chaptersCount" to chapters.size,
                            "sampleChapters" to chapters.take(5).map {
                                mapOf(
                                    "index" to it.index,
                                    "title" to it.title,
                                    "url" to it.url,
                                )
                            }
                        )
                    )
                }
                "CONTENT" -> {
                    val sampleBook = Book(
                        bookUrl = source.bookSourceUrl,
                        origin = source.bookSourceUrl,
                        originName = source.bookSourceName,
                        name = "Test Book",
                    )
                    val sampleChapter = io.legado.app.data.entities.BookChapter(
                        url = targetUrl.ifBlank { source.bookSourceUrl },
                        title = "Test Chapter",
                        bookUrl = sampleBook.bookUrl,
                        index = 0,
                    )
                    val content = io.legado.app.model.webBook.WebBook.getContentAwait(
                        bookSource = source,
                        book = sampleBook,
                        bookChapter = sampleChapter,
                    )
                    GSON.toJson(
                        mapOf(
                            "success" to true,
                            "testType" to "CONTENT",
                            "contentLength" to content.length,
                            "sampleContent" to content.take(300),
                        )
                    )
                }
                "INFO" -> {
                    val sampleBook = Book(
                        bookUrl = targetUrl.ifBlank { source.bookSourceUrl },
                        origin = source.bookSourceUrl,
                        originName = source.bookSourceName,
                        name = "Test Book",
                    )
                    val bookInfo = io.legado.app.model.webBook.WebBook.getBookInfoAwait(
                        bookSource = source,
                        book = sampleBook,
                    )
                    GSON.toJson(
                        mapOf(
                            "success" to true,
                            "testType" to "INFO",
                            "book" to mapOf(
                                "name" to bookInfo.name,
                                "author" to bookInfo.author,
                                "intro" to bookInfo.intro?.take(150),
                                "tocUrl" to bookInfo.tocUrl,
                                "coverUrl" to bookInfo.coverUrl,
                            )
                        )
                    )
                }
                else -> """{"error":"Unsupported testType: $testType. Supported: SEARCH, TOC, CONTENT, INFO"}"""
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Book source rule test failed", error)
        }
    }

    private suspend fun createStoryMemory(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved from bookUrl or bookName"}"""
        val content = args.string("content")?.trim().orEmpty()
        val chapterIndex = args.intOrNull("chapterIndex") ?: book.durChapterIndex
        val chapterCount = args.int("chapterCount", 1).coerceIn(1, 10)
        val chapterTitle = args.string("chapterTitle")?.trim().orEmpty()

        val preset = aiProfileGateway.getTaskPreset(AiTaskType.EXTRACT_STORY_MEMORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: return """{"error":"No AI task preset available"}"""

        return try {
            if (content.isNotBlank()) {
                val resolvedTitle = if (chapterTitle.isNotBlank()) {
                    useCase.formatVietnameseChapterTitle(book.bookUrl, chapterIndex, chapterTitle)
                } else {
                    useCase.formatVietnameseChapterTitle(book.bookUrl, chapterIndex, "")
                }
                val analysisResult = useCase.analyzeTextContent(book, content, preset)
                if (analysisResult.isFailure) {
                    return toolError("Analyze text content failed", analysisResult.exceptionOrNull() ?: Exception("Unknown error"))
                }
                val analysis = analysisResult.getOrThrow()
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "bookName" to book.name,
                        "chapterTitle" to resolvedTitle,
                        "entitiesCount" to analysis.entities.size,
                        "relationshipsCount" to analysis.relationships.size,
                        "worldEntriesCount" to analysis.worldBuilding.size,
                        "hasTimeline" to (analysis.timeline != null),
                    )
                )
            } else {
                val range = chapterIndex until (chapterIndex + chapterCount)
                val countResult = useCase.batchAnalyzeChapters(book, range, preset, force = true)
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "bookName" to book.name,
                        "analyzedChapters" to (countResult.getOrNull() ?: 0),
                        "requestedRange" to "${range.first}..${range.last}",
                    )
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Create story memory failed", error)
        }
    }

    private suspend fun getStoryMemory(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved from bookUrl or bookName"}"""
        val filter = args.string("filter")?.lowercase() ?: "all"
        val limit = args.int("limit", 50).coerceIn(1, 200)

        val snapshot = useCase.loadSnapshotWithSeriesInheritance(book)
        val data = mutableMapOf<String, Any>()
        data["bookName"] = book.name
        data["bookUrl"] = book.bookUrl

        if (filter == "all" || filter == "entities") {
            data["entities"] = snapshot.entities.take(limit).map {
                mapOf(
                    "raw" to it.raw,
                    "target" to it.target,
                    "type" to it.type,
                    "description" to it.description,
                    "aliases" to it.aliases,
                )
            }
        }
        if (filter == "all" || filter == "world") {
            data["worldBuilding"] = snapshot.worldBuilding.take(limit).map {
                mapOf(
                    "raw" to it.raw,
                    "target" to it.target,
                    "category" to it.category,
                    "description" to it.description,
                )
            }
        }
        if (filter == "all" || filter == "relationships") {
            data["relationships"] = snapshot.relationships.take(limit).map {
                mapOf(
                    "source" to it.source,
                    "target" to it.target,
                    "relationship" to it.relationship,
                    "description" to it.description,
                )
            }
        }
        if (filter == "all" || filter == "timeline") {
            data["timelines"] = snapshot.timelines.take(limit).map { tl ->
                val displayTitle = useCase.formatVietnameseChapterTitle(book.bookUrl, tl.chapterIndex, tl.chapterTitle)
                mapOf(
                    "chapterIndex" to tl.chapterIndex,
                    "chapterTitle" to displayTitle,
                    "summary" to tl.summary,
                    "events" to tl.events,
                    "characters" to tl.characters.map { it.target.ifBlank { it.raw } },
                )
            }
        }
        return GSON.toJson(data)
    }

    private suspend fun retrofitStoryTranslations(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val translateUseCase = translateChapterUseCase
            ?: return """{"error":"TranslateChapterUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved from bookUrl or bookName"}"""
        val dryRun = args.boolean("dryRun", false)
        val snapshot = useCase.loadSnapshot(book.bookUrl)
        if (dryRun) {
            return GSON.toJson(
                mapOf(
                    "dryRun" to true,
                    "bookName" to book.name,
                    "analyzedChapters" to snapshot.analyzedChapterIndices.size,
                    "canonicalTerms" to snapshot.canonicalMemory.size,
                )
            )
        }
        val chapterIndex = args.intOrNull("chapterIndex")
        val chapterCount = args.int("chapterCount", 1).coerceIn(1, 100)
        val chapterIndices = if (chapterIndex != null) {
            (chapterIndex until (chapterIndex + chapterCount)).toList()
        } else {
            snapshot.analyzedChapterIndices.sorted().ifEmpty { listOf(book.durChapterIndex) }
        }
        val affected = useCase.retrofitChapterTranslations(book, chapterIndices, translateUseCase).getOrThrow()
        postEvent(EventBus.REFRESH_BOOK_CONTENT, true)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "bookName" to book.name,
                "retrofittedChapters" to affected,
            )
        )
    }

    private suspend fun getStoryChronicle(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val periods = useCase.getChroniclePeriods(book.bookUrl)
        val includeRaw = args.boolean("includeRawTimelines", false)
        val data = mutableMapOf<String, Any>(
            "bookName" to book.name,
            "bookUrl" to book.bookUrl,
            "chroniclePeriodsCount" to periods.size,
            "chroniclePeriods" to periods,
        )
        if (includeRaw) {
            val snapshot = useCase.loadSnapshot(book.bookUrl)
            data["rawTimelines"] = snapshot.timelines.map { tl ->
                mapOf(
                    "chapterIndex" to tl.chapterIndex,
                    "chapterTitle" to useCase.formatVietnameseChapterTitle(book.bookUrl, tl.chapterIndex, tl.chapterTitle),
                    "summary" to tl.summary,
                    "events" to tl.events,
                )
            }
        }
        return GSON.toJson(data)
    }

    private suspend fun synthesizeStoryChronicle(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val textGateway = aiTextGateway
            ?: return """{"error":"AiTextGateway is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val directive = args.string("directive")?.trim().orEmpty()
        val startChapter = args.int("startChapter", 0)
        val endChapter = args.intOrNull("endChapter")

        val snapshot = useCase.loadSnapshot(book.bookUrl)
        val timelines = snapshot.timelines
            .filter { it.chapterIndex >= startChapter && (endChapter == null || it.chapterIndex <= endChapter) }
            .sortedBy { it.chapterIndex }

        if (timelines.isEmpty()) {
            return GSON.toJson(
                mapOf(
                    "error" to "No timelines found in specified range to synthesize chronicle",
                    "hint" to "Use create_story_memory to analyze chapters first.",
                )
            )
        }

        val timelineDigest = timelines.joinToString("\n---\n") { tl ->
            val title = useCase.formatVietnameseChapterTitle(book.bookUrl, tl.chapterIndex, tl.chapterTitle)
            "Chương ${tl.chapterIndex + 1} ($title): ${tl.summary}\nSự kiện: ${tl.events.joinToString("; ")}\nNhân vật: ${tl.characters.joinToString(", ") { it.target.ifBlank { it.raw } }}"
        }

        val prompt = buildString {
            appendLine("Bạn là chuyên gia biên niên sử và tổng hợp cốt truyện tiểu thuyết (Story Chronicler).")
            appendLine("Dưới đây là dòng thời gian các chương truyện của cuốn sách '${book.name}':")
            appendLine(timelineDigest)
            appendLine()
            if (directive.isNotBlank()) {
                appendLine("YÊU CẦU ĐẶC BIỆT TỪ NGƯỜI DÙNG: $directive")
            }
            appendLine("HÃY TỔNG HỢP VÀ VIẾT LẠI THÀNH CÁC THỜI KỲ / GIAI ĐOẠN / QUYỂN LỚN (Eras / Story Arcs) MẠCH LẠC:")
            appendLine("QUY TẮC:")
            appendLine("1. KHÔNG được chép nguyên văn timeline từng chương vụn vặt.")
            appendLine("2. Phân chia cốt truyện thành các Thời kỳ/Giai đoạn hợp lý theo diễn biến thế giới, cảnh giới, hoặc biến cố trọng đại.")
            appendLine("3. Trả về đúng MỘT MẢNG JSON thuần túy, không Markdown:")
            appendLine("""[{"eraTitle":"Tên thời kỳ","chapterRange":"Chương X - Chương Y","eraSummary":"Tóm lược biến chuyển cốt truyện...","milestoneEvents":["Sự kiện 1","Sự kiện 2"],"keyCharacters":["Tên 1","Tên 2"],"startChapterIndex":0,"endChapterIndex":50}]""")
        }

        val preset = aiProfileGateway.getTaskPreset(AiTaskType.EXTRACT_STORY_MEMORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: return """{"error":"No AI task preset configured"}"""
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(AiMessage(AiMessageRole.USER, prompt)),
            params = preset.params,
            taskType = preset.taskType.ifBlank { AiTaskType.EXTRACT_STORY_MEMORY },
            routeProfileId = preset.runtimeOptions.routeProfileId,
        )
        val responseResult = textGateway.generate(request)
        if (responseResult.isFailure) {
            return toolError("Generate chronicle failed", responseResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
        val response = responseResult.getOrThrow().text
        val jsonCleaned = response.substringAfter("[").substringBeforeLast("]")
        val jsonArrayStr = if (jsonCleaned.isNotBlank()) "[$jsonCleaned]" else response
        val periods = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<StoryChroniclePeriod>>() {}.type
            GSON.fromJson<List<StoryChroniclePeriod>>(jsonArrayStr, type)
        }.getOrNull().orEmpty()

        periods.forEach { period ->
            useCase.upsertChroniclePeriod(book.bookUrl, period)
        }

        return GSON.toJson(
            mapOf(
                "success" to true,
                "bookName" to book.name,
                "periodsCreated" to periods.size,
                "periods" to periods,
            )
        )
    }

    private suspend fun saveStoryChronicle(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val eraTitle = args.string("eraTitle")?.trim().orEmpty()
        require(eraTitle.isNotBlank()) { "eraTitle is required" }
        val chapterRange = args.string("chapterRange")?.trim().orEmpty()
        val eraSummary = args.string("eraSummary")?.trim().orEmpty()
        val milestoneEvents = args.stringList("milestoneEvents")
        val keyCharacters = args.stringList("keyCharacters")
        val startChapterIndex = args.int("startChapterIndex", 0)
        val endChapterIndex = args.int("endChapterIndex", 0)
        val id = args.string("id")?.trim().orEmpty().ifBlank { UUID.randomUUID().toString().take(8) }

        val period = StoryChroniclePeriod(
            id = id,
            bookUrl = book.bookUrl,
            eraTitle = eraTitle,
            chapterRange = chapterRange,
            eraSummary = eraSummary,
            milestoneEvents = milestoneEvents,
            keyCharacters = keyCharacters,
            startChapterIndex = startChapterIndex,
            endChapterIndex = endChapterIndex,
        )
        useCase.upsertChroniclePeriod(book.bookUrl, period)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "period" to period,
            )
        )
    }

    private suspend fun upsertStoryWikiEntity(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val raw = args.string("raw")?.trim().orEmpty()
        val target = args.string("target")?.trim().orEmpty()
        require(raw.isNotBlank()) { "raw is required" }
        require(target.isNotBlank()) { "target is required" }
        val senseKey = args.string("senseKey")?.trim().orEmpty()
        val kind = args.string("kind")?.trim().orEmpty().ifBlank { "character" }
        val summary = args.string("summary")?.trim().orEmpty()
        val aliases = args.stringList("aliases")

        val entity = AiTranslationStoryEntity(
            raw = raw,
            target = target,
            senseKey = senseKey,
            type = kind,
            description = summary,
            aliases = aliases,
            userEdited = true,
        )
        useCase.upsertEntity(book.bookUrl, entity)
        return GSON.toJson(mapOf("success" to true, "entity" to entity))
    }

    private suspend fun deleteStoryWikiEntity(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val raw = args.string("raw")?.trim().orEmpty()
        require(raw.isNotBlank()) { "raw is required" }
        val senseKey = args.string("senseKey")?.trim().orEmpty()
        useCase.deleteEntity(book.bookUrl, raw, senseKey)
        return GSON.toJson(mapOf("success" to true, "deleted" to raw))
    }

    private suspend fun upsertStoryWikiWorld(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val term = args.string("term")?.trim().orEmpty()
        val vietnamese = args.string("vietnamese")?.trim().orEmpty()
        require(term.isNotBlank()) { "term is required" }
        require(vietnamese.isNotBlank()) { "vietnamese is required" }
        val category = args.string("category")?.trim().orEmpty().ifBlank { "other" }
        val description = args.string("description")?.trim().orEmpty()

        val entry = AiTranslationWorldEntry(
            raw = term,
            target = vietnamese,
            category = category,
            description = description,
            userEdited = true,
        )
        useCase.upsertWorldEntry(book.bookUrl, entry)
        return GSON.toJson(mapOf("success" to true, "worldEntry" to entry))
    }

    private suspend fun deleteStoryWikiWorld(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val term = args.string("term")?.trim().orEmpty()
        require(term.isNotBlank()) { "term is required" }
        val entry = AiTranslationWorldEntry(raw = term)
        useCase.deleteWorldEntry(book.bookUrl, entry)
        return GSON.toJson(mapOf("success" to true, "deleted" to term))
    }

    private suspend fun upsertStoryWikiRelationship(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val source = args.string("source")?.trim().orEmpty()
        val target = args.string("target")?.trim().orEmpty()
        val relationship = args.string("relationship")?.trim().orEmpty()
        require(source.isNotBlank()) { "source is required" }
        require(target.isNotBlank()) { "target is required" }
        require(relationship.isNotBlank()) { "relationship is required" }
        val description = args.string("description")?.trim().orEmpty()
        val chapterIndex = args.int("chapterIndex", -1)

        val rel = AiTranslationStoryRelationship(
            source = source,
            target = target,
            relationship = relationship,
            description = description,
            chapterIndex = chapterIndex,
        )
        useCase.upsertRelationship(book.bookUrl, rel)
        return GSON.toJson(mapOf("success" to true, "relationship" to rel))
    }

    private suspend fun deleteStoryWikiRelationship(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val source = args.string("source")?.trim().orEmpty()
        val target = args.string("target")?.trim().orEmpty()
        val relationship = args.string("relationship")?.trim().orEmpty()
        val rel = AiTranslationStoryRelationship(
            source = source,
            target = target,
            relationship = relationship,
        )
        useCase.deleteRelationship(book.bookUrl, rel)
        return GSON.toJson(mapOf("success" to true, "deleted" to "$source -> $target ($relationship)"))
    }

    private suspend fun getStoryWiki(args: JsonObject): String {
        val useCase = translationStoryMemoryUseCase
            ?: return """{"error":"TranslationStoryMemoryUseCase is not available"}"""
        val book = resolveBook(args)
            ?: return """{"error":"Book could not be resolved"}"""
        val tab = args.string("tab")?.uppercase() ?: "ALL"
        val query = args.string("query")?.trim().orEmpty().lowercase()

        val snapshot = useCase.loadSnapshotWithSeriesInheritance(book)
        val data = mutableMapOf<String, Any>(
            "bookName" to book.name,
            "bookUrl" to book.bookUrl,
        )
        if (tab == "ALL" || tab == "ENTITY") {
            val entities = snapshot.entities.filter { query.isBlank() || it.raw.lowercase().contains(query) || it.target.lowercase().contains(query) }
            data["entities"] = entities
        }
        if (tab == "ALL" || tab == "WORLD") {
            val world = snapshot.worldBuilding.filter { query.isBlank() || it.raw.lowercase().contains(query) || it.target.lowercase().contains(query) }
            data["worldBuilding"] = world
        }
        if (tab == "ALL" || tab == "RELATIONSHIP") {
            val rels = snapshot.relationships.filter { query.isBlank() || it.source.lowercase().contains(query) || it.target.lowercase().contains(query) || it.relationship.lowercase().contains(query) }
            data["relationships"] = rels
        }
        if (tab == "ALL" || tab == "CHRONICLE") {
            val periods = useCase.getChroniclePeriods(book.bookUrl).filter { query.isBlank() || it.eraTitle.lowercase().contains(query) || it.eraSummary.lowercase().contains(query) }
            data["chronicle"] = periods
        }
        return GSON.toJson(data)
    }

    private suspend fun generateCharacterImage(args: JsonObject): String {
        val useCase = storyIllustrationUseCase ?: return """{"error":"Story illustration engine is unavailable"}"""
        val bookUrl = args.string("bookUrl")?.trim().orEmpty()
        val entityRaw = args.string("entityRaw")?.trim().orEmpty()
        if (bookUrl.isBlank() || entityRaw.isBlank()) {
            return """{"error":"bookUrl and entityRaw are required"}"""
        }
        val force = args.boolean("force", true)
        return try {
            val imagePath = useCase.generateEntity(bookUrl, entityRaw, force)
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "bookUrl" to bookUrl,
                    "entityRaw" to entityRaw,
                    "imagePath" to imagePath,
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            toolError("Generate character image failed", e)
        }
    }

    private suspend fun generateBookCover(args: JsonObject): String {
        val useCase = storyIllustrationUseCase ?: return """{"error":"Story illustration engine is unavailable"}"""
        val bookUrl = args.string("bookUrl")?.trim().orEmpty()
        if (bookUrl.isBlank()) {
            return """{"error":"bookUrl is required"}"""
        }
        val book = bookDao.getBook(bookUrl)
        var title = args.string("title")?.trim()?.takeIf(String::isNotBlank)
        var author = args.string("author")?.trim()?.takeIf(String::isNotBlank)

        if (title == null && book != null) {
            val bookName = book.name.trim()
            title = if (bookName.containsCjk()) {
                translateChapterUseCase?.executeDynamicUiText("book:$bookUrl:bookname", bookName, book)
                    ?.getOrNull()?.takeIf(String::isNotBlank) ?: bookName
            } else {
                bookName
            }
        }

        if (author == null && book != null) {
            val bookAuthor = book.getRealAuthor().trim().ifBlank { book.author.trim() }
            author = if (bookAuthor.containsCjk()) {
                translateChapterUseCase?.executeDynamicUiText("book:$bookUrl:author", bookAuthor, book)
                    ?.getOrNull()?.takeIf(String::isNotBlank) ?: bookAuthor
            } else {
                bookAuthor
            }
        }

        val prompt = args.string("prompt")?.trim()
        val force = args.boolean("force", true)
        return try {
            val imagePath = useCase.generateBookCover(
                bookUrl = bookUrl,
                customPrompt = prompt,
                title = title,
                author = author,
                force = force,
            )
            if (book != null) {
                book.customCoverUrl = imagePath
                bookDao.update(book)
            }
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "bookUrl" to bookUrl,
                    "bookName" to book?.name,
                    "coverTitle" to title,
                    "coverAuthor" to author,
                    "coverPath" to imagePath,
                    "updatedBookshelf" to (book != null),
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            toolError("Generate book cover failed", e)
        }
    }

    private suspend fun generateWorldImage(args: JsonObject): String {
        val useCase = storyIllustrationUseCase ?: return """{"error":"Story illustration engine is unavailable"}"""
        val bookUrl = args.string("bookUrl")?.trim().orEmpty()
        val raw = args.string("raw")?.trim().orEmpty()
        val category = args.string("category")?.trim().orEmpty().lowercase()
        if (bookUrl.isBlank()) {
            return """{"error":"bookUrl is required"}"""
        }
        val force = args.boolean("force", true)
        return try {
            val imagePath = if (category == "world_map" || raw == StoryIllustrationUseCase.WORLD_MAP_RAW) {
                useCase.generateWorldMap(bookUrl, force)
            } else {
                if (raw.isBlank()) return """{"error":"raw entry name is required for world image"}"""
                val snapshot = translationStoryMemoryUseCase?.loadSnapshot(bookUrl)
                val entry = snapshot?.worldBuilding?.firstOrNull { it.raw.equals(raw, ignoreCase = true) }
                    ?: AiTranslationWorldEntry(
                        raw = raw,
                        target = raw,
                        category = category.ifBlank { "concept" },
                        description = args.string("description").orEmpty(),
                    )
                useCase.generateWorldEntry(bookUrl, entry, force)
            }
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "bookUrl" to bookUrl,
                    "raw" to raw,
                    "category" to category,
                    "imagePath" to imagePath,
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            toolError("Generate world image failed", e)
        }
    }

    private suspend fun addBookToBookshelf(args: JsonObject): String {
        val bookUrl = args.string("bookUrl")?.trim().orEmpty()
        if (bookUrl.isBlank()) return """{"error":"bookUrl is required"}"""
        searchBookDao.getSearchBook(bookUrl)?.let { searchBook ->
            addToBookshelfUseCase.execute(searchBook)
            return GSON.toJson(
                mapOf(
                    "added" to true,
                    "source" to "search_cache",
                    "book" to (bookDao.getBook(bookUrl)?.toSummaryMap() ?: searchBook.toToolMap()),
                )
            )
        }
        val addedCount = addBookUseCase.execute(bookUrl)
        return GSON.toJson(
            mapOf(
                "added" to (addedCount > 0),
                "source" to "url",
                "bookUrl" to bookUrl,
                "addedCount" to addedCount,
                "book" to bookDao.getBook(bookUrl)?.toSummaryMap(),
            )
        )
    }

    private fun createVbookPluginDraft(args: JsonObject): String {
        val name = args.string("name")?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"name is required"}"""
        val source = args.string("source")?.trim()?.takeIf(String::isNotBlank) ?: "ai-agent"
        val author = args.string("author")?.trim()?.takeIf(String::isNotBlank) ?: "AI Agent"
        val type = args.string("type")?.trim()?.takeIf(String::isNotBlank) ?: "novel"
        validateAgentVbookPluginDraftMetadata(source)
        val metadata = linkedMapOf(
            "name" to name,
            "source" to source,
            "author" to author,
            "version" to (args.string("version")?.trim()?.takeIf(String::isNotBlank) ?: "0.1.0"),
            "type" to type,
            "description" to args.string("description").orEmpty().take(1_000),
        )
        val files = args.pluginDraftFiles().ifEmpty {
            listOf("search.js" to DEFAULT_VBOOK_PLUGIN_SCRIPT)
        }.take(MAX_PLUGIN_DRAFT_FILES).map { (fileName, content) ->
            validateAgentVbookPluginDraftFile(fileName, content, MAX_PLUGIN_SCRIPT_CHARS)
            fileName to content
        }
        val scriptMap = args.pluginScriptMap(files.mapTo(mutableSetOf()) { it.first })
        val draftId = "draft_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val root = File(pluginDraftRoot(), draftId)
        val src = File(root, "src")
        check(src.mkdirs()) { "Cannot create plugin draft directory" }
        File(root, "plugin.json").writeText(
            GSON.toJson(
                buildMap<String, Any> {
                    put("metadata", metadata)
                    if (scriptMap.isNotEmpty()) put("script", scriptMap)
                }
            )
        )
        files.forEach { (fileName, content) ->
            File(src, fileName).writeText(content)
        }
        return GSON.toJson(
            mapOf(
                "created" to true,
                "draftId" to draftId,
                "draftPath" to root.absolutePath,
                "files" to listOf("plugin.json") + files.map { "src/${it.first}" },
                "script" to scriptMap,
                "nextStep" to "Review the draft, then call install_vbook_plugin with draftId. Chatbot installs are disabled by default unless enableAfterInstall is true.",
            )
        )
    }

    private suspend fun installVbookPlugin(args: JsonObject): String {
        return try {
            val uri = when {
                !args.string("draftId").isNullOrBlank() -> {
                    Uri.fromFile(zipPluginDraft(args.string("draftId").orEmpty()))
                }
                !args.string("filePath").isNullOrBlank() -> {
                    Uri.fromFile(validateAgentVbookPluginInstallFilePath(args.string("filePath").orEmpty()))
                }
                !args.string("uri").isNullOrBlank() -> {
                    Uri.parse(args.string("uri").orEmpty())
                }
                else -> return """{"error":"draftId, filePath, or uri is required"}"""
            }
            val source = VbookPluginImporter.import(appCtx, uri)
            val enableAfterInstall = args.boolean("enableAfterInstall", false)
            source.enabled = enableAfterInstall
            source.enabledExplore = enableAfterInstall && source.enabledExplore
            SourceHelp.insertBookSource(source)
            GSON.toJson(
                mapOf(
                    "installed" to true,
                    "enabled" to source.enabled,
                    "enabledExplore" to source.enabledExplore,
                    "source" to source.toToolMap(),
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("VBook plugin install failed", error)
        }
    }

    private fun createLegadoBookSourceDraft(args: JsonObject): String {
        return try {
            val sourceJson = args.string("sourceJson").orEmpty()
            val source = parseAgentLegadoBookSourceJson(sourceJson)
            val draftId = "legado_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val root = File(legadoSourceDraftRoot(), draftId)
            check(root.mkdirs()) { "Cannot create Legado source draft directory" }
            File(root, LEGADO_SOURCE_DRAFT_FILE).writeText(GSON.toJson(source))
            GSON.toJson(
                mapOf(
                    "created" to true,
                    "draftId" to draftId,
                    "draftPath" to root.absolutePath,
                    "source" to source.toToolMap(),
                    "installed" to false,
                    "nextStep" to "Review the draft, then call install_legado_book_source with draftId. AI-created sources are disabled by default unless enableAfterInstall is true.",
                )
            )
        } catch (error: Throwable) {
            toolError("Legado book source draft failed", error)
        }
    }

    private fun installLegadoBookSource(args: JsonObject): String {
        return try {
            val sourceJson = when {
                !args.string("draftId").isNullOrBlank() -> {
                    readLegadoSourceDraft(args.string("draftId").orEmpty())
                }
                !args.string("sourceJson").isNullOrBlank() -> args.string("sourceJson").orEmpty()
                else -> return """{"error":"draftId or sourceJson is required"}"""
            }
            val source = parseAgentLegadoBookSourceJson(sourceJson)
            val existing = bookSourceDao.getBookSource(source.bookSourceUrl)
            val overwrite = args.boolean("overwrite", false)
            if (existing != null && !overwrite) {
                return GSON.toJson(
                    mapOf(
                        "error" to "A Legado book source with this source URL is already installed",
                        "source" to existing.toToolMap(),
                        "hint" to "Set overwrite to true only after the user confirms replacing the existing source.",
                    )
                )
            }
            val enableAfterInstall = args.boolean("enableAfterInstall", false)
            source.enabled = enableAfterInstall
            source.enabledExplore = enableAfterInstall && source.enabledExplore
            SourceHelp.insertBookSource(source)
            val installed = bookSourceDao.getBookSource(source.bookSourceUrl)
                ?: return """{"error":"Legado book source was blocked or could not be installed"}"""
            GSON.toJson(
                mapOf(
                    "installed" to true,
                    "replaced" to (existing != null),
                    "enabled" to installed.enabled,
                    "enabledExplore" to installed.enabledExplore,
                    "source" to installed.toToolMap(),
                )
            )
        } catch (error: Throwable) {
            toolError("Legado book source install failed", error)
        }
    }

    private suspend fun listAgentSkills(): String {
        val skills = aiSkillGateway.observeSkills().first()
        return GSON.toJson(mapOf("skills" to skills.map { it.toToolMap() }))
    }

    private suspend fun createAgentSkillDraft(args: JsonObject): String {
        return try {
            val draft = AgentSkillDraft(
                slug = args.string("id").orEmpty(),
                name = args.string("name").orEmpty(),
                description = args.string("description").orEmpty(),
                version = args.string("version")?.takeIf(String::isNotBlank) ?: "0.1.0",
                instructions = args.string("instructions").orEmpty(),
                allowedTools = args.stringList("allowedTools"),
                requirements = args.stringList("requirements"),
            )
            val skill = aiSkillGateway.createDraft(
                draft = draft,
                availableTools = toolDefinitions.mapTo(hashSetOf(), AiToolDefinition::name),
            )
            GSON.toJson(
                mapOf(
                    "created" to true,
                    "skill" to skill.toToolMap(),
                    "enabled" to false,
                    "nextStep" to "Review the draft, then activate its version and enable the skill after user confirmation.",
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Agent skill draft failed", error)
        }
    }

    private suspend fun setAgentSkillEnabled(args: JsonObject): String {
        return try {
            val skillId = args.string("skillId")?.trim().orEmpty()
            if (skillId.isBlank()) return """{"error":"skillId is required"}"""
            val skill = aiSkillGateway.setEnabled(
                skillId = skillId,
                enabled = args.boolean("enabled", false),
            )
            GSON.toJson(mapOf("updated" to true, "skill" to skill.toToolMap()))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Agent skill state update failed", error)
        }
    }

    private suspend fun activateAgentSkillVersion(args: JsonObject): String {
        return try {
            val skillId = args.string("skillId")?.trim().orEmpty()
            val versionId = args.string("versionId")?.trim().orEmpty()
            if (skillId.isBlank() || versionId.isBlank()) {
                return """{"error":"skillId and versionId are required"}"""
            }
            val skill = aiSkillGateway.activateVersion(skillId, versionId)
            GSON.toJson(mapOf("activated" to true, "skill" to skill.toToolMap()))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Agent skill activation failed", error)
        }
    }

    private suspend fun rollbackAgentSkill(args: JsonObject): String {
        return try {
            val skillId = args.string("skillId")?.trim().orEmpty()
            if (skillId.isBlank()) return """{"error":"skillId is required"}"""
            val skill = aiSkillGateway.rollback(skillId)
            GSON.toJson(mapOf("rolledBack" to true, "skill" to skill.toToolMap()))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toolError("Agent skill rollback failed", error)
        }
    }

    private fun getBookDetail(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        return GSON.toJson(
            book.toSummaryMap() + mapOf(
                "bookUrl" to book.bookUrl,
                "kind" to book.kind,
                "intro" to book.getDisplayIntro(),
                "remark" to book.remark,
                "latestChapterTitle" to book.latestChapterTitle,
                "lastCheckCount" to book.lastCheckCount,
                "canUpdate" to book.canUpdate
            )
        )
    }

    private fun listBookChapters(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        val start = args.int("start", 0).coerceAtLeast(0)
        val limit = args.int("limit", 20).coerceIn(1, 80)
        val chapters = if (query.isBlank()) {
            bookChapterDao.getChapterList(book.bookUrl, start, start + limit - 1)
        } else {
            bookChapterDao.search(book.bookUrl, query).drop(start).take(limit)
        }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "chapters" to chapters.map {
                    mapOf(
                        "index" to it.index,
                        "title" to it.title,
                        "isVolume" to it.isVolume,
                        "wordCount" to it.wordCount,
                        "tag" to it.tag
                    )
                }
            )
        )
    }

    private fun getChapterContent(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val chapterIndex = args.int("chapterIndex", book.durChapterIndex).coerceAtLeast(0)
        val maxChars = args.int("maxChars", 6000).coerceIn(500, 12000)
        val chapter = bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            ?: return """{"error":"Chapter not found"}"""
        val rawContent = BookHelp.getContent(book, chapter)
            ?: return """{"error":"Chapter content is not cached locally"}"""
        val content = ContentProcessor.get(book.name, book.origin)
            .getContent(book, chapter, rawContent, includeTitle = false)
            .toString()
            .take(maxChars)
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "chapter" to mapOf("index" to chapter.index, "title" to chapter.title),
                "truncated" to (content.length < rawContent.length),
                "content" to content
            )
        )
    }

    private fun getChapterWindow(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val centerChapterIndex = args.int("chapterIndex", book.durChapterIndex).coerceAtLeast(0)
        val before = args.int("before", 1).coerceIn(0, 5)
        val after = args.int("after", 1).coerceIn(0, 5)
        val maxCharsPerChapter = args.int("maxCharsPerChapter", 2500).coerceIn(300, 6000)
        val start = (centerChapterIndex - before).coerceAtLeast(0)
        val end = centerChapterIndex + after
        val chapters = bookChapterDao.getChapterList(book.bookUrl, start, end)
            .map { chapter ->
                val rawContent = BookHelp.getContent(book, chapter)
                val processedContent = rawContent?.let {
                    ContentProcessor.get(book.name, book.origin)
                        .getContent(book, chapter, it, includeTitle = false)
                        .toString()
                }
                mapOf(
                    "index" to chapter.index,
                    "title" to chapter.title,
                    "isCenter" to (chapter.index == centerChapterIndex),
                    "isCached" to (processedContent != null),
                    "truncated" to ((processedContent?.length ?: 0) > maxCharsPerChapter),
                    "content" to processedContent.orEmpty().take(maxCharsPerChapter)
                )
            }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "centerChapterIndex" to centerChapterIndex,
                "chapters" to chapters
            )
        )
    }

    private fun searchChapterContent(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        if (query.isBlank()) return """{"error":"query is required"}"""
        val aroundChapterIndex = args.int("aroundChapterIndex", book.durChapterIndex)
        val limit = args.int("limit", 6).coerceIn(1, 20)
        val maxChars = args.int("maxChars", 800).coerceIn(200, 2000)
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
            .sortedWith(
                compareBy<io.legado.app.data.entities.BookChapter> {
                    kotlin.math.abs(it.index - aroundChapterIndex)
                }.thenBy { it.index }
            )
        val matches = mutableListOf<Map<String, Any?>>()
        for (chapter in chapters) {
            if (matches.size >= limit) break
            val rawContent = BookHelp.getContent(book, chapter) ?: continue
            val content = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, rawContent, includeTitle = false)
                .toString()
            val titleMatch = chapter.title.contains(query, ignoreCase = true)
            val contentIndex = content.indexOf(query, ignoreCase = true)
            if (!titleMatch && contentIndex < 0) continue
            val excerpt = if (contentIndex >= 0) {
                content.excerptAround(contentIndex, maxChars)
            } else {
                content.take(maxChars)
            }
            matches += mapOf(
                "index" to chapter.index,
                "title" to chapter.title,
                "matchedTitle" to titleMatch,
                "excerpt" to excerpt,
                "truncated" to (excerpt.length < content.length)
            )
        }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "query" to query,
                "aroundChapterIndex" to aroundChapterIndex,
                "matches" to matches
            )
        )
    }

    private fun searchBookmarks(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 10).coerceIn(1, 30)
        val bookName = args.string("bookName")?.trim().orEmpty()
        val bookAuthor = args.string("bookAuthor")?.trim().orEmpty()
        val bookmarks = bookmarkDao.all
            .asSequence()
            .filter { bookmark ->
                (bookName.isBlank() || bookmark.bookName.equals(bookName, ignoreCase = true)) &&
                    (bookAuthor.isBlank() || bookmark.bookAuthor.equals(bookAuthor, ignoreCase = true)) &&
                    (query.isBlank() ||
                        bookmark.bookName.contains(query, ignoreCase = true) ||
                        bookmark.bookAuthor.contains(query, ignoreCase = true) ||
                        bookmark.chapterName.contains(query, ignoreCase = true) ||
                        bookmark.content.contains(query, ignoreCase = true) ||
                        bookmark.bookText.contains(query, ignoreCase = true))
            }
            .sortedWith(compareBy({ it.bookName }, { it.chapterIndex }, { it.chapterPos }))
            .take(limit)
            .map {
                mapOf(
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "chapterIndex" to it.chapterIndex,
                    "chapterName" to it.chapterName,
                    "chapterPos" to it.chapterPos,
                    "note" to it.content,
                    "text" to it.bookText.take(500),
                    "time" to it.time
                )
            }
            .toList()
        return GSON.toJson(mapOf("bookmarks" to bookmarks))
    }

    private suspend fun listAuthoringProjects(args: JsonObject): String {
        val requestedKind = args.string("kind")?.trim()?.uppercase()
        val kinds = if (requestedKind.isNullOrBlank()) {
            AuthoringProjectKind.entries
        } else {
            listOfNotNull(runCatching { AuthoringProjectKind.valueOf(requestedKind) }.getOrNull())
        }
        if (kinds.isEmpty()) return """{"error":"kind must be WRITING or EBOOK_EDITOR"}"""
        val projects = kinds.flatMap { kind -> authoringProjectGateway.observeProjects(kind).first() }
            .sortedByDescending(AuthoringProject::updatedAt)
            .take(args.int("limit", 50).coerceIn(1, 200))
        return GSON.toJson(mapOf("projects" to projects.map { it.toToolSummary() }))
    }

    private suspend fun getAuthoringProject(args: JsonObject): String {
        val id = args.string("id")?.trim().orEmpty()
        if (id.isBlank()) return """{"error":"id is required"}"""
        val project = authoringProjectGateway.getProject(id)
            ?: return """{"error":"Authoring project not found"}"""
        return GSON.toJson(mapOf("project" to project))
    }

    private suspend fun saveAuthoringProject(args: JsonObject): String {
        val json = args.getAsJsonObject("project")
            ?: return """{"error":"project object is required"}"""
        val project = runCatching { GSON.fromJson(json, AuthoringProject::class.java) }
            .getOrElse { return toolError("Invalid authoring project", it) }
        if (project.id.isBlank() || project.title.isBlank()) {
            return """{"error":"project id and title are required"}"""
        }
        val now = System.currentTimeMillis()
        val stored = project.copy(
            createdAt = project.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
        authoringProjectGateway.saveProject(stored)
        return GSON.toJson(mapOf("saved" to true, "project" to stored.toToolSummary()))
    }

    private suspend fun deleteAuthoringProject(args: JsonObject): String {
        val id = args.string("id")?.trim().orEmpty()
        if (id.isBlank()) return """{"error":"id is required"}"""
        val existing = authoringProjectGateway.getProject(id)
            ?: return """{"error":"Authoring project not found"}"""
        authoringProjectGateway.deleteProject(id)
        return GSON.toJson(mapOf("deleted" to true, "project" to existing.toToolSummary()))
    }

    private fun getReadingStats(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val date = args.string("date")?.trim().orEmpty()
        val limit = args.int("limit", 10).coerceIn(1, 30)
        val records = readRecordDao.all
            .asSequence()
            .filter {
                query.isBlank() ||
                    it.bookName.contains(query, ignoreCase = true) ||
                    it.bookAuthor.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.lastRead }
            .take(limit)
            .map {
                mapOf(
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "readTimeMillis" to it.readTime,
                    "lastReadTime" to it.lastRead
                )
            }
            .toList()
        val dailyDetails = readRecordDao.allDetail
            .asSequence()
            .filter {
                (date.isBlank() || it.date == date) &&
                    (query.isBlank() ||
                        it.bookName.contains(query, ignoreCase = true) ||
                        it.bookAuthor.contains(query, ignoreCase = true))
            }
            .sortedWith(compareByDescending<io.legado.app.data.entities.readRecord.ReadRecordDetail> { it.date }
                .thenByDescending { it.lastReadTime })
            .take(limit)
            .map {
                mapOf(
                    "date" to it.date,
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "readTimeMillis" to it.readTime,
                    "readWords" to it.readWords,
                    "firstReadTime" to it.firstReadTime,
                    "lastReadTime" to it.lastReadTime
                )
            }
            .toList()
        return GSON.toJson(
            mapOf(
                "totalReadTimeMillis" to readRecordDao.all.sumOf { it.readTime },
                "recentRecords" to records,
                "dailyDetails" to dailyDetails
            )
        )
    }

    private suspend fun getAiArtifacts(args: JsonObject): String {
        val book = resolveBook(args)
        val taskType = args.string("taskType")?.trim()?.takeIf { it.isNotBlank() }
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val limit = args.int("limit", 8).coerceIn(1, 30)
        val artifacts = aiArtifactDao.queryArtifacts(
            bookUrl = book?.bookUrl,
            taskType = taskType,
            chapterIndex = chapterIndex,
            limit = limit
        ).map {
            mapOf(
                "id" to it.id,
                "bookUrl" to it.bookUrl,
                "chapterIndex" to it.chapterIndex,
                "taskType" to it.taskType,
                "status" to it.status,
                "modelProfileId" to it.modelProfileId,
                "updatedAt" to it.updatedAt,
                "output" to it.output.orEmpty().take(4000),
                "errorMessage" to it.errorMessage,
                "truncated" to (it.output.orEmpty().length > 4000)
            )
        }
        return GSON.toJson(
            mapOf(
                "book" to book?.toIdentityMap(),
                "artifacts" to artifacts
            )
        )
    }

    private suspend fun saveAiArtifact(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val output = args.string("output")?.trim().orEmpty()
        if (output.isBlank()) return """{"error":"output is required"}"""
        val taskType = args.string("taskType")?.trim()?.takeIf { it.isNotBlank() } ?: "ai_note"
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val now = System.currentTimeMillis()
        val contentHash = MD5Utils.md5Encode(output)
        val promptHash = MD5Utils.md5Encode("tool:$TOOL_SAVE_AI_ARTIFACT:$taskType")
        val artifact = AiArtifact(
            id = "tool_${book.bookUrl}_${chapterIndex ?: "book"}_${taskType}_${contentHash}",
            taskType = taskType,
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = "tool",
            status = AiArtifact.STATUS_SUCCESS,
            output = output,
            createdAt = now,
            updatedAt = now
        )
        aiArtifactDao.upsert(artifact)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "artifactId" to artifact.id,
                "book" to book.toIdentityMap(),
                "taskType" to taskType,
                "chapterIndex" to chapterIndex,
                "updatedAt" to now
            )
        )
    }

    private suspend fun saveMemory(args: JsonObject): String {
        val key = args.string("key")?.trim().orEmpty()
        if (key.isBlank()) return """{"error":"key is required"}"""
        val value = args.string("value")?.trim().orEmpty()
        if (value.isBlank()) return """{"error":"value is required"}"""
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        val scope = args.memoryScope()
        val scopeId = args.memoryScopeId(scope, conversationId)
        if (scope != AiMemory.SCOPE_GLOBAL && scopeId.isBlank()) {
            return """{"error":"scopeId or conversationId is required for scoped memory"}"""
        }
        val primaryConversationId = memoryPrimaryConversationId(scope, scopeId)
        aiMemoryGateway.upsert(
            AiMemory(
                conversationId = primaryConversationId,
                key = key,
                value = value,
                scope = scope,
                scopeId = scopeId,
                type = args.memoryType(),
                sourceConversationId = args.string("sourceConversationId")?.trim()?.takeIf { it.isNotBlank() }
                    ?: conversationId.takeIf { it.isNotBlank() },
                sourceMessageId = args.string("sourceMessageId")?.trim()?.takeIf { it.isNotBlank() },
                confidence = args.double("confidence", 1.0).coerceIn(0.0, 1.0),
                pinned = args.boolean("pinned", false),
            )
        )
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "key" to key,
                "scope" to scope,
                "scopeId" to scopeId,
            )
        )
    }

    private suspend fun recallMemory(args: JsonObject): String {
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        val explicitScope = args.string("scope")?.trim()?.takeIf { it.isNotBlank() }
        val query = args.string("query")?.trim().orEmpty()
        val limit = args.int("limit", DEFAULT_MEMORY_SEARCH_LIMIT)
            .coerceIn(1, MAX_MEMORY_SEARCH_LIMIT)
        val memories = when {
            query.isNotBlank() && explicitScope != null -> {
                val scope = args.memoryScope()
                aiMemoryGateway.search(
                    query = query,
                    scope = scope,
                    scopeId = args.memoryScopeId(scope, conversationId),
                    limit = limit,
                )
            }
            query.isNotBlank() -> aiMemoryGateway.searchForPrompt(
                query = query,
                conversationId = conversationId,
                limit = limit,
            )
            explicitScope != null -> {
                val scope = args.memoryScope()
                aiMemoryGateway.getByScope(scope, args.memoryScopeId(scope, conversationId))
            }
            else -> aiMemoryGateway.getForPrompt(conversationId)
        }
        return GSON.toJson(
            mapOf(
                "query" to query.takeIf(String::isNotBlank),
                "memories" to memories.map { it.toToolMap() }
            )
        )
    }

    private suspend fun deleteMemory(args: JsonObject): String {
        val key = args.string("key")?.trim().orEmpty()
        if (key.isBlank()) return """{"error":"key is required"}"""
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        val scope = args.memoryScope()
        val scopeId = args.memoryScopeId(scope, conversationId)
        aiMemoryGateway.delete(memoryPrimaryConversationId(scope, scopeId), key)
        return GSON.toJson(mapOf("deleted" to true, "key" to key))
    }

    private suspend fun updateBook(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        if (!book.canUpdate) return """{"error":"Book does not allow source updates"}"""
        val oldChapterCount = book.totalChapterNum.coerceAtLeast(0)
        var updatedBook: Book? = null
        val result = refreshTocUseCase.execute(book.bookUrl) { _, refreshed ->
            updatedBook = refreshed
        }
        result.exceptionOrNull()?.let { error ->
            return GSON.toJson(mapOf("updated" to false, "error" to (error.message ?: "Update failed")))
        }
        val refreshed = updatedBook ?: return """{"error":"Updated book was not returned"}"""
        val newChapterCount = refreshed.totalChapterNum.coerceAtLeast(0)
        val newChapters = (newChapterCount - oldChapterCount).coerceAtLeast(0)
        val downloadNew = args.boolean("downloadNew", false)
        if (downloadNew && newChapters > 0) {
            bookCacheDownloadGateway.start(
                CacheDownloadRequest(
                    bookUrl = refreshed.bookUrl,
                    selection = ChapterSelection.Range(oldChapterCount, newChapterCount - 1),
                    source = CacheDownloadSource.Batch,
                )
            )
        }
        return GSON.toJson(
            mapOf(
                "updated" to true,
                "book" to refreshed.toSummaryMap(),
                "oldChapterCount" to oldChapterCount,
                "newChapterCount" to newChapterCount,
                "newChapters" to newChapters,
                "downloadQueued" to (downloadNew && newChapters > 0),
            )
        )
    }

    private suspend fun downloadBookChapters(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val lastChapterIndex = (book.totalChapterNum - 1).coerceAtLeast(0)
        val startIndex = args.int("startIndex", book.durChapterIndex)
            .coerceIn(0, lastChapterIndex)
        val endIndex = args.int("endIndex", lastChapterIndex)
            .coerceIn(startIndex, lastChapterIndex)
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = book.bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
                source = CacheDownloadSource.Batch,
            )
        )
        return GSON.toJson(
            mapOf(
                "queued" to true,
                "book" to book.toIdentityMap(),
                "startIndex" to startIndex,
                "endIndex" to endIndex,
                "chapterCount" to (endIndex - startIndex + 1),
            )
        )
    }

    private fun getDownloadStatus(args: JsonObject): String {
        val book = resolveBook(args)
        val state = CacheBook.downloadStateFlow.value
        val selected = book?.bookUrl?.let(state.books::get)
        return GSON.toJson(
            mapOf(
                "running" to state.isRunning,
                "totalWaiting" to state.totalWaiting,
                "totalRunning" to state.totalRunning,
                "totalPaused" to state.totalPaused,
                "totalSuccess" to state.totalSuccess,
                "totalFailure" to state.totalFailure,
                "book" to book?.toIdentityMap(),
                "bookStatus" to selected,
            )
        )
    }

    private fun listBookDictionaryTerms(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book identity is required"}"""
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 40).coerceIn(1, 200)
        val dictionary = dictionaryGateway.getBookDictionaries(book)
        val pairs = dictionary.pairs
            .asSequence()
            .filter { pair ->
                query.isBlank() ||
                    pair.original.contains(query, ignoreCase = true) ||
                    pair.translation.contains(query, ignoreCase = true)
            }
            .take(limit)
            .map { it.toToolMap() }
            .toList()
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "updatedAt" to dictionary.updatedAt,
                "terms" to pairs,
            )
        )
    }

    private fun saveBookDictionaryTerm(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book identity is required"}"""
        val original = args.string("original")?.trim().orEmpty()
        val translation = args.string("translation")?.trim().orEmpty()
        if (original.isBlank()) return """{"error":"original is required"}"""
        if (translation.isBlank()) return """{"error":"translation is required"}"""
        val existing = dictionaryGateway.getBookDictionaries(book)
        val updatedPairs = existing.pairs
            .filterNot { it.original == original }
            .plus(DictPair(original = original, translation = translation))
        dictionaryGateway.replaceBookDictionary(book, updatedPairs)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "term" to DictPair(original, translation).toToolMap(),
            )
        )
    }

    private fun deleteBookDictionaryTerm(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book identity is required"}"""
        val original = args.string("original")?.trim().orEmpty()
        if (original.isBlank()) return """{"error":"original is required"}"""
        val existing = dictionaryGateway.getBookDictionaries(book)
        val updatedPairs = existing.pairs.filterNot { it.original == original }
        if (updatedPairs.size == existing.pairs.size) {
            return GSON.toJson(
                mapOf("deleted" to false, "book" to book.toIdentityMap(), "original" to original)
            )
        }
        dictionaryGateway.replaceBookDictionary(book, updatedPairs)
        return GSON.toJson(
            mapOf("deleted" to true, "book" to book.toIdentityMap(), "original" to original)
        )
    }

    private fun clearBookDictionary(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book identity is required"}"""
        dictionaryGateway.clearBookDictionary(book)
        return GSON.toJson(mapOf("cleared" to true, "book" to book.toIdentityMap()))
    }

    private suspend fun listDictionaryEntries(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 20).coerceIn(1, 100)
        val book = resolveBook(args)
        val entries = quickDictionaryGateway.observeEntries().first()
            .asSequence()
            .filter { entry ->
                query.isBlank() ||
                    entry.raw.contains(query, ignoreCase = true) ||
                    entry.target.contains(query, ignoreCase = true) ||
                    entry.hanViet.contains(query, ignoreCase = true)
            }
            .filter { entry ->
                book == null || entry.scope != QuickDictionaryScope.PROJECT ||
                    entry.scopeKey == book.bookUrl
            }
            .take(limit)
            .map { it.toToolMap() }
            .toList()
        return GSON.toJson(mapOf("entries" to entries, "book" to book?.toIdentityMap()))
    }

    private suspend fun saveDictionaryEntry(args: JsonObject): String {
        val id = args.string("id")?.toLongOrNull() ?: 0L
        val raw = args.string("raw")?.trim().orEmpty()
        if (raw.isBlank()) return """{"error":"raw is required"}"""
        val type = args.enumValue<QuickDictionaryType>("type") ?: QuickDictionaryType.VIETPHRASE
        val scope = args.enumValue<QuickDictionaryScope>("scope") ?: QuickDictionaryScope.PROJECT
        val book = resolveBook(args)
        val scopeKey = when (scope) {
            QuickDictionaryScope.GLOBAL -> ""
            QuickDictionaryScope.PROJECT -> args.string("scopeKey")?.trim()
                ?.takeIf(String::isNotBlank) ?: book?.bookUrl.orEmpty()
            QuickDictionaryScope.UNIVERSE -> args.string("scopeKey")?.trim().orEmpty()
        }
        if (scope != QuickDictionaryScope.GLOBAL && scopeKey.isBlank()) {
            return """{"error":"scopeKey or book identity is required"}"""
        }
        val existing = quickDictionaryGateway.observeEntries().first().firstOrNull { entry ->
            entry.raw.equals(raw, ignoreCase = true) &&
                entry.type == type && entry.scope == scope && entry.scopeKey == scopeKey
        }
        if (id <= 0 && existing != null) {
            return GSON.toJson(
                mapOf("saved" to false, "duplicate" to true, "entry" to existing.toToolMap())
            )
        }
        val entry = QuickDictionaryEntry(
            id = id,
            raw = raw,
            hanViet = args.string("hanViet")?.trim().orEmpty(),
            target = args.string("target")?.trim().orEmpty(),
            type = type,
            scope = scope,
            scopeKey = scopeKey,
            enabled = args.boolean("enabled", true),
        )
        quickDictionaryGateway.save(entry)
        return GSON.toJson(mapOf("saved" to true, "entry" to entry.toToolMap()))
    }

    private suspend fun deleteDictionaryEntry(args: JsonObject): String {
        val id = args.string("id")?.toLongOrNull() ?: 0L
        if (id <= 0) return """{"error":"A positive user dictionary entry id is required"}"""
        quickDictionaryGateway.deleteEntry(id)
        return GSON.toJson(mapOf("deleted" to true, "id" to id))
    }

    private fun getBookshelfAutomation(): String = GSON.toJson(
        mapOf(
            "enabled" to BookshelfAutomationConfig.enabled,
            "intervalHours" to BookshelfAutomationConfig.intervalHours,
            "autoDownloadNewChapters" to BookshelfAutomationConfig.autoDownloadNewChapters,
            "notifyNewChapters" to BookshelfAutomationConfig.notifyNewChapters,
            "lastCheckAt" to BookshelfAutomationConfig.lastCheckAt,
            "lastUpdatedBookCount" to BookshelfAutomationConfig.lastUpdatedBookCount,
            "lastNewChapterCount" to BookshelfAutomationConfig.lastNewChapterCount,
        )
    )

    private fun setBookshelfAutomation(args: JsonObject): String {
        args.booleanOrNull("enabled")?.let { BookshelfAutomationConfig.enabled = it }
        args.string("intervalHours")?.toIntOrNull()?.let {
            BookshelfAutomationConfig.intervalHours = it
        }
        args.booleanOrNull("autoDownloadNewChapters")?.let {
            BookshelfAutomationConfig.autoDownloadNewChapters = it
        }
        args.booleanOrNull("notifyNewChapters")?.let {
            BookshelfAutomationConfig.notifyNewChapters = it
        }
        BookshelfAutomationScheduler.applyConfig(appCtx)
        return getBookshelfAutomation()
    }

    private fun buildBookSearchScope(args: JsonObject, sourceLimit: Int): BookSearchScope {
        val sourceUrl = args.string("sourceUrl")?.trim()?.takeIf(String::isNotBlank)
        if (sourceUrl != null) {
            val sourceName = bookSourceDao.getBookSource(sourceUrl)?.bookSourceName.orEmpty()
            return BookSearchScope(
                BookSearchScope.encodeSources(
                    listOf(BookSearchScope.ScopeSourceItem(sourceName, sourceUrl))
                )
            )
        }
        val sourceQuery = args.string("sourceQuery")?.trim()?.takeIf(String::isNotBlank)
        if (sourceQuery != null) {
            val sources = bookSourceDao.search(sourceQuery)
                .asSequence()
                .filter { it.enabled && !it.searchUrl.isNullOrBlank() }
                .take(sourceLimit)
                .map { BookSearchScope.ScopeSourceItem(it.bookSourceName, it.bookSourceUrl) }
                .toList()
            return BookSearchScope(BookSearchScope.encodeSources(sources))
        }
        val sourceGroup = args.string("sourceGroup")?.trim()?.takeIf(String::isNotBlank)
        if (sourceGroup != null) {
            return BookSearchScope(BookSearchScope.encodeGroups(listOf(sourceGroup)))
        }
        val sources = bookSourceDao.allEnabledPart
            .asSequence()
            .take(sourceLimit)
            .map { BookSearchScope.ScopeSourceItem(it.bookSourceName, it.bookSourceUrl) }
            .toList()
        return BookSearchScope(BookSearchScope.encodeSources(sources))
    }

    private fun normalizeDuckDuckGoUrl(rawHref: String): String {
        val raw = rawHref.trim()
        if (raw.isBlank()) return ""
        val absolute = when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://duckduckgo.com$raw"
            else -> raw
        }
        val uri = runCatching { Uri.parse(absolute) }.getOrNull() ?: return absolute
        uri.getQueryParameter("uddg")?.takeIf(String::isNotBlank)?.let { return it }
        val uddg = uri.encodedQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("uddg=") }
            ?.removePrefix("uddg=")
        return if (uddg.isNullOrBlank()) {
            absolute
        } else {
            URLDecoder.decode(uddg, StandardCharsets.UTF_8.name())
        }
    }

    private fun pluginDraftRoot(): File =
        File(appCtx.filesDir, "vbook_plugin_drafts").apply { mkdirs() }

    private fun legadoSourceDraftRoot(): File =
        File(appCtx.filesDir, "legado_source_drafts").apply { mkdirs() }

    private fun readLegadoSourceDraft(draftId: String): String {
        require(SAFE_LEGADO_SOURCE_DRAFT_ID.matches(draftId)) {
            "Invalid Legado source draft id"
        }
        val draftsRoot = legadoSourceDraftRoot().canonicalFile
        val draftFile = File(File(draftsRoot, draftId), LEGADO_SOURCE_DRAFT_FILE).canonicalFile
        require(draftFile.path.startsWith(draftsRoot.path + File.separator) && draftFile.isFile) {
            "Legado source draft not found"
        }
        return draftFile.readText()
    }

    private fun JsonObject.pluginDraftFiles(): List<Pair<String, String>> {
        val files = get("files")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return files.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val rawPath = item.string("path")?.trim()
            rawPath?.let {
                require(!isUnsafeAgentVbookPluginPath(it)) {
                    "Unsafe plugin script path: $it"
                }
            }
            val name = item.string("name")?.trim()?.takeIf(String::isNotBlank)
                ?: rawPath?.substringAfterLast('/')?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val content = item.string("content") ?: return@mapNotNull null
            name to content
        }
    }

    private fun JsonObject.pluginScriptMap(fileNames: Set<String>): Map<String, String> {
        val explicit = listOfNotNull(
            get("script")?.takeIf { it.isJsonObject }?.asJsonObject,
            get("scripts")?.takeIf { it.isJsonObject }?.asJsonObject,
        ).firstOrNull()
        val mapped = linkedMapOf<String, String>()
        explicit?.entrySet()?.forEach { (role, value) ->
            val normalizedRole = role.trim().lowercase()
            val scriptName = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.trim()
                ?.substringAfterLast('/')
                .orEmpty()
            require(SAFE_PLUGIN_SCRIPT_ROLE.matches(normalizedRole)) {
                "Unsafe plugin script role: $role"
            }
            require(SAFE_PLUGIN_SCRIPT_NAME.matches(scriptName)) {
                "Unsafe plugin script name: $scriptName"
            }
            require(scriptName in fileNames) {
                "script.$normalizedRole points to a missing file: $scriptName"
            }
            mapped[normalizedRole] = scriptName
        }
        if (mapped.isNotEmpty()) return mapped

        fileNames.forEach { fileName ->
            val role = fileName.substringBeforeLast('.').lowercase()
            if (role in VBOOK_SCRIPT_ROLES) mapped[role] = fileName
        }
        if ("search.js" in fileNames && "search" !in mapped) {
            mapped["search"] = "search.js"
        }
        return mapped
    }

    private fun zipPluginDraft(draftId: String): File {
        require(SAFE_PLUGIN_DRAFT_ID.matches(draftId)) { "Invalid plugin draft id" }
        val root = File(pluginDraftRoot(), draftId).canonicalFile
        val draftsRoot = pluginDraftRoot().canonicalFile
        require(root.isDirectory && root.path.startsWith(draftsRoot.path + File.separator)) {
            "Plugin draft not found"
        }
        require(File(root, "plugin.json").isFile) { "Plugin draft has no plugin.json" }
        val zipFile = File(appCtx.cacheDir, "$draftId.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().buffered().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
        }
        return zipFile
    }

    private fun resolveBook(args: JsonObject): Book? {
        args.string("bookUrl")?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.let { return it }
        }
        val name = args.string("bookName")?.trim().orEmpty()
        val author = args.string("bookAuthor")?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.let { return it }
        }
        if (name.isNotBlank()) {
            return bookDao.findByName(name).firstOrNull()
        }
        return bookDao.lastReadBook
    }

    private fun Book.toIdentityMap(): Map<String, Any?> {
        return mapOf(
            "bookUrl" to bookUrl,
            "name" to name,
            "author" to author
        )
    }

    private fun Book.toSummaryMap(): Map<String, Any?> {
        return toIdentityMap() + mapOf(
            "originName" to originName,
            "currentChapterIndex" to durChapterIndex,
            "currentChapterTitle" to durChapterTitle,
            "totalChapterNum" to totalChapterNum,
            "wordCount" to wordCount,
            "lastReadTime" to durChapterTime
        )
    }

    private fun BookSource.toToolMap(): Map<String, Any?> = mapOf(
        "sourceUrl" to bookSourceUrl,
        "name" to bookSourceName,
        "group" to bookSourceGroup,
        "type" to bookSourceType,
        "enabled" to enabled,
        "enabledExplore" to enabledExplore,
        "hasSearch" to !searchUrl.isNullOrBlank(),
        "hasExplore" to !exploreUrl.isNullOrBlank(),
        "hasLogin" to !loginUrl.isNullOrBlank(),
        "comment" to bookSourceComment.orEmpty().take(500),
    )

    private fun AiTaskPresetConfig.toAiPresetToolMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "taskType" to taskType,
        "name" to name,
        "routeProfileId" to runtimeOptions.routeProfileId,
        "model" to mapOf(
            "profileId" to model.id,
            "displayName" to model.displayName,
            "modelId" to model.modelId,
            "contextWindow" to model.contextWindow,
            "maxOutputTokens" to model.maxOutputTokens,
            "capabilities" to model.capabilities.sorted(),
        ),
        "provider" to mapOf(
            "id" to model.provider.id,
            "name" to model.provider.name,
            "protocol" to model.provider.protocol,
            "baseUrl" to model.provider.baseUrl,
            "authType" to model.provider.authType,
            "hasApiKey" to model.provider.apiKey.isNotBlank(),
        ),
        "params" to mapOf(
            "temperature" to params.temperature,
            "topP" to params.topP,
            "topK" to params.topK,
            "maxOutputTokens" to params.maxOutputTokens,
            "reasoningLevel" to params.reasoningLevel,
        ),
    )

    private fun SourceCheckRun.toToolMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "sourceUrl" to sourceUrl,
        "sourceName" to sourceName,
        "profile" to profile.name,
        "status" to status.name,
        "healthStatus" to healthStatus.name,
        "latencyMs" to latencyMs,
        "failureStep" to failureStep,
        "message" to messageRedacted,
        "stageCount" to stageCount,
        "passedStageCount" to passedStageCount,
        "failedStageCount" to failedStageCount,
        "skippedStageCount" to skippedStageCount,
    )

    private fun SourceCheckStageResult.toToolMap(): Map<String, Any?> = mapOf(
        "stage" to stageKey,
        "order" to stageOrder,
        "status" to status.name,
        "latencyMs" to latencyMs,
        "httpStatus" to httpStatus,
        "failureStep" to failureStep,
        "message" to messageRedacted,
    )

    private fun resolveBookSource(args: JsonObject): BookSource? {
        args.string("sourceUrl")?.trim()?.takeIf(String::isNotBlank)?.let { sourceUrl ->
            bookSourceDao.getBookSource(sourceUrl)?.let { return it }
        }
        val query = listOf(
            args.string("sourceName"),
            args.string("query"),
        ).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotBlank) } ?: return null
        val matches = bookSourceDao.search(query)
        return matches.firstOrNull { it.bookSourceName.equals(query, ignoreCase = true) }
            ?: matches.firstOrNull { it.bookSourceUrl.equals(query, ignoreCase = true) }
            ?: matches.firstOrNull()
    }

    private fun repairHints(
        source: BookSource,
        run: SourceCheckRun,
        stages: List<SourceCheckStageResult>,
    ): List<String> = buildList {
        if (source.isVbookInstalledSource()) {
            add("For VBook sources, run repair_book_source to reconcile source type and vbook:// rule placeholders before rewriting scripts.")
        }
        val failedStage = stages
            .filter { it.status == SourceCheckStageStatus.FAILED }
            .minWithOrNull(compareBy<SourceCheckStageResult> { it.stageOrder }.thenBy { it.stageKey })
        when (failedStage?.stageKey ?: run.failureStep) {
            "search" -> add("Search failed: inspect search.js or search rule input/output. The result must return books with title/name and URL.")
            "explore" -> add("Explore failed: inspect home/gen/genre scripts or explore rules. Ensure each category returns a usable URL.")
            "detail" -> add("Detail failed: inspect detail.js or ruleBookInfo, especially bookUrl, name, tocUrl and cover fields.")
            "toc" -> add("Chapter list failed: inspect toc.js or ruleToc. For comics, preserve raw chapter input and return chapter URLs with url/href/link/chapter_id/slug aliases.")
            "content" -> add("Content failed: inspect chap.js or ruleContent. For comics, return image URLs or objects with url/src/image/link/fallback fields.")
            "media" -> add("Media failed: inspect chap.js/track.js and return playable HLS/MP4/audio variants, not only an iframe page.")
            "engine" -> add("The check engine failed before a source stage completed; review the message field for timeout or runtime errors.")
            else -> Unit
        }
        if (isEmpty()) {
            add("No automatic repair hint was needed. If the source still behaves incorrectly, run FULL diagnosis and inspect the failing script output.")
        }
    }

    private fun BookSource.isVbookInstalledSource(): Boolean =
        bookSourceUrl.startsWith(VbookPluginAdapter.SOURCE_PREFIX)

    private fun BookSourcePart.toToolMap(): Map<String, Any?> = mapOf(
        "sourceUrl" to bookSourceUrl,
        "name" to bookSourceName,
        "group" to bookSourceGroup,
        "enabled" to enabled,
        "enabledExplore" to enabledExplore,
        "hasExplore" to hasExploreUrl,
        "hasLogin" to hasLoginUrl,
    )

    private fun SearchBook.toToolMap(): Map<String, Any?> = mapOf(
        "bookUrl" to bookUrl,
        "name" to name,
        "author" to author,
        "origin" to origin,
        "originName" to originName,
        "kind" to kind,
        "type" to type,
        "wordCount" to wordCount,
        "latestChapterTitle" to latestChapterTitle,
        "intro" to intro.orEmpty().take(800),
        "coverUrl" to coverUrl,
        "originCount" to origins.size,
    )

    private fun JsonObject.string(name: String): String? {
        return get(name)?.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.stringList(name: String): List<String> {
        val array = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            runCatching { item.asString.trim() }.getOrNull()?.takeIf(String::isNotBlank)
        }.distinct()
    }

    private fun JsonObject.int(name: String, defaultValue: Int): Int {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()
    }

    private fun JsonObject.boolean(name: String, defaultValue: Boolean): Boolean {
        return booleanOrNull(name) ?: defaultValue
    }

    private fun JsonObject.booleanOrNull(name: String): Boolean? {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
    }

    private fun JsonObject.aiTaskType(defaultValue: String): String {
        return when (string("taskType")?.trim()?.lowercase()) {
            AiTaskType.CHAT -> AiTaskType.CHAT
            AiTaskType.TRANSLATE_CHAPTER -> AiTaskType.TRANSLATE_CHAPTER
            AiTaskType.SUMMARIZE_CHAPTER -> AiTaskType.SUMMARIZE_CHAPTER
            AiTaskType.SUMMARIZE_BOOK -> AiTaskType.SUMMARIZE_BOOK
            AiTaskType.EXPLAIN_SELECTION -> AiTaskType.EXPLAIN_SELECTION
            AiTaskType.CLEAN_SELECTION -> AiTaskType.CLEAN_SELECTION
            AiTaskType.TEXT_FACTORY -> AiTaskType.TEXT_FACTORY
            AiTaskType.REWRITE_TEXT -> AiTaskType.REWRITE_TEXT
            AiTaskType.AUTHORING_DIRECTOR -> AiTaskType.AUTHORING_DIRECTOR
            AiTaskType.AUTHORING_WRITER -> AiTaskType.AUTHORING_WRITER
            else -> defaultValue
        }
    }

    private fun JsonObject.sourceCheckProfile(defaultValue: SourceCheckProfile): SourceCheckProfile {
        return enumValue<SourceCheckProfile>("profile") ?: defaultValue
    }

    private fun JsonObject.double(name: String, defaultValue: Double): Double {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asDouble }.getOrNull()
            ?: defaultValue
    }

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String): T? {
        val value = string(name)?.trim()?.uppercase() ?: return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private fun JsonObject.memoryScope(): String {
        return when (string("scope")?.trim()?.lowercase()) {
            AiMemory.SCOPE_GLOBAL -> AiMemory.SCOPE_GLOBAL
            AiMemory.SCOPE_BOOK -> AiMemory.SCOPE_BOOK
            AiMemory.SCOPE_WRITING_PROJECT -> AiMemory.SCOPE_WRITING_PROJECT
            AiMemory.SCOPE_EBOOK_PROJECT -> AiMemory.SCOPE_EBOOK_PROJECT
            else -> AiMemory.scopeFromConversation(string("conversationId").orEmpty())
        }
    }

    private fun JsonObject.memoryScopeId(scope: String, conversationId: String): String {
        return when (scope) {
            AiMemory.SCOPE_GLOBAL -> ""
            AiMemory.SCOPE_CONVERSATION -> string("scopeId")?.trim()?.takeIf { it.isNotBlank() }
                ?: conversationId
            AiMemory.SCOPE_BOOK -> string("bookUrl")?.trim()?.takeIf { it.isNotBlank() }
                ?: string("scopeId")?.trim().orEmpty()
            else -> string("projectKey")?.trim()?.takeIf { it.isNotBlank() }
                ?: string("scopeId")?.trim().orEmpty()
        }
    }

    private fun JsonObject.memoryType(): String {
        return when (string("type")?.trim()?.lowercase()) {
            AiMemory.TYPE_PREFERENCE -> AiMemory.TYPE_PREFERENCE
            AiMemory.TYPE_DECISION -> AiMemory.TYPE_DECISION
            AiMemory.TYPE_GLOSSARY -> AiMemory.TYPE_GLOSSARY
            AiMemory.TYPE_RELATIONSHIP -> AiMemory.TYPE_RELATIONSHIP
            AiMemory.TYPE_WORKFLOW_RESULT -> AiMemory.TYPE_WORKFLOW_RESULT
            AiMemory.TYPE_SUMMARY -> AiMemory.TYPE_SUMMARY
            else -> AiMemory.TYPE_FACT
        }
    }

    private fun memoryPrimaryConversationId(scope: String, scopeId: String): String {
        return when (scope) {
            AiMemory.SCOPE_GLOBAL -> ""
            AiMemory.SCOPE_CONVERSATION -> scopeId
            else -> "$scope:$scopeId"
        }
    }

    private fun AiMemory.toToolMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "value" to value,
        "scope" to scope,
        "scopeId" to scopeId,
        "type" to type,
        "confidence" to confidence,
        "pinned" to pinned,
        "updatedAt" to updatedAt,
    )

    private fun QuickDictionaryEntry.toToolMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "raw" to raw,
        "hanViet" to hanViet,
        "target" to target,
        "type" to type.name,
        "scope" to scope.name,
        "scopeKey" to scopeKey,
        "enabled" to enabled,
        "updatedAt" to updatedAt,
    )

    private fun DictPair.toToolMap(): Map<String, Any?> = mapOf(
        "original" to original,
        "translation" to translation,
    )

    private fun AgentSkillSnapshot.toToolMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "slug" to slug,
        "name" to name,
        "description" to description,
        "enabled" to enabled,
        "activeVersionId" to activeVersionId,
        "activeVersion" to activeVersion?.version,
        "latestVersionId" to latestVersion?.id,
        "latestVersion" to latestVersion?.version,
        "latestVersionValid" to latestVersion?.valid,
        "versions" to versions.map { version ->
            mapOf(
                "id" to version.id,
                "version" to version.version,
                "valid" to version.valid,
                "validationMessage" to version.validationMessage,
                "allowedTools" to version.allowedTools,
                "requirements" to version.requirements,
                "createdAt" to version.createdAt,
            )
        },
        "updatedAt" to updatedAt,
    )

    private fun AuthoringProject.toToolSummary(): Map<String, Any?> = mapOf(
        "id" to id,
        "kind" to kind.name,
        "title" to title,
        "author" to author,
        "language" to language,
        "chapterCount" to chapters.size,
        "sourceBookUrl" to sourceBookUrl,
        "updatedAt" to updatedAt,
    )

    private fun String.toJsonObject(): JsonObject {
        return runCatching { GSON.fromJson(this, JsonObject::class.java) }.getOrNull() ?: JsonObject()
    }

    private fun toolError(error: String, throwable: Throwable): String {
        return GSON.toJson(
            mapOf(
                "error" to error,
                "message" to (throwable.message ?: throwable::class.java.simpleName),
            )
        )
    }

    companion object {
        const val TOOL_SEARCH_INTERNET = "search_internet"
        const val TOOL_FETCH_INTERNET_PAGE = "fetch_internet_page"
        const val TOOL_SEARCH_BOOKS = "search_books"
        const val TOOL_SEARCH_BOOK_SOURCES = "search_book_sources"
        const val TOOL_SEARCH_ONLINE_BOOKS = "search_online_books"
        const val TOOL_GET_AI_RUNTIME_STATUS = "get_ai_runtime_status"
        const val TOOL_GET_AI_QUOTA_STATUS = "get_ai_quota_status"
        const val TOOL_DIAGNOSE_BOOK_SOURCE = "diagnose_book_source"
        const val TOOL_REPAIR_BOOK_SOURCE = "repair_book_source"
        const val TOOL_ADD_BOOK_TO_BOOKSHELF = "add_book_to_bookshelf"
        const val TOOL_CREATE_VBOOK_PLUGIN_DRAFT = "create_vbook_plugin_draft"
        const val TOOL_INSTALL_VBOOK_PLUGIN = "install_vbook_plugin"
        const val TOOL_CREATE_LEGADO_BOOK_SOURCE_DRAFT = "create_legado_book_source_draft"
        const val TOOL_INSTALL_LEGADO_BOOK_SOURCE = "install_legado_book_source"
        const val TOOL_LIST_AGENT_SKILLS = "list_agent_skills"
        const val TOOL_CREATE_AGENT_SKILL_DRAFT = "create_agent_skill_draft"
        const val TOOL_SET_AGENT_SKILL_ENABLED = "set_agent_skill_enabled"
        const val TOOL_ACTIVATE_AGENT_SKILL_VERSION = "activate_agent_skill_version"
        const val TOOL_ROLLBACK_AGENT_SKILL = "rollback_agent_skill"
        const val TOOL_GET_BOOK_DETAIL = "get_book_detail"
        const val TOOL_LIST_BOOK_CHAPTERS = "list_book_chapters"
        const val TOOL_GET_CHAPTER_CONTENT = "get_chapter_content"
        const val TOOL_GET_CHAPTER_WINDOW = "get_chapter_window"
        const val TOOL_SEARCH_CHAPTER_CONTENT = "search_chapter_content"
        const val TOOL_SEARCH_BOOKMARKS = "search_bookmarks"
        const val TOOL_LIST_AUTHORING_PROJECTS = "list_authoring_projects"
        const val TOOL_GET_AUTHORING_PROJECT = "get_authoring_project"
        const val TOOL_SAVE_AUTHORING_PROJECT = "save_authoring_project"
        const val TOOL_DELETE_AUTHORING_PROJECT = "delete_authoring_project"
        const val TOOL_GET_READING_STATS = "get_reading_stats"
        const val TOOL_GET_AI_ARTIFACTS = "get_ai_artifacts"
        const val TOOL_SAVE_AI_ARTIFACT = "save_ai_artifact"
        const val TOOL_SAVE_MEMORY = "save_memory"
        const val TOOL_RECALL_MEMORY = "recall_memory"
        const val TOOL_DELETE_MEMORY = "delete_memory"
        const val TOOL_UPDATE_BOOK = "update_book"
        const val TOOL_DOWNLOAD_BOOK_CHAPTERS = "download_book_chapters"
        const val TOOL_GET_DOWNLOAD_STATUS = "get_download_status"
        const val TOOL_LIST_BOOK_DICTIONARY_TERMS = "list_book_dictionary_terms"
        const val TOOL_SAVE_BOOK_DICTIONARY_TERM = "save_book_dictionary_term"
        const val TOOL_DELETE_BOOK_DICTIONARY_TERM = "delete_book_dictionary_term"
        const val TOOL_CLEAR_BOOK_DICTIONARY = "clear_book_dictionary"
        const val TOOL_LIST_DICTIONARY_ENTRIES = "list_dictionary_entries"
        const val TOOL_SAVE_DICTIONARY_ENTRY = "save_dictionary_entry"
        const val TOOL_DELETE_DICTIONARY_ENTRY = "delete_dictionary_entry"
        const val TOOL_GET_BOOKSHELF_AUTOMATION = "get_bookshelf_automation"
        const val TOOL_SET_BOOKSHELF_AUTOMATION = "set_bookshelf_automation"
        const val TOOL_SAVE_BOOK_SOURCE = "save_book_source"
        const val TOOL_TEST_BOOK_SOURCE_RULE = "test_book_source_rule"
        const val TOOL_CREATE_STORY_MEMORY = "create_story_memory"
        const val TOOL_GET_STORY_MEMORY = "get_story_memory"
        const val TOOL_RETROFIT_STORY_TRANSLATIONS = "retrofit_story_translations"
        const val TOOL_GET_STORY_CHRONICLE = "get_story_chronicle"
        const val TOOL_SYNTHESIZE_STORY_CHRONICLE = "synthesize_story_chronicle"
        const val TOOL_SAVE_STORY_CHRONICLE = "save_story_chronicle"
        const val TOOL_UPSERT_STORY_WIKI_ENTITY = "upsert_story_wiki_entity"
        const val TOOL_DELETE_STORY_WIKI_ENTITY = "delete_story_wiki_entity"
        const val TOOL_UPSERT_STORY_WIKI_WORLD = "upsert_story_wiki_world"
        const val TOOL_DELETE_STORY_WIKI_WORLD = "delete_story_wiki_world"
        const val TOOL_UPSERT_STORY_WIKI_RELATIONSHIP = "upsert_story_wiki_relationship"
        const val TOOL_DELETE_STORY_WIKI_RELATIONSHIP = "delete_story_wiki_relationship"
        const val TOOL_GET_STORY_WIKI = "get_story_wiki"
        const val TOOL_GENERATE_CHARACTER_IMAGE = "generate_character_image"
        const val TOOL_GENERATE_BOOK_COVER = "generate_book_cover"
        const val TOOL_GENERATE_WORLD_IMAGE = "generate_world_image"

        internal val toolDefinitions = listOf(
            AiToolDefinition(
                name = TOOL_SEARCH_INTERNET,
                description = "Search the public web for current information. Returns compact title, URL, snippet, and source fields.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Web search query."),
                    "limit" to intSchema("Maximum results, 1 to 10."),
                    "timeoutMs" to intSchema("Optional timeout in milliseconds, 5000 to 45000.")
                )
            ),
            AiToolDefinition(
                name = TOOL_FETCH_INTERNET_PAGE,
                description = "Fetch and extract readable text from a public web page URL returned by search_internet. Blocks local/private network targets and binary content.",
                inputSchema = objectSchema(
                    "url" to stringSchema("Public http/https URL to read."),
                    "maxChars" to intSchema("Maximum extracted text characters, 1000 to 20000."),
                    "timeoutMs" to intSchema("Optional timeout in milliseconds, 5000 to 45000.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_BOOKS,
                description = "Search the local bookshelf by book title, author, or source name.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Search keyword. Leave empty to list recently read books."),
                    "limit" to intSchema("Maximum number of books to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_BOOK_SOURCES,
                description = "Search installed book sources by name, group, URL, or comment. Use before online book search when the user wants a specific source.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Source keyword. Leave empty to list sources."),
                    "limit" to intSchema("Maximum sources, 1 to 100."),
                    "enabledOnly" to boolSchema("Only return enabled sources."),
                    "requireSearch" to boolSchema("Only return sources that support search."),
                    "sourceType" to intSchema("Optional source type: 0 text, 1 audio, 2 image, 3 file, 4 video.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_ONLINE_BOOKS,
                description = "Search online books through installed Legado book sources. Results are cached and can be added with add_book_to_bookshelf.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Book title, author, or keyword."),
                    "sourceUrl" to stringSchema("Optional exact book source URL from search_book_sources."),
                    "sourceQuery" to stringSchema("Optional source keyword to choose a small set of sources."),
                    "sourceGroup" to stringSchema("Optional source group name."),
                    "sourceType" to intSchema("Optional source type: 0 text, 1 audio, 2 image, 3 file, 4 video."),
                    "page" to intSchema("Search page, starting at 1."),
                    "limit" to intSchema("Maximum returned books, 1 to 20."),
                    "sourceLimit" to intSchema("Maximum sources to query when sourceUrl is omitted, 1 to 20."),
                    "concurrency" to intSchema("Concurrent source searches, 1 to 8."),
                    "timeoutMs" to intSchema("Overall timeout in milliseconds, 10000 to 90000."),
                    "exact" to boolSchema("Use exact title/author filtering.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_AI_RUNTIME_STATUS,
                description = "Read the current AI model, provider, protocol, routing preset, and registered/available tool counts. Never exposes API keys or tokens.",
                inputSchema = objectSchema(
                    "taskType" to stringSchema("Optional AI task type, for example chat or translate_chapter. Defaults to chat.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_AI_QUOTA_STATUS,
                description = "Report what the app can know about the selected AI quota/limit state. If the provider does not expose remaining quota, the tool says so instead of guessing.",
                inputSchema = objectSchema(
                    "taskType" to stringSchema("Optional AI task type, for example chat or translate_chapter. Defaults to chat.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DIAGNOSE_BOOK_SOURCE,
                description = "Run the native source health checker for one installed book source and report the failing stage, evidence, and repair hints.",
                inputSchema = objectSchema(
                    "sourceUrl" to stringSchema("Exact source URL/id from search_book_sources."),
                    "sourceName" to stringSchema("Source display name when sourceUrl is unavailable."),
                    "query" to stringSchema("Fallback source keyword."),
                    "profile" to stringSchema("QUICK, STANDARD, or FULL. STANDARD checks search/explore/detail/toc; FULL also checks content/media."),
                    "persist" to boolSchema("Persist the diagnosis in source health history."),
                    "timeoutMs" to intSchema("Diagnosis timeout in milliseconds, 5000 to 180000.")
                )
            ),
            AiToolDefinition(
                name = TOOL_REPAIR_BOOK_SOURCE,
                description = "Repair local source metadata, selector rules, and VBook compatibility placeholders for one installed source. Requires user confirmation.",
                inputSchema = objectSchema(
                    "sourceUrl" to stringSchema("Exact source URL/id from search_book_sources."),
                    "sourceName" to stringSchema("Source display name when sourceUrl is unavailable."),
                    "query" to stringSchema("Fallback source keyword."),
                    "enabled" to boolSchema("Optional enabled state to apply."),
                    "enabledExplore" to boolSchema("Optional explore enabled state to apply."),
                    "repairVbookCompatibility" to boolSchema("For VBook sources, reconcile type and vbook:// rule placeholders. Defaults to true."),
                    "runHealthCheck" to boolSchema("Run a diagnosis after repair. Defaults to true."),
                    "profile" to stringSchema("QUICK, STANDARD, or FULL diagnosis profile used after repair."),
                    "timeoutMs" to intSchema("Post-repair diagnosis timeout in milliseconds, 5000 to 180000."),
                    "bookSourceName" to stringSchema("Optional updated source name."),
                    "bookSourceGroup" to stringSchema("Optional source group."),
                    "bookSourceUrl" to stringSchema("Optional updated source URL."),
                    "searchUrl" to stringSchema("Optional updated search URL."),
                    "exploreUrl" to stringSchema("Optional updated explore URL."),
                    "header" to stringSchema("Optional custom HTTP headers JSON or string."),
                    "loginUrl" to stringSchema("Optional login URL."),
                    "weight" to intSchema("Optional source priority weight."),
                    "ruleSearch" to openObjectSchema("Optional updated search rule JSON object."),
                    "ruleBookInfo" to openObjectSchema("Optional updated book info rule JSON object."),
                    "ruleToc" to openObjectSchema("Optional updated table of contents rule JSON object."),
                    "ruleContent" to openObjectSchema("Optional updated content rule JSON object."),
                    "ruleExplore" to openObjectSchema("Optional updated explore rule JSON object.")
                )
            ),
            AiToolDefinition(
                name = TOOL_ADD_BOOK_TO_BOOKSHELF,
                description = "Add one online book to the bookshelf by bookUrl. Prefer a bookUrl returned by search_online_books. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL from search_online_books or a supported source detail URL.")
                )
            ),
            AiToolDefinition(
                name = TOOL_CREATE_VBOOK_PLUGIN_DRAFT,
                description = "Create a local VBook plugin draft with plugin.json and src/*.js files. Each mapped script must expose a global execute(...) function. Use sandbox APIs such as fetch, Http, Html, Response, localStorage, or the network-only java.connect/java.ajax compatibility facade; CommonJS module.exports and Java/Android platform packages are unavailable. This does not install or enable the plugin.",
                inputSchema = objectSchema(
                    "name" to stringSchema("Plugin display name."),
                    "source" to stringSchema("Stable plugin source id or upstream site URL."),
                    "author" to stringSchema("Plugin author."),
                    "version" to stringSchema("Plugin version, for example 0.1.0."),
                    "type" to stringSchema("Plugin type: novel, comic, audio, video, or file."),
                    "description" to stringSchema("Short plugin description."),
                    "script" to mapSchema(
                        "Optional VBook script role map, for example {\"search\":\"search.js\", \"detail\":\"detail.js\", \"toc\":\"toc.js\", \"chap\":\"chap.js\"}. Roles are inferred from standard file names when omitted."
                    ),
                    "files" to arraySchema(
                        "JavaScript source files. File names must be simple src/*.js names and every role entry file must define global function execute(...).",
                        objectSchema(
                            "name" to stringSchema("Script file name, for example search.js."),
                            "content" to stringSchema("JavaScript source code.")
                        )
                    )
                )
            ),
            AiToolDefinition(
                name = TOOL_INSTALL_VBOOK_PLUGIN,
                description = "Install a VBook plugin from a draftId, local filePath, or URI. Requires user confirmation as plugin install. Installed sources are disabled by default unless enableAfterInstall is true.",
                inputSchema = objectSchema(
                    "draftId" to stringSchema("Draft id returned by create_vbook_plugin_draft."),
                    "filePath" to stringSchema("Local plugin zip file path."),
                    "uri" to stringSchema("content://, file://, or other URI returned by Android file picker."),
                    "enableAfterInstall" to boolSchema("Enable the installed source immediately. Defaults to false for chatbot-created plugins.")
                )
            ),
            AiToolDefinition(
                name = TOOL_CREATE_LEGADO_BOOK_SOURCE_DRAFT,
                description = "Create an app-private draft of a standard Legado BookSource JSON object. The JSON may contain normal Legado selector and JavaScript rules, but cannot access Android/Java platform packages or local/private network targets. This does not install or enable the source. Requires user confirmation.",
                inputSchema = objectSchema(
                    "sourceJson" to stringSchema("Complete standard Legado BookSource JSON object. It must include non-empty bookSourceName and a public http/https bookSourceUrl; include searchUrl and ruleSearch, ruleBookInfo, ruleToc, and ruleContent as needed.")
                )
            ),
            AiToolDefinition(
                name = TOOL_INSTALL_LEGADO_BOOK_SOURCE,
                description = "Install a standard Legado BookSource from a reviewed draftId or sourceJson. Requires user confirmation. AI-created sources are disabled by default unless enableAfterInstall is true; replacing an existing source also requires overwrite=true.",
                inputSchema = objectSchema(
                    "draftId" to stringSchema("Draft id returned by create_legado_book_source_draft."),
                    "sourceJson" to stringSchema("Optional complete Legado BookSource JSON when no draftId is available."),
                    "overwrite" to boolSchema("Replace an installed source with the same bookSourceUrl. Defaults to false."),
                    "enableAfterInstall" to boolSchema("Enable the installed source immediately. Defaults to false for AI-created sources.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_AGENT_SKILLS,
                description = "List Agent skills, active/latest versions, validation state, allowed tools, and enabled state.",
                inputSchema = objectSchema()
            ),
            AiToolDefinition(
                name = TOOL_CREATE_AGENT_SKILL_DRAFT,
                description = "Create a versioned Agent skill draft with manifest.json and SKILL.md. Drafts are disabled and are never activated automatically. Requires user confirmation.",
                inputSchema = objectSchema(
                    "id" to stringSchema("Stable lowercase skill id, for example summarize_chapter."),
                    "name" to stringSchema("Skill display name."),
                    "description" to stringSchema("Short skill description."),
                    "version" to stringSchema("Semantic version, for example 1.0.0."),
                    "instructions" to stringSchema("Complete SKILL.md instructions. Do not include secrets or executable code."),
                    "allowedTools" to arraySchema(
                        "Exact registered tool names this skill may use.",
                        stringSchema("Registered Agent tool name."),
                    ),
                    "requirements" to arraySchema(
                        "Short declarative requirements for using this skill.",
                        stringSchema("Requirement text."),
                    ),
                )
            ),
            AiToolDefinition(
                name = TOOL_SET_AGENT_SKILL_ENABLED,
                description = "Enable or disable an Agent skill after user review. Invalid versions cannot be enabled. Requires strong user confirmation.",
                inputSchema = objectSchema(
                    "skillId" to stringSchema("Skill database id returned by list_agent_skills."),
                    "enabled" to boolSchema("True to enable the active valid version; false to disable."),
                )
            ),
            AiToolDefinition(
                name = TOOL_ACTIVATE_AGENT_SKILL_VERSION,
                description = "Activate one validated skill version after user review. Does not automatically enable a disabled skill. Requires strong user confirmation.",
                inputSchema = objectSchema(
                    "skillId" to stringSchema("Skill database id."),
                    "versionId" to stringSchema("Validated version id returned by list_agent_skills."),
                )
            ),
            AiToolDefinition(
                name = TOOL_ROLLBACK_AGENT_SKILL,
                description = "Rollback a skill to its previous validated version. Requires strong user confirmation.",
                inputSchema = objectSchema(
                    "skillId" to stringSchema("Skill database id."),
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_BOOK_DETAIL,
                description = "Get metadata and reading progress for one book. If no identifier is given, use the last read book.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title."),
                    "bookAuthor" to stringSchema("Book author.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_BOOK_CHAPTERS,
                description = "List chapter metadata for a local bookshelf book.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Optional chapter title keyword."),
                    "start" to intSchema("Zero-based offset for returned chapters."),
                    "limit" to intSchema("Maximum number of chapters to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_CHAPTER_CONTENT,
                description = "Read cached text content for a chapter. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Zero-based chapter index. Defaults to current reading chapter."),
                    "maxChars" to intSchema("Maximum characters to return, capped by the app.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_CHAPTER_WINDOW,
                description = "Read cached text for a chapter and its neighboring chapters in one call. Useful for continuity checks before rewriting or summarizing. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Zero-based center chapter index. Defaults to current reading chapter."),
                    "before" to intSchema("How many previous chapters to include, capped by the app."),
                    "after" to intSchema("How many following chapters to include, capped by the app."),
                    "maxCharsPerChapter" to intSchema("Maximum characters to return per chapter, capped by the app.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_CHAPTER_CONTENT,
                description = "Search cached chapter text in a local bookshelf book by character name, plot keyword, place, or phrase. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Character name, plot keyword, place, or phrase to search in cached chapter text."),
                    "aroundChapterIndex" to intSchema("Prefer matches closest to this zero-based chapter index. Defaults to current reading chapter."),
                    "limit" to intSchema("Maximum number of matching chapters to return."),
                    "maxChars" to intSchema("Maximum excerpt characters per match, capped by the app.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_BOOKMARKS,
                description = "Search local bookmarks and notes across the bookshelf or within one book.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Keyword for bookmark text, note, book, or chapter."),
                    "bookName" to stringSchema("Optional book title filter."),
                    "bookAuthor" to stringSchema("Optional book author filter."),
                    "limit" to intSchema("Maximum number of bookmarks to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_AUTHORING_PROJECTS,
                description = "List local Writing and Ebook Editor projects without exposing files or Android objects.",
                inputSchema = objectSchema(
                    "kind" to stringSchema("Optional WRITING or EBOOK_EDITOR filter."),
                    "limit" to intSchema("Maximum projects, 1 to 200."),
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_AUTHORING_PROJECT,
                description = "Read one local Writing or Ebook Editor project including chapters and style.",
                inputSchema = objectSchema(
                    "id" to stringSchema("Exact project id from list_authoring_projects."),
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_AUTHORING_PROJECT,
                description = "Create or update a complete Writing or Ebook Editor project after user review. Requires confirmation.",
                inputSchema = objectSchema(
                    "project" to openObjectSchema("Serialized AuthoringProject object with id, kind, title, chapters and style."),
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_AUTHORING_PROJECT,
                description = "Delete one Writing or Ebook Editor project by exact id. Requires confirmation.",
                inputSchema = objectSchema(
                    "id" to stringSchema("Exact project id from list_authoring_projects."),
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_READING_STATS,
                description = "Get local reading statistics, recent read books, and daily read records.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Optional book title or author filter."),
                    "date" to stringSchema("Optional date filter for daily records, format YYYY-MM-DD."),
                    "limit" to intSchema("Maximum number of records to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_AI_ARTIFACTS,
                description = "Read existing AI artifacts such as chapter summaries for a book or chapter.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Optional zero-based chapter index."),
                    "taskType" to stringSchema("Optional artifact task type, such as summarize_chapter."),
                    "limit" to intSchema("Maximum number of artifacts to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_AI_ARTIFACT,
                description = "Save a user-approved AI note or summary into local AI artifacts. Use only when the user explicitly asks to save content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Optional zero-based chapter index."),
                    "taskType" to stringSchema("Artifact task type, such as ai_note or summarize_chapter."),
                    "output" to stringSchema("The note or summary content to save.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_MEMORY,
                description = "Save a fact or preference about the user to long-term memory. Use when the user shares a preference, fact, or instruction you should remember for future conversations.",
                inputSchema = objectSchema(
                    "key" to stringSchema("Short label for the memory, e.g. 'favorite_genre', 'reading_goal'."),
                    "value" to stringSchema("The fact or preference to remember."),
                    "conversationId" to stringSchema("Current conversation id. Leave empty for global memory unless scope says otherwise."),
                    "scope" to stringSchema("global, conversation, book, writing_project, or ebook_project."),
                    "scopeId" to stringSchema("Conversation, book, or project id for scoped memory."),
                    "bookUrl" to stringSchema("Book URL used as scopeId when scope is book."),
                    "projectKey" to stringSchema("Project key used as scopeId for project memory."),
                    "type" to stringSchema("fact, preference, decision, glossary, relationship, workflow_result, or summary."),
                    "confidence" to numberSchema("Confidence from 0.0 to 1.0."),
                    "pinned" to boolSchema("Whether this memory should be highlighted.")
                )
            ),
            AiToolDefinition(
                name = TOOL_RECALL_MEMORY,
                description = "Recall or full-text search saved memories without crossing the requested scope.",
                inputSchema = objectSchema(
                    "conversationId" to stringSchema("Leave empty to recall global memories, or pass current conversation id for scoped memories."),
                    "scope" to stringSchema("Optional explicit scope: global, conversation, book, writing_project, or ebook_project."),
                    "scopeId" to stringSchema("Scope id when scope is explicit."),
                    "bookUrl" to stringSchema("Book URL used as scopeId when scope is book."),
                    "projectKey" to stringSchema("Project key used as scopeId for project memory."),
                    "query" to stringSchema("Optional full-text query matched against memory key, value, scope, and type."),
                    "limit" to intSchema("Maximum result count, from 1 to 200.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_MEMORY,
                description = "Delete a saved memory entry.",
                inputSchema = objectSchema(
                    "key" to stringSchema("The memory key to delete."),
                    "conversationId" to stringSchema("Leave empty for global scope, or pass conversation id for scoped memory."),
                    "scope" to stringSchema("Optional explicit scope: global, conversation, book, writing_project, or ebook_project."),
                    "scopeId" to stringSchema("Scope id when scope is explicit."),
                    "bookUrl" to stringSchema("Book URL used as scopeId when scope is book."),
                    "projectKey" to stringSchema("Project key used as scopeId for project memory.")
                )
            ),
            AiToolDefinition(
                name = TOOL_UPDATE_BOOK,
                description = "Refresh one bookshelf book from its source and report newly discovered chapters. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "downloadNew" to boolSchema("Queue only newly discovered chapters for offline download.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DOWNLOAD_BOOK_CHAPTERS,
                description = "Queue a chapter range of a bookshelf book for offline use. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "startIndex" to intSchema("Zero-based first chapter index."),
                    "endIndex" to intSchema("Zero-based last chapter index, inclusive.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_DOWNLOAD_STATUS,
                description = "Read the current offline download queue and optional per-book status.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Optional exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Optional book title."),
                    "bookAuthor" to stringSchema("Optional book author.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_BOOK_DICTIONARY_TERMS,
                description = "Read the per-book AI translation dictionary stored with the original source book. This is separate from the user Quick Translation dictionary.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Optional source or translated term keyword."),
                    "limit" to intSchema("Maximum number of terms to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_DICTIONARY_TERM,
                description = "Add or edit one term in the per-book AI translation dictionary. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "original" to stringSchema("Exact original source term."),
                    "translation" to stringSchema("Preferred translated term.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_BOOK_DICTIONARY_TERM,
                description = "Delete one term from the per-book AI translation dictionary by exact original source text. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "original" to stringSchema("Exact original source term to delete.")
                )
            ),
            AiToolDefinition(
                name = TOOL_CLEAR_BOOK_DICTIONARY,
                description = "Clear the entire per-book AI translation dictionary. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact local bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_DICTIONARY_ENTRIES,
                description = "Search user-created Quick Translation dictionary entries.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Source, Hán-Việt, or translated text to find."),
                    "limit" to intSchema("Maximum number of entries."),
                    "bookUrl" to stringSchema("Optional project book URL/id."),
                    "bookName" to stringSchema("Optional project book title."),
                    "bookAuthor" to stringSchema("Optional project book author.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_DICTIONARY_ENTRY,
                description = "Add or edit a user Quick Translation dictionary entry. Requires user confirmation. Omit id when adding; duplicate additions are ignored.",
                inputSchema = objectSchema(
                    "id" to intSchema("Existing positive entry id when editing; omit when adding."),
                    "raw" to stringSchema("Exact original source phrase."),
                    "hanViet" to stringSchema("Optional Hán-Việt reading."),
                    "target" to stringSchema("Vietnamese translation."),
                    "type" to stringSchema("NAME, VIETPHRASE, PHONETIC, PRONOUN, LUAT_NHAN, IGNORE, or TERM."),
                    "scope" to stringSchema("GLOBAL, UNIVERSE, or PROJECT."),
                    "scopeKey" to stringSchema("Universe key or project book URL; empty for GLOBAL."),
                    "bookUrl" to stringSchema("Project book URL used when scopeKey is omitted."),
                    "bookName" to stringSchema("Project book title used when bookUrl is omitted."),
                    "bookAuthor" to stringSchema("Project book author."),
                    "enabled" to boolSchema("Whether the entry is active.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_DICTIONARY_ENTRY,
                description = "Delete one user Quick Translation dictionary entry by id. Requires user confirmation.",
                inputSchema = objectSchema(
                    "id" to intSchema("Positive user dictionary entry id from list_dictionary_entries.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_BOOKSHELF_AUTOMATION,
                description = "Read the non-AI automatic bookshelf update, download, and notification schedule.",
                inputSchema = objectSchema()
            ),
            AiToolDefinition(
                name = TOOL_SET_BOOKSHELF_AUTOMATION,
                description = "Configure non-AI periodic bookshelf updates, new-chapter downloads, and notifications. Requires user confirmation.",
                inputSchema = objectSchema(
                    "enabled" to boolSchema("Enable or disable periodic checks."),
                    "intervalHours" to intSchema("Check interval in hours, from 1 to 168."),
                    "autoDownloadNewChapters" to boolSchema("Download newly discovered chapters automatically."),
                    "notifyNewChapters" to boolSchema("Show a notification when new chapters are found.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_SOURCE,
                description = "Save or update a Legado book source directly in the database. Can accept a full sourceJson or individual fields (bookSourceUrl, bookSourceName, rules, etc.). Requires user confirmation.",
                inputSchema = objectSchema(
                    "sourceJson" to stringSchema("Optional complete standard Legado BookSource JSON object."),
                    "bookSourceUrl" to stringSchema("Book source unique URL/key."),
                    "bookSourceName" to stringSchema("Book source display name."),
                    "bookSourceGroup" to stringSchema("Optional source group."),
                    "searchUrl" to stringSchema("Optional search URL."),
                    "exploreUrl" to stringSchema("Optional explore URL."),
                    "header" to stringSchema("Optional custom HTTP headers JSON or string."),
                    "loginUrl" to stringSchema("Optional login URL."),
                    "weight" to intSchema("Optional priority weight."),
                    "enableAfterSave" to boolSchema("Whether to enable the source immediately after saving. Defaults to true.")
                )
            ),
            AiToolDefinition(
                name = TOOL_TEST_BOOK_SOURCE_RULE,
                description = "Test a specific rule (SEARCH, TOC, CONTENT, INFO) on a book source before or after saving.",
                inputSchema = objectSchema(
                    "sourceUrl" to stringSchema("Installed book source URL to test."),
                    "sourceName" to stringSchema("Book source name when sourceUrl is omitted."),
                    "query" to stringSchema("Fallback keyword to find book source."),
                    "sourceJson" to stringSchema("Optional in-memory book source JSON to test without installing first."),
                    "testType" to stringSchema("Type of test to run: SEARCH, TOC, CONTENT, or INFO. Defaults to SEARCH."),
                    "targetUrl" to stringSchema("Target chapter or book URL for TOC/CONTENT/INFO tests."),
                    "keyword" to stringSchema("Keyword to use for SEARCH test. Defaults to 'test'.")
                )
            ),
            AiToolDefinition(
                name = TOOL_CREATE_STORY_MEMORY,
                description = "Extract and build Story Memory (entities, world-building, relationships, and chapter timeline) by analyzing chapter content or reading chapters from the novel. Automatically translates CJK titles to natural Vietnamese chapter titles.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "content" to stringSchema("Optional raw text content of the chapter to analyze directly."),
                    "chapterIndex" to intSchema("Starting chapter index (0-based) to analyze. Defaults to current reading chapter."),
                    "chapterCount" to intSchema("Number of chapters to analyze in batch (1 to 10) when content is omitted. Defaults to 1."),
                    "chapterTitle" to stringSchema("Optional chapter title.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_STORY_MEMORY,
                description = "Retrieve Story Memory items (entities, worldBuilding, relationships, or timelines) for a book. Timeline chapter titles are always formatted in natural Vietnamese.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "filter" to stringSchema("Filter category: all, entities, world, relationships, or timeline. Defaults to all."),
                    "limit" to intSchema("Maximum items to return, 1 to 200. Defaults to 50.")
                )
            ),
            AiToolDefinition(
                name = TOOL_RETROFIT_STORY_TRANSLATIONS,
                description = "Retrofit previously translated chapters by applying current story memory entities and canonical terms. Refreshes reader content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "dryRun" to boolSchema("If true, only reports how many chapters and terms would be affected without modifying translations.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_STORY_CHRONICLE,
                description = "Retrieve the synthesized story chronicle (eras/arcs) for a book. Unlike raw chapter timelines, chronicles summarize broader historical periods and world changes.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "includeRawTimelines" to boolSchema("Whether to also include chapter-level raw timeline events.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SYNTHESIZE_STORY_CHRONICLE,
                description = "Analyze the story timeline and synthesize it into coherent chronological eras/story arcs (niên biểu / đại sự ký) instead of copying raw chapter timelines. Saves the periods to the story chronicle.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "directive" to stringSchema("Optional guidance or focus for the chronicle synthesis (e.g. focus on realm ascension, warfare, secret realms)."),
                    "startChapter" to intSchema("Starting chapter index (0-based) to include. Defaults to 0."),
                    "endChapter" to intSchema("Optional ending chapter index (0-based) to include.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_STORY_CHRONICLE,
                description = "Save or update a specific story chronicle era/period record. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "eraTitle" to stringSchema("Title of the era/story arc (e.g. 'Thời Kỳ Luyện Khí Kỳ - Thanh Vân Môn')."),
                    "chapterRange" to stringSchema("Human-readable chapter range string (e.g. 'Chương 1 - Chương 45')."),
                    "eraSummary" to stringSchema("Narrative summary of key developments and milestones during this era."),
                    "milestoneEvents" to arraySchema("Key milestone events that occurred in this period.", stringSchema("Event description.")),
                    "keyCharacters" to arraySchema("Key active characters in this period.", stringSchema("Character name.")),
                    "startChapterIndex" to intSchema("Starting chapter index (0-based)."),
                    "endChapterIndex" to intSchema("Ending chapter index (0-based)."),
                    "id" to stringSchema("Optional period id when editing an existing era.")
                )
            ),
            AiToolDefinition(
                name = TOOL_UPSERT_STORY_WIKI_ENTITY,
                description = "Add or update an entity (character, sect, item, pet) in the Story Wiki. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "raw" to stringSchema("Original source term / name (e.g. Hanzi or original text)."),
                    "target" to stringSchema("Vietnamese translation / canonical name."),
                    "senseKey" to stringSchema("Optional sense or context disambiguation key."),
                    "kind" to stringSchema("Entity kind: character, sect, item, pet, or other. Defaults to character."),
                    "summary" to stringSchema("Description or profile of the entity."),
                    "aliases" to arraySchema("Known aliases or alternate names.", stringSchema("Alias name."))
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_STORY_WIKI_ENTITY,
                description = "Delete an entity from the Story Wiki by original term. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "raw" to stringSchema("Original term of the entity to delete."),
                    "senseKey" to stringSchema("Optional sense key of the entity.")
                )
            ),
            AiToolDefinition(
                name = TOOL_UPSERT_STORY_WIKI_WORLD,
                description = "Add or update a world-building entry (realms, laws, geography, organizations) in the Story Wiki. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "term" to stringSchema("Original term (e.g. Hanzi or concept name)."),
                    "vietnamese" to stringSchema("Vietnamese translation / canonical concept name."),
                    "category" to stringSchema("Category: realm, law, geography, organization, or other. Defaults to other."),
                    "description" to stringSchema("Description and lore of the concept.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_STORY_WIKI_WORLD,
                description = "Delete a world-building entry from the Story Wiki by original term. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "term" to stringSchema("Original term of the world-building concept to delete.")
                )
            ),
            AiToolDefinition(
                name = TOOL_UPSERT_STORY_WIKI_RELATIONSHIP,
                description = "Add or update an interpersonal or faction relationship in the Story Wiki. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "source" to stringSchema("Source character or faction name."),
                    "target" to stringSchema("Target character or faction name."),
                    "relationship" to stringSchema("Type of relationship (e.g. sư đồ, huynh đệ, kẻ thù, đồng minh)."),
                    "description" to stringSchema("Context or details of the relationship."),
                    "chapterIndex" to intSchema("Optional chapter index where the relationship was established.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_STORY_WIKI_RELATIONSHIP,
                description = "Delete a relationship entry from the Story Wiki. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "source" to stringSchema("Source character or faction name."),
                    "target" to stringSchema("Target character or faction name."),
                    "relationship" to stringSchema("Optional relationship type to match.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_STORY_WIKI,
                description = "Query the full Story Wiki for a book, including entities, world-building, relationships, and chronicle periods.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact bookshelf book URL/id."),
                    "bookName" to stringSchema("Book title when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "tab" to stringSchema("Category tab to fetch: ALL, ENTITY, WORLD, RELATIONSHIP, or CHRONICLE. Defaults to ALL."),
                    "query" to stringSchema("Optional search keyword to filter items.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GENERATE_CHARACTER_IMAGE,
                description = "Generate an AI concept portrait for a character entity from verified story memory facts. Returns the saved local image path.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Book URL."),
                    "entityRaw" to stringSchema("Raw character/entity name as stored in story memory."),
                    "force" to boolSchema("Optional. Force generation even if verified details are brief. Defaults to true.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GENERATE_BOOK_COVER,
                description = "Generate a cinematic vertical 2:3 book cover illustration for a bookshelf novel with the translated book title and author credit prominently rendered. Automatically sets the novel's custom cover.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Book URL."),
                    "title" to stringSchema("Optional translated book title in Vietnamese to display prominently on the cover (user-edited or confirmed with user). Defaults to the book's translated name."),
                    "author" to stringSchema("Optional author name to display on the cover. Defaults to the book's author."),
                    "prompt" to stringSchema("Optional custom illustration prompt. If omitted, synthesized from story memory."),
                    "force" to boolSchema("Optional. Force generation. Defaults to true.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GENERATE_WORLD_IMAGE,
                description = "Generate an AI concept illustration for a world-building entry (weapon, technique, location, faction) or the entire world map.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Book URL."),
                    "raw" to stringSchema("Raw key of the world entry (or '__story_world_map__' for world map)."),
                    "category" to stringSchema("Optional category, e.g. location, faction, weapon, technique, world_map."),
                    "force" to boolSchema("Optional. Force generation. Defaults to true.")
                )
            )
        )

        internal fun validateToolArguments(
            definition: AiToolDefinition,
            rawArguments: String,
        ): Result<JsonObject> = runCatching {
            val parsed = runCatching {
                GSON.fromJson(rawArguments.ifBlank { "{}" }, JsonObject::class.java)
            }.getOrNull()
            require(parsed != null) {
                "Tool arguments must be a JSON object"
            }
            val schemaType = definition.inputSchema["type"] as? String
            require(schemaType == "object") {
                "Tool input schema must be an object"
            }
            val properties = (definition.inputSchema["properties"] as? Map<*, *>).orEmpty()
                .keys
                .mapNotNull { it as? String }
                .toSet()
            if (definition.inputSchema["additionalProperties"] == false) {
                val unknown = parsed.keySet().filterNot(properties::contains).sorted()
                require(unknown.isEmpty()) {
                    "Unknown argument(s): ${unknown.joinToString()}"
                }
            }
            parsed
        }

        private fun objectSchema(vararg properties: Pair<String, Map<String, Any?>>): Map<String, Any?> {
            return mapOf(
                "type" to "object",
                "properties" to properties.toMap(),
                "additionalProperties" to false
            )
        }

        private fun arraySchema(
            description: String,
            items: Map<String, Any?>,
        ): Map<String, Any?> {
            return mapOf(
                "type" to "array",
                "description" to description,
                "items" to items,
            )
        }

        private fun mapSchema(description: String): Map<String, Any?> {
            return mapOf(
                "type" to "object",
                "description" to description,
                "additionalProperties" to stringSchema("Script file name.")
            )
        }

        private fun openObjectSchema(description: String): Map<String, Any?> {
            return mapOf(
                "type" to "object",
                "description" to description,
                "additionalProperties" to true,
            )
        }

        private fun stringSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "string", "description" to description)
        }

        private fun intSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "integer", "description" to description)
        }

        private fun numberSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "number", "description" to description)
        }

        private fun boolSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "boolean", "description" to description)
        }

        private const val TOOL_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) LegadoAiAgent/1.0"
        private const val DEFAULT_MEMORY_SEARCH_LIMIT = 50
        private const val MAX_MEMORY_SEARCH_LIMIT = 200
        private const val MAX_PLUGIN_DRAFT_FILES = 20
        private const val MAX_PLUGIN_SCRIPT_CHARS = 200_000
        private val SAFE_PLUGIN_SCRIPT_NAME = Regex("""[A-Za-z0-9._-]+\.js""")
        private val SAFE_PLUGIN_SCRIPT_ROLE = Regex("""[a-z0-9_-]+""")
        private val SAFE_PLUGIN_DRAFT_ID = Regex("""draft_[A-Za-z0-9_-]+""")
        private val SAFE_LEGADO_SOURCE_DRAFT_ID = Regex("""legado_[A-Za-z0-9_-]+""")
        private const val LEGADO_SOURCE_DRAFT_FILE = "bookSource.json"
        private val VBOOK_SCRIPT_ROLES = setOf(
            "home",
            "gen",
            "genre",
            "search",
            "detail",
            "toc",
            "page",
            "chap",
            "track",
            "voice",
            "tts",
        )
        private const val DEFAULT_VBOOK_PLUGIN_SCRIPT =
            """// VBook plugin draft generated by AI Agent.
// Replace this scaffold with source-specific VBook logic before installing.
function execute(keyword, page) {
  return JSON.stringify({
    success: true,
    data: []
  });
}
"""
    }
}

internal fun validateAgentVbookPluginDraftMetadata(source: String) {
    source.takeIf { it.contains("://") }?.let { rawUrl ->
        validateAgentVbookPluginDraftUrl(rawUrl).getOrThrow()
    }
}

internal fun validateAgentVbookPluginDraftFile(
    fileName: String,
    content: String,
    maxChars: Int = AGENT_VBOOK_MAX_SCRIPT_CHARS,
) {
    require(AGENT_VBOOK_SAFE_SCRIPT_NAME.matches(fileName)) {
        "Unsafe plugin script name: $fileName"
    }
    require(content.length <= maxChars) {
        "Plugin script is too large: $fileName"
    }
    require(!content.contains('\u0000')) {
        "Plugin script contains invalid null bytes: $fileName"
    }
    require(!isUnsafeAgentVbookPluginPath(content)) {
        "Plugin script contains unsafe path traversal: $fileName"
    }
    AGENT_VBOOK_FORBIDDEN_SCRIPT_PATTERNS.firstOrNull { (pattern, _) ->
        pattern.containsMatchIn(content)
    }?.let { (_, label) ->
        throw IllegalArgumentException("Plugin script uses a forbidden API ($label): $fileName")
    }
    AGENT_VBOOK_URL_PATTERN.findAll(content).forEach { match ->
        validateAgentVbookPluginDraftUrl(match.value).getOrThrow()
    }
}

internal fun parseAgentLegadoBookSourceJson(rawJson: String): BookSource {
    val sourceJson = rawJson.trim()
    require(sourceJson.isNotBlank()) { "sourceJson is required" }
    require(sourceJson.length <= 500_000) { "Legado source JSON is too large" }
    require(sourceJson.startsWith('{') && sourceJson.endsWith('}')) {
        "sourceJson must be one Legado BookSource JSON object"
    }
    require(!sourceJson.contains('\u0000')) { "Legado source JSON contains invalid null bytes" }
    require(!isUnsafeAgentVbookPluginPath(sourceJson)) {
        "Legado source JSON contains unsafe path traversal"
    }
    AGENT_VBOOK_FORBIDDEN_SCRIPT_PATTERNS.firstOrNull { (pattern, _) ->
        pattern.containsMatchIn(sourceJson)
    }?.let { (_, label) ->
        throw IllegalArgumentException("Legado source uses a forbidden API ($label)")
    }
    AGENT_VBOOK_URL_PATTERN.findAll(sourceJson).forEach { match ->
        val staticUrl = match.value
            .substringBefore("{{")
            .substringBefore("\${")
        if (staticUrl.isNotBlank()) {
            validateAgentVbookPluginDraftUrl(staticUrl).getOrThrow()
        }
    }
    val source = GSON.fromJsonObject<BookSource>(sourceJson).getOrThrow()
    require(source.bookSourceName.isNotBlank()) { "bookSourceName is required" }
    require(source.bookSourceName.length <= 200) { "bookSourceName is too long" }
    require(source.bookSourceUrl.isNotBlank()) { "bookSourceUrl is required" }
    validateAgentVbookPluginDraftUrl(source.bookSourceUrl).getOrThrow()
    require(source.bookSourceType in BookSourceType.default..BookSourceType.video) {
        "bookSourceType must be between 0 and 4"
    }
    return source
}

internal fun validateAgentVbookPluginDraftUrl(rawUrl: String): Result<String> = runCatching {
    val uri = URI(rawUrl.trim())
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "Plugin draft only allows http and https URLs"
    }
    require(uri.rawUserInfo.isNullOrBlank()) {
        "Plugin draft URLs with embedded credentials are not allowed"
    }
    val host = uri.host?.trim()?.trim('[', ']')?.lowercase()
    require(!host.isNullOrBlank()) {
        "Plugin draft URL host is required"
    }
    require(!host.isBlockedInternetFetchHost()) {
        "Plugin draft cannot target local host names"
    }
    host.toInetAddressIfLiteral()?.let { address ->
        require(!address.isBlockedInternetFetchAddress()) {
            "Plugin draft cannot target local or private network addresses"
        }
    }
    URI(
        scheme,
        null,
        host,
        uri.port,
        uri.path?.takeIf(String::isNotBlank),
        uri.query,
        null,
    ).normalize().toASCIIString()
}

internal fun isUnsafeAgentVbookPluginPath(value: String): Boolean {
    return AGENT_VBOOK_PATH_TRAVERSAL_PATTERN.containsMatchIn(value) ||
        AGENT_VBOOK_WINDOWS_ABSOLUTE_PATH_PATTERN.containsMatchIn(value)
}

internal fun validateAgentVbookPluginInstallFilePath(rawPath: String): File {
    val path = rawPath.trim()
    require(path.isNotBlank()) {
        "Plugin filePath is required"
    }
    require(!path.contains('\u0000')) {
        "Plugin filePath contains invalid null bytes"
    }
    require("://" !in path) {
        "Use the uri argument for URI plugin installs"
    }
    require(!isUnsafeAgentVbookPluginPath(path)) {
        "Unsafe plugin filePath: $path"
    }
    require(path.substringAfterLast('/').substringAfterLast('\\').endsWith(".zip", ignoreCase = true)) {
        "Plugin filePath must point to a .zip package"
    }
    return File(path)
}

internal fun validateInternetFetchUrl(
    rawUrl: String,
    resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
): Result<String> = runCatching {
    val uri = URI(rawUrl.trim())
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "Only http and https URLs are allowed"
    }
    require(uri.rawUserInfo.isNullOrBlank()) {
        "URLs with embedded credentials are not allowed"
    }
    val host = uri.host?.trim()?.lowercase()
    require(!host.isNullOrBlank()) {
        "URL host is required"
    }
    require(!host.isBlockedInternetFetchHost()) {
        "Local host names are not allowed"
    }
    val addresses = runCatching { resolver(host) }
        .getOrElse { throw IllegalArgumentException("URL host cannot be resolved") }
    require(addresses.isNotEmpty()) {
        "URL host cannot be resolved"
    }
    require(addresses.none(InetAddress::isBlockedInternetFetchAddress)) {
        "Local or private network addresses are not allowed"
    }
    URI(
        scheme,
        null,
        host,
        uri.port,
        uri.path?.takeIf(String::isNotBlank),
        uri.query,
        null,
    ).normalize().toASCIIString()
}

internal fun isReadableInternetContentType(contentType: String?): Boolean {
    if (contentType.isNullOrBlank()) return true
    val mediaType = contentType.substringBefore(';').trim().lowercase()
    return mediaType.startsWith("text/") ||
        mediaType == "application/xhtml+xml" ||
        mediaType == "application/xml" ||
        mediaType.endsWith("+xml")
}

internal fun extractInternetPageContent(
    raw: String,
    baseUrl: String,
    contentType: String?,
    maxChars: Int,
): InternetPageContent {
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val looksLikeHtml = "html" in mediaType ||
        raw.contains("<html", ignoreCase = true) ||
        raw.contains("<body", ignoreCase = true)
    val parsed = if (looksLikeHtml) {
        val doc = Jsoup.parse(raw, baseUrl)
        doc.select(
            "script,style,noscript,input,textarea,select,button,svg,canvas,iframe,form,template"
        ).remove()
        val title = doc.title().trim().take(300)
        val description = doc.selectFirst("meta[name=description]")
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: doc.selectFirst("meta[property=og:description]")
                ?.attr("content")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        InternetPageContent(
            title = title,
            description = description?.take(1_000).orEmpty(),
            text = cleanInternetPageText(doc.body().text()),
            truncated = false,
        )
    } else {
        InternetPageContent(
            title = "",
            description = "",
            text = cleanInternetPageText(raw),
            truncated = false,
        )
    }
    val cap = maxChars.coerceAtLeast(0)
    val cappedText = parsed.text.take(cap)
    return parsed.copy(
        text = cappedText,
        truncated = parsed.text.length > cappedText.length,
    )
}

private fun readInternetBodyPreview(
    body: okhttp3.ResponseBody,
    maxBytes: Int,
): InternetBodyPreview {
    val limit = maxBytes.coerceAtLeast(1)
    val contentType = body.contentType()
    val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_INTERNET_READ_BUFFER_BYTES)
    var total = 0
    var truncated = false
    body.byteStream().use { input ->
        while (total <= limit) {
            val remaining = limit + 1 - total
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            if (total > limit) {
                truncated = true
                break
            }
        }
    }
    val bytes = output.toByteArray().let { value ->
        if (value.size > limit) value.copyOf(limit) else value
    }
    return InternetBodyPreview(
        text = String(bytes, charset),
        truncated = truncated,
    )
}

private fun cleanInternetPageText(raw: String): String {
    return raw
        .replace('\u00A0', ' ')
        .replace(Regex("[\\t\\r\\f]+"), " ")
        .lines()
        .map { line -> line.trim().replace(Regex(" {2,}"), " ") }
        .filter(String::isNotBlank)
        .joinToString("\n")
}

private fun String.isBlockedInternetFetchHost(): Boolean {
    return this == "localhost" ||
        endsWith(".localhost") ||
        endsWith(".local")
}

private fun InetAddress.isBlockedInternetFetchAddress(): Boolean {
    if (
        isAnyLocalAddress ||
        isLoopbackAddress ||
        isLinkLocalAddress ||
        isSiteLocalAddress ||
        isMulticastAddress
    ) {
        return true
    }
    val bytes = address.map { it.toInt() and 0xff }
    return when (bytes.size) {
        4 -> {
            val first = bytes[0]
            val second = bytes[1]
            first == 0 ||
                first == 10 ||
                first == 127 ||
                (first == 100 && second in 64..127) ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 198 && second in 18..19)
        }
        16 -> (bytes[0] and 0xfe) == 0xfc
        else -> true
    }
}

internal data class InternetPageContent(
    val title: String,
    val description: String,
    val text: String,
    val truncated: Boolean,
)

private data class InternetBodyPreview(
    val text: String,
    val truncated: Boolean,
)

private fun String.toInetAddressIfLiteral(): InetAddress? {
    val candidate = trim().trim('[', ']')
    if (!AGENT_VBOOK_IPV4_LITERAL_PATTERN.matches(candidate) && ':' !in candidate) {
        return null
    }
    return runCatching { InetAddress.getByName(candidate) }.getOrNull()
}

private val AGENT_VBOOK_SAFE_SCRIPT_NAME = Regex("""[A-Za-z0-9._-]+\.js""")
private const val AGENT_VBOOK_MAX_SCRIPT_CHARS = 200_000
private val AGENT_VBOOK_IPV4_LITERAL_PATTERN = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
private val AGENT_VBOOK_URL_PATTERN =
    Regex("""(?i)\b(?:https?|file|content|android\.resource|jar|ftp)://[^\s"'`<>\])}]+""")
private val AGENT_VBOOK_PATH_TRAVERSAL_PATTERN =
    Regex("""(?:^|[\\/])\.\.(?:[\\/]|$)""")
private val AGENT_VBOOK_WINDOWS_ABSOLUTE_PATH_PATTERN =
    Regex("""(?i)\b[A-Z]:[\\/][^\s"'`<>]+""")
private val AGENT_VBOOK_FORBIDDEN_SCRIPT_PATTERNS = listOf(
    Regex("""(?i)\bPackages(?:\.|\b)""") to "Rhino Packages bridge",
    Regex("""(?i)\bimport(?:Class|Package)\s*\(""") to "Rhino Java import bridge",
    Regex("""(?i)\b(?:javax|android|kotlin)\.""") to "platform package access",
    Regex(
        """(?i)\bjava\.(?:awt|beans|io|lang|math|net|nio|reflect|security|sql|text|time|util)(?:\.|\b)"""
    ) to "Java platform package access",
    Regex("""(?i)\bProcessBuilder\b""") to "process execution",
    Regex("""(?i)\bRuntime\s*\.\s*getRuntime\s*\(""") to "runtime execution",
    Regex("""(?i)\bSystem\s*\.\s*(?:exit|getenv|setProperty|getProperty)\s*\(""") to "system access",
    Regex("""(?i)\b(?:eval|load)\s*\(""") to "dynamic code loading",
    Regex("""\b(?:new\s+)?Function\s*\(""") to "dynamic code loading",
    Regex("""(?i)\b(?:readFile|writeFile|deleteFile|mkdirs?|renameTo)\s*\(""") to "direct file access",
)

private const val INTERNET_FETCH_MIN_CHARS = 1_000
private const val INTERNET_FETCH_MAX_CHARS = 20_000
private const val INTERNET_FETCH_MIN_RESPONSE_BYTES = 128 * 1024
private const val INTERNET_FETCH_MAX_RESPONSE_BYTES = 1_000_000
private const val DEFAULT_INTERNET_READ_BUFFER_BYTES = 8 * 1024

private fun String.excerptAround(index: Int, maxChars: Int): String {
    if (length <= maxChars) return trim()
    val start = (index - maxChars / 2).coerceAtLeast(0)
    val end = (start + maxChars).coerceAtMost(length)
    return substring(start, end).trim()
}
