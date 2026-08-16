package io.legado.app.web.utils

import android.content.res.AssetManager
import android.text.TextUtils
import splitties.init.appCtx
import java.io.File
import java.io.InputStream


class AssetsWeb(rootPath: String) {
    private val assetManager: AssetManager = appCtx.assets
    /**
     * Android's AssetManager reads compressed APK entries from the same backing
     * file. Opening many entries concurrently can put every Ktor worker into
     * uninterruptible I/O sleep and make the whole web service appear frozen.
     * Keep the small web bundle in memory after the first read and serialize the
     * initial read so one browser page cannot fan out dozens of APK reads.
     */
    private val bytesCache = HashMap<String, ByteArray>()
    private val bytesCacheLock = Any()
    private var rootPath = "web"

    init {
        if (!TextUtils.isEmpty(rootPath)) {
            this.rootPath = rootPath
        }
    }

    fun getInputStream(path: String): InputStream? {
        val path1 = (rootPath + path).replace("/+".toRegex(), File.separator)
        return try {
            assetManager.open(path1)
        } catch (e: Exception) {
            null
        }
    }

    fun getBytes(path: String): ByteArray? {
        synchronized(bytesCacheLock) {
            bytesCache[path]?.let { return it }
            val bytes = getInputStream(path)?.use { stream -> stream.readBytes() } ?: return null
            bytesCache[path] = bytes
            return bytes
        }
    }

    fun preload() {
        listAssetPaths("").forEach(::getBytes)
    }

    private fun listAssetPaths(path: String): List<String> {
        val assetPath = (rootPath + path).replace("/+".toRegex(), File.separator)
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) return if (path.isBlank()) emptyList() else listOf(path)
        return children.flatMap { child ->
            val childPath = "$path/$child"
            listAssetPaths(childPath)
        }
    }

    fun getMimeType(path: String): String {
        val lastDot = path.lastIndexOf(".")
        if (lastDot == -1) return "text/html"
        val suffix = path.substring(lastDot)
        return when {
            suffix.equals(".html", ignoreCase = true)
                    || suffix.equals(".htm", ignoreCase = true) -> "text/html"
            suffix.equals(".js", ignoreCase = true) -> "text/javascript"
            suffix.equals(".css", ignoreCase = true) -> "text/css"
            suffix.equals(".ico", ignoreCase = true) -> "image/x-icon"
            suffix.equals(".jpg", ignoreCase = true) -> "image/jpg"
            suffix.equals(".png", ignoreCase = true) -> "image/png"
            suffix.equals(".svg", ignoreCase = true) -> "image/svg+xml"
            else -> "text/html"
        }
    }
}
