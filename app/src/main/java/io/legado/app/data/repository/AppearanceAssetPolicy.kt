package io.legado.app.data.repository

import io.legado.app.domain.model.AppearanceAssetKind
import java.security.MessageDigest
import java.util.Locale

object AppearanceAssetPolicy {
    private const val MAX_ICON_BYTES = 4L * 1024 * 1024
    private const val MAX_WALLPAPER_BYTES = 24L * 1024 * 1024
    private const val MAX_FONT_BYTES = 16L * 1024 * 1024

    data class ValidatedAsset(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val sha256: String,
    )

    fun validate(
        bytes: ByteArray,
        displayName: String?,
        mimeType: String?,
        kind: AppearanceAssetKind,
    ): ValidatedAsset {
        require(bytes.isNotEmpty()) { "Tệp giao diện rỗng" }
        require(bytes.size.toLong() <= maxBytes(kind)) { "Tệp giao diện vượt quá giới hạn" }

        val detected = detect(bytes, displayName, mimeType)
        require(
            when (kind) {
                AppearanceAssetKind.ICON -> detected.extension in setOf("png", "webp", "svg")
                AppearanceAssetKind.WALLPAPER -> detected.extension in setOf("png", "jpg", "webp")
                AppearanceAssetKind.FONT -> detected.extension in setOf("ttf", "otf")
            }
        ) { "Định dạng tệp không được hỗ trợ" }

        val sanitized = if (detected.extension == "svg") sanitizeSvg(bytes) else bytes
        return ValidatedAsset(
            bytes = sanitized,
            extension = detected.extension,
            mimeType = detected.mimeType,
            sha256 = sha256(sanitized),
        )
    }

    fun maxBytes(kind: AppearanceAssetKind): Long = when (kind) {
        AppearanceAssetKind.ICON -> MAX_ICON_BYTES
        AppearanceAssetKind.WALLPAPER -> MAX_WALLPAPER_BYTES
        AppearanceAssetKind.FONT -> MAX_FONT_BYTES
    }

    fun sanitizeSvg(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8)
        val normalized = text.lowercase(Locale.ROOT)
        val document = normalized.trimStart().let {
            if (it.startsWith("<?xml")) it.substringAfter("?>").trimStart() else it
        }
        require(document.startsWith("<svg")) { "SVG không hợp lệ" }
        val denied = listOf(
            "<script",
            "<!doctype",
            "<!entity",
            "<foreignobject",
            "@import",
            "javascript:",
            "data:text/html",
            "href=\"http:",
            "href=\"https:",
            "href='http:",
            "href='https:",
            "url(http:",
            "url(https:",
            "onload=",
            "onclick=",
            "onerror=",
        )
        require(denied.none(normalized::contains)) { "SVG chứa nội dung không an toàn" }
        return text.trim().toByteArray(Charsets.UTF_8)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private data class DetectedType(
        val extension: String,
        val mimeType: String,
    )

    private fun detect(
        bytes: ByteArray,
        displayName: String?,
        mimeType: String?,
    ): DetectedType {
        val lowerName = displayName.orEmpty().lowercase(Locale.ROOT)
        val lowerMime = mimeType.orEmpty().lowercase(Locale.ROOT)
        return when {
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ) -> DetectedType("png", "image/png")
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" ->
                DetectedType("webp", "image/webp")
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> DetectedType("jpg", "image/jpeg")
            bytes.toString(Charsets.UTF_8).trimStart().let {
                it.startsWith("<svg", ignoreCase = true) ||
                    it.startsWith("<?xml", ignoreCase = true) && it.contains("<svg", ignoreCase = true)
            } ->
                DetectedType("svg", "image/svg+xml")
            bytes.size >= 4 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "OTTO" ->
                DetectedType("otf", "font/otf")
            bytes.size >= 4 &&
                bytes[0] == 0.toByte() &&
                bytes[1] == 1.toByte() &&
                bytes[2] == 0.toByte() &&
                bytes[3] == 0.toByte() -> DetectedType("ttf", "font/ttf")
            lowerName.endsWith(".ttf") && lowerMime.startsWith("font/") ->
                DetectedType("ttf", "font/ttf")
            lowerName.endsWith(".otf") && lowerMime.startsWith("font/") ->
                DetectedType("otf", "font/otf")
            else -> error("Không thể xác định định dạng tệp")
        }
    }
}
