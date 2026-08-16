package io.legado.app.data.repository

import io.legado.app.domain.gateway.AuthoringRecoveryType
import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookBlockGeometry
import io.legado.app.domain.model.EbookCodeBlock
import io.legado.app.domain.model.EbookDividerBlock
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookHeadingBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookInlineStyle
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.EbookListBlock
import io.legado.app.domain.model.EbookPageBreakBlock
import io.legado.app.domain.model.EbookPageSize
import io.legado.app.domain.model.EbookParagraphBlock
import io.legado.app.domain.model.EbookQuoteBlock
import io.legado.app.domain.model.WritingWorkflowStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AuthoringProjectFileStoreTest {

    private lateinit var root: File
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "blockType"
    }
    private val legacyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("authoring-store-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun saveProjectWritesVersionedManifestAndRoundTripsAllBlockTypes() {
        val store = AuthoringProjectFileStore(root)
        val project = projectWithAllBlocks("round-trip")

        store.saveProject(project)

        val envelope = readEnvelope("round-trip")
        assertEquals(AuthoringProjectMigrationDispatcher.CURRENT_SCHEMA_VERSION, envelope.schemaVersion)
        assertEquals(project, envelope.project)
        assertTrue(envelope.contentHash.matches(Regex("[0-9a-f]{64}")))
        assertEquals(project, store.loadProjects().single())
    }

    @Test
    fun legacyRawProjectJsonMigratesToVersionedManifest() {
        val project = writingProject("legacy", title = "Legacy")
        File(root, "projects").mkdirs()
        File(root, "projects/legacy.json").writeText(legacyJson.encodeToString(project))

        val loaded = AuthoringProjectFileStore(root).loadProjects()

        assertEquals(project, loaded.single())
        assertTrue(File(root, "projects/legacy/manifest.json").isFile)
        assertTrue(File(root, "legacy-projects/legacy.json").isFile)
        assertFalse(File(root, "projects/legacy.json").exists())
    }

    @Test
    fun versionOneManifestLoadsAfterWorkflowFieldsChangeProjectHash() {
        val project = writingProject("workflow-v1", title = "Legacy workflow")
        val directory = File(root, "projects/workflow-v1").apply { mkdirs() }
        File(directory, "manifest.json").writeText(
            json.encodeToString(
                AuthoringProjectEnvelope(
                    schemaVersion = 1,
                    project = project,
                    contentHash = "legacy-v1-hash",
                    savedAt = 1L,
                )
            )
        )

        val loaded = AuthoringProjectFileStore(root).loadProjects().single()

        assertEquals("Legacy workflow", loaded.title)
        assertEquals(WritingWorkflowStage.READY_TO_WRITE, loaded.writingWorkflow.stage)
    }

    @Test
    fun interruptedTemporaryCommitKeepsPreviousValidManifest() {
        val store = AuthoringProjectFileStore(root)
        val oldProject = writingProject("interrupted", title = "Old")
        val newProject = writingProject("interrupted", title = "New")
        store.saveProject(oldProject)
        File(root, "projects/interrupted/manifest.json.tmp").writeText(
            json.encodeToString(
                AuthoringProjectEnvelope(
                    project = newProject,
                    contentHash = "",
                    savedAt = 2L,
                )
            )
        )

        val loaded = AuthoringProjectFileStore(root).loadProjects().single()

        assertEquals(oldProject, loaded)
    }

    @Test
    fun importAssetUsesContentHashDedupeAndUpdatesProjectManifest() {
        val store = AuthoringProjectFileStore(root)
        store.saveProject(writingProject("asset-project"))
        val bytes = "image bytes".toByteArray()

        val first = store.importAsset("asset-project", "Cover Image.JPG", bytes)
        val second = store.importAsset("asset-project", "Cover Image.JPG", bytes)

        assertEquals(first.absolutePath, second.absolutePath)
        assertTrue(first.isFile)
        assertTrue(first.name.matches(Regex("[0-9a-f]{64}\\.jpg")))
        val envelope = readEnvelope("asset-project")
        assertEquals(1, envelope.assets.size)
        assertEquals(first.nameWithoutExtension, envelope.assets.single().sha256)
        assertTrue(File(root, "assets/asset-project/asset-index.json").isFile)
    }

    @Test
    fun concurrentRepositorySavesForSameProjectKeepManifestDecodable() = runBlocking {
        val repository = AuthoringProjectRepository(root)
        val expectedTitles = (1..30).map { "Draft $it" }.toSet()

        coroutineScope {
            (1..30).map { index ->
                async(Dispatchers.IO) {
                    repository.saveProject(
                        writingProject(
                            id = "shared",
                            title = "Draft $index",
                            updatedAt = index.toLong(),
                        )
                    )
                }
            }.awaitAll()
        }

        val loaded = AuthoringProjectRepository(root).getProject("shared")

        assertNotNull(loaded)
        assertTrue(loaded!!.title in expectedTitles)
        assertEquals(loaded, readEnvelope("shared").project)
        assertTrue(readEnvelope("shared").contentHash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun corruptManifestIsQuarantinedAndLatestSnapshotRestored() {
        val store = AuthoringProjectFileStore(root)
        val project = writingProject("recover", title = "Recover", updatedAt = 2L)
        store.saveProject(project)
        File(root, "projects/recover/manifest.json").writeText("{not-json")

        val loaded = store.loadProjects().single()
        val diagnostic = store.recoveryDiagnostics().single()

        assertEquals(project, loaded)
        assertEquals(AuthoringRecoveryType.CORRUPT_MANIFEST, diagnostic.type)
        val recoveryPath = requireNotNull(diagnostic.recoveryPath)
        assertTrue(recoveryPath.startsWith("recovery/quarantine/"))
        assertTrue(File(root, recoveryPath).isFile)
        assertEquals(project, readEnvelope("recover").project)
    }

    @Test
    fun hashMismatchIsQuarantinedAndLatestSnapshotRestored() {
        val store = AuthoringProjectFileStore(root)
        val project = writingProject("hash-recover", title = "Hash", updatedAt = 2L)
        store.saveProject(project)
        val manifest = File(root, "projects/hash-recover/manifest.json")
        val envelope = readEnvelope("hash-recover")
        manifest.writeText(json.encodeToString(envelope.copy(contentHash = "bad-hash")))

        val loaded = store.loadProjects().single()
        val diagnostic = store.recoveryDiagnostics().single()

        assertEquals(project, loaded)
        assertEquals(AuthoringRecoveryType.HASH_MISMATCH, diagnostic.type)
        assertEquals(project, readEnvelope("hash-recover").project)
    }

    @Test
    fun saveHistoryKeepsFiveLatestSnapshots() {
        val store = AuthoringProjectFileStore(root)

        (1..8).forEach { index ->
            store.saveProject(writingProject("history", title = "Draft $index", updatedAt = index.toLong()))
        }

        val snapshots = File(root, "recovery/history/history")
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()

        assertEquals(5, snapshots.size)
    }

    @Test
    fun corruptAssetIndexIsQuarantinedWithoutBreakingSave() {
        val store = AuthoringProjectFileStore(root)
        store.saveProject(writingProject("asset-index"))
        File(root, "assets/asset-index").mkdirs()
        File(root, "assets/asset-index/asset-index.json").writeText("{not-json")

        store.saveProject(writingProject("asset-index", title = "Next", updatedAt = 2L))

        val diagnostic = store.recoveryDiagnostics().single()
        assertEquals(AuthoringRecoveryType.CORRUPT_ASSET_INDEX, diagnostic.type)
        assertEquals("Next", store.loadProjects().single().title)
    }

    @Test
    fun missingAssetIsReportedWithoutCrashingManifestRefresh() {
        val store = AuthoringProjectFileStore(root)
        store.saveProject(writingProject("missing-asset"))
        File(root, "assets/missing-asset").mkdirs()
        File(root, "assets/missing-asset/asset-index.json").writeText(
            """
            {
              "schemaVersion": 1,
              "projectId": "missing-asset",
              "assets": [
                {
                  "assetId": "lost",
                  "originalName": "lost.png",
                  "relativePath": "assets/missing-asset/lost.png",
                  "sha256": "lost",
                  "sizeBytes": 12,
                  "createdAt": 1,
                  "updatedAt": 1
                }
              ],
              "contentHash": "",
              "updatedAt": 1
            }
            """.trimIndent()
        )

        store.saveProject(writingProject("missing-asset", title = "Still here", updatedAt = 2L))

        val diagnostic = store.recoveryDiagnostics().single()
        assertEquals(AuthoringRecoveryType.MISSING_ASSET, diagnostic.type)
        assertEquals("Still here", store.loadProjects().single().title)
    }

    private fun readEnvelope(id: String): AuthoringProjectEnvelope =
        json.decodeFromString(File(root, "projects/$id/manifest.json").readText())

    private fun writingProject(
        id: String,
        title: String = "Project",
        updatedAt: Long = 1L,
    ) = AuthoringProject(
        id = id,
        kind = AuthoringProjectKind.WRITING,
        title = title,
        chapters = listOf(
            AuthoringChapter(
                id = "chapter",
                title = "Chapter",
                content = "Hello",
                createdAt = 1L,
                updatedAt = updatedAt,
            )
        ),
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    private fun projectWithAllBlocks(id: String): AuthoringProject {
        val blocks: List<EbookBlock> = listOf(
            EbookParagraphBlock(
                id = "paragraph",
                name = "Paragraph",
                readingOrder = 0,
                text = "Body",
                style = EbookInlineStyle(bold = true),
            ),
            EbookHeadingBlock(id = "heading", readingOrder = 1, text = "Heading", level = 2),
            EbookQuoteBlock(id = "quote", readingOrder = 2, text = "Quote", attribution = "Author"),
            EbookImageBlock(
                id = "image",
                readingOrder = 3,
                uri = "assets/image.jpg",
                alt = "Alt",
                caption = "Caption",
                originalWidth = 800,
                originalHeight = 600,
                geometry = EbookBlockGeometry(x = 10f, y = 20f),
            ),
            EbookDividerBlock(id = "divider", readingOrder = 4),
            EbookPageBreakBlock(id = "page-break", readingOrder = 5),
            EbookCodeBlock(id = "code", readingOrder = 6, text = "println()", language = "kotlin"),
            EbookListBlock(id = "list", readingOrder = 7, items = listOf("One", "Two"), ordered = true),
        )
        return AuthoringProject(
            id = id,
            kind = AuthoringProjectKind.EBOOK_EDITOR,
            title = "Ebook",
            author = "Author",
            document = EbookDocument(
                layoutMode = EbookLayoutMode.FIXED_PAGE,
                pageSize = EbookPageSize(width = 800f, height = 1200f),
                chapters = listOf(
                    EbookDocumentChapter(
                        id = "ebook-chapter",
                        title = "Chapter",
                        blocks = blocks,
                    )
                ),
            ),
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
