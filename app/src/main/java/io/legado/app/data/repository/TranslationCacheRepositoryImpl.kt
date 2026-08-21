package io.legado.app.data.repository

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.entities.TranslationCacheMetadata
import io.legado.app.data.entities.TranslationRevisionStatus
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.TranslationRevision
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap
import java.util.UUID

class TranslationCacheRepositoryImpl(
    private val cacheDir: File = File(BookHelp.cachePath),
    private val dynamicUiDir: File = File(appCtx.filesDir, "translations/dynamic_ui"),
) : TranslationCacheGateway {

    private val gson = Gson()
    private val revisionMutex = Mutex()
    private val chunkCacheLock = Any()
    private val dynamicUiMemory = object : LinkedHashMap<String, String>(
        DYNAMIC_UI_MEMORY_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?,
        ): Boolean = size > DYNAMIC_UI_MEMORY_ENTRIES
    }

    override fun getCacheFile(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ): File {
        val bookFolder = File(cacheDir, book.getFolderName())
        val chapterFileName = bookChapter.getFileName().removeSuffix(".nb")
        val providerSuffix = provider?.takeIf(String::isNotBlank)
            ?.let(::safeSegment)
            ?.let { ".$it" }
            .orEmpty()
        return File(bookFolder, "$chapterFileName.$targetLanguage$providerSuffix.nb")
    }

    override fun readCurrentTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        originalContentHash: String,
        provider: String,
    ): String? {
        val providerPayload = getCacheFile(book, bookChapter, targetLanguage, provider)
        val providerMetadata = metadataFile(providerPayload)
        readValidated(
            payload = providerPayload,
            metadataFile = providerMetadata,
            originalContentHash = originalContentHash,
            provider = provider,
            targetLanguage = targetLanguage,
        )?.let { return it }

        // Compatibility with payloads written before provider became part of the cache key.
        val legacyPayload = getCacheFile(book, bookChapter, targetLanguage, null)
        return readValidated(
            payload = legacyPayload,
            metadataFile = metadataFile(legacyPayload),
            originalContentHash = originalContentHash,
            provider = provider,
            targetLanguage = targetLanguage,
        )
    }

    override suspend fun readCacheIgnoringHash(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        expectedContentHash: String?,
    ): TranslationRevision? = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            if (!payload.exists()) return@withLock null
            val metadata = readMetadata(metadataFile(payload)) ?: return@withLock null
            val content = runCatching { payload.readText() }.getOrNull()?.ifEmpty { null }
                ?: return@withLock null
            val revision = metadata.toRevision(content)
            if (expectedContentHash != null && revision.cacheContentHash != expectedContentHash) {
                revision.copy(status = RevisionStatus.STALE)
            } else {
                revision
            }
        }
    }

    override suspend fun listProviderCaches(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
    ): List<TranslationRevision> = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val bookFolder = File(cacheDir, book.getFolderName())
            if (!bookFolder.exists()) return@withLock emptyList()
            val chapterPrefix = bookChapter.getFileName().removeSuffix(".nb")
            val metaFiles = bookFolder.listFiles()?.filter { file ->
                file.name.startsWith("$chapterPrefix.$targetLanguage") && file.name.endsWith(".nb.meta.json")
            }.orEmpty()
            metaFiles.mapNotNull { metaFile ->
                val payloadFile = File(metaFile.path.removeSuffix(".meta.json"))
                if (!payloadFile.exists()) return@mapNotNull null
                val metadata = readMetadata(metaFile) ?: return@mapNotNull null
                val content = runCatching { payloadFile.readText() }.getOrNull()?.ifEmpty { null }
                    ?: return@mapNotNull null
                metadata.toRevision(content)
            }.sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ): String? = withContext(Dispatchers.IO) {
        getCacheFile(book, bookChapter, targetLanguage, provider)
            .takeIf(File::exists)
            ?.readText()
            ?.ifEmpty { null }
    }

    override suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String,
        originalContentHash: String?,
        provider: String?,
        revisionStatus: TranslationRevisionStatus,
        actor: String,
        parentRevisionId: String?,
        rawContentHash: String?,
        dictionaryRevision: String?,
        providerModelPromptRevision: String?,
    ) = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            writeRevisionLocked(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = content,
                originalContentHash = originalContentHash,
                provider = provider,
                revisionStatus = revisionStatus,
                actor = actor,
                parentRevisionId = parentRevisionId,
                rawContentHash = rawContentHash,
                dictionaryRevision = dictionaryRevision,
                providerModelPromptRevision = providerModelPromptRevision,
            )
        }
        Unit
    }

    suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String,
        originalContentHash: String? = null,
        provider: String? = null,
    ) {
        writeTranslation(
            book = book,
            bookChapter = bookChapter,
            targetLanguage = targetLanguage,
            content = content,
            originalContentHash = originalContentHash,
            provider = provider,
            revisionStatus = TranslationRevisionStatus.MACHINE_DRAFT,
            actor = "machine",
            parentRevisionId = null,
            rawContentHash = originalContentHash,
            dictionaryRevision = null,
            providerModelPromptRevision = null,
        )
    }

    override suspend fun getCurrentRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String?,
    ): TranslationRevision? = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            currentRevisionLocked(
                payload = getCacheFile(book, bookChapter, targetLanguage, provider),
                currentRawContentHash = currentRawContentHash,
            )
        }
    }

    override suspend fun getRevisionHistory(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String?,
    ): List<TranslationRevision> = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            val archived = readRevisionArchive(revisionHistoryFile(payload)).revisions
            val current = currentRevisionLocked(payload, currentRawContentHash)
            val combined = if (current == null || archived.any { it.revisionId == current.revisionId }) {
                archived
            } else {
                archived + current
            }
            combined
                .map { revision ->
                    if (currentRawContentHash != null &&
                        revision.rawContentHash != currentRawContentHash &&
                        revision.status != RevisionStatus.STALE
                    ) {
                        revision.copy(status = RevisionStatus.STALE)
                    } else {
                        revision
                    }
                }
                .sortedByDescending(TranslationRevision::updatedAt)
        }
    }

    override suspend fun saveUserEdit(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        content: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = writeAndReadRevision(
        book = book,
        bookChapter = bookChapter,
        targetLanguage = targetLanguage,
        provider = provider,
        content = content,
        originalContentHash = originalContentHash,
        rawContentHash = rawContentHash,
        status = RevisionStatus.USER_EDITED,
        actor = actor,
    )

    override suspend fun finalizeChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        actor: String,
    ): TranslationRevision = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            val current = currentRevisionLocked(payload, null)
                ?: error("No translation is available to finalize")
            writeRevisionLocked(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = current.content,
                originalContentHash = current.cacheContentHash,
                rawContentHash = current.rawContentHash,
                provider = provider,
                revisionStatus = RevisionStatus.FINAL,
                actor = actor,
                parentRevisionId = current.revisionId,
                dictionaryRevision = current.dictionaryRevision,
                providerModelPromptRevision = current.providerModelPromptRevision,
            ) ?: error("Unable to finalize translation")
        }
    }

    override suspend fun unlockChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            val current = currentRevisionLocked(payload, null)
                ?: error("No translation is available to unlock")
            writeRevisionLocked(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = current.content,
                originalContentHash = originalContentHash,
                rawContentHash = rawContentHash,
                provider = provider,
                revisionStatus = RevisionStatus.USER_EDITED,
                actor = actor,
                parentRevisionId = current.revisionId,
                dictionaryRevision = current.dictionaryRevision,
                providerModelPromptRevision = current.providerModelPromptRevision,
            ) ?: error("Unable to unlock translation")
        }
    }

    override suspend fun restoreRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        revisionId: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            val target = readRevisionArchive(revisionHistoryFile(payload)).revisions
                .firstOrNull { it.revisionId == revisionId }
                ?: currentRevisionLocked(payload, null)?.takeIf { it.revisionId == revisionId }
                ?: error("Translation revision not found")
            val current = currentRevisionLocked(payload, null)
            writeRevisionLocked(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = target.content,
                originalContentHash = originalContentHash,
                rawContentHash = rawContentHash,
                provider = provider,
                revisionStatus = RevisionStatus.USER_EDITED,
                actor = actor,
                parentRevisionId = current?.revisionId,
                dictionaryRevision = target.dictionaryRevision,
                providerModelPromptRevision = target.providerModelPromptRevision,
            ) ?: error("Unable to restore translation revision")
        }
    }

    override suspend fun deleteTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ) = withContext(Dispatchers.IO) {
        if (provider == null) {
            val bookFolder = File(cacheDir, book.getFolderName())
            val prefix = "${bookChapter.getFileName().removeSuffix(".nb")}.$targetLanguage"
            bookFolder.listFiles()?.filter { file ->
                file.name.startsWith(prefix) &&
                    (file.name.endsWith(".nb") ||
                        file.name.endsWith(".meta.json") ||
                        file.name.endsWith(".revisions.json"))
            }?.forEach(File::delete)
        } else {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            payload.delete()
            metadataFile(payload).delete()
            revisionHistoryFile(payload).delete()
        }
        clearChunkCacheForChapter(book, bookChapter, targetLanguage, provider)
        Unit
    }

    override suspend fun deleteTranslationForBook(
        book: Book,
        targetLanguage: String,
    ) = withContext(Dispatchers.IO) {
        val bookFolder = File(cacheDir, book.getFolderName())
        bookFolder.listFiles()?.filter { file ->
            file.name.contains(".$targetLanguage.") &&
                (file.name.endsWith(".nb") ||
                    file.name.endsWith(".meta.json") ||
                    file.name.endsWith(".revisions.json")) ||
                file.name.endsWith(".$targetLanguage.nb") ||
                file.name.endsWith(".$targetLanguage.nb.meta.json") ||
                file.name.endsWith(".$targetLanguage.nb.revisions.json")
        }?.forEach(File::delete)
        clearChunkCacheForBook(book, targetLanguage)
        Unit
    }

    override suspend fun deleteAllTranslation() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { bookFolder ->
            bookFolder.listFiles()?.filter { file ->
                file.name.endsWith(".meta.json") ||
                    file.name.endsWith(".revisions.json") ||
                    file.name.endsWith(".chunks.jsonl")
            }?.forEach { metadataOrChunk ->
                if (metadataOrChunk.name.endsWith(".meta.json")) {
                    File(metadataOrChunk.path.removeSuffix(".meta.json")).delete()
                }
                metadataOrChunk.delete()
            }
        }
        Unit
    }

    override fun getTranslationCacheSize(): Long {
        return cacheDir.listFiles().orEmpty().sumOf { bookFolder ->
            bookFolder.listFiles().orEmpty()
                .filter {
                    it.name.endsWith(".meta.json") ||
                        it.name.endsWith(".revisions.json") ||
                        it.name.endsWith(".chunks.jsonl")
                }
                .sumOf { metadataOrChunk ->
                    val payloadSize = if (metadataOrChunk.name.endsWith(".meta.json")) {
                        File(metadataOrChunk.path.removeSuffix(".meta.json")).length()
                    } else {
                        0L
                    }
                    metadataOrChunk.length() + payloadSize
                }
        }
    }

    override fun computeContentHash(content: String): String = MD5Utils.md5Encode(content)

    override fun computeCacheKey(
        bookUrl: String,
        chapterIndex: Int,
        chunkIndex: Int,
        targetLanguage: String,
    ): String = "${bookUrl}_${chapterIndex}_${chunkIndex}_$targetLanguage"

    override suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        provider: String,
    ): List<TranslationCache> = withContext(Dispatchers.IO) {
        readAllChunks(book, bookChapter, targetLanguage, provider).values
            .filter {
                it.originalContentHash == contentHash &&
                    it.provider == provider &&
                    it.isSuccess
            }
            .sortedBy(TranslationCache::chunkIndex)
    }

    override suspend fun getCachedChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        provider: String,
    ): TranslationCache? = withContext(Dispatchers.IO) {
        readAllChunks(book, bookChapter, targetLanguage, provider)[chunkIndex]
    }

    override suspend fun saveChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        originalChunkContent: String,
        originalContentHash: String,
        provider: String,
        status: Int,
        translatedContent: String?,
        errorMessage: String?,
    ) = withContext(Dispatchers.IO) {
        val chunk = TranslationCache(
            chunkIndex = chunkIndex,
            originalChunkContent = originalChunkContent,
            translatedChunkContent = translatedContent,
            status = status,
            errorMessage = errorMessage,
            originalContentHash = originalContentHash,
            provider = provider,
        )
        val file = chunkFile(book, bookChapter, targetLanguage, provider)
        synchronized(chunkCacheLock) {
            val chunks = readAllChunksUnlocked(file).toMutableMap()
            chunks[chunkIndex] = chunk
            file.parentFile?.mkdirs()
            val content = chunks.values
                .sortedBy(TranslationCache::chunkIndex)
                .joinToString(separator = "\n", postfix = "\n", transform = gson::toJson)
            writeAtomically(file, content)
        }
    }

    override suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ) = withContext(Dispatchers.IO) {
        synchronized(chunkCacheLock) {
            if (provider == null) {
                val folder = File(cacheDir, book.getFolderName())
                val prefix = "${bookChapter.getFileName().removeSuffix(".nb")}.$targetLanguage."
                folder.listFiles()?.filter {
                    it.name.startsWith(prefix) && it.name.endsWith(".chunks.jsonl")
                }?.forEach(File::delete)
            } else {
                chunkFile(book, bookChapter, targetLanguage, provider).delete()
            }
        }
        Unit
    }

    override suspend fun clearChunkCacheForBook(
        book: Book,
        targetLanguage: String,
    ) = withContext(Dispatchers.IO) {
        synchronized(chunkCacheLock) {
            val folder = File(cacheDir, book.getFolderName())
            folder.listFiles()?.filter {
                it.name.contains(".$targetLanguage.") && it.name.endsWith(".chunks.jsonl")
            }?.forEach(File::delete)
        }
        Unit
    }

    override suspend fun clearAllChunkCache() = withContext(Dispatchers.IO) {
        synchronized(chunkCacheLock) {
            cacheDir.listFiles()?.forEach { bookFolder ->
                bookFolder.listFiles()?.filter {
                    it.name.endsWith(".chunks.jsonl")
                }?.forEach(File::delete)
            }
        }
        Unit
    }

    override fun readDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
    ): String? {
        val identity = dynamicUiIdentity(scopeKey, originalText, targetLanguage, provider)
        synchronized(dynamicUiMemory) {
            dynamicUiMemory[identity]?.let { return it }
        }
        val file = File(dynamicUiDir, "$identity.json")
        if (!file.isFile) return null
        return runCatching {
            val record = gson.fromJson(file.readText(), DynamicUiTranslationRecord::class.java)
            if (record.scopeKey == scopeKey &&
                record.originalContentHash == computeContentHash(originalText) &&
                record.provider == provider &&
                record.targetLanguage == targetLanguage
            ) {
                record.translatedText.takeIf(String::isNotBlank)?.also { translated ->
                    synchronized(dynamicUiMemory) {
                        dynamicUiMemory[identity] = translated
                    }
                }
            } else {
                null
            }
        }.getOrNull()
    }

    override suspend fun writeDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
        translatedText: String,
    ) = withContext(Dispatchers.IO) {
        val identity = dynamicUiIdentity(scopeKey, originalText, targetLanguage, provider)
        synchronized(dynamicUiMemory) {
            dynamicUiMemory[identity] = translatedText
        }
        val file = File(dynamicUiDir, "$identity.json")
        file.parentFile?.mkdirs()
        writeAtomically(
            file,
            gson.toJson(
                DynamicUiTranslationRecord(
                    scopeKey = scopeKey,
                    originalContentHash = computeContentHash(originalText),
                    provider = provider,
                    targetLanguage = targetLanguage,
                    translatedText = translatedText,
                )
            ),
        )
    }

    override suspend fun clearDynamicUiTranslations() = withContext(Dispatchers.IO) {
        synchronized(dynamicUiMemory) {
            dynamicUiMemory.clear()
        }
        // The directory is private and contains only flat JSON records owned by this repository.
        dynamicUiDir.listFiles()?.forEach(File::delete)
        Unit
    }

    private fun readAllChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
    ): Map<Int, TranslationCache> {
        val file = chunkFile(book, bookChapter, targetLanguage, provider)
        return synchronized(chunkCacheLock) {
            readAllChunksUnlocked(file)
        }
    }

    private fun readAllChunksUnlocked(file: File): Map<Int, TranslationCache> {
        if (!file.exists()) return emptyMap()
        val result = mutableMapOf<Int, TranslationCache>()
        file.forEachLine { line ->
            runCatching { gson.fromJson(line, TranslationCache::class.java) }
                .getOrNull()
                ?.let { result[it.chunkIndex] = it }
        }
        return result
    }

    private fun chunkFile(
        book: Book,
        chapter: BookChapter,
        targetLanguage: String,
        provider: String,
    ): File {
        val folder = File(cacheDir, book.getFolderName())
        val chapterName = chapter.getFileName().removeSuffix(".nb")
        return File(
            folder,
            "$chapterName.$targetLanguage.${safeSegment(provider)}.chunks.jsonl",
        )
    }

    private suspend fun writeAndReadRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        content: String,
        originalContentHash: String,
        rawContentHash: String,
        status: RevisionStatus,
        actor: String,
    ): TranslationRevision = withContext(Dispatchers.IO) {
        revisionMutex.withLock {
            val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
            val current = currentRevisionLocked(payload, null)
            writeRevisionLocked(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = content,
                originalContentHash = originalContentHash,
                rawContentHash = rawContentHash,
                provider = provider,
                revisionStatus = status,
                actor = actor,
                parentRevisionId = current?.revisionId,
                dictionaryRevision = current?.dictionaryRevision,
                providerModelPromptRevision = current?.providerModelPromptRevision,
            ) ?: error("Unable to save translation revision")
        }
    }

    private fun writeRevisionLocked(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String,
        originalContentHash: String?,
        rawContentHash: String?,
        provider: String?,
        revisionStatus: RevisionStatus,
        actor: String,
        parentRevisionId: String?,
        dictionaryRevision: String?,
        providerModelPromptRevision: String?,
    ): TranslationRevision? {
        val payload = getCacheFile(book, bookChapter, targetLanguage, provider)
        val metadataPath = metadataFile(payload)
        val existingMetadata = readMetadata(metadataPath)
        if (revisionStatus == RevisionStatus.MACHINE_DRAFT &&
            existingMetadata?.protectsMachineDraft == true
        ) {
            return currentRevisionLocked(payload, originalContentHash)
        }

        val existingRevision = currentRevisionLocked(payload, null)
        payload.parentFile?.mkdirs()
        writeAtomically(payload, content)
        if (originalContentHash == null || provider == null) return null

        val now = System.currentTimeMillis()
        val metadata = TranslationCacheMetadata(
            revisionId = UUID.randomUUID().toString(),
            originalContentHash = originalContentHash,
            rawContentHash = rawContentHash ?: originalContentHash,
            provider = provider,
            targetLanguage = targetLanguage,
            dictionaryRevision = dictionaryRevision,
            providerModelPromptRevision = providerModelPromptRevision,
            updatedAt = now,
            status = revisionStatus,
            createdAt = now,
            finalizedAt = now.takeIf { revisionStatus == RevisionStatus.FINAL },
            actor = actor,
            parentRevisionId = parentRevisionId ?: existingRevision?.revisionId,
        )
        writeAtomically(metadataPath, gson.toJson(metadata))
        val revision = metadata.toRevision(content)
        val archivePath = revisionHistoryFile(payload)
        val revisions = buildList {
            addAll(readRevisionArchive(archivePath).revisions)
            existingRevision?.let(::add)
            add(revision)
        }
            .distinctBy(TranslationRevision::revisionId)
            .sortedBy(TranslationRevision::updatedAt)
            .takeLast(MAX_REVISION_HISTORY)
        writeAtomically(
            archivePath,
            gson.toJson(TranslationRevisionArchive(schemaVersion = 1, revisions = revisions)),
        )
        return revision
    }

    private fun currentRevisionLocked(
        payload: File,
        currentRawContentHash: String?,
    ): TranslationRevision? {
        if (!payload.exists()) return null
        val metadata = readMetadata(metadataFile(payload)) ?: return null
        val content = runCatching { payload.readText() }.getOrNull() ?: return null
        val revision = metadata.toRevision(content)
        return if (currentRawContentHash != null &&
            revision.rawContentHash != currentRawContentHash
        ) {
            revision.copy(status = RevisionStatus.STALE)
        } else {
            revision
        }
    }

    private fun TranslationCacheMetadata.toRevision(content: String): TranslationRevision {
        // Gson can materialize null for non-null Kotlin properties when reading metadata written
        // before the field existed. Normalize every required string at this persistence boundary.
        val persistedRevisionId: String? = revisionId
        val persistedOriginalHash: String? = originalContentHash
        val persistedProvider: String? = provider
        val persistedTargetLanguage: String? = targetLanguage
        val persistedActor: String? = actor
        val resolvedOriginalHash = persistedOriginalHash
            ?.takeIf(String::isNotBlank)
            ?: computeContentHash(content)
        val resolvedRevisionId = persistedRevisionId?.takeIf(String::isNotBlank) ?: run {
            "legacy-${updatedAt}-${content.hashCode().toUInt().toString(16)}"
        }
        return TranslationRevision(
            revisionId = resolvedRevisionId,
            content = content,
            status = normalizedStatus,
            rawContentHash = rawContentHash ?: resolvedOriginalHash,
            cacheContentHash = resolvedOriginalHash,
            dictionaryRevision = dictionaryRevision,
            providerModelPromptRevision = providerModelPromptRevision,
            provider = persistedProvider.orEmpty(),
            targetLanguage = persistedTargetLanguage.orEmpty(),
            createdAt = createdAt.takeIf { it > 0L } ?: updatedAt,
            updatedAt = updatedAt,
            finalizedAt = finalizedAt,
            actor = persistedActor?.takeIf(String::isNotBlank) ?: "machine",
            parentRevisionId = parentRevisionId,
        )
    }

    private fun readRevisionArchive(file: File): TranslationRevisionArchive {
        if (!file.exists()) return TranslationRevisionArchive()
        return runCatching {
            val root = JsonParser.parseString(file.readText())
            val revisionsElement = when {
                root.isJsonObject -> root.asJsonObject.get("revisions")
                root.isJsonArray -> root // legacy array-only archive
                else -> null
            }
            val revisions = revisionsElement
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.mapNotNull { element ->
                    runCatching { gson.fromJson(element, TranslationRevision::class.java) }.getOrNull()
                }
                .orEmpty()
            TranslationRevisionArchive(
                schemaVersion = root.asJsonObjectOrNull()?.get("schemaVersion")?.asInt ?: 1,
                revisions = revisions,
            )
        }.getOrElse { TranslationRevisionArchive() }
    }

    private fun readValidated(
        payload: File,
        metadataFile: File,
        originalContentHash: String,
        provider: String,
        targetLanguage: String,
    ): String? {
        if (!payload.exists() || !metadataFile.exists()) return null
        return runCatching {
            val metadata = readMetadata(metadataFile) ?: return@runCatching null
            if (metadata.originalContentHash == originalContentHash &&
                metadata.provider == provider &&
                metadata.targetLanguage == targetLanguage
            ) {
                payload.readText().ifEmpty { null }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun readMetadata(file: File): TranslationCacheMetadata? {
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(), TranslationCacheMetadata::class.java)
        }.getOrNull()
    }

    private fun writeAtomically(destination: File, content: String) {
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        temporary.writeText(content)
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun metadataFile(payload: File): File = File(payload.path + ".meta.json")

    private fun revisionHistoryFile(payload: File): File = File(payload.path + ".revisions.json")

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun dynamicUiIdentity(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
    ): String {
        return computeContentHash(
            listOf(scopeKey, originalText, targetLanguage, provider).joinToString("\u0000")
        )
    }

    private companion object {
        const val DYNAMIC_UI_MEMORY_ENTRIES = 2_048
        const val MAX_REVISION_HISTORY = 100
    }
}

@Keep
private data class TranslationRevisionArchive(
    val schemaVersion: Int = 1,
    val revisions: List<TranslationRevision> = emptyList(),
)

private fun JsonElement.asJsonObjectOrNull() = takeIf(JsonElement::isJsonObject)?.asJsonObject

private data class DynamicUiTranslationRecord(
    val scopeKey: String,
    val originalContentHash: String,
    val provider: String,
    val targetLanguage: String,
    val translatedText: String,
)
