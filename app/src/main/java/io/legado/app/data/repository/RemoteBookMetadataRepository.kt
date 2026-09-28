package io.legado.app.data.repository

import androidx.collection.LruCache
import io.legado.app.data.RemoteBookCacheDatabase
import io.legado.app.data.entities.RemoteBookMetadata
import io.legado.app.help.drive.extractor.DocxRemoteExtractor
import io.legado.app.help.drive.extractor.EpubRemoteExtractor
import io.legado.app.help.drive.extractor.MobiRemoteExtractor
import io.legado.app.help.drive.extractor.PdfRemoteExtractor
import io.legado.app.help.drive.extractor.TextRemoteExtractor
import io.legado.app.lib.webdav.Authorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class RemoteBookMetadataRepository {

    private val lruCache = LruCache<String, RemoteBookMetadata>(500)
    private val dao = RemoteBookCacheDatabase.instance.remoteBookMetadataDao
    private val extractionSemaphore = Semaphore(3)

    suspend fun getCached(path: String): RemoteBookMetadata? {
        lruCache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            val dbItem = dao.getByPath(path)
            if (dbItem != null) {
                lruCache.put(path, dbItem)
            }
            dbItem
        }
    }

    suspend fun resolveMetadata(
        url: String,
        auth: Authorization?,
        path: String,
        filename: String,
        fileSize: Long,
        existingThumbnail: String? = null,
        companionCover: String? = null
    ): RemoteBookMetadata {
        getCached(path)?.let { return it }

        return extractionSemaphore.withPermit {
            getCached(path)?.let { return@withPermit it }

            val ext = filename.substringAfterLast(".", "").lowercase()
            val extracted = withContext(Dispatchers.IO) {
                try {
                    when (ext) {
                        "epub" -> EpubRemoteExtractor.extract(url, auth, fileSize)
                        "mobi", "azw", "azw3", "prc" -> MobiRemoteExtractor.extract(url, auth, fileSize)
                        "docx" -> DocxRemoteExtractor.extract(url, auth, fileSize)
                        "pdf" -> PdfRemoteExtractor.extract(url, auth, fileSize, existingThumbnail)
                        "txt", "md", "html", "htm", "doc" -> TextRemoteExtractor.extract(url, auth, ext, filename)
                        else -> null
                    }
                } catch (e: Throwable) {
                    null
                }
            }

            val finalTitle = extracted?.title?.takeIf { it.isNotBlank() } ?: filename.substringBeforeLast(".")
            val finalCover = extracted?.coverUrl?.takeIf { it.isNotBlank() }
                ?: existingThumbnail?.takeIf { it.isNotBlank() }
                ?: companionCover

            val metadata = RemoteBookMetadata(
                path = path,
                title = finalTitle,
                author = extracted?.author,
                intro = extracted?.intro,
                format = ext,
                coverUrl = finalCover,
                fileSize = fileSize,
                lastModify = System.currentTimeMillis()
            )

            lruCache.put(path, metadata)
            withContext(Dispatchers.IO) {
                try {
                    dao.insert(metadata)
                } catch (e: Throwable) {
                    // Ignore DB cache insert failure
                }
            }

            metadata
        }
    }
}
