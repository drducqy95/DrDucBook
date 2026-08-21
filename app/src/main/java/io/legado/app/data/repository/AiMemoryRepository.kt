package io.legado.app.data.repository

import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.entities.AiMemory
import io.legado.app.domain.gateway.AiMemoryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiMemoryRepository(
    private val aiMemoryDao: AiMemoryDao
) : AiMemoryGateway {

    override fun observeByConversation(conversationId: String): Flow<List<AiMemory>> =
        aiMemoryDao.observeByConversation(conversationId)

    override fun observeGlobal(): Flow<List<AiMemory>> = aiMemoryDao.observeGlobal()

    override fun observeRecent(limit: Int): Flow<List<AiMemory>> = aiMemoryDao.observeRecent(limit)

    override fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>> =
        aiMemoryDao.observeByScope(scope, scopeId)

    override fun observeByScopeIds(scope: String, scopeIds: List<String>): Flow<List<AiMemory>> =
        if (scopeIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else aiMemoryDao.observeByScopeIds(scope, scopeIds)

    override fun observeAllByScope(scope: String): Flow<List<AiMemory>> =
        aiMemoryDao.observeAllByScope(scope)

    override suspend fun getByConversation(conversationId: String): List<AiMemory> =
        withContext(Dispatchers.IO) { aiMemoryDao.getByConversation(conversationId) }

    override suspend fun getGlobal(): List<AiMemory> =
        withContext(Dispatchers.IO) { aiMemoryDao.getGlobal() }

    override suspend fun getByScope(scope: String, scopeId: String): List<AiMemory> =
        withContext(Dispatchers.IO) { aiMemoryDao.getByScope(scope, scopeId) }

    override suspend fun getByScopeIds(scope: String, scopeIds: List<String>): List<AiMemory> =
        withContext(Dispatchers.IO) {
            if (scopeIds.isEmpty()) emptyList() else aiMemoryDao.getByScopeIds(scope, scopeIds)
        }

    override suspend fun getForPrompt(conversationId: String): List<AiMemory> =
        withContext(Dispatchers.IO) {
            val global = aiMemoryDao.getGlobal()
            val scoped = if (conversationId.isNotBlank()) {
                aiMemoryDao.getByConversation(conversationId)
            } else {
                emptyList()
            }
            global + scoped
        }

    override suspend fun search(
        query: String,
        scope: String?,
        scopeId: String?,
        limit: Int,
    ): List<AiMemory> = withContext(Dispatchers.IO) {
        val matchQuery = buildAiMemoryFtsQuery(query) ?: return@withContext emptyList()
        aiMemoryDao.search(
            matchQuery = matchQuery,
            scope = scope?.takeIf(String::isNotBlank),
            scopeId = scopeId?.takeIf(String::isNotBlank),
            limit = limit.coerceIn(1, MAX_SEARCH_RESULTS),
        )
    }

    override suspend fun searchForPrompt(
        query: String,
        conversationId: String,
        limit: Int,
    ): List<AiMemory> = withContext(Dispatchers.IO) {
        val matchQuery = buildAiMemoryFtsQuery(query) ?: return@withContext emptyList()
        aiMemoryDao.searchForPrompt(
            matchQuery = matchQuery,
            conversationId = conversationId,
            limit = limit.coerceIn(1, MAX_SEARCH_RESULTS),
        )
    }

    override suspend fun upsert(memory: AiMemory) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedScope = memory.scope.ifBlank {
            AiMemory.scopeFromConversation(memory.conversationId)
        }
        val normalizedScopeId = memory.normalizedScopeId(normalizedScope)
        aiMemoryDao.upsertWithSearch(
            memory.copy(
                conversationId = primaryConversationId(normalizedScope, normalizedScopeId),
                scope = normalizedScope,
                scopeId = normalizedScopeId,
                createdAt = memory.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            )
        )
    }

    override suspend fun updatePinned(conversationId: String, key: String, pinned: Boolean) =
        withContext(Dispatchers.IO) {
            aiMemoryDao.updatePinned(
                conversationId = conversationId,
                key = key,
                pinned = pinned,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun delete(conversationId: String, key: String) = withContext(Dispatchers.IO) {
        aiMemoryDao.deleteWithSearch(conversationId, key)
    }

    override suspend fun deleteAllForConversation(conversationId: String) =
        withContext(Dispatchers.IO) {
            aiMemoryDao.deleteAllForConversationWithSearch(conversationId)
        }

    private fun AiMemory.normalizedScopeId(scope: String): String {
        return when (scope) {
            AiMemory.SCOPE_GLOBAL -> ""
            AiMemory.SCOPE_CONVERSATION -> scopeId.ifBlank { conversationId }
            else -> scopeId.ifBlank {
                conversationId.removePrefix("$scope:")
            }
        }
    }

    private fun primaryConversationId(scope: String, scopeId: String): String {
        return when (scope) {
            AiMemory.SCOPE_GLOBAL -> ""
            AiMemory.SCOPE_CONVERSATION -> scopeId
            else -> "$scope:$scopeId"
        }
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 200
    }
}

internal fun buildAiMemoryFtsQuery(query: String): String? {
    val tokens = Regex("[\\p{L}\\p{N}_]+")
        .findAll(query)
        .map { it.value.lowercase() }
        .filter(String::isNotBlank)
        .distinct()
        .take(12)
        .toList()
    return tokens.takeIf(List<String>::isNotEmpty)
        ?.joinToString(" ") { "$it*" }
}
