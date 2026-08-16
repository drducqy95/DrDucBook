package io.legado.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.legado.app.data.dao.QuickDictionaryDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.QuickDictionaryEntryEntity
import io.legado.app.data.entities.QuickDictionaryUniverseEntity
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryRevision
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.QuickDictionaryUniverseMatcher
import io.legado.app.domain.model.keyFor
import io.legado.app.domain.model.resolveQuickDictionaryScopeConflicts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

class QuickDictionaryRepository(
    private val dao: QuickDictionaryDao,
    private val packStore: QuickDictionaryPackStore,
    private val translationGateway: QuickTranslationGateway? = null,
    private val revisionPreferences: SharedPreferences = appCtx.getSharedPreferences(
        REVISION_PREFERENCES,
        Context.MODE_PRIVATE,
    ),
) : QuickDictionaryGateway {

    init {
        migrateLegacyRevision()
    }

    override val currentRevision: Long
        get() = revisionPreferences.getLong(REVISION_KEY, 0L)

    override fun revisionFor(scope: QuickDictionaryScope, scopeKey: String): Long {
        val normalizedKey = normalizedScopeKey(scope, scopeKey) ?: return 0L
        return revisionPreferences.getLong(revisionKey(scope, normalizedKey), 0L)
    }

    override suspend fun getEffectiveRevision(
        book: Book,
        context: String,
    ): QuickDictionaryRevision {
        val activeUniverseKey = QuickDictionaryUniverseMatcher.activeUniverseKey(
            universes = getUniverses(),
            context = context,
        ).orEmpty()
        val projectKey = QuickDictionaryScope.PROJECT.keyFor(book)
        return QuickDictionaryRevision(
            global = revisionFor(QuickDictionaryScope.GLOBAL),
            universeKey = activeUniverseKey,
            universe = activeUniverseKey.takeIf(String::isNotEmpty)?.let { key ->
                revisionFor(QuickDictionaryScope.UNIVERSE, key)
            } ?: 0L,
            projectKey = projectKey,
            project = revisionFor(QuickDictionaryScope.PROJECT, projectKey),
        )
    }

    override fun observeEntries(): Flow<List<QuickDictionaryEntry>> {
        return dao.observeEntries().map { rows -> rows.map { it.toDomain() } }
    }

    override fun observePacks(): Flow<List<QuickDictionaryPack>> = packStore.packs

    override suspend fun getEffectiveEntries(
        book: Book,
        context: String,
    ): List<QuickDictionaryEntry> {
        val activeUniverseKey = QuickDictionaryUniverseMatcher.activeUniverseKey(
            universes = getUniverses(),
            context = context,
        ).orEmpty()
        val projectKey = QuickDictionaryScope.PROJECT.keyFor(book)
        val roomEntries = dao.getEffectiveEntries(
            activeUniverseKey = activeUniverseKey,
            projectKey = projectKey,
        ).map { it.toDomain() }
        val packEntries = packStore.matchEntries(
            context = context,
            projectKey = projectKey,
            activeUniverseKey = activeUniverseKey,
        )
        return resolveQuickDictionaryScopeConflicts(roomEntries + packEntries)
    }

    override suspend fun getUniverses(): List<QuickDictionaryUniverse> {
        return dao.getEnabledUniverses().map { it.toDomain() }
    }

    override suspend fun saveUniverse(universe: QuickDictionaryUniverse) {
        require(universe.key.isNotBlank()) { "Universe key must not be empty" }
        require(universe.name.isNotBlank()) { "Universe name must not be empty" }
        require(universe.contextMarkers.any(String::isNotBlank)) {
            "A universe requires at least one context marker"
        }
        dao.upsertUniverse(universe.toEntity())
        bumpRevision(setOf(QuickDictionaryScope.GLOBAL to ""))
    }

    override suspend fun save(entry: QuickDictionaryEntry) {
        val normalized = entry.normalizedForStorage()
        require(normalized.raw.isNotBlank()) { "Source text must not be empty" }
        require(normalized.scope == QuickDictionaryScope.GLOBAL || normalized.scopeKey.isNotBlank()) {
            "Dictionary scope key must not be empty"
        }
        require(normalized.type == QuickDictionaryType.IGNORE ||
            normalized.hanViet.isNotBlank() ||
            normalized.target.isNotBlank()
        ) {
            "Hán-Việt or target text must not be empty"
        }
        val previous = normalized.id.takeIf { it > 0 }?.let { dao.getEntry(it) }
        dao.upsert(normalized.toEntity())
        bumpRevision(
            buildSet {
                add(normalized.scope to normalized.scopeKey)
                previous?.let { add(QuickDictionaryScope.valueOf(it.scope) to it.scopeKey) }
            }
        )
    }

    override suspend fun saveAll(entries: List<QuickDictionaryEntry>): Int {
        if (entries.isEmpty()) return 0
        val valid = entries.map { it.normalizedForStorage() }.filter { entry ->
            entry.raw.isNotBlank() &&
                (entry.scope == QuickDictionaryScope.GLOBAL || entry.scopeKey.isNotBlank()) &&
                (entry.type == QuickDictionaryType.IGNORE ||
                    entry.hanViet.isNotBlank() ||
                    entry.target.isNotBlank())
        }
        if (valid.isEmpty()) return 0
        val previous = mutableListOf<QuickDictionaryEntryEntity>()
        valid.asSequence()
            .map(QuickDictionaryEntry::id)
            .filter { it > 0 }
            .distinct()
            .chunked(MAX_ROOM_IN_ARGS)
            .forEach { ids -> previous += dao.getEntries(ids) }
        dao.upsertAll(valid.map { it.toEntity() })
        bumpRevision(
            buildSet {
                valid.forEach { add(it.scope to it.scopeKey) }
                previous.forEach { add(QuickDictionaryScope.valueOf(it.scope) to it.scopeKey) }
            }
        )
        return valid.size
    }

    override suspend fun importPack(
        localPath: String,
        displayName: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        scopeKey: String,
        onProgress: (QuickDictionaryImportProgress) -> Unit,
    ): QuickDictionaryImportResult {
        val callerContext = currentCoroutineContext()
        val normalizedScopeKey = if (scope == QuickDictionaryScope.GLOBAL) "" else scopeKey.trim()
        val roomKeys = dao.getImportDuplicateEntries(scope.name, normalizedScopeKey)
            .asSequence()
            .map { entry -> duplicateKey(QuickDictionaryType.valueOf(entry.type), entry.raw) }
            .toHashSet()
        return packStore.importPack(
            sourceFile = File(localPath),
            displayName = displayName,
            type = type,
            scope = scope,
            scopeKey = normalizedScopeKey,
            isExistingEntry = { entryType, raw ->
                duplicateKey(entryType, raw) in roomKeys ||
                    translationGateway?.containsBuiltInEntry(entryType, raw) == true ||
                    packStore.containsEntry(
                        type = entryType,
                        raw = raw,
                        scope = scope,
                        scopeKey = normalizedScopeKey,
                    )
            },
            onProgress = { progress ->
                callerContext.ensureActive()
                onProgress(progress)
            },
        ).also { result ->
            result.pack?.let { pack ->
                bumpRevision(setOf(pack.scope to pack.scopeKey))
            }
        }
    }

    override suspend fun deletePack(id: String) {
        val pack = packStore.getPack(id) ?: return
        packStore.deletePack(id)
        bumpRevision(setOf(pack.scope to pack.scopeKey))
    }

    override suspend fun deleteEntry(id: Long) {
        require(id > 0) { "Only user dictionary entries can be deleted" }
        val entry = dao.getEntry(id) ?: return
        dao.deleteEntry(id)
        bumpRevision(
            setOf(QuickDictionaryScope.valueOf(entry.scope) to entry.scopeKey)
        )
    }

    override suspend fun deleteUniverse(key: String) {
        val normalizedKey = key.trim()
        require(normalizedKey.isNotEmpty()) { "Universe key must not be empty" }
        dao.deleteUniverseEntries(normalizedKey)
        dao.deleteUniverse(normalizedKey)
        bumpRevision(
            setOf(
                QuickDictionaryScope.GLOBAL to "",
                QuickDictionaryScope.UNIVERSE to normalizedKey,
            )
        )
    }

    private fun bumpRevision(scopes: Set<Pair<QuickDictionaryScope, String>>) {
        if (scopes.isEmpty()) return
        val editor = revisionPreferences.edit()
        editor.putLong(REVISION_KEY, nextRevision(currentRevision))
        scopes.asSequence()
            .mapNotNull { (scope, scopeKey) ->
                normalizedScopeKey(scope, scopeKey)?.let { scope to it }
            }
            .distinct()
            .forEach { (scope, scopeKey) ->
                val key = revisionKey(scope, scopeKey)
                editor.putLong(key, nextRevision(revisionPreferences.getLong(key, 0L)))
            }
        editor.apply()
    }

    private fun migrateLegacyRevision() {
        if (revisionPreferences.getBoolean(SCOPED_REVISION_MIGRATED, false)) return
        val legacyRevision = revisionPreferences.getLong(REVISION_KEY, 0L)
        revisionPreferences.edit()
            .putLong(revisionKey(QuickDictionaryScope.GLOBAL, ""), legacyRevision)
            .putBoolean(SCOPED_REVISION_MIGRATED, true)
            .apply()
    }

    private fun normalizedScopeKey(scope: QuickDictionaryScope, scopeKey: String): String? {
        if (scope == QuickDictionaryScope.GLOBAL) return ""
        return scopeKey.trim().takeIf(String::isNotEmpty)
    }

    private fun revisionKey(scope: QuickDictionaryScope, scopeKey: String): String {
        val identity = if (scope == QuickDictionaryScope.GLOBAL) {
            "global"
        } else {
            MessageDigest.getInstance("SHA-256")
                .digest(scopeKey.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
        return "$SCOPED_REVISION_PREFIX${scope.name.lowercase()}:$identity"
    }

    private fun nextRevision(current: Long): Long =
        if (current == Long.MAX_VALUE) 1L else current + 1L

    private fun duplicateKey(type: QuickDictionaryType, raw: String): String {
        val lane = if (type == QuickDictionaryType.PHONETIC) "phonetic" else "translation"
        return "$lane\u0000${raw.trim().lowercase()}"
    }

    private fun QuickDictionaryEntryEntity.toDomain() = QuickDictionaryEntry(
        id = id,
        raw = raw,
        hanViet = hanViet,
        target = if (QuickDictionaryType.valueOf(type) == QuickDictionaryType.IGNORE) {
            ""
        } else {
            cleanQuickDictionaryTarget(target)
        },
        type = QuickDictionaryType.valueOf(type),
        scope = QuickDictionaryScope.valueOf(scope),
        scopeKey = scopeKey,
        enabled = enabled,
        updatedAt = updatedAt,
    )

    private fun QuickDictionaryEntry.toEntity() = QuickDictionaryEntryEntity(
        id = id,
        raw = raw.trim(),
        hanViet = hanViet.trim(),
        target = target.trim(),
        type = type.name,
        scope = scope.name,
        scopeKey = scopeKey,
        enabled = enabled,
        updatedAt = updatedAt,
    )

    private fun QuickDictionaryEntry.normalizedForStorage(): QuickDictionaryEntry {
        return copy(
            raw = raw.trim(),
            hanViet = hanViet.trim(),
            target = if (type == QuickDictionaryType.IGNORE) {
                ""
            } else {
                cleanQuickDictionaryTarget(target)
            },
            scopeKey = scopeKey.trim(),
        )
    }

    private fun QuickDictionaryUniverseEntity.toDomain() = QuickDictionaryUniverse(
        key = universeKey,
        name = name,
        contextMarkers = contextMarkers.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList(),
        enabled = enabled,
        updatedAt = updatedAt,
    )

    private fun QuickDictionaryUniverse.toEntity() = QuickDictionaryUniverseEntity(
        universeKey = key.trim(),
        name = name.trim(),
        contextMarkers = contextMarkers.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n"),
        enabled = enabled,
        updatedAt = updatedAt,
    )

    private companion object {
        const val REVISION_PREFERENCES = "quick_dictionary_revision"
        const val REVISION_KEY = "revision"
        const val SCOPED_REVISION_PREFIX = "revision.scope."
        const val SCOPED_REVISION_MIGRATED = "revision.scope.migrated"
        const val MAX_ROOM_IN_ARGS = 900
    }
}
