package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiMemoryFts
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMemoryDao {

    @Query("SELECT * FROM ai_memory WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    fun observeByConversation(conversationId: String): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE conversationId = '' ORDER BY updatedAt DESC")
    fun observeGlobal(): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory ORDER BY pinned DESC, updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId = :scopeId ORDER BY pinned DESC, updatedAt DESC")
    fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE scope = :scope ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllByScope(scope: String): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): List<AiMemory>

    @Query("SELECT * FROM ai_memory WHERE conversationId = ''")
    suspend fun getGlobal(): List<AiMemory>

    @Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId = :scopeId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getByScope(scope: String, scopeId: String): List<AiMemory>

    @Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId IN (:scopeIds) ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getByScopeIds(scope: String, scopeIds: List<String>): List<AiMemory>

    @Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId IN (:scopeIds) ORDER BY pinned DESC, updatedAt DESC")
    fun observeByScopeIds(scope: String, scopeIds: List<String>): Flow<List<AiMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AiMemory)

    @Insert
    suspend fun insertSearchEntry(entry: AiMemoryFts)

    @Query("DELETE FROM ai_memory_fts WHERE conversationId = :conversationId AND `key` = :key")
    suspend fun deleteSearchEntry(conversationId: String, key: String)

    @Query("DELETE FROM ai_memory_fts WHERE conversationId = :conversationId")
    suspend fun deleteSearchEntries(conversationId: String)

    @Transaction
    suspend fun upsertWithSearch(memory: AiMemory) {
        upsert(memory)
        deleteSearchEntry(memory.conversationId, memory.key)
        insertSearchEntry(AiMemoryFts.from(memory))
    }

    @Query("UPDATE ai_memory SET pinned = :pinned, updatedAt = :updatedAt WHERE conversationId = :conversationId AND `key` = :key")
    suspend fun updatePinned(conversationId: String, key: String, pinned: Boolean, updatedAt: Long)

    @Query("DELETE FROM ai_memory WHERE conversationId = :conversationId AND `key` = :key")
    suspend fun delete(conversationId: String, key: String)

    @Transaction
    suspend fun deleteWithSearch(conversationId: String, key: String) {
        delete(conversationId, key)
        deleteSearchEntry(conversationId, key)
    }

    @Query("DELETE FROM ai_memory WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: String)

    @Transaction
    suspend fun deleteAllForConversationWithSearch(conversationId: String) {
        deleteAllForConversation(conversationId)
        deleteSearchEntries(conversationId)
    }

    @Query(
        """
        SELECT memory.* FROM ai_memory AS memory
        INNER JOIN ai_memory_fts AS search
            ON search.conversationId = memory.conversationId AND search.`key` = memory.`key`
        WHERE ai_memory_fts MATCH :matchQuery
          AND (:scope IS NULL OR memory.scope = :scope)
          AND (:scopeId IS NULL OR memory.scopeId = :scopeId)
        ORDER BY memory.pinned DESC, memory.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun search(
        matchQuery: String,
        scope: String?,
        scopeId: String?,
        limit: Int,
    ): List<AiMemory>

    @Query(
        """
        SELECT memory.* FROM ai_memory AS memory
        INNER JOIN ai_memory_fts AS search
            ON search.conversationId = memory.conversationId AND search.`key` = memory.`key`
        WHERE ai_memory_fts MATCH :matchQuery
          AND memory.conversationId IN ('', :conversationId)
        ORDER BY memory.pinned DESC, memory.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchForPrompt(
        matchQuery: String,
        conversationId: String,
        limit: Int,
    ): List<AiMemory>
}
