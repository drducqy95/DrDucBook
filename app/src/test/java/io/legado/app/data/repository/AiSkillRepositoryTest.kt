package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonParser
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.AiSkill
import io.legado.app.data.entities.AiSkillVersion
import io.legado.app.domain.agent.AgentSkillDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiSkillRepositoryTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: AiSkillRepository

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        File(application.filesDir, "agent_skills").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AiSkillRepository(application, database.aiSkillDao)
    }

    @After
    fun tearDown() {
        database.close()
        File(application.filesDir, "agent_skills").deleteRecursively()
    }

    @Test
    fun draftIsDisabledAndNewVersionDoesNotReplaceActiveUntilApproved() = runBlocking {
        val first = repository.createDraft(draft("1.0.0"), AVAILABLE_TOOLS)
        assertFalse(first.enabled)
        assertEquals("1.0.0", first.activeVersion?.version)
        assertTrue(versionFile(first.id, first.activeVersionId!!, "manifest.json").isFile)
        assertTrue(versionFile(first.id, first.activeVersionId!!, "SKILL.md").isFile)
        val manifest = JsonParser.parseString(
            versionFile(first.id, first.activeVersionId!!, "manifest.json").readText(),
        ).asJsonObject
        val provenance = manifest.getAsJsonObject("provenance")
        val lifecycle = manifest.getAsJsonObject("lifecycle")
        assertEquals("agent_skill_draft", provenance.get("source").asString)
        assertEquals("ai_agent", provenance.get("createdBy").asString)
        assertEquals("agent_skill_v1", provenance.get("compatibilitySurface").asString)
        assertEquals("draft", lifecycle.get("state").asString)
        assertFalse(lifecycle.get("enabledByDefault").asBoolean)
        assertTrue(lifecycle.get("activationRequired").asBoolean)

        repository.setEnabled(first.id, true)
        val second = repository.createDraft(draft("1.1.0"), AVAILABLE_TOOLS)
        assertTrue(second.enabled)
        assertEquals("1.0.0", second.activeVersion?.version)
        assertEquals("1.1.0", second.latestVersion?.version)
        assertNotEquals(second.activeVersionId, second.latestVersion?.id)

        val activated = repository.activateVersion(second.id, second.latestVersion!!.id)
        assertEquals("1.1.0", activated.activeVersion?.version)
        val rolledBack = repository.rollback(second.id)
        assertEquals("1.0.0", rolledBack.activeVersion?.version)
        assertEquals(1, repository.observeSkills().first().size)
    }

    @Test
    fun invalidDraftCannotBeActivated() {
        runBlocking {
            val first = repository.createDraft(draft("1.0.0"), AVAILABLE_TOOLS)
            val invalid = repository.createDraft(
                draft("2.0.0").copy(allowedTools = listOf("shell_exec")),
                AVAILABLE_TOOLS,
            )
            assertFalse(invalid.latestVersion!!.valid)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.activateVersion(first.id, invalid.latestVersion!!.id) }
            }
        }
    }

    @Test
    fun legacySkillVersionWithoutProvenanceStillEnablesWhenFilesAreComplete() = runBlocking {
        val skillId = "legacy_skill"
        val versionId = "legacy_version_1"
        val now = 1_000L
        val directory = versionFile(skillId, versionId, "manifest.json").parentFile!!
        assertTrue(directory.mkdirs())
        versionFile(skillId, versionId, "manifest.json").writeText(
            """{"schemaVersion":1,"id":"legacy","version":"1.0.0"}""",
        )
        versionFile(skillId, versionId, "SKILL.md").writeText(
            "Read the chapter with get_chapter_content before answering.",
        )
        database.aiSkillDao.saveDraft(
            AiSkill(
                id = skillId,
                slug = "legacy_skill",
                name = "Legacy skill",
                description = "Legacy draft without provenance fields",
                enabled = false,
                activeVersionId = versionId,
                createdAt = now,
                updatedAt = now,
            ),
            AiSkillVersion(
                id = versionId,
                skillId = skillId,
                version = "1.0.0",
                name = "Legacy skill",
                description = "Legacy draft without provenance fields",
                manifestJson = """{"schemaVersion":1,"id":"legacy","version":"1.0.0"}""",
                skillMarkdown = "Read the chapter with get_chapter_content before answering.",
                allowedToolsJson = """["get_chapter_content"]""",
                requirementsJson = "[]",
                validationStatus = AiSkillVersion.STATUS_VALID,
                validationMessage = "",
                createdAt = now,
            ),
        )

        val enabled = repository.setEnabled(skillId, true)

        assertTrue(enabled.enabled)
        assertEquals("1.0.0", enabled.activeVersion?.version)
    }

    private fun draft(version: String) = AgentSkillDraft(
        slug = "chapter_reader",
        name = "Chapter reader $version",
        description = "Read cached chapters",
        version = version,
        instructions = "Read the chapter with get_chapter_content before answering.",
        allowedTools = listOf("get_chapter_content"),
    )

    private fun versionFile(skillId: String, versionId: String, name: String): File =
        File(application.filesDir, "agent_skills/$skillId/versions/$versionId/$name")

    private companion object {
        val AVAILABLE_TOOLS = setOf("get_chapter_content")
    }
}
