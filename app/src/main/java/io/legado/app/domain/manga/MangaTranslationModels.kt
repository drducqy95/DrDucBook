package io.legado.app.domain.manga

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.TranslationConstants
import java.security.MessageDigest

enum class MangaOcrScript { LATIN, CHINESE, JAPANESE, KOREAN }

enum class MangaTextOrientation { HORIZONTAL, VERTICAL }

enum class MangaTranslationStage { HASHING, OCR, ORDERING, TRANSLATING, COMPLETE }

@Stable
data class MangaPoint(val x: Int, val y: Int)

@Stable
data class MangaRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun union(other: MangaRect): MangaRect = MangaRect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )
}

@Stable
data class MangaTextBlock(
    val id: String,
    val text: String,
    val polygon: List<MangaPoint>,
    val boundingBox: MangaRect,
    val confidence: Float,
    val orientation: MangaTextOrientation,
    val readingOrder: Int = 0,
    val script: MangaOcrScript,
)

@Stable
data class MangaBubbleRegion(
    val id: String,
    val blockIds: List<String>,
    val sourceText: String,
    val boundingBox: MangaRect,
    val readingOrder: Int,
    val userAdjustedOrder: Int? = null,
)

@Stable
data class MangaOverlayStyle(
    val textColor: Long = 0xFF111111,
    val backgroundColor: Long = 0xEFFFFFFF,
    val textSizeSp: Float = 16f,
    val alignment: String = "center",
)

@Stable
data class MangaTranslationResult(
    val region: MangaBubbleRegion,
    val translatedText: String,
    val style: MangaOverlayStyle = MangaOverlayStyle(),
    val userEdited: Boolean = false,
    val dependencyHash: String = "",
)

@Stable
data class MangaOverlayPage(
    val imageId: String,
    val imageHash: String,
    val cacheKey: String,
    val width: Int,
    val height: Int,
    val blocks: List<MangaTextBlock>,
    val translations: List<MangaTranslationResult>,
    val createdAt: Long = System.currentTimeMillis(),
)

@Stable
data class MangaExportManifest(
    val schemaVersion: Int = 1,
    val sourceHashes: List<String>,
    val ocrVersion: String,
    val providerModelPromptRevision: String,
    val targetLanguage: String,
    val exportedAt: Long = System.currentTimeMillis(),
)

enum class MangaExportFormat { IMAGE_SET, CBZ, PDF }

data class MangaExportPage(
    val page: MangaOverlayPage,
    val sourceBytes: ByteArray,
)

data class MangaExportPlan(
    val baseName: String,
    val pages: List<MangaExportPage>,
    val format: MangaExportFormat,
    val manifest: MangaExportManifest,
) {
    fun validate() {
        require(baseName.isNotBlank()) { "Manga export name is empty" }
        require(pages.isNotEmpty()) { "Manga export has no pages" }
        require(pages.map { it.page.imageHash }.distinct().size == pages.size) {
            "Manga export contains duplicate source pages"
        }
        pages.forEach { item ->
            require(item.sourceBytes.isNotEmpty()) { "Manga source image is empty" }
            require(mangaImageHash(item.sourceBytes) == item.page.imageHash) {
                "Manga source image changed after translation"
            }
        }
        require(manifest.sourceHashes == pages.map { it.page.imageHash }) {
            "Manga export manifest does not match source pages"
        }
    }

    fun outputFileName(): String = safeMangaExportName(baseName) + when (format) {
        MangaExportFormat.IMAGE_SET -> ""
        MangaExportFormat.CBZ -> ".cbz"
        MangaExportFormat.PDF -> ".pdf"
    }
}

fun safeMangaExportName(value: String): String = value.trim()
    .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
    .trim('.', ' ')
    .ifBlank { "manga-translation" }

data class MangaTranslationRequest(
    val imageId: String,
    val imageBytes: ByteArray,
    val script: MangaOcrScript,
    val verticalReading: Boolean,
    val provider: String,
    val targetLanguage: String,
    val ocrVersion: String,
    val providerModelPromptRevision: String,
    val book: Book? = null,
)

fun mangaImageHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

fun mangaTranslationCacheKey(
    imageHash: String,
    script: MangaOcrScript,
    ocrVersion: String,
    provider: String,
    providerModelPromptRevision: String,
    targetLanguage: String,
): String = MessageDigest.getInstance("SHA-256")
    .digest(
        listOf(
            imageHash,
            script.name,
            ocrVersion,
            provider,
            providerModelPromptRevision.takeUnless {
                provider == TranslationConstants.PROVIDER_APP_AI
            }.orEmpty(),
            targetLanguage,
        ).joinToString("\u0000").toByteArray()
    )
    .joinToString("") { "%02x".format(it) }

object MangaReadingOrder {
    fun order(
        blocks: List<MangaTextBlock>,
        verticalReading: Boolean,
    ): List<MangaTextBlock> {
        if (blocks.size <= 1) return blocks.mapIndexed { index, block ->
            block.copy(readingOrder = index)
        }
        val medianHeight = blocks.map { it.boundingBox.height.coerceAtLeast(1) }
            .sorted()
            .let { it[it.size / 2] }
        val rowTolerance = (medianHeight * 0.6f).toInt().coerceAtLeast(8)
        val comparator = if (verticalReading) {
            compareByDescending<MangaTextBlock> { it.boundingBox.centerX / rowTolerance }
                .thenBy { it.boundingBox.top }
                .thenByDescending { it.boundingBox.right }
        } else {
            compareBy<MangaTextBlock> { it.boundingBox.centerY / rowTolerance }
                .thenBy { it.boundingBox.left }
                .thenBy { it.boundingBox.top }
        }
        return blocks.sortedWith(comparator).mapIndexed { index, block ->
            block.copy(readingOrder = index)
        }
    }

    fun groupIntoBubbles(blocks: List<MangaTextBlock>): List<MangaBubbleRegion> {
        if (blocks.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<MangaTextBlock>>()
        blocks.sortedBy(MangaTextBlock::readingOrder).forEach { block ->
            val group = groups.lastOrNull()
            val previous = group?.lastOrNull()
            if (previous != null && belongsToSameBubble(previous.boundingBox, block.boundingBox)) {
                group += block
            } else {
                groups += mutableListOf(block)
            }
        }
        return groups.mapIndexed { index, group ->
            MangaBubbleRegion(
                id = "bubble-$index-${group.first().id}",
                blockIds = group.map(MangaTextBlock::id),
                sourceText = group.joinToString(" ") { it.text.trim() }.trim(),
                boundingBox = group.drop(1).fold(group.first().boundingBox) { bounds, block ->
                    bounds.union(block.boundingBox)
                },
                readingOrder = index,
            )
        }
    }

    private fun belongsToSameBubble(first: MangaRect, second: MangaRect): Boolean {
        val horizontalGap = maxOf(0, maxOf(first.left, second.left) - minOf(first.right, second.right))
        val verticalGap = maxOf(0, maxOf(first.top, second.top) - minOf(first.bottom, second.bottom))
        val scale = maxOf(first.height, second.height, 1)
        return horizontalGap <= scale * 2 && verticalGap <= scale
    }
}
