package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonParser
import io.legado.app.data.AppDatabase
import io.legado.app.domain.agenttools.CustomAgentToolDraft
import io.legado.app.domain.agenttools.CustomAgentToolLifecycleState
import io.legado.app.domain.agenttools.CustomAgentToolTestStatus
import io.legado.app.domain.agenttools.CustomAgentToolValidationStatus
import io.legado.app.domain.model.AiToolCall
import io.legado.app.utils.GSON
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CustomAgentToolRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CustomAgentToolRepository

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CustomAgentToolRepository(database.aiCustomToolDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun draftValidateTestApproveEnableAndRollbackUpdateRegistryAtomically() {
        runBlocking {
        val first = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.0.0", add = 0),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )

        assertFalse(first.enabled)
        assertEquals(null, first.activeVersionId)
        assertEquals(CustomAgentToolLifecycleState.DRAFT, first.latestVersion!!.lifecycleState)
        assertEquals(CustomAgentToolValidationStatus.VALID, first.latestVersion!!.validationStatus)
        assertTrue(repository.registeredToolDefinitions().isEmpty())
        assertTrue(repository.availableToolDefinitions().isEmpty())

        repository.validateLatestDraft(first.id)
        val fixture = repository.runFixture(first.id)
        assertEquals(CustomAgentToolTestStatus.PASS, fixture.status)
        val approved = repository.approveLatestVersion(first.id)
        assertFalse(approved.enabled)
        assertEquals("1.0.0", approved.activeVersion!!.version)
        assertEquals(listOf("custom_sum_tool"), repository.registeredToolDefinitions().map { it.name })
        assertTrue(repository.availableToolDefinitions().isEmpty())

        val enabled = repository.setEnabled(first.id, true)
        assertTrue(enabled.enabled)
        assertEquals(listOf("custom_sum_tool"), repository.availableToolDefinitions().map { it.name })
        assertEquals(
            JsonParser.parseString("""{"sum":5}"""),
            JsonParser.parseString(execute("""{"a":2,"b":3}""")),
        )

        val second = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.1.0", add = 10),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )
        assertTrue(second.enabled)
        assertEquals("1.0.0", second.activeVersion!!.version)
        assertNotEquals(second.activeVersionId, second.latestVersion!!.id)
        assertEquals(
            JsonParser.parseString("""{"sum":5}"""),
            JsonParser.parseString(execute("""{"a":2,"b":3}""")),
        )

        repository.validateLatestDraft(second.id)
        repository.runFixture(second.id)
        val activated = repository.approveLatestVersion(second.id)
        assertEquals("1.1.0", activated.activeVersion!!.version)
        assertEquals(
            JsonParser.parseString("""{"sum":15}"""),
            JsonParser.parseString(execute("""{"a":2,"b":3}""")),
        )

        val rolledBack = repository.rollback(second.id)
        assertEquals("1.0.0", rolledBack.activeVersion!!.version)
        assertEquals(
            JsonParser.parseString("""{"sum":5}"""),
            JsonParser.parseString(execute("""{"a":2,"b":3}""")),
        )
        }
    }

    @Test
    fun approvalRequiresValidationAndPassingFixture() {
        runBlocking {
        val snapshot = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.0.0", add = 0),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.approveLatestVersion(snapshot.id) }
        }

        repository.validateLatestDraft(snapshot.id)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.approveLatestVersion(snapshot.id) }
        }

        repository.runFixture(snapshot.id)
        val approved = repository.approveLatestVersion(snapshot.id)
        assertEquals("1.0.0", approved.activeVersion!!.version)
        }
    }

    @Test
    fun invalidDraftCannotBeApprovedOrEnabled() {
        runBlocking {
        val snapshot = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(
                    version = "1.0.0",
                    script = """
                        function execute(input, context) {
                          java.lang.Runtime.getRuntime().exec("id");
                          return { sum: 0 };
                        }
                    """.trimIndent(),
                ),
                fixtureArgumentsJson = "{}",
            )
        )

        val validated = repository.validateLatestDraft(snapshot.id)
        assertEquals(CustomAgentToolValidationStatus.INVALID, validated.latestVersion!!.validationStatus)
        assertEquals(CustomAgentToolLifecycleState.DRAFT, validated.latestVersion!!.lifecycleState)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.approveLatestVersion(snapshot.id) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setEnabled(snapshot.id, true) }
        }
        }
    }

    @Test
    fun builtInToolNameIsRejectedAndDeleteRemovesDefinition() {
        runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createDraft(
                    CustomAgentToolDraft(
                        manifestJson = manifestJson(toolName = "search_books", version = "1.0.0"),
                    )
                )
            }
        }

        val snapshot = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.0.0"),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )
        repository.validateLatestDraft(snapshot.id)
        repository.runFixture(snapshot.id)
        repository.approveLatestVersion(snapshot.id)
        repository.setEnabled(snapshot.id, true)
        assertTrue(repository.availableToolDefinitions().isNotEmpty())

        repository.delete(snapshot.id)

        assertTrue(repository.registeredToolDefinitions().isEmpty())
        assertTrue(repository.availableToolDefinitions().isEmpty())
        }
    }

    @Test
    fun repositoryRecreationRestoresApprovedStateWithoutAutoEnablingNewDraft() {
        runBlocking {
        val first = repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.0.0", add = 0),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )
        repository.validateLatestDraft(first.id)
        repository.runFixture(first.id)
        repository.approveLatestVersion(first.id)
        repository.setEnabled(first.id, true)

        repository.createDraft(
            CustomAgentToolDraft(
                manifestJson = manifestJson(version = "1.1.0", add = 10),
                fixtureArgumentsJson = """{"a":2,"b":3}""",
            )
        )
        val restored = CustomAgentToolRepository(database.aiCustomToolDao)
        val snapshot = restored.observeTools().first().single()

        assertEquals(listOf("custom_sum_tool"), restored.availableToolDefinitions().map { it.name })
        assertEquals("1.1.0", snapshot.latestVersion!!.version)
        assertEquals("1.0.0", snapshot.activeVersion!!.version)
        assertEquals(
            JsonParser.parseString("""{"sum":5}"""),
            JsonParser.parseString(
                restored.execute(
                    AiToolCall(
                        id = "call_1",
                        name = "custom_sum_tool",
                        arguments = """{"a":2,"b":3}""",
                    )
                )!!.content,
            ),
        )
        }
    }

    private suspend fun execute(argumentsJson: String): String {
        return repository.execute(
            AiToolCall(
                id = "call_1",
                name = "custom_sum_tool",
                arguments = argumentsJson,
            )
        )!!.content
    }

    private fun manifestJson(
        toolName: String = "custom_sum_tool",
        version: String,
        add: Int = 0,
        script: String = """
            function execute(input, context) {
              return { sum: input.a + input.b + $add };
            }
        """.trimIndent(),
    ): String {
        return GSON.toJson(
            linkedMapOf(
                "schemaVersion" to 1,
                "id" to toolName,
                "name" to "Custom sum",
                "description" to "A deterministic custom sum tool",
                "version" to version,
                "inputSchema" to objectSchema(
                    properties = mapOf(
                        "a" to mapOf("type" to "integer", "description" to "First number"),
                        "b" to mapOf("type" to "integer", "description" to "Second number"),
                    ),
                    required = listOf("a", "b"),
                ),
                "outputSchema" to objectSchema(
                    properties = mapOf(
                        "sum" to mapOf("type" to "integer", "description" to "Sum"),
                    ),
                    required = listOf("sum"),
                ),
                "capabilities" to listOf("READ"),
                "allowedDomains" to emptyList<String>(),
                "timeoutMs" to 1_000,
                "maxOutputChars" to 2_000,
                "script" to script,
            )
        )
    }

    private fun objectSchema(
        properties: Map<String, Any?> = emptyMap(),
        required: List<String> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to required,
        "additionalProperties" to false,
    )
}
