package io.legado.app.ui.book.read.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.MangaTranslationCacheGateway
import io.legado.app.domain.manga.MangaOcrScript
import io.legado.app.domain.manga.MangaExportFormat
import io.legado.app.domain.manga.MangaExportManifest
import io.legado.app.domain.manga.MangaExportPage
import io.legado.app.domain.manga.MangaExportPlan
import io.legado.app.domain.manga.MangaTranslationRequest
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaTranslationResult
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.usecase.TranslateMangaPageUseCase
import io.legado.app.constant.FeatureFlags
import io.legado.app.ui.config.readMangaConfig.ReadMangaConfig
import io.legado.app.ui.config.translation.TranslationConfig
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MangaTranslationOverlayController(
    private val scope: CoroutineScope,
    private val translateMangaPage: TranslateMangaPageUseCase,
    private val quickTranslationGateway: QuickTranslationGateway,
    private val cacheGateway: MangaTranslationCacheGateway,
    private val onEditRequest: (String, MangaTranslationResult) -> Unit,
    private val bookProvider: () -> Book? = { null },
) {
    private val jobs = mutableMapOf<String, Job>()
    private val concurrency = Semaphore(2)
    private val overlays = mutableMapOf<String, WeakReference<MangaTranslationOverlay>>()
    private val pageOrder = linkedMapOf<String, Long>()
    private var nextPageOrder = 0L
    private val pages = object : LinkedHashMap<String, MangaOverlayPage>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MangaOverlayPage>?,
        ): Boolean = size > 8
    }
    private val exportPages = object : LinkedHashMap<String, MangaExportPage>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MangaExportPage>?,
        ): Boolean = size > 8
    }

    fun onImageReady(imageUrl: String, drawable: Drawable, imageView: ImageView) {
        synchronized(pageOrder) {
            if (imageUrl !in pageOrder) pageOrder[imageUrl] = nextPageOrder++
        }
        val root = imageView.parent as? FrameLayout ?: return
        val overlay = root.children.filterIsInstance<MangaTranslationOverlay>().firstOrNull()
            ?: MangaTranslationOverlay(root.context).also { child ->
                root.addView(
                    child,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )
            }
        overlay.imageView = imageView
        overlay.onTranslationClick = { result -> onEditRequest(imageUrl, result) }
        overlays[imageUrl] = WeakReference(overlay)
        if (!FeatureFlags.mangaTranslation || !ReadMangaConfig.mangaTranslationEnabled) {
            jobs.remove(imageUrl)?.cancel()
            overlay.clearPage()
            return
        }
        if (jobs[imageUrl]?.isActive == true) return
        jobs[imageUrl] = scope.launch {
            concurrency.withPermit {
                runCatching {
                    val bytes = withContext(Dispatchers.Default) { drawable.toOcrBytes() }
                    val provider = TranslationConfig.llmProvider
                    val revision = providerRevision(provider)
                    val translatedPage = translateMangaPage.execute(
                        request = MangaTranslationRequest(
                            imageId = imageUrl,
                            imageBytes = bytes,
                            script = ReadMangaConfig.mangaTranslationScript.toOcrScript(),
                            verticalReading = ReadMangaConfig.mangaTranslationVerticalReading,
                            provider = provider,
                            targetLanguage = TranslationConfig.llmTargetLanguage,
                            ocrVersion = OCR_VERSION,
                            providerModelPromptRevision = revision,
                            book = bookProvider(),
                        ),
                        onProgress = { stage, current, total ->
                            overlay.post {
                                if (imageView.tag == imageUrl) {
                                    overlay.showProgress(stage, current, total)
                                }
                            }
                        },
                        onPartialPage = { partialPage ->
                            synchronized(pages) { pages[imageUrl] = partialPage }
                            synchronized(exportPages) {
                                exportPages[imageUrl] = MangaExportPage(
                                    page = partialPage,
                                    sourceBytes = bytes,
                                )
                            }
                            overlay.post {
                                if (imageView.tag == imageUrl &&
                                    FeatureFlags.mangaTranslation && ReadMangaConfig.mangaTranslationEnabled
                                ) {
                                    overlay.showPage(partialPage)
                                }
                            }
                        },
                    )
                    val page = withContext(Dispatchers.Default) {
                        translatedPage.withEstimatedBubbleStyles(bytes)
                    }
                    if (page != translatedPage) cacheGateway.write(page)
                    page to bytes
                }.onSuccess { (page, bytes) ->
                    synchronized(pages) { pages[imageUrl] = page }
                    synchronized(exportPages) {
                        exportPages[imageUrl] = MangaExportPage(page = page, sourceBytes = bytes)
                    }
                    overlay.post {
                        if (imageView.tag == imageUrl &&
                            FeatureFlags.mangaTranslation &&
                            ReadMangaConfig.mangaTranslationEnabled
                        ) {
                            overlay.showPage(page)
                        }
                    }
                }.onFailure { error ->
                    overlay.post {
                        if (imageView.tag == imageUrl) {
                            overlay.showError(error.localizedMessage ?: "Manga translation failed")
                        }
                    }
                }
            }
            jobs.remove(imageUrl)
        }
    }

    fun cancelAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }

    fun updateTranslation(imageUrl: String, updated: MangaTranslationResult) {
        val page = synchronized(pages) {
            val current = pages[imageUrl] ?: return
            current.copy(
                translations = current.translations.map { translation ->
                    if (translation.region.id == updated.region.id) updated else translation
                }.sortedBy { it.region.userAdjustedOrder ?: it.region.readingOrder },
            ).also { pages[imageUrl] = it }
        }
        overlays[imageUrl]?.get()?.showPage(page)
        synchronized(exportPages) {
            exportPages[imageUrl]?.let { current ->
                exportPages[imageUrl] = current.copy(page = page)
            }
        }
        scope.launch(Dispatchers.IO) { cacheGateway.write(page) }
    }

    suspend fun buildExportPlan(baseName: String): MangaExportPlan {
        val items = synchronized(exportPages) {
            exportPages.entries
                .sortedBy { (imageUrl, _) ->
                    synchronized(pageOrder) { pageOrder[imageUrl] ?: Long.MAX_VALUE }
                }
                .map { it.value }
        }
        require(items.isNotEmpty()) { "No translated manga pages are ready to export" }
        val provider = TranslationConfig.llmProvider
        return MangaExportPlan(
            baseName = baseName,
            pages = items,
            format = MangaExportFormat.CBZ,
            manifest = MangaExportManifest(
                sourceHashes = items.map { it.page.imageHash },
                ocrVersion = OCR_VERSION,
                providerModelPromptRevision = providerRevision(provider),
                targetLanguage = TranslationConfig.llmTargetLanguage,
            ),
        )
    }

    private suspend fun providerRevision(provider: String): String {
        if (provider != TranslationConstants.PROVIDER_APP_AI) {
            return "$provider:${quickTranslationGateway.packVersion}"
        }
        return provider
    }

    private fun Drawable.toOcrBytes(): ByteArray {
        val source = (this as? BitmapDrawable)?.bitmap ?: toBitmap()
        return ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not prepare manga image for OCR"
            }
            output.toByteArray()
        }
    }

    private fun MangaOverlayPage.withEstimatedBubbleStyles(sourceBytes: ByteArray): MangaOverlayPage {
        if (translations.isEmpty() || translations.all(MangaTranslationResult::userEdited)) return this
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return this
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1_024) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            sourceBytes,
            0,
            sourceBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return this
        return try {
            copy(
                translations = translations.map { translation ->
                    if (translation.userEdited) translation else {
                        val background = estimateBorderColor(
                            bitmap = bitmap,
                            left = translation.region.boundingBox.left / sampleSize,
                            top = translation.region.boundingBox.top / sampleSize,
                            right = translation.region.boundingBox.right / sampleSize,
                            bottom = translation.region.boundingBox.bottom / sampleSize,
                        )
                        val luminance = Color.luminance(background)
                        translation.copy(
                            style = translation.style.copy(
                                backgroundColor = (0xEE000000L or (background.toLong() and 0xFFFFFFL)),
                                textColor = if (luminance > 0.45f) 0xFF111111L else 0xFFFFFFFFL,
                            )
                        )
                    }
                }
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun estimateBorderColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Int {
        val x0 = left.coerceIn(0, bitmap.width - 1)
        val y0 = top.coerceIn(0, bitmap.height - 1)
        val x1 = right.coerceIn(x0, bitmap.width - 1)
        val y1 = bottom.coerceIn(y0, bitmap.height - 1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        repeat(BORDER_SAMPLES) { index ->
            val ratio = index.toFloat() / (BORDER_SAMPLES - 1).coerceAtLeast(1)
            val x = (x0 + (x1 - x0) * ratio).toInt().coerceIn(0, bitmap.width - 1)
            val y = (y0 + (y1 - y0) * ratio).toInt().coerceIn(0, bitmap.height - 1)
            listOf(bitmap.getPixel(x, y0), bitmap.getPixel(x, y1),
                bitmap.getPixel(x0, y), bitmap.getPixel(x1, y)).forEach { color ->
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
                count++
            }
        }
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun String.toOcrScript(): MangaOcrScript = runCatching {
        MangaOcrScript.valueOf(uppercase())
    }.getOrDefault(MangaOcrScript.CHINESE)

    private companion object {
        const val OCR_VERSION = "mlkit-v2-unbundled-2026-07"
        const val BORDER_SAMPLES = 12
    }
}
