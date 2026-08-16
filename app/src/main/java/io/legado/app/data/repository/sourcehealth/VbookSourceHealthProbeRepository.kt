package io.legado.app.data.repository.sourcehealth

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.VbookSourceHealthProbeGateway
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.domain.model.VbookCapability
import io.legado.app.domain.model.VbookCapabilityProfile
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.vbook.VbookPluginInspector
import splitties.init.appCtx
import java.io.File

class VbookSourceHealthProbeRepository(
    private val now: () -> Long = System::currentTimeMillis,
    private val pluginRoot: File = File(appCtx.filesDir, PLUGIN_ROOT_DIRECTORY),
    private val inspectProfile: (File, String, Long) -> VbookCapabilityProfile = { directory, pluginId, inspectedAt ->
        VbookPluginInspector.loadOrInspect(directory, pluginId, inspectedAt)
    },
    private val exploreKinds: suspend (BookSource) -> List<ExploreKind> = { source ->
        VbookPluginAdapter.exploreKinds(source)
    },
    private val exploreBooks: suspend (BookSource, String) -> List<SearchBook> = { source, url ->
        VbookPluginAdapter.explore(source, url, FIRST_PAGE)
    },
    private val searchBooks: suspend (BookSource, String) -> List<SearchBook> = { source, key ->
        VbookPluginAdapter.search(source, key, FIRST_PAGE)
    },
    private val enrichBookInfo: suspend (BookSource, Book) -> Book = { source, book ->
        VbookPluginAdapter.enrichBookInfo(source, book)
    },
    private val getChapters: suspend (BookSource, Book) -> List<BookChapter> = { source, book ->
        VbookPluginAdapter.chapters(source, book)
    },
    private val getContent: suspend (BookSource, Book, BookChapter) -> String = { source, book, chapter ->
        VbookPluginAdapter.content(source, book, chapter, needSave = false)
    },
    private val resolveMedia: suspend (BookSource, Book, BookChapter) -> ResolvedMedia = { source, book, chapter ->
        VbookPluginAdapter.resolveMedia(source, book, chapter)
    },
) : VbookSourceHealthProbeGateway {

    override fun supports(source: BookSource): Boolean {
        val pluginId = pluginId(source) ?: return false
        return File(pluginRoot, "$pluginId/plugin.json").isFile
    }

    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult {
        val runner = SourceCheckStageRunner(now)
        val stages = mutableListOf<SourceCheckStageEvidence>()
        var capabilityProfile: VbookCapabilityProfile? = null
        var sample: SearchBook? = null
        var book: Book? = null
        var chapters: List<BookChapter> = emptyList()

        val manifest = runner.run(STAGE_MANIFEST, ORDER_MANIFEST) {
            val pluginId = pluginId(source)
                ?: throw NoStackTraceException("Source URL is not a VBook plugin")
            val directory = File(pluginRoot, pluginId)
            require(File(directory, "plugin.json").isFile) { "VBook manifest is missing" }
            inspectProfile(directory, pluginId, now())
        }
        stages += manifest.evidence
        capabilityProfile = manifest.value

        val scripts = if (capabilityProfile != null) {
            runner.run(STAGE_SCRIPTS, ORDER_SCRIPTS) {
                capabilityProfile!!.also {
                    if (it.scriptRoles.isEmpty()) {
                        throw NoStackTraceException("VBook manifest declares no scripts")
                    }
                }
            }
        } else {
            SourceCheckStageOutcome(
                runner.skipped(STAGE_SCRIPTS, ORDER_SCRIPTS, "Manifest stage did not complete"),
                null,
            )
        }
        stages += scripts.evidence

        if (!profile.includesStandard()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        val vbookProfile = capabilityProfile
        if (vbookProfile == null) {
            stages += runner.skipped(STAGE_HOME, ORDER_HOME, "No VBook profile is available")
            stages += runner.skipped(STAGE_SEARCH, ORDER_SEARCH, "No VBook profile is available")
            stages += runner.skipped(STAGE_DETAIL, ORDER_DETAIL, "No VBook profile is available")
            stages += runner.skipped(STAGE_TOC, ORDER_TOC, "No VBook profile is available")
        } else {
            if (VbookCapability.EXPLORE !in vbookProfile.capabilities) {
                stages += runner.skipped(STAGE_HOME, ORDER_HOME, "VBook home capability is not declared")
            } else {
                val home = runner.run(STAGE_HOME, ORDER_HOME) {
                    val url = exploreKinds(source)
                        .firstOrNull { !it.url.isNullOrBlank() }
                        ?.url
                        ?: throw NoStackTraceException("VBook home returned no usable category")
                    exploreBooks(source, url).firstValidSearchBook("VBook home")
                }
                stages += home.evidence
                sample = sample ?: home.value
            }

            if (VbookCapability.SEARCH !in vbookProfile.capabilities) {
                stages += runner.skipped(STAGE_SEARCH, ORDER_SEARCH, "VBook search capability is not declared")
            } else {
                val search = runner.run(STAGE_SEARCH, ORDER_SEARCH) {
                    searchBooks(source, DEFAULT_SEARCH_KEYWORD).firstValidSearchBook("VBook search")
                }
                stages += search.evidence
                sample = sample ?: search.value
            }

            book = sample?.toBook()
            if (book == null) {
                stages += runner.skipped(STAGE_DETAIL, ORDER_DETAIL, "No VBook item is available")
            } else if (VbookCapability.DETAIL !in vbookProfile.capabilities) {
                stages += runner.skipped(STAGE_DETAIL, ORDER_DETAIL, "VBook detail capability is not declared")
            } else {
                val detail = runner.run(STAGE_DETAIL, ORDER_DETAIL) {
                    enrichBookInfo(source, book!!).also {
                        require(it.bookUrl.isNotBlank()) { "VBook detail returned no book URL" }
                    }
                }
                stages += detail.evidence
                book = detail.value ?: book
            }

            if (book == null) {
                stages += runner.skipped(STAGE_TOC, ORDER_TOC, "No VBook item is available")
            } else if (VbookCapability.EPISODE_LIST !in vbookProfile.capabilities) {
                stages += runner.skipped(STAGE_TOC, ORDER_TOC, "VBook episode list capability is not declared")
            } else {
                val toc = runner.run(STAGE_TOC, ORDER_TOC) {
                    getChapters(source, book!!)
                        .filterNot { it.isVolume }
                        .also {
                            if (it.isEmpty()) {
                                throw NoStackTraceException("VBook TOC returned no chapters")
                            }
                        }
                }
                stages += toc.evidence
                chapters = toc.value.orEmpty()
            }
        }

        if (!profile.includesFull()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        val fullProfile = capabilityProfile
        val fullBook = book
        val chapter = chapters.firstOrNull()
        if (fullProfile == null) {
            stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "No VBook profile is available")
            stages += runner.skipped(STAGE_TRACK, ORDER_TRACK, "No VBook profile is available")
        } else {
            if (!fullProfile.hasReadableContentCapability()) {
                stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "VBook readable content capability is not declared")
            } else if (fullBook == null || chapter == null) {
                stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "No VBook chapter is available")
            } else {
                stages += runner.run(STAGE_CONTENT, ORDER_CONTENT) {
                    getContent(source, fullBook, chapter).also {
                        if (it.isBlank()) {
                            throw NoStackTraceException("VBook content returned empty text")
                        }
                    }
                }.evidence
            }

            if (!fullProfile.hasTrackCapability()) {
                stages += runner.skipped(STAGE_TRACK, ORDER_TRACK, "VBook media track capability is not declared")
            } else if (fullBook == null || chapter == null) {
                stages += runner.skipped(STAGE_TRACK, ORDER_TRACK, "No VBook chapter is available")
            } else {
                stages += runner.run(STAGE_TRACK, ORDER_TRACK) {
                    resolveMedia(source, fullBook, chapter).also {
                        if (it.variants.isEmpty()) {
                            throw NoStackTraceException("VBook track returned no media variants")
                        }
                    }
                }.evidence
            }
        }

        return SourceCheckProbeResult(profile = profile, stages = stages)
    }

    private fun List<SearchBook>.firstValidSearchBook(stage: String): SearchBook {
        val book = firstOrNull()
            ?: throw NoStackTraceException("$stage returned no items")
        if (book.name.isBlank() || book.bookUrl.isBlank()) {
            throw NoStackTraceException("$stage returned an item without title or URL")
        }
        return book
    }

    private fun VbookCapabilityProfile.hasReadableContentCapability(): Boolean =
        capabilities.any {
            it == VbookCapability.TEXT_CONTENT || it == VbookCapability.IMAGE_CONTENT
        }

    private fun VbookCapabilityProfile.hasTrackCapability(): Boolean =
        capabilities.any {
            it in setOf(
                VbookCapability.MEDIA_TRACK,
                VbookCapability.AUDIO_CONTENT,
                VbookCapability.VIDEO_CONTENT,
                VbookCapability.HLS,
                VbookCapability.DASH,
                VbookCapability.DIRECT_AUDIO,
                VbookCapability.DIRECT_VIDEO,
            )
        }

    private fun pluginId(source: BookSource): String? = source.bookSourceUrl
        .takeIf { it.startsWith(VbookPluginAdapter.SOURCE_PREFIX) }
        ?.removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
        ?.takeIf { PLUGIN_ID.matches(it) }

    private companion object {
        const val STAGE_MANIFEST = "manifest"
        const val STAGE_SCRIPTS = "scripts"
        const val STAGE_HOME = "home"
        const val STAGE_SEARCH = "search"
        const val STAGE_DETAIL = "detail"
        const val STAGE_TOC = "toc"
        const val STAGE_CONTENT = "content"
        const val STAGE_TRACK = "track"
        const val ORDER_MANIFEST = 0
        const val ORDER_SCRIPTS = 1
        const val ORDER_HOME = 2
        const val ORDER_SEARCH = 3
        const val ORDER_DETAIL = 4
        const val ORDER_TOC = 5
        const val ORDER_CONTENT = 6
        const val ORDER_TRACK = 7
        const val FIRST_PAGE = 1
        const val DEFAULT_SEARCH_KEYWORD = "test"
        const val PLUGIN_ROOT_DIRECTORY = "vbook_plugins"
        val PLUGIN_ID = Regex("[a-f0-9]{16,64}")
    }
}
