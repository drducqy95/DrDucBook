package io.legado.app.web

import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class CloudDriveListingProvider(
    private val webDavBaseUrl: String,
    private val auth: Authorization,
) {
    suspend fun listDirectory(relPath: String): List<DriveFileInfo> = withContext(Dispatchers.IO) {
        val fullUrl = buildFullUrl(webDavBaseUrl, relPath)
        val files = WebDav(fullUrl, auth).listFiles()
        files.map { f ->
            val itemPath = combinePath(relPath, f.displayName)
            DriveFileInfo(
                name = f.displayName,
                path = itemPath,
                isDir = f.isDir,
                size = f.size,
                lastModified = f.lastModify,
                mimeType = f.contentType,
                thumbnailUrl = f.thumbnailUrl,
                intro = f.description,
            )
        }
    }

    suspend fun downloadFile(relPath: String): InputStream = withContext(Dispatchers.IO) {
        val fullUrl = buildFullUrl(webDavBaseUrl, relPath)
        WebDav(fullUrl, auth).downloadInputStream()
    }

    private fun buildFullUrl(base: String, rel: String): String {
        val trimmedBase = base.trimEnd('/')
        val trimmedRel = rel.trimStart('/')
        return if (trimmedRel.isEmpty()) "$trimmedBase/" else "$trimmedBase/$trimmedRel"
    }

    private fun combinePath(base: String, name: String): String {
        val trimmedBase = base.trimEnd('/')
        return if (trimmedBase.isEmpty()) "/$name" else "$trimmedBase/$name"
    }
}
