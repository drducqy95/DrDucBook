package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.entities.TranslationRevisionStatus
import io.legado.app.domain.model.protectsMachineTranslation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TranslationCacheRepositoryImplTest {

    private lateinit var root: File
    private lateinit var repository: TranslationCacheRepositoryImpl
    private val book = Book(bookUrl = "https://example.test/book", name = "Test book")
    private val chapter = BookChapter(
        url = "chapter-1",
        title = "Chapter 1",
        bookUrl = book.bookUrl,
        index = 0,
    )

    @Before
    fun setUp() {
        root = Files.createTempDirectory("translation-cache-test").toFile()
        repository = TranslationCacheRepositoryImpl(
            cacheDir = File(root, "chapters"),
            dynamicUiDir = File(root, "dynamic-ui"),
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun chapterCache_isValidatedAndIsolatedByProviderAndTarget() = runBlocking {
        repository.writeTranslation(
            book = book,
            bookChapter = chapter,
            targetLanguage = "vi",
            content = "Bản dịch Google",
            originalContentHash = "source-hash",
            provider = "google",
        )
        repository.writeTranslation(
            book = book,
            bookChapter = chapter,
            targetLanguage = "vi",
            content = "Bản dịch AI",
            originalContentHash = "source-hash",
            provider = "app_ai",
        )

        assertEquals(
            "Bản dịch Google",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "google"),
        )
        assertEquals(
            "Bản dịch AI",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "app_ai"),
        )
        assertNull(
            repository.readCurrentTranslation(book, chapter, "en", "source-hash", "google")
        )
        assertNull(
            repository.readCurrentTranslation(book, chapter, "vi", "changed-source", "google")
        )
    }

    @Test
    fun partialOrFailedChunk_doesNotReplacePermanentChapterPayload() = runBlocking {
        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Bản dịch đã hoàn tất",
            "source-hash",
            "app_ai",
        )

        repository.saveChunk(
            book = book,
            bookChapter = chapter,
            targetLanguage = "vi",
            chunkIndex = 0,
            originalChunkContent = "raw",
            originalContentHash = "source-hash",
            provider = "app_ai",
            status = TranslationCache.STATUS_FAILED,
            translatedContent = null,
            errorMessage = "network error",
        )

        assertEquals(
            "Bản dịch đã hoàn tất",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "app_ai"),
        )

        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Bản dịch retrans thành công",
            "source-hash",
            "app_ai",
        )
        assertEquals(
            "Bản dịch retrans thành công",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "app_ai"),
        )
    }

    @Test
    fun concurrentChunkCheckpointsAreAtomicallyUpsertedByIndex() = runBlocking {
        coroutineScope {
            (0 until 24).map { chunkIndex ->
                async {
                    repository.saveChunk(
                        book = book,
                        bookChapter = chapter,
                        targetLanguage = "vi",
                        chunkIndex = chunkIndex,
                        originalChunkContent = "raw-$chunkIndex",
                        originalContentHash = "stable-hash",
                        provider = "app_ai",
                        status = TranslationCache.STATUS_SUCCESS,
                        translatedContent = "translated-$chunkIndex",
                        errorMessage = null,
                    )
                }
            }.awaitAll()
        }

        repository.saveChunk(
            book = book,
            bookChapter = chapter,
            targetLanguage = "vi",
            chunkIndex = 0,
            originalChunkContent = "raw-0",
            originalContentHash = "stable-hash",
            provider = "app_ai",
            status = TranslationCache.STATUS_SUCCESS,
            translatedContent = "replacement-0",
            errorMessage = null,
        )

        val chunks = repository.getCachedChunks(
            book = book,
            bookChapter = chapter,
            targetLanguage = "vi",
            contentHash = "stable-hash",
            provider = "app_ai",
        )
        assertEquals(24, chunks.size)
        assertEquals("replacement-0", chunks.first { it.chunkIndex == 0 }.translatedChunkContent)
    }

    @Test
    fun machineDraft_doesNotOverwriteUserEditedOrFinalTranslation() = runBlocking {
        listOf(
            TranslationRevisionStatus.USER_EDITED to "Bản user đã sửa",
            TranslationRevisionStatus.FINAL to "Bản user đã chốt",
        ).forEachIndexed { index, (status, userContent) ->
            val chapterForStatus = chapter.copy(index = index, url = "chapter-$index")
            repository.writeTranslation(
                book,
                chapterForStatus,
                "vi",
                "Machine draft",
                "source-hash",
                "app_ai",
            )
            repository.writeTranslation(
                book = book,
                bookChapter = chapterForStatus,
                targetLanguage = "vi",
                content = userContent,
                originalContentHash = "source-hash",
                provider = "app_ai",
                revisionStatus = status,
                actor = "user",
                parentRevisionId = null,
            )

            repository.writeTranslation(
                book,
                chapterForStatus,
                "vi",
                "Background retry",
                "source-hash",
                "app_ai",
            )

            assertEquals(
                userContent,
                repository.readCurrentTranslation(book, chapterForStatus, "vi", "source-hash", "app_ai"),
            )
        }
    }

    @Test
    fun finalizeUnlockAndRestore_preserveImmutableRevisionHistory() = runBlocking {
        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Machine draft",
            "raw-v1",
            "app_ai",
        )
        val draft = repository.getCurrentRevision(book, chapter, "vi", "app_ai", "raw-v1")!!
        val edited = repository.saveUserEdit(
            book,
            chapter,
            "vi",
            "app_ai",
            "User edited",
            "raw-v1",
        )
        val final = repository.finalizeChapter(book, chapter, "vi", "app_ai")

        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Background retry must not win",
            "raw-v1",
            "app_ai",
        )

        assertEquals(TranslationRevisionStatus.FINAL, final.status)
        assertEquals(edited.revisionId, final.parentRevisionId)
        assertEquals(
            "User edited",
            repository.getCurrentRevision(book, chapter, "vi", "app_ai", "raw-v1")?.content,
        )
        assertEquals(
            listOf(final.revisionId, edited.revisionId, draft.revisionId),
            repository.getRevisionHistory(book, chapter, "vi", "app_ai", "raw-v1")
                .map { it.revisionId },
        )

        val unlocked = repository.unlockChapter(
            book,
            chapter,
            "vi",
            "app_ai",
            originalContentHash = "raw-v2",
        )
        assertEquals(TranslationRevisionStatus.USER_EDITED, unlocked.status)
        assertEquals(final.revisionId, unlocked.parentRevisionId)
        assertNotEquals(final.revisionId, unlocked.revisionId)

        val restored = repository.restoreRevision(
            book,
            chapter,
            "vi",
            "app_ai",
            revisionId = draft.revisionId,
            originalContentHash = "raw-v2",
        )
        assertEquals("Machine draft", restored.content)
        assertEquals(TranslationRevisionStatus.USER_EDITED, restored.status)
        assertTrue(repository.getRevisionHistory(book, chapter, "vi", "app_ai").size >= 5)
    }

    @Test
    fun rawHashChange_exposesStaleRevisionWithoutUnlockingFinalForBackground() = runBlocking {
        repository.writeTranslation(book, chapter, "vi", "Final text", "raw-v1", "app_ai")
        repository.finalizeChapter(book, chapter, "vi", "app_ai")

        val stale = repository.getCurrentRevision(book, chapter, "vi", "app_ai", "raw-v2")
        assertEquals(TranslationRevisionStatus.STALE, stale?.status)
        assertEquals(TranslationRevisionStatus.FINAL, stale?.sourceStatus)
        assertTrue(stale?.protectsMachineTranslation == true)

        repository.writeTranslation(book, chapter, "vi", "New machine text", "raw-v2", "app_ai")
        assertEquals(
            "Final text",
            repository.getCurrentRevision(book, chapter, "vi", "app_ai", "raw-v2")?.content,
        )
        assertEquals(
            TranslationRevisionStatus.STALE,
            repository.getCurrentRevision(book, chapter, "vi", "app_ai", "raw-v2")?.status,
        )
    }

    @Test
    fun legacyMetadataWithoutRevisionId_isReadableAndCanBeUpdated() = runBlocking {
        val payload = repository.getCacheFile(book, chapter, "vi", "quick_translator")
        payload.parentFile?.mkdirs()
        payload.writeText("Bản dịch QT cũ")
        File(payload.path + ".meta.json").writeText(
            """{
                "originalContentHash":"raw-v1",
                "provider":"quick_translator",
                "targetLanguage":"vi",
                "updatedAt":123,
                "actor":"machine"
            }""".trimIndent()
        )

        val legacy = repository.getCurrentRevision(
            book,
            chapter,
            "vi",
            "quick_translator",
            "raw-v1",
        )
        assertTrue(legacy?.revisionId?.startsWith("legacy-") == true)
        assertEquals("Bản dịch QT cũ", legacy?.content)

        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Bản dịch QT mới",
            "raw-v1",
            "quick_translator",
        )
        assertEquals(
            "Bản dịch QT mới",
            repository.readCurrentTranslation(
                book,
                chapter,
                "vi",
                "raw-v1",
                "quick_translator",
            ),
        )
    }

    @Test
    fun deletingOneProvider_keepsOtherProviderCache() = runBlocking {
        listOf("google" to "Google", "app_ai" to "AI").forEach { (provider, content) ->
            repository.writeTranslation(
                book,
                chapter,
                "vi",
                content,
                "source-hash",
                provider,
            )
        }

        repository.deleteTranslation(book, chapter, "vi", "google")

        assertNull(
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "google")
        )
        assertEquals(
            "AI",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "app_ai"),
        )
    }

    @Test
    fun clearingDynamicUiCache_doesNotDeleteChapterTranslation() = runBlocking {
        repository.writeTranslation(
            book,
            chapter,
            "vi",
            "Nội dung chương",
            "source-hash",
            "google",
        )
        repository.writeDynamicUiTranslation(
            scopeKey = book.bookUrl,
            originalText = "Original title",
            targetLanguage = "vi",
            provider = "google",
            translatedText = "Tên đã dịch",
        )

        assertEquals(
            "Tên đã dịch",
            repository.readDynamicUiTranslation(
                book.bookUrl,
                "Original title",
                "vi",
                "google",
            ),
        )

        repository.clearDynamicUiTranslations()

        assertNull(
            repository.readDynamicUiTranslation(
                book.bookUrl,
                "Original title",
                "vi",
                "google",
            )
        )
        assertEquals(
            "Nội dung chương",
            repository.readCurrentTranslation(book, chapter, "vi", "source-hash", "google"),
        )
    }
}
