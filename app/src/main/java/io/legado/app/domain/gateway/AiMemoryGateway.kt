package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AiMemoryGateway {
    fun observeByConversation(conversationId: String): Flow<List<AiMemory>>
    fun observeGlobal(): Flow<List<AiMemory>>
    fun observeRecent(limit: Int): Flow<List<AiMemory>>
    fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>>
    fun observeAllByScope(scope: String): Flow<List<AiMemory>> =
        observeRecent(Int.MAX_VALUE).map { memories -> memories.filter { it.scope == scope } }
    suspend fun getByConversation(conversationId: String): List<AiMemory>
    suspend fun getGlobal(): List<AiMemory>
    suspend fun getByScope(scope: String, scopeId: String): List<AiMemory>
    suspend fun getForPrompt(conversationId: String): List<AiMemory>
    suspend fun search(
        query: String,
        scope: String? = null,
        scopeId: String? = null,
        limit: Int = 50,
    ): List<AiMemory>
    suspend fun searchForPrompt(
        query: String,
        conversationId: String,
        limit: Int = 50,
    ): List<AiMemory>
    suspend fun upsert(memory: AiMemory)
    suspend fun updatePinned(conversationId: String, key: String, pinned: Boolean)
    suspend fun delete(conversationId: String, key: String)
    suspend fun deleteAllForConversation(conversationId: String)
}
