package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.VbookContentLockPolicy
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.flow.toList
import org.jsoup.Jsoup
import java.util.UUID

enum class CloneContentVariant { RAW, MACHINE_DRAFT, USER_EDITED, FINAL }

data class CloneBookCandidate(
    val bookUrl: String,
    val title: String,
    val author: String,
    val chapterCount: Int,
)

data class CloneDownloadedBookRequest(
    val bookUrl: String,
    val kind: AuthoringProjectKind = AuthoringProjectKind.EBOOK_EDITOR,
    val chapterIndices: Set<Int>? = null,
    val variant: CloneContentVariant = CloneContentVariant.RAW,
    val targetLanguage: String = "vi",
    val provider: String = "",
)

class CloneDownloadedBookUseCase(
    private val projectGateway: AuthoringProjectGateway,
    private val cachedChapterGateway: CachedChapterGateway,
    private val translationCacheGateway: TranslationCacheGateway,
) {
    suspend fun candidates(query: String = ""): List<CloneBookCandidate> {
        val normalized = query.trim()
        return cachedChapterGateway.getBooks().mapNotNull { book ->
            val count = cachedChapterGateway.getChapterCount(book.bookUrl)
            if (count <= 0 || normalized.isNotBlank() &&
                !book.name.contains(normalized, ignoreCase = true) &&
                !book.author.contains(normalized, ignoreCase = true)
            ) return@mapNotNull null
            CloneBookCandidate(book.bookUrl, book.name, book.author, count)
        }.sortedBy(CloneBookCandidate::title)
    }

    suspend fun execute(request: CloneDownloadedBookRequest): AuthoringProject {
        val book = requireNotNull(cachedChapterGateway.getBook(request.bookUrl.trim())) {
            "Book was not found in the bookshelf"
        }
        VbookContentLockPolicy.requireUnlocked(book.origin, AppConfig.vbookEbookUnlockCode)
        val snapshots = cachedChapterGateway.streamChapterCache(book).toList()
            .filter { snapshot ->
                !snapshot.content.isNullOrBlank() &&
                    (request.chapterIndices == null || snapshot.index in request.chapterIndices)
            }
        require(snapshots.isNotEmpty()) { "No downloaded chapter matches the selected scope" }
        val now = System.currentTimeMillis()
        val chapters = snapshots.mapNotNull { snapshot ->
            val raw = snapshot.content.orEmpty()
            val content = when (request.variant) {
                CloneContentVariant.RAW -> raw
                CloneContentVariant.MACHINE_DRAFT -> translatedContent(
                    book,
                    snapshot.index,
                    raw,
                    request,
                    RevisionStatus.MACHINE_DRAFT,
                )
                CloneContentVariant.USER_EDITED -> translatedContent(
                    book,
                    snapshot.index,
                    raw,
                    request,
                    RevisionStatus.USER_EDITED,
                )
                CloneContentVariant.FINAL -> translatedContent(
                    book,
                    snapshot.index,
                    raw,
                    request,
                    RevisionStatus.FINAL,
                )
            } ?: return@mapNotNull null
            AuthoringChapter(
                id = UUID.randomUUID().toString(),
                title = snapshot.title.orEmpty(),
                content = htmlToEditableText(content),
                createdAt = now,
                updatedAt = now,
            )
        }
        require(chapters.isNotEmpty()) { "The selected translation variant has no matching chapter" }
        var project = AuthoringProject(
            id = UUID.randomUUID().toString(),
            kind = request.kind,
            title = book.name,
            author = book.author,
            description = book.intro.orEmpty(),
            coverPath = book.getDisplayCover(),
            sourceBookUrl = book.bookUrl,
            sourceOrigin = book.origin,
            chapters = chapters,
            createdAt = now,
            updatedAt = now,
        )
        if (request.kind == AuthoringProjectKind.EBOOK_EDITOR) {
            project = project.copy(document = project.resolveEbookDocument())
        }
        projectGateway.saveProject(project)
        return project
    }

    private suspend fun translatedContent(
        book: Book,
        chapterIndex: Int,
        raw: String,
        request: CloneDownloadedBookRequest,
        status: RevisionStatus,
    ): String? {
        val chapter = cachedChapterGateway.getChapter(book.bookUrl, chapterIndex) ?: return null
        if (request.provider.isBlank()) {
            return if (status == RevisionStatus.MACHINE_DRAFT) {
                translationCacheGateway.readTranslation(
                    book,
                    chapter,
                    request.targetLanguage,
                    provider = null,
                )
            } else null
        }
        val revision = translationCacheGateway.getCurrentRevision(
            book,
            chapter,
            request.targetLanguage,
            request.provider,
            translationCacheGateway.computeContentHash(raw),
        ) ?: return null
        return revision.content.takeIf { revision.sourceStatus == status }
    }

    private fun htmlToEditableText(html: String): String {
        if (!html.contains('<')) return html
        val document = Jsoup.parseBodyFragment(html)
        document.select("br").append("\\n")
        document.select("p,div,h1,h2,h3,h4,h5,h6,li").prepend("\\n").append("\\n")
        document.select("img[src]").forEach { image ->
            image.after("\\n[image:${image.attr("src")}]\\n")
        }
        return document.text().replace("\\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
