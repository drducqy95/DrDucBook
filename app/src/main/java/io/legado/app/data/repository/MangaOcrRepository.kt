package io.legado.app.data.repository

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.legado.app.domain.gateway.MangaOcrGateway
import io.legado.app.domain.gateway.MangaOcrResult
import io.legado.app.domain.manga.MangaOcrScript
import io.legado.app.domain.manga.MangaPoint
import io.legado.app.domain.manga.MangaRect
import io.legado.app.domain.manga.MangaTextBlock
import io.legado.app.domain.manga.MangaTextOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MangaOcrRepository : MangaOcrGateway {
    override suspend fun recognize(
        imageBytes: ByteArray,
        script: MangaOcrScript,
    ): MangaOcrResult = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable manga image" }
        val sampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        val bitmap = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("Could not decode manga image")
        val recognizer = recognizer(script)
        try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val blocks = result.textBlocks.mapIndexedNotNull { index, block ->
                val text = block.text.trim()
                val boundsRect = block.boundingBox ?: return@mapIndexedNotNull null
                if (text.isEmpty()) return@mapIndexedNotNull null
                val confidence = block.lines.asSequence()
                    .flatMap { it.elements.asSequence() }
                    .mapNotNull { it.confidence }
                    .average()
                    .takeUnless(Double::isNaN)
                    ?.toFloat()
                    ?: 0.5f
                MangaTextBlock(
                    id = "ocr-$index",
                    text = text,
                    polygon = block.cornerPoints.orEmpty().map { point ->
                        MangaPoint(point.x * sampleSize, point.y * sampleSize)
                    },
                    boundingBox = MangaRect(
                        left = boundsRect.left * sampleSize,
                        top = boundsRect.top * sampleSize,
                        right = boundsRect.right * sampleSize,
                        bottom = boundsRect.bottom * sampleSize,
                    ),
                    confidence = confidence.coerceIn(0f, 1f),
                    orientation = if (boundsRect.height() > boundsRect.width() * 1.35f) {
                        MangaTextOrientation.VERTICAL
                    } else {
                        MangaTextOrientation.HORIZONTAL
                    },
                    script = script,
                )
            }
            MangaOcrResult(bounds.outWidth, bounds.outHeight, blocks)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun recognizer(script: MangaOcrScript): TextRecognizer = when (script) {
        MangaOcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        MangaOcrScript.CHINESE -> TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        MangaOcrScript.JAPANESE -> TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        MangaOcrScript.KOREAN -> TextRecognition.getClient(
            KoreanTextRecognizerOptions.Builder().build()
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_OCR_DIMENSION) sample *= 2
        return sample
    }

    private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
        addOnSuccessListener(continuation::resume)
        addOnFailureListener(continuation::resumeWithException)
    }

    private companion object {
        const val MAX_OCR_DIMENSION = 2_048
    }
}
