package io.legado.app.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.legado.app.data.repository.AppearanceAssetPolicy
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.webservice.WebServiceBackgroundAssetResponse
import io.legado.app.domain.webservice.WebServiceBackgroundPolicy
import java.io.ByteArrayOutputStream
import java.io.File

object WebServiceBackgroundStore {

    const val MAX_UPLOAD_BYTES = 5L * 1024L * 1024L
    const val MAX_STORED_BYTES = 12L * 1024L * 1024L
    const val MAX_PIXEL_DIMENSION = 4096
    private const val CONTENT_TYPE = "image/png"
    private const val DIRECTORY_NAME = "web_service_background"

    fun save(
        context: Context,
        bytes: ByteArray,
        displayName: String?,
        mimeType: String?,
    ): WebServiceBackgroundAssetResponse {
        require(bytes.isNotEmpty()) { "BACKGROUND_EMPTY" }
        require(bytes.size.toLong() <= MAX_UPLOAD_BYTES) { "BACKGROUND_TOO_LARGE" }

        val validated = runCatching {
            AppearanceAssetPolicy.validate(
                bytes = bytes,
                displayName = displayName,
                mimeType = mimeType,
                kind = AppearanceAssetKind.WALLPAPER,
            )
        }.getOrElse {
            throw IllegalArgumentException("BACKGROUND_UNSUPPORTED_TYPE")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(validated.bytes, 0, validated.bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "BACKGROUND_CORRUPT" }
        require(
            bounds.outWidth <= MAX_PIXEL_DIMENSION &&
                bounds.outHeight <= MAX_PIXEL_DIMENSION
        ) { "BACKGROUND_DIMENSIONS_TOO_LARGE" }

        val bitmap = BitmapFactory.decodeByteArray(validated.bytes, 0, validated.bytes.size)
            ?: throw IllegalArgumentException("BACKGROUND_CORRUPT")
        val sanitizedBytes = encodePng(bitmap)
        require(sanitizedBytes.size.toLong() <= MAX_STORED_BYTES) { "BACKGROUND_STORED_TOO_LARGE" }

        val sha256 = AppearanceAssetPolicy.sha256(sanitizedBytes)
        val assetId = "$sha256.png"
        val directory = backgroundDir(context)
        if (!directory.exists()) directory.mkdirs()
        val target = File(directory, assetId)
        if (!target.isFile) {
            val temp = File(directory, "$assetId.tmp")
            temp.outputStream().use { it.write(sanitizedBytes) }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        return assetResponse(assetId, target, bounds.outWidth, bounds.outHeight)
    }

    fun find(
        context: Context,
        assetId: String?,
    ): File? {
        val normalized = WebServiceBackgroundPolicy.normalizeAssetId(assetId) ?: return null
        val directory = backgroundDir(context)
        val file = File(directory, normalized)
        return file.takeIf { it.isFile && it.parentFile?.canonicalFile == directory.canonicalFile }
    }

    fun responseFor(
        context: Context,
        assetId: String?,
    ): WebServiceBackgroundAssetResponse? {
        val normalized = WebServiceBackgroundPolicy.normalizeAssetId(assetId) ?: return null
        val file = find(context, normalized) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return assetResponse(normalized, file, bounds.outWidth, bounds.outHeight)
    }

    fun delete(
        context: Context,
        assetId: String?,
    ) {
        find(context, assetId)?.delete()
    }

    fun etag(assetId: String): String =
        "\"web-background-${assetId.substringBefore('.')}\""

    private fun encodePng(bitmap: Bitmap): ByteArray {
        return try {
            ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "BACKGROUND_ENCODE_FAILED"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assetResponse(
        assetId: String,
        file: File,
        width: Int,
        height: Int,
    ): WebServiceBackgroundAssetResponse =
        WebServiceBackgroundAssetResponse(
            assetId = assetId,
            contentType = CONTENT_TYPE,
            sizeBytes = file.length(),
            width = width,
            height = height,
            etag = etag(assetId),
        )

    private fun backgroundDir(context: Context): File =
        File(context.filesDir, DIRECTORY_NAME)
}
