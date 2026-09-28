package io.legado.app.help.drive.extractor

import io.legado.app.utils.MD5Utils
import splitties.init.appCtx
import java.io.File

object RemoteCoverCache {
    private val coverDir: File by lazy {
        File(appCtx.cacheDir, "remote_covers").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getCoverFile(key: String): File {
        val hash = MD5Utils.md5Encode(key)
        return File(coverDir, "$hash.jpg")
    }

    fun saveCoverBytes(key: String, bytes: ByteArray): String? {
        return try {
            val file = getCoverFile(key)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun hasCover(key: String): Boolean {
        val file = getCoverFile(key)
        return file.exists() && file.length() > 0
    }
}
