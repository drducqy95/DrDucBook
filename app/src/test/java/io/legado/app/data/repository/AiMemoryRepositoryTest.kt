package io.legado.app.data.repository

import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiMemoryFts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMemoryRepositoryTest {

    @Test
    fun upsertCanonicalizesGlobalMemoryPrimaryKey() = runBlocking {
        val dao = FakeAiMemoryDao()
        val repository = AiMemoryRepository(dao)

        repository.upsert(
            AiMemory(
                conversationId = "chat_1",
                key = "language",
                value = "Vietnamese",
                scope = AiMemory.SCOPE_GLOBAL,
                scopeId = "ignored",
            )
        )

        assertEquals(listOf("language"), repository.getGlobal().map { it.key })
        assertTrue(repository.getByConversation("chat_1").isEmpty())
        assertEquals("", dao.memories.single().conversationId)
        assertEquals("", dao.memories.single().scopeId)
    }

    @Test
    fun getForPromptDoesNotLeakBookScopedMemoryAcrossBooks() = runBlocking {
        val dao = FakeAiMemoryDao()
        val repository = AiMemoryRepository(dao)

        repository.upsert(
            AiMemory(
                conversationId = "",
                key = "tone",
                value = "concise",
                scope = AiMemory.SCOPE_GLOBAL,
            )
        )
        repository.upsert(
            AiMemory(
                conversationId = "chat_a",
                key = "hero",
                value = "book A hero",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-a",
            )
        )
        repository.upsert(
            AiMemory(
                conversationId = "chat_b",
                key = "hero",
                value = "book B hero",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-b",
            )
        )

        val bookAMemories = repository.getForPrompt("book:book-a")
        val bookBMemories = repository.getForPrompt("book:book-b")

        assertEquals(listOf("tone", "hero"), bookAMemories.map { it.key })
        assertEquals("book A hero", bookAMemories.single { it.key == "hero" }.value)
        assertEquals("book B hero", bookBMemories.single { it.key == "hero" }.value)
        assertEquals(
            listOf("book:book-a", "book:book-b"),
            dao.memories.filter { it.scope == AiMemory.SCOPE_BOOK }.map { it.conversationId },
        )
    }

    @Test
    fun searchUsesSanitizedPrefixTokensAndKeepsScopeBoundary() = runBlocking {
        val dao = FakeAiMemoryDao()
        val repository = AiMemoryRepository(dao)
        repository.upsert(
            AiMemory(
                conversationId = "book:book-a",
                key = "hero",
                value = "Linh Canh Hanh Gia",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-a",
            )
        )
        repository.upsert(
            AiMemory(
                conversationId = "book:book-b",
                key = "hero",
                value = "Linh Canh cua sach khac",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-b",
            )
        )

        val result = repository.search(
            query = "Linh, Hanh!",
            scope = AiMemory.SCOPE_BOOK,
            scopeId = "book-a",
        )

        assertEquals(listOf("Linh Canh Hanh Gia"), result.map(AiMemory::value))
        assertEquals("linh* hanh*", dao.lastMatchQuery)
    }

    @Test
    fun updatingMemoryRemovesStaleSearchText() = runBlocking {
        val dao = FakeAiMemoryDao()
        val repository = AiMemoryRepository(dao)
        val original = AiMemory(
            conversationId = "",
            key = "preference",
            value = "dark theme",
            scope = AiMemory.SCOPE_GLOBAL,
        )
        repository.upsert(original)
        repository.upsert(original.copy(value = "light theme"))

        assertTrue(repository.search("dark").isEmpty())
        assertEquals(listOf("light theme"), repository.search("light").map(AiMemory::value))
    }

    private class FakeAiMemoryDao : AiMemoryDao {
        val memories = mutableListOf<AiMemory>()
        val searchEntries = mutableListOf<AiMemoryFts>()
        var lastMatchQuery: String? = null

        override fun observeByConversation(conversationId: String): Flow<List<AiMemory>> =
            flowOf(getByConversationNow(conversationId))

        override fun observeGlobal(): Flow<List<AiMemory>> = flowOf(getGlobalNow())

        override fun observeRecent(limit: Int): Flow<List<AiMemory>> =
            flowOf(memories.sortedByDescending { it.updatedAt }.take(limit))

        override fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>> =
            flowOf(getByScopeNow(scope, scopeId))

        override fun observeAllByScope(scope: String): Flow<List<AiMemory>> =
            flowOf(memories.filter { it.scope == scope }.sortedByDescending { it.updatedAt })

        override suspend fun getByConversation(conversationId: String): List<AiMemory> =
            getByConversationNow(conversationId)

        override suspend fun getGlobal(): List<AiMemory> = getGlobalNow()

        override suspend fun getByScope(scope: String, scopeId: String): List<AiMemory> =
            getByScopeNow(scope, scopeId)

        override suspend fun upsert(memory: AiMemory) {
            memories.removeAll { it.conversationId == memory.conversationId && it.key == memory.key }
            memories += memory
        }

        override suspend fun insertSearchEntry(entry: AiMemoryFts) {
            searchEntries += entry
        }

        override suspend fun deleteSearchEntry(conversationId: String, key: String) {
            searchEntries.removeAll { it.conversationId == conversationId && it.key == key }
        }

        override suspend fun deleteSearchEntries(conversationId: String) {
            searchEntries.removeAll { it.conversationId == conversationId }
        }

        override suspend fun updatePinned(
            conversationId: String,
            key: String,
            pinned: Boolean,
            updatedAt: Long,
        ) {
            memories.replaceAll { memory ->
                if (memory.conversationId == conversationId && memory.key == key) {
                    memory.copy(pinned = pinned, updatedAt = updatedAt)
                } else {
                    memory
                }
            }
        }

        override suspend fun delete(conversationId: String, key: String) {
            memories.removeAll { it.conversationId == conversationId && it.key == key }
        }

        override suspend fun deleteAllForConversation(conversationId: String) {
            memories.removeAll { it.conversationId == conversationId }
        }

        override suspend fun search(
            matchQuery: String,
            scope: String?,
            scopeId: String?,
            limit: Int,
        ): List<AiMemory> {
            lastMatchQuery = matchQuery
            return matchedMemories(matchQuery)
                .filter { scope == null || it.scope == scope }
                .filter { scopeId == null || it.scopeId == scopeId }
                .take(limit)
        }

        override suspend fun searchForPrompt(
            matchQuery: String,
            conversationId: String,
            limit: Int,
        ): List<AiMemory> {
            lastMatchQuery = matchQuery
            return matchedMemories(matchQuery)
                .filter { it.conversationId.isBlank() || it.conversationId == conversationId }
                .take(limit)
        }

        private fun matchedMemories(matchQuery: String): List<AiMemory> {
            val prefixes = matchQuery.split(' ').map { it.removeSuffix("*") }
            val matchedKeys = searchEntries.filter { entry ->
                val indexed = listOf(
                    entry.key,
                    entry.value,
                    entry.scope,
                    entry.scopeId,
                    entry.type,
                ).joinToString(" ").lowercase()
                prefixes.all(indexed::contains)
            }.mapTo(hashSetOf()) { it.conversationId to it.key }
            return memories.filter { (it.conversationId to it.key) in matchedKeys }
        }

        private fun getByConversationNow(conversationId: String): List<AiMemory> =
            memories.filter { it.conversationId == conversationId }
                .sortedByDescending { it.updatedAt }

        private fun getGlobalNow(): List<AiMemory> =
            getByConversationNow("")

        private fun getByScopeNow(scope: String, scopeId: String): List<AiMemory> =
            memories.filter { it.scope == scope && it.scopeId == scopeId }
                .sortedWith(compareByDescending<AiMemory> { it.pinned }.thenByDescending { it.updatedAt })
    }
}
