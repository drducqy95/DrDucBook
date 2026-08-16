package io.legado.app.domain.usecase

import android.content.Context
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.VbookContentLockPolicy
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.help.config.AppConfig
import io.legado.app.service.export.EbookExportChapter
import io.legado.app.service.export.EbookExportFormat
import io.legado.app.service.export.EbookExportImage
import io.legado.app.service.export.EbookExportLabels
import io.legado.app.service.export.EbookExportPayload
import io.legado.app.service.export.EbookExportWriter
import io.legado.app.service.export.EbookLayoutRenderer
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

class ExportAuthoringProjectUseCase(
    private val context: Context,
    private val cachedChapterGateway: CachedChapterGateway,
    private val validateEbookProject: ValidateEbookProjectUseCase,
    private val accountEntitlementUseCase: AccountEntitlementUseCase? = null,
) {
    suspend fun execute(
        project: AuthoringProject,
        format: EbookExportFormat,
    ): File = withContext(Dispatchers.IO) {
        accountEntitlementUseCase?.consume(
            AccountQuotaKind.EXPORT_EBOOK,
            listOf(project.id),
        )
        val sourceOrigin = project.sourceOrigin
            ?: project.sourceBookUrl
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { cachedChapterGateway.getBook(it)?.origin }
        VbookContentLockPolicy.requireUnlocked(sourceOrigin, AppConfig.vbookEbookUnlockCode)
        val validation = validateEbookProject.execute(project)
        require(validation.none { it.severity == EbookValidationSeverity.ERROR }) {
            validation.first { it.severity == EbookValidationSeverity.ERROR }.message
        }
        val directory = File(context.cacheDir, "authoring_exports").apply { mkdirs() }
        val document = project.resolveEbookDocument()
        val rendered = EbookLayoutRenderer.render(document, project.style)
        val payload = EbookExportPayload(
            title = project.title,
            author = project.author,
            intro = project.description,
            language = project.language,
            description = project.description,
            identifier = "urn:drducbook:authoring:${project.id}",
            subjects = listOfNotNull(project.kind.name),
            cover = project.coverPath?.let(::File)?.takeIf(File::isFile),
            chapters = rendered.chapters.mapIndexed { index, chapter ->
                val imagePaths = imageSources(chapter.html)
                val images = imagePaths.mapNotNull { source ->
                    localFile(source).takeIf(File::isFile)?.let { file ->
                        EbookExportImage(
                            source = source,
                            file = file,
                            fileName = file.name,
                            aliases = imageAliases(source),
                        )
                    }
                }
                EbookExportChapter(
                    index = index,
                    title = chapter.title,
                    html = chapter.html,
                    plainText = chapter.plainText,
                    images = images,
                    documentChapter = document.chapters.getOrNull(index)?.copy(blocks = chapter.blocks),
                )
            },
            labels = EbookExportLabels(
                author = "Tác giả",
                introduction = "Giới thiệu",
                tableOfContents = "Mục lục",
            ),
            layoutMode = rendered.layoutMode.name,
            viewportWidth = rendered.viewportWidth,
            viewportHeight = rendered.viewportHeight,
            layoutCss = rendered.css,
        )
        val safeName = project.title.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "ebook" }
        val target = EbookExportWriter(
            outputDirectory = FileDoc.fromDir(directory.absolutePath),
            charset = Charsets.UTF_8,
        ).write(payload, format, "$safeName.${format.extension}")
        requireNotNull(target.asFile()) { "Không thể tạo tệp xuất" }
    }

    private companion object {
        val IMAGE_SOURCE_ATTRIBUTES = listOf("src", "data-src", "data-original", "data-lazy-src")

        fun localFile(path: String): File = File(path.removePrefix("file://"))

        fun imageSources(html: String): List<String> {
            val result = linkedSetOf<String>()
            Jsoup.parseBodyFragment(html).select("img").forEach { image ->
                IMAGE_SOURCE_ATTRIBUTES.forEach { attribute ->
                    image.attr(attribute).trim().takeIf(String::isNotBlank)?.let(result::add)
                }
                image.attr("srcset")
                    .split(',')
                    .asSequence()
                    .map { it.trim().substringBefore(' ').trim() }
                    .filter(String::isNotBlank)
                    .forEach(result::add)
            }
            return result.toList()
        }

        fun imageAliases(source: String): List<String> = buildList {
            val localPath = source.removePrefix("file://")
            if (localPath != source) add(localPath)
            val escapedSource = xmlAttribute(source)
            if (escapedSource != source) add(escapedSource)
            val escapedLocalPath = xmlAttribute(localPath)
            if (escapedLocalPath != localPath && escapedLocalPath != escapedSource) {
                add(escapedLocalPath)
            }
        }.distinct()

        fun xmlAttribute(value: String): String = value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
    }
}
