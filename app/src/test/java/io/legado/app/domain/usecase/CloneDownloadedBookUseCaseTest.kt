package io.legado.app.domain.usecase

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.CachedChapterSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CloneDownloadedBookUseCaseTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun clonesOnlySelectedRawChaptersWithoutWritingBackToSource() = runBlocking {
        val sourceBook = Book(
            bookUrl = "book://one",
            origin = "https://example.test/source",
            name = "Source book",
            author = "Author",
        )
        val snapshots = listOf(
            CachedChapterSnapshot(0, "One", "<p>Alpha</p>"),
            CachedChapterSnapshot(1, "Two", "<p>Beta</p>"),
            CachedChapterSnapshot(2, "Three", "<p>Gamma</p>"),
        )
        val projects = CloneProjectGateway()
        val useCase = CloneDownloadedBookUseCase(
            projects,
            CloneCachedGateway(sourceBook, snapshots),
            unusedTranslationGateway(),
        )

        val project = useCase.execute(
            CloneDownloadedBookRequest(
                bookUrl = sourceBook.bookUrl,
                chapterIndices = setOf(0, 2),
                variant = CloneContentVariant.RAW,
            )
        )

        assertEquals(listOf("One", "Three"), project.chapters.map { it.title })
        assertEquals(listOf("Alpha", "Gamma"), project.chapters.map { it.content })
        assertEquals("Source book", sourceBook.name)
        assertEquals("<p>Alpha</p>", snapshots.first().content)
        assertEquals(project, projects.saved)
        assertNull(project.document?.chapters?.firstOrNull()?.blocks?.firstOrNull()?.geometry)
    }

    private fun unusedTranslationGateway(): TranslationCacheGateway = Proxy.newProxyInstance(
        TranslationCacheGateway::class.java.classLoader,
        arrayOf(TranslationCacheGateway::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            else -> null
        }
    } as TranslationCacheGateway
}

private class CloneProjectGateway : AuthoringProjectGateway {
    var saved: AuthoringProject? = null
    override fun observeProjects(kind: AuthoringProjectKind): Flow<List<AuthoringProject>> = flowOf(emptyList())
    override suspend fun getProject(id: String): AuthoringProject? = saved?.takeIf { it.id == id }
    override suspend fun saveProject(project: AuthoringProject) { saved = project }
    override suspend fun deleteProject(id: String) = Unit
    override suspend fun importImage(projectId: String, displayName: String, bytes: ByteArray): String = ""
}

private class CloneCachedGateway(
    private val book: Book,
    private val snapshots: List<CachedChapterSnapshot>,
) : CachedChapterGateway {
    override suspend fun getBooks(): List<Book> = listOf(book)
    override suspend fun getBook(bookUrl: String): Book? = book.takeIf { it.bookUrl == bookUrl }
    override suspend fun getChapterCount(bookUrl: String): Int = snapshots.size
    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> =
        flowOf(*snapshots.toTypedArray())
}
