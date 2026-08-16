package io.legado.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.google.gson.Gson
import io.legado.app.domain.manga.MangaExportFormat
import io.legado.app.domain.manga.MangaExportPlan
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaTranslationResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class MangaTranslationExportRepository {
    suspend fun export(plan: MangaExportPlan, outputDirectory: File): List<File> =
        withContext(Dispatchers.IO) {
            plan.validate()
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                error("Could not create manga export directory")
            }
            when (plan.format) {
                MangaExportFormat.IMAGE_SET -> exportImages(plan, outputDirectory)
                MangaExportFormat.CBZ -> listOf(exportCbz(plan, outputDirectory))
                MangaExportFormat.PDF -> listOf(exportPdf(plan, outputDirectory))
            }
        }

    private suspend fun exportImages(plan: MangaExportPlan, outputDirectory: File): List<File> {
        val directory = File(outputDirectory, plan.outputFileName())
        if (!directory.exists() && !directory.mkdirs()) error("Could not create image export")
        val files = plan.pages.mapIndexed { index, item ->
            coroutineContext.ensureActive()
            val file = File(directory, "page-${(index + 1).toString().padStart(4, '0')}.png")
            writeAtomically(file, renderPng(item.sourceBytes, item.page))
            file
        }
        val manifest = File(directory, "translation-manifest.json")
        writeAtomically(manifest, Gson().toJson(plan.manifest).toByteArray())
        return files + manifest
    }

    private suspend fun exportCbz(plan: MangaExportPlan, outputDirectory: File): File {
        val destination = File(outputDirectory, plan.outputFileName())
        val temporary = File(outputDirectory, destination.name + ".tmp")
        ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
            plan.pages.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(4, '0')}.png"))
                zip.write(renderPng(item.sourceBytes, item.page))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("translation-manifest.json"))
            zip.write(Gson().toJson(plan.manifest).toByteArray())
            zip.closeEntry()
        }
        replaceAtomically(temporary, destination)
        return destination
    }

    private suspend fun exportPdf(plan: MangaExportPlan, outputDirectory: File): File {
        val destination = File(outputDirectory, plan.outputFileName())
        val temporary = File(outputDirectory, destination.name + ".tmp")
        val document = PdfDocument()
        try {
            plan.pages.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val bitmap = renderBitmap(item.sourceBytes, item.page)
                try {
                    val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = document.startPage(info)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            temporary.outputStream().buffered().use(document::writeTo)
        } finally {
            document.close()
        }
        replaceAtomically(temporary, destination)
        val manifest = File(outputDirectory, destination.name + ".manifest.json")
        writeAtomically(manifest, Gson().toJson(plan.manifest).toByteArray())
        return destination
    }

    private fun renderPng(sourceBytes: ByteArray, page: MangaOverlayPage): ByteArray {
        val bitmap = renderBitmap(sourceBytes, page)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode translated manga page"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderBitmap(sourceBytes: ByteArray, page: MangaOverlayPage): Bitmap {
        val source = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("Could not decode source manga page")
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Could not create translated manga page")
        if (mutable !== source) source.recycle()
        val canvas = Canvas(mutable)
        page.translations.forEach { drawTranslation(canvas, it) }
        return mutable
    }

    private fun drawTranslation(canvas: Canvas, translation: MangaTranslationResult) {
        val box = translation.region.boundingBox
        val rect = RectF(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = translation.style.backgroundColor.toInt()
        }
        canvas.drawRoundRect(rect, 4f, 4f, background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = translation.style.textColor.toInt()
            textAlign = Paint.Align.CENTER
        }
        val text = translation.translatedText.replace('\n', ' ').trim()
        val lines = fitLines(
            text = text,
            paint = paint,
            width = (rect.width() - 8f).coerceAtLeast(8f),
            height = (rect.height() - 8f).coerceAtLeast(8f),
            preferredSize = translation.style.textSizeSp,
        )
        val lineHeight = paint.fontSpacing
        var baseline = rect.centerY() - (lines.size - 1) * lineHeight / 2f -
            (paint.ascent() + paint.descent()) / 2f
        lines.forEach { line ->
            canvas.drawText(line, rect.centerX(), baseline, paint)
            baseline += lineHeight
        }
    }

    private fun fitLines(
        text: String,
        paint: Paint,
        width: Float,
        height: Float,
        preferredSize: Float,
    ): List<String> {
        var low = 8f
        var high = minOf(height, preferredSize.coerceIn(8f, 48f)).coerceAtLeast(low)
        var best = listOf(text)
        repeat(8) {
            val size = (low + high) / 2f
            paint.textSize = size
            val lines = wrapText(text, paint, width)
            if (lines.size * paint.fontSpacing <= height) {
                best = lines
                low = size
            } else {
                high = size
            }
        }
        paint.textSize = low
        return best
    }

    private fun wrapText(text: String, paint: Paint, width: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter(String::isNotBlank)
            .flatMap { word -> splitWordToWidth(word, paint, width) }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > width) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun splitWordToWidth(word: String, paint: Paint, width: Float): List<String> {
        if (paint.measureText(word) <= width) return listOf(word)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            val count = paint.breakText(word, start, word.length, true, width, null)
                .coerceAtLeast(1)
            parts += word.substring(start, (start + count).coerceAtMost(word.length))
            start += count
        }
        return parts
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        temporary.writeBytes(bytes)
        replaceAtomically(temporary, destination)
    }

    private fun replaceAtomically(temporary: File, destination: File) {
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Could not replace manga export: ${destination.name}")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }
}
