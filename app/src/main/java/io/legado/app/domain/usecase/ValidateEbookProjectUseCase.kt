package io.legado.app.domain.usecase

import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.model.resolveEbookDocument
import java.io.File

enum class EbookValidationSeverity { ERROR, WARNING, INFO }

data class EbookValidationIssue(
    val severity: EbookValidationSeverity,
    val message: String,
    val chapterId: String? = null,
    val blockId: String? = null,
)

class ValidateEbookProjectUseCase {

    fun execute(project: AuthoringProject): List<EbookValidationIssue> {
        val document = project.resolveEbookDocument()
        val issues = mutableListOf<EbookValidationIssue>()
        if (document.chapters.any { it.id.isBlank() }) {
            issues += EbookValidationIssue(EbookValidationSeverity.ERROR, "Chapter id is missing")
        }
        duplicateIds(document.chapters.map { it.id }, "Duplicate chapter id", issues)
        val allBlocks = document.chapters.flatMap { chapter -> chapter.blocks.map { chapter.id to it } }
        if (allBlocks.any { it.second.id.isBlank() }) {
            issues += EbookValidationIssue(EbookValidationSeverity.ERROR, "Block id is missing")
        }
        duplicateIds(allBlocks.map { it.second.id }, "Duplicate block id", issues)
        validateResources(project, issues)
        validateInternalLinks(project, issues)
        document.chapters.forEach { chapter ->
            if (chapter.blocks.isEmpty()) {
                issues += EbookValidationIssue(
                    EbookValidationSeverity.WARNING,
                    "Chapter has no blocks",
                    chapter.id,
                )
            } else if (chapter.blocks.all { blockPlainText(it).isBlank() }) {
                issues += EbookValidationIssue(
                    EbookValidationSeverity.WARNING,
                    "Chapter content is empty",
                    chapter.id,
                )
            }
            chapter.blocks.forEach { block -> validateBlock(project, document.layoutMode, chapter.id, block, issues) }
        }
        return issues
    }

    private fun validateBlock(
        project: AuthoringProject,
        mode: EbookLayoutMode,
        chapterId: String,
        block: EbookBlock,
        issues: MutableList<EbookValidationIssue>,
    ) {
        if (block is EbookImageBlock) {
            val file = localFile(block.uri)
            if (!file.isFile || file.length() <= 0L) {
                issues += issue(EbookValidationSeverity.ERROR, "Image is missing or corrupt", chapterId, block)
            }
            if (block.alt.isBlank()) {
                issues += issue(EbookValidationSeverity.WARNING, "Image is missing alt text", chapterId, block)
            }
        }
        if (mode == EbookLayoutMode.FIXED_PAGE) {
            val geometry = block.geometry
            if (geometry == null || geometry.width <= 0f || geometry.height <= 0f ||
                geometry.x < 0f || geometry.y < 0f || geometry.page < 0
            ) {
                issues += issue(EbookValidationSeverity.ERROR, "Invalid fixed-layout geometry", chapterId, block)
            } else {
                val page = project.resolveEbookDocument().pageSize
                if (page != null && (geometry.x + geometry.width > page.width ||
                        geometry.y + geometry.height > page.height)
                ) {
                    issues += issue(EbookValidationSeverity.WARNING, "Block extends outside the page", chapterId, block)
                }
            }
        }
    }

    private fun validateResources(
        project: AuthoringProject,
        issues: MutableList<EbookValidationIssue>,
    ) {
        val document = project.resolveEbookDocument()
        val imagePaths = document.chapters.flatMap { chapter ->
            chapter.blocks.filterIsInstance<EbookImageBlock>().map(EbookImageBlock::uri)
        }.toSet()
        document.metadata.customFontPaths.forEach { path ->
            if (!localFile(path).isFile) {
                issues += EbookValidationIssue(EbookValidationSeverity.ERROR, "Font is missing: $path")
            }
        }
        val used = (imagePaths + document.metadata.customFontPaths + listOfNotNull(project.coverPath))
            .filter(String::isNotBlank)
            .map(::resourceKey)
            .toSet()
        document.metadata.resources.forEach { path ->
            val file = localFile(path)
            if (!file.isFile) {
                issues += EbookValidationIssue(EbookValidationSeverity.WARNING, "Resource is missing: $path")
            } else if (resourceKey(path) !in used) {
                issues += EbookValidationIssue(EbookValidationSeverity.INFO, "Orphan resource: $path")
            }
        }
    }

    private fun validateInternalLinks(
        project: AuthoringProject,
        issues: MutableList<EbookValidationIssue>,
    ) {
        val document = project.resolveEbookDocument()
        val targets = buildSet {
            document.chapters.forEach { chapter ->
                add(chapter.id)
                chapter.blocks.forEach { add(it.id) }
            }
        }
        document.chapters.forEach { chapter ->
            chapter.blocks.forEach { block ->
                INTERNAL_LINK.findAll(blockPlainText(block)).forEach { match ->
                    val target = match.groupValues[1].ifBlank { match.groupValues[2] }
                    if (target !in targets) {
                        issues += issue(
                            EbookValidationSeverity.ERROR,
                            "Broken internal link: #$target",
                            chapter.id,
                            block,
                        )
                    }
                }
            }
        }
    }

    private fun duplicateIds(
        ids: List<String>,
        message: String,
        issues: MutableList<EbookValidationIssue>,
    ) {
        ids.groupingBy(String::trim).eachCount().filterValues { it > 1 }.keys.forEach { id ->
            issues += EbookValidationIssue(EbookValidationSeverity.ERROR, "$message: $id")
        }
    }

    private fun issue(
        severity: EbookValidationSeverity,
        message: String,
        chapterId: String,
        block: EbookBlock,
    ) = EbookValidationIssue(severity, message, chapterId, block.id)

    private companion object {
        val INTERNAL_LINK = Regex("""(?:href=["']#([^"']+)|\]\(#([^)]+)\))""")

        fun localFile(path: String): File = File(path.removePrefix("file://"))

        fun resourceKey(path: String): String = localFile(path).absolutePath
    }
}
