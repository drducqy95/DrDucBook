package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.MangaOcrGateway
import io.legado.app.domain.gateway.MangaOcrResult
import io.legado.app.domain.gateway.MangaTextTranslationGateway
import io.legado.app.domain.gateway.MangaTranslationCacheGateway
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaReadingOrder
import io.legado.app.domain.manga.MangaTranslationRequest
import io.legado.app.domain.manga.MangaTranslationResult
import io.legado.app.domain.manga.MangaTranslationStage
import io.legado.app.domain.manga.mangaImageHash
import io.legado.app.domain.manga.mangaTranslationCacheKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class TranslateMangaPageUseCase(
    private val ocrGateway: MangaOcrGateway,
    private val translationGateway: MangaTextTranslationGateway,
    private val cacheGateway: MangaTranslationCacheGateway,
) {
    suspend fun execute(
        request: MangaTranslationRequest,
        force: Boolean = false,
        onProgress: (MangaTranslationStage, Int, Int) -> Unit = { _, _, _ -> },
        onPartialPage: suspend (MangaOverlayPage) -> Unit = {},
    ): MangaOverlayPage {
        require(request.imageBytes.isNotEmpty()) { "Manga image is empty" }
        onProgress(MangaTranslationStage.HASHING, 0, 1)
        val imageHash = mangaImageHash(request.imageBytes)
        val cacheKey = mangaTranslationCacheKey(
            imageHash = imageHash,
            script = request.script,
            ocrVersion = request.ocrVersion,
            provider = request.provider,
            providerModelPromptRevision = request.providerModelPromptRevision,
            targetLanguage = request.targetLanguage,
        )
        val cachedPage = if (force) null else cacheGateway.read(cacheKey)

        currentCoroutineContext().ensureActive()
        val ocr = if (cachedPage != null) {
            MangaOcrResult(cachedPage.width, cachedPage.height, cachedPage.blocks)
        } else {
            onProgress(MangaTranslationStage.OCR, 0, 1)
            ocrGateway.recognize(request.imageBytes, request.script)
        }
        val ordered = if (cachedPage != null) {
            cachedPage.blocks
        } else {
            onProgress(MangaTranslationStage.ORDERING, 0, ocr.blocks.size)
            MangaReadingOrder.order(ocr.blocks, request.verticalReading)
        }
        val regions = MangaReadingOrder.groupIntoBubbles(ordered)
        val dependencies = regions.associate { region ->
            region.id to translationGateway.dependencyHash(
                text = region.sourceText,
                provider = request.provider,
                book = request.book,
            )
        }
        val cachedByRegion = cachedPage?.translations
            .orEmpty()
            .associateBy { it.region.id }
        val translations = linkedMapOf<String, MangaTranslationResult>()
        if (!force) {
            regions.forEach { region ->
                val cached = cachedByRegion[region.id] ?: return@forEach
                val cachedDependencyHash: String? = cached.dependencyHash
                if (cached.region.sourceText == region.sourceText &&
                    (cached.userEdited || cachedDependencyHash.orEmpty() == dependencies[region.id])
                ) {
                    translations[region.id] = cached.copy(region = region)
                }
            }
        }

        fun currentPage(): MangaOverlayPage = MangaOverlayPage(
            imageId = request.imageId,
            imageHash = imageHash,
            cacheKey = cacheKey,
            width = ocr.width,
            height = ocr.height,
            blocks = ordered,
            translations = regions.mapNotNull { translations[it.id] },
            createdAt = cachedPage?.createdAt ?: System.currentTimeMillis(),
        )

        if (translations.size == regions.size) {
            onProgress(MangaTranslationStage.COMPLETE, regions.size, regions.size)
            return currentPage()
        }

        currentPage().also {
            cacheGateway.write(it)
            onPartialPage(it)
        }
        regions.forEachIndexed { index, region ->
            if (region.id in translations) return@forEachIndexed
            currentCoroutineContext().ensureActive()
            onProgress(MangaTranslationStage.TRANSLATING, index, regions.size)
            val translated = translationGateway.translate(
                text = region.sourceText,
                provider = request.provider,
                targetLanguage = request.targetLanguage,
                book = request.book,
            ).trim()
            require(translated.isNotEmpty()) { "Manga translation returned empty text" }
            val previous = cachedByRegion[region.id]
            translations[region.id] = MangaTranslationResult(
                region = region,
                translatedText = translated,
                style = previous?.style ?: io.legado.app.domain.manga.MangaOverlayStyle(),
                dependencyHash = dependencies.getValue(region.id),
            )
            currentPage().also {
                cacheGateway.write(it)
                onPartialPage(it)
            }
        }
        val page = currentPage()
        cacheGateway.write(page)
        onProgress(MangaTranslationStage.COMPLETE, regions.size, regions.size)
        return page
    }
}
