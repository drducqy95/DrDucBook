package io.legado.app.domain.usecase

import android.app.Application
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.domain.model.VbookContentLockPolicy
import io.legado.app.domain.model.WritingWorkflowStage
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AuthoringProjectUseCaseTest {

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        application.putPrefString(PreferKey.vbookEbookUnlockCode, "")
    }

    @Test
    fun createWritingProjectStartsAtIdeaInput() = runBlocking {
        val useCase = useCaseFor(book(origin = "https://example.org/source"))

        val project = useCase.create(AuthoringProjectKind.WRITING, "New story")

        assertEquals(WritingWorkflowStage.IDEA_INPUT, project.writingWorkflow.stage)
    }

    @Test
    fun cloneDownloadedBookBlocksExternalVbookWithoutUnlockCode() = runBlocking {
        val useCase = useCaseFor(
            book = book(origin = "vbook://plugin/source-id"),
        )

        val error = runCatching {
            useCase.cloneDownloadedBook("book://one")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(VbookContentLockPolicy.LOCKED_MESSAGE, error?.message)
    }

    @Test
    fun cloneDownloadedBookAllowsExternalVbookWithUnlockCode() = runBlocking {
        AppConfig.vbookEbookUnlockCode = VbookContentLockPolicy.REQUIRED_UNLOCK_CODE
        val gateway = FakeAuthoringProjectGateway()
        val useCase = useCaseFor(
            book = book(origin = "vbook://plugin/source-id"),
            projectGateway = gateway,
        )

        val project = useCase.cloneDownloadedBook("book://one")

        assertEquals("vbook://plugin/source-id", project.sourceOrigin)
        assertEquals(1, project.chapters.size)
        assertEquals(project, gateway.savedProject)
    }

    @Test
    fun cloneDownloadedBookAllowsNonVbookSourceWithoutUnlockCode() = runBlocking {
        val useCase = useCaseFor(
            book = book(origin = "https://example.org/source"),
        )

        val project = useCase.cloneDownloadedBook("book://one")

        assertEquals("https://example.org/source", project.sourceOrigin)
        assertEquals(1, project.chapters.size)
    }

    @Test
    fun duplicateProjectCreatesNewIdsAndKeepsDraftContent() = runBlocking {
        val gateway = FakeAuthoringProjectGateway()
        val useCase = useCaseFor(
            book = book(origin = "https://example.org/source"),
            projectGateway = gateway,
        )
        val project = AuthoringProject(
            id = "original",
            kind = AuthoringProjectKind.WRITING,
            title = "Draft",
            chapters = listOf(
                io.legado.app.domain.model.AuthoringChapter(
                    id = "chapter",
                    title = "One",
                    content = "Body",
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            ),
            createdAt = 1L,
            updatedAt = 1L,
        )

        val duplicate = useCase.duplicate(project)

        assertNotEquals(project.id, duplicate.id)
        assertEquals("Draft Copy", duplicate.title)
        assertNotEquals(project.chapters.single().id, duplicate.chapters.single().id)
        assertEquals("Body", duplicate.chapters.single().content)
        assertEquals(duplicate, gateway.savedProject)
    }

    private fun useCaseFor(
        book: Book,
        projectGateway: FakeAuthoringProjectGateway = FakeAuthoringProjectGateway(),
    ): AuthoringProjectUseCase {
        return AuthoringProjectUseCase(
            gateway = projectGateway,
            cachedChapterGateway = FakeAuthoringCachedChapterGateway(
                book = book,
                chapters = listOf(CachedChapterSnapshot(0, "Chapter 1", "<p>Hello</p>")),
            ),
        )
    }

    private fun book(origin: String): Book = Book(
        bookUrl = "book://one",
        origin = origin,
        name = "Book",
        author = "Author",
    )
}

private class FakeAuthoringProjectGateway : AuthoringProjectGateway {
    var savedProject: AuthoringProject? = null

    override fun observeProjects(kind: AuthoringProjectKind): Flow<List<AuthoringProject>> =
        flowOf(savedProject?.let(::listOf).orEmpty())

    override suspend fun getProject(id: String): AuthoringProject? =
        savedProject?.takeIf { it.id == id }

    override suspend fun saveProject(project: AuthoringProject) {
        savedProject = project
    }

    override suspend fun deleteProject(id: String) {
        if (savedProject?.id == id) savedProject = null
    }

    override suspend fun importImage(projectId: String, displayName: String, bytes: ByteArray): String =
        "/tmp/$displayName"
}

private class FakeAuthoringCachedChapterGateway(
    private val book: Book,
    private val chapters: List<CachedChapterSnapshot>,
) : CachedChapterGateway {
    override suspend fun getBook(bookUrl: String): Book? = book.takeIf { it.bookUrl == bookUrl }

    override suspend fun getChapterCount(bookUrl: String): Int = chapters.size

    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> =
        flowOf(*chapters.toTypedArray())
}
