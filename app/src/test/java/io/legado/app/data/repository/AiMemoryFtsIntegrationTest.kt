package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.AiMemory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiMemoryFtsIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AiMemoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AiMemoryRepository(database.aiMemoryDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ftsFindsKeyAndValuePrefixes() = runBlocking {
        repository.upsert(
            AiMemory(
                conversationId = "chat-a",
                key = "translation_style",
                value = "Prefer concise Vietnamese prose",
            )
        )

        assertEquals(listOf("translation_style"), repository.search("translat").map(AiMemory::key))
        assertEquals(listOf("translation_style"), repository.search("Viet prose").map(AiMemory::key))
    }

    @Test
    fun ftsUpdateAndDeleteCannotReturnStaleRows() = runBlocking {
        val memory = AiMemory(
            conversationId = "",
            key = "theme",
            value = "dark mode",
            scope = AiMemory.SCOPE_GLOBAL,
        )
        repository.upsert(memory)
        repository.upsert(memory.copy(value = "light mode"))

        assertTrue(repository.search("dark").isEmpty())
        assertEquals(1, repository.search("light").size)

        repository.delete("", "theme")
        assertTrue(repository.search("light").isEmpty())
    }

    @Test
    fun promptSearchDoesNotLeakAnotherBookScope() = runBlocking {
        repository.upsert(
            AiMemory(
                conversationId = "book:book-a",
                key = "hero",
                value = "Azure Sword",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-a",
            )
        )
        repository.upsert(
            AiMemory(
                conversationId = "book:book-b",
                key = "hero",
                value = "Azure Spear",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = "book-b",
            )
        )

        val result = repository.searchForPrompt("Azure", "book:book-a")

        assertEquals(listOf("Azure Sword"), result.map(AiMemory::value))
    }
}
