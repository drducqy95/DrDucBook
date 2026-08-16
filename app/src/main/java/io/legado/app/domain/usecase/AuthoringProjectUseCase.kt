package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.AuthoringRecoveryDiagnostic
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.WritingWorkflow
import io.legado.app.domain.model.WritingWorkflowStage
import io.legado.app.domain.model.VbookContentLockPolicy
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.jsoup.Jsoup
import java.util.UUID

class AuthoringProjectUseCase(
    private val gateway: AuthoringProjectGateway,
    private val cachedChapterGateway: CachedChapterGateway,
) {
    fun observe(kind: AuthoringProjectKind): Flow<List<AuthoringProject>> =
        gateway.observeProjects(kind)

    suspend fun get(id: String): AuthoringProject? = gateway.getProject(id)

    suspend fun create(kind: AuthoringProjectKind, title: String): AuthoringProject {
        val now = System.currentTimeMillis()
        return AuthoringProject(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title.trim().ifBlank {
                if (kind == AuthoringProjectKind.WRITING) "Tác phẩm mới" else "Ebook mới"
            },
            writingWorkflow = WritingWorkflow(
                stage = if (kind == AuthoringProjectKind.WRITING) {
                    WritingWorkflowStage.IDEA_INPUT
                } else {
                    WritingWorkflowStage.READY_TO_WRITE
                }
            ),
            createdAt = now,
            updatedAt = now,
        ).also { gateway.saveProject(it) }
    }

    suspend fun save(project: AuthoringProject) {
        gateway.saveProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = gateway.deleteProject(id)

    suspend fun duplicate(project: AuthoringProject): AuthoringProject {
        val now = System.currentTimeMillis()
        val copy = project.copy(
            id = UUID.randomUUID().toString(),
            title = "${project.title} Copy".trim(),
            chapters = project.chapters.map { chapter ->
                chapter.copy(
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now,
                )
            },
            createdAt = now,
            updatedAt = now,
        )
        gateway.saveProject(copy)
        return copy
    }

    suspend fun addChapter(project: AuthoringProject, title: String): AuthoringProject {
        val now = System.currentTimeMillis()
        return project.copy(
            chapters = project.chapters + AuthoringChapter(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "Chương ${project.chapters.size + 1}" },
                createdAt = now,
                updatedAt = now,
            ),
            updatedAt = now,
        ).also { gateway.saveProject(it) }
    }

    suspend fun cloneDownloadedBook(bookUrl: String): AuthoringProject {
        val book = requireNotNull(cachedChapterGateway.getBook(bookUrl.trim())) {
            "Không tìm thấy sách trong giá sách"
        }
        VbookContentLockPolicy.requireUnlocked(book.origin, AppConfig.vbookEbookUnlockCode)
        val snapshots = cachedChapterGateway.streamChapterCache(book).toList()
        val downloaded = snapshots.filter { !it.content.isNullOrBlank() }
        require(downloaded.isNotEmpty()) { "Sách chưa có chương đã tải" }
        val now = System.currentTimeMillis()
        return AuthoringProject(
            id = UUID.randomUUID().toString(),
            kind = AuthoringProjectKind.EBOOK_EDITOR,
            title = book.name,
            author = book.author,
            description = book.intro.orEmpty(),
            coverPath = book.getDisplayCover(),
            sourceBookUrl = book.bookUrl,
            sourceOrigin = book.origin,
            chapters = downloaded.map { snapshot ->
                AuthoringChapter(
                    id = UUID.randomUUID().toString(),
                    title = snapshot.title.orEmpty(),
                    content = htmlToEditableText(snapshot.content.orEmpty()),
                    createdAt = now,
                    updatedAt = now,
                )
            },
            createdAt = now,
            updatedAt = now,
        ).also { gateway.saveProject(it) }
    }

    suspend fun importImage(
        projectId: String,
        displayName: String,
        bytes: ByteArray,
    ): String = gateway.importImage(projectId, displayName, bytes)

    suspend fun recoveryDiagnostics(): List<AuthoringRecoveryDiagnostic> =
        gateway.recoveryDiagnostics()

    suspend fun restoreLatestProjectSnapshot(projectId: String): AuthoringProject? =
        gateway.restoreLatestProjectSnapshot(projectId)

    suspend fun deleteRecoveryDiagnostic(id: String) =
        gateway.deleteRecoveryDiagnostic(id)

    private fun htmlToEditableText(html: String): String {
        if (!html.contains('<')) return html
        val document = Jsoup.parseBodyFragment(html)
        document.select("br").append("\\n")
        document.select("p,div,h1,h2,h3,h4,h5,h6,li").prepend("\\n").append("\\n")
        document.select("img[src]").forEach { image ->
            image.after("\\n[image:${image.attr("src")}]\\n")
        }
        return document.text()
            .replace("\\n", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
