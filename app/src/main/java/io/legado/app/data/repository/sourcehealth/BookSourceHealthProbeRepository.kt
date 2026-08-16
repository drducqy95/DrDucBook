package io.legado.app.data.repository.sourcehealth

import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.BookSourceHealthProbeGateway
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook

class BookSourceHealthProbeRepository(
    private val now: () -> Long = System::currentTimeMillis,
    private val getExploreKinds: suspend (BookSource) -> List<ExploreKind> = { source ->
        source.exploreKinds()
    },
    private val searchBooks: suspend (BookSource, String) -> List<SearchBook> = { source, key ->
        WebBook.searchBookAwait(source, key)
    },
    private val exploreBooks: suspend (BookSource, String) -> List<SearchBook> = { source, url ->
        WebBook.exploreBookAwait(source, url)
    },
    private val getBookInfo: suspend (BookSource, Book) -> Book = { source, book ->
        WebBook.getBookInfoAwait(source, book)
    },
    private val getChapters: suspend (BookSource, Book) -> List<BookChapter> = { source, book ->
        WebBook.getChapterListAwait(source, book).getOrThrow()
    },
    private val getContent: suspend (BookSource, Book, BookChapter) -> String = { source, book, chapter ->
        WebBook.getContentAwait(
            bookSource = source,
            book = book,
            bookChapter = chapter,
            needSave = false,
        )
    },
) : BookSourceHealthProbeGateway {

    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult {
        val runner = SourceCheckStageRunner(now)
        val stages = mutableListOf<SourceCheckStageEvidence>()
        var sample: SearchBook? = null
        var book: Book? = null
        var chapters: List<BookChapter> = emptyList()
        var content: String? = null

        stages += runner.run(STAGE_REACHABILITY, ORDER_REACHABILITY) {
            require(source.bookSourceUrl.isNotBlank()) { "Book source URL is blank" }
            require(!source.searchUrl.isNullOrBlank() || !source.exploreUrl.isNullOrBlank()) {
                "Book source has no search or explore rule"
            }
        }.evidence

        if (source.searchUrl.isNullOrBlank()) {
            stages += runner.skipped(STAGE_SEARCH, ORDER_SEARCH, "Search rule is not configured")
        } else {
            val search = runner.run(STAGE_SEARCH, ORDER_SEARCH) {
                searchBooks(source, source.getCheckKeyword(DEFAULT_SEARCH_KEYWORD))
                    .firstValidSearchBook("Search")
            }
            stages += search.evidence
            sample = sample ?: search.value
        }

        if (!source.enabledExplore || source.exploreUrl.isNullOrBlank()) {
            stages += runner.skipped(STAGE_EXPLORE, ORDER_EXPLORE, "Explore rule is not enabled")
        } else {
            val explore = runner.run(STAGE_EXPLORE, ORDER_EXPLORE) {
                val exploreUrl = getExploreKinds(source)
                    .firstOrNull { !it.url.isNullOrBlank() }
                    ?.url
                    ?: throw NoStackTraceException("Explore rule returned no usable URL")
                exploreBooks(source, exploreUrl)
                    .firstValidSearchBook("Explore")
            }
            stages += explore.evidence
            sample = sample ?: explore.value
        }

        if (!profile.includesStandard()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        book = sample?.toBook()
        if (book == null) {
            stages += runner.skipped(STAGE_DETAIL, ORDER_DETAIL, "No book item is available")
        } else if (!source.hasBookInfoCapability()) {
            stages += runner.skipped(STAGE_DETAIL, ORDER_DETAIL, "Book detail rule is not configured")
        } else {
            val detail = runner.run(STAGE_DETAIL, ORDER_DETAIL) {
                getBookInfo(source, book!!).also {
                    require(it.bookUrl.isNotBlank()) { "Book detail returned no book URL" }
                }
            }
            stages += detail.evidence
            book = detail.value ?: book
        }

        if (book == null) {
            stages += runner.skipped(STAGE_TOC, ORDER_TOC, "No book detail is available")
        } else if (!source.hasTocCapability()) {
            stages += runner.skipped(STAGE_TOC, ORDER_TOC, "Book TOC rule is not configured")
        } else {
            val tocBook = book
            val toc = runner.run(STAGE_TOC, ORDER_TOC) {
                getChapters(source, tocBook)
                    .filterNot { it.isVolume }
                    .also {
                        if (it.isEmpty()) {
                            throw NoStackTraceException("TOC returned no chapters")
                        }
                    }
            }
            stages += toc.evidence
            chapters = toc.value.orEmpty()
        }

        if (!profile.includesFull()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        val contentBook = book
        val chapter = chapters.firstOrNull()
        if (!source.hasContentCapability()) {
            stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "Book content rule is not configured")
        } else if (contentBook == null || chapter == null) {
            stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "No chapter is available")
        } else {
            val contentResult = runner.run(STAGE_CONTENT, ORDER_CONTENT) {
                getContent(source, contentBook, chapter).also {
                    if (it.isBlank()) {
                        throw NoStackTraceException("Content rule returned empty text")
                    }
                }
            }
            stages += contentResult.evidence
            content = contentResult.value
        }

        if (source.bookSourceType !in setOf(BookSourceType.audio, BookSourceType.video)) {
            stages += runner.skipped(STAGE_MEDIA, ORDER_MEDIA, "Media stage is only for audio or video sources")
        } else if (chapter == null) {
            stages += runner.skipped(STAGE_MEDIA, ORDER_MEDIA, "No chapter is available")
        } else {
            stages += runner.run(STAGE_MEDIA, ORDER_MEDIA) {
                val chapterMedia = chapter.resourceUrl.orEmpty()
                val contentMedia = content.orEmpty()
                require(chapterMedia.isNotBlank() || HTTP_URL_REGEX.containsMatchIn(contentMedia)) {
                    "Media URL was not resolved"
                }
            }.evidence
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

    private fun BookSource.hasBookInfoCapability(): Boolean =
        ruleBookInfo?.let { rule ->
            listOf(
                rule.init,
                rule.name,
                rule.author,
                rule.intro,
                rule.kind,
                rule.lastChapter,
                rule.updateTime,
                rule.coverUrl,
                rule.tocUrl,
                rule.wordCount,
                rule.downloadUrls,
            ).any { !it.isNullOrBlank() }
        } == true

    private fun BookSource.hasTocCapability(): Boolean =
        ruleToc?.let { rule ->
            listOf(
                rule.preUpdateJs,
                rule.chapterList,
                rule.chapterName,
                rule.chapterUrl,
                rule.nextTocUrl,
            ).any { !it.isNullOrBlank() }
        } == true

    private fun BookSource.hasContentCapability(): Boolean =
        ruleContent?.let { rule ->
            listOf(
                rule.content,
                rule.subContent,
                rule.title,
                rule.nextContentUrl,
                rule.webJs,
                rule.sourceRegex,
            ).any { !it.isNullOrBlank() }
        } == true

    private companion object {
        const val STAGE_REACHABILITY = "reachability"
        const val STAGE_SEARCH = "search"
        const val STAGE_EXPLORE = "explore"
        const val STAGE_DETAIL = "detail"
        const val STAGE_TOC = "toc"
        const val STAGE_CONTENT = "content"
        const val STAGE_MEDIA = "media"
        const val ORDER_REACHABILITY = 0
        const val ORDER_SEARCH = 1
        const val ORDER_EXPLORE = 2
        const val ORDER_DETAIL = 3
        const val ORDER_TOC = 4
        const val ORDER_CONTENT = 5
        const val ORDER_MEDIA = 6
        const val DEFAULT_SEARCH_KEYWORD = "test"
        val HTTP_URL_REGEX = Regex("https?://\\S+")
    }
}
