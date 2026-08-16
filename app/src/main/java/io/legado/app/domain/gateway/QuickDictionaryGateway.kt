package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryRevision
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import kotlinx.coroutines.flow.Flow

interface QuickDictionaryGateway {
    /**
     * Monotonic persistent revision used to invalidate translated content after a dictionary edit.
     */
    val currentRevision: Long

    /** Persistent revision for one exact scope. */
    fun revisionFor(scope: QuickDictionaryScope, scopeKey: String = ""): Long = currentRevision

    /** Revision vector used by cache entries for this book and active universe context. */
    suspend fun getEffectiveRevision(
        book: Book,
        context: String = "",
    ): QuickDictionaryRevision = QuickDictionaryRevision(
        global = currentRevision,
        projectKey = book.bookUrl,
    )

    fun observeEntries(): Flow<List<QuickDictionaryEntry>>

    fun observePacks(): Flow<List<QuickDictionaryPack>>

    suspend fun getEffectiveEntries(book: Book, context: String = ""): List<QuickDictionaryEntry>

    suspend fun getUniverses(): List<QuickDictionaryUniverse>

    suspend fun saveUniverse(universe: QuickDictionaryUniverse)

    suspend fun save(entry: QuickDictionaryEntry)

    suspend fun saveAll(entries: List<QuickDictionaryEntry>): Int

    suspend fun importPack(
        localPath: String,
        displayName: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        scopeKey: String,
        onProgress: (QuickDictionaryImportProgress) -> Unit = {},
    ): QuickDictionaryImportResult

    suspend fun deletePack(id: String)

    suspend fun deleteEntry(id: Long)

    suspend fun deleteUniverse(key: String)
}
