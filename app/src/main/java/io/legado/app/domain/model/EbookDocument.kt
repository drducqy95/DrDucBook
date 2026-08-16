package io.legado.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class EbookLayoutMode { REFLOW, FIXED_PAGE }

@Serializable
data class EbookPageSize(
    val width: Float = 794f,
    val height: Float = 1123f,
)

@Serializable
data class EbookPadding(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

@Serializable
data class EbookBlockGeometry(
    val x: Float = 40f,
    val y: Float = 40f,
    val width: Float = 420f,
    val height: Float = 120f,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val page: Int = 0,
    val padding: EbookPadding = EbookPadding(),
    val backgroundColor: String? = null,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
)

@Serializable
data class EbookInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

@Serializable
sealed interface EbookBlock {
    val id: String
    val name: String
    val readingOrder: Int
    val geometry: EbookBlockGeometry?
}

@Serializable
@SerialName("paragraph")
data class EbookParagraphBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Paragraph",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val text: String = "",
    val style: EbookInlineStyle = EbookInlineStyle(),
) : EbookBlock

@Serializable
@SerialName("heading")
data class EbookHeadingBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Heading",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val text: String = "",
    val level: Int = 2,
) : EbookBlock

@Serializable
@SerialName("quote")
data class EbookQuoteBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Quote",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val text: String = "",
    val attribution: String = "",
) : EbookBlock

@Serializable
@SerialName("image")
data class EbookImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Image",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val uri: String = "",
    val alt: String = "",
    val caption: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
) : EbookBlock

@Serializable
enum class EbookDividerType { LINE, ORNAMENT, SPACE }

@Serializable
@SerialName("divider")
data class EbookDividerBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Divider",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val type: EbookDividerType = EbookDividerType.LINE,
) : EbookBlock

@Serializable
@SerialName("page_break")
data class EbookPageBreakBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Page break",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
) : EbookBlock

@Serializable
@SerialName("code")
data class EbookCodeBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Code",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val text: String = "",
    val language: String = "",
) : EbookBlock

@Serializable
@SerialName("list")
data class EbookListBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "List",
    override val readingOrder: Int = 0,
    override val geometry: EbookBlockGeometry? = null,
    val items: List<String> = emptyList(),
    val ordered: Boolean = false,
) : EbookBlock

@Serializable
data class EbookDocumentChapter(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val pageBreakBefore: Boolean = true,
    val blocks: List<EbookBlock> = emptyList(),
)

@Serializable
data class EbookMetadata(
    val title: String = "",
    val author: String = "",
    val language: String = "vi",
    val customFontPaths: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
)

@Serializable
data class EbookDocument(
    val layoutMode: EbookLayoutMode = EbookLayoutMode.REFLOW,
    val pageSize: EbookPageSize? = null,
    val chapters: List<EbookDocumentChapter> = emptyList(),
    val metadata: EbookMetadata = EbookMetadata(),
)

fun AuthoringProject.resolveEbookDocument(): EbookDocument = document ?: EbookDocument(
    layoutMode = EbookLayoutMode.REFLOW,
    metadata = EbookMetadata(title, author, language),
    chapters = chapters.map { chapter ->
        EbookDocumentChapter(
            id = chapter.id,
            title = chapter.title,
            blocks = legacyContentToBlocks(chapter.content),
        )
    },
)

fun legacyContentToBlocks(content: String): List<EbookBlock> {
    if (content.isBlank()) return emptyList()
    return content.split(Regex("\\n\\s*\\n"))
        .mapIndexedNotNull { index, part ->
            val value = part.trim()
            when {
                value.isBlank() -> null
                IMAGE_MARKER.matches(value) -> EbookImageBlock(
                    uri = IMAGE_MARKER.matchEntire(value)?.groupValues?.get(1).orEmpty(),
                    readingOrder = index,
                )
                else -> EbookParagraphBlock(text = value, readingOrder = index)
            }
        }
}

fun EbookDocument.toAuthoringChapters(
    existing: List<AuthoringChapter>,
    now: Long,
): List<AuthoringChapter> = chapters.map { chapter ->
    val previous = existing.firstOrNull { it.id == chapter.id }
    AuthoringChapter(
        id = chapter.id,
        title = chapter.title,
        content = chapter.blocks.sortedBy(EbookBlock::readingOrder).joinToString("\n\n", transform = ::blockPlainText),
        createdAt = previous?.createdAt ?: now,
        updatedAt = now,
    )
}

fun blockPlainText(block: EbookBlock): String = when (block) {
    is EbookParagraphBlock -> block.text
    is EbookHeadingBlock -> block.text
    is EbookQuoteBlock -> listOf(block.text, block.attribution).filter(String::isNotBlank).joinToString("\n")
    is EbookImageBlock -> "[image:${block.uri}]"
    is EbookDividerBlock -> if (block.type == EbookDividerType.SPACE) "" else "---"
    is EbookPageBreakBlock -> ""
    is EbookCodeBlock -> block.text
    is EbookListBlock -> block.items.mapIndexed { index, value ->
        if (block.ordered) "${index + 1}. $value" else "- $value"
    }.joinToString("\n")
}

fun EbookBlock.withGeometry(value: EbookBlockGeometry?): EbookBlock = when (this) {
    is EbookParagraphBlock -> copy(geometry = value)
    is EbookHeadingBlock -> copy(geometry = value)
    is EbookQuoteBlock -> copy(geometry = value)
    is EbookImageBlock -> copy(geometry = value)
    is EbookDividerBlock -> copy(geometry = value)
    is EbookPageBreakBlock -> copy(geometry = value)
    is EbookCodeBlock -> copy(geometry = value)
    is EbookListBlock -> copy(geometry = value)
}

fun EbookBlock.withReadingOrder(value: Int): EbookBlock = when (this) {
    is EbookParagraphBlock -> copy(readingOrder = value)
    is EbookHeadingBlock -> copy(readingOrder = value)
    is EbookQuoteBlock -> copy(readingOrder = value)
    is EbookImageBlock -> copy(readingOrder = value)
    is EbookDividerBlock -> copy(readingOrder = value)
    is EbookPageBreakBlock -> copy(readingOrder = value)
    is EbookCodeBlock -> copy(readingOrder = value)
    is EbookListBlock -> copy(readingOrder = value)
}

private val IMAGE_MARKER = Regex("\\[image:(.+?)]")
