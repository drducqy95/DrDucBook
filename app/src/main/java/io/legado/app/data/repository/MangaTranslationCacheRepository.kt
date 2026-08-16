package io.legado.app.data.repository

import android.content.Context
import com.google.gson.Gson
import io.legado.app.domain.gateway.MangaTranslationCacheGateway
import io.legado.app.domain.manga.MangaOverlayPage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MangaTranslationCacheRepository(
    context: Context,
) : MangaTranslationCacheGateway {
    private val root = File(context.filesDir, "manga_translation_cache")
    private val gson = Gson()
    private val mutex = Mutex()

    override suspend fun read(cacheKey: String): MangaOverlayPage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cacheFile(cacheKey).takeIf(File::isFile)?.let { file ->
                runCatching { gson.fromJson(file.readText(), MangaOverlayPage::class.java) }
                    .getOrNull()
                    ?.takeIf { it.cacheKey == cacheKey }
            }
        }
    }

    override suspend fun write(page: MangaOverlayPage) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!root.exists() && !root.mkdirs()) error("Could not create manga translation cache")
            val destination = cacheFile(page.cacheKey)
            val temporary = File(root, "${page.cacheKey}.tmp")
            temporary.writeText(gson.toJson(page))
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    override suspend fun delete(cacheKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock { cacheFile(cacheKey).delete() }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.listFiles()?.forEach(File::delete)
        }
        Unit
    }

    private fun cacheFile(cacheKey: String): File {
        require(cacheKey.matches(Regex("[a-f0-9]{64}"))) { "Invalid manga cache key" }
        return File(root, "$cacheKey.json")
    }
}
