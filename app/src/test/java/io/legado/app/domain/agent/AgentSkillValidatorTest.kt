package io.legado.app.domain.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSkillValidatorTest {

    @Test
    fun validManifestUsesOnlyRegisteredTools() {
        val result = AgentSkillValidator.validate(
            draft = draft(allowedTools = listOf("search_books", "get_chapter_content")),
            availableTools = setOf("search_books", "get_chapter_content"),
        )

        assertTrue(result.errors.toString(), result.valid)
    }

    @Test
    fun rejectsUnknownToolInvalidVersionAndEmbeddedSecret() {
        val result = AgentSkillValidator.validate(
            draft = draft(
                version = "latest",
                instructions = "Use bearer abcdefghijklmnopqrstuvwxyz123456 for requests",
                allowedTools = listOf("shell_exec"),
            ),
            availableTools = setOf("search_books"),
        )

        assertFalse(result.valid)
        assertTrue(result.message.contains("semantic versioning"))
        assertTrue(result.message.contains("Unknown tools"))
        assertTrue(result.message.contains("secret"))
    }

    @Test
    fun rejectsPathTraversalAndRemoteExecutableDependency() {
        val result = AgentSkillValidator.validate(
            draft = draft().copy(
                requirements = listOf("../outside/plugin.js", "https://example.test/install.sh"),
            ),
            availableTools = emptySet(),
        )

        assertFalse(result.valid)
        assertTrue(result.message.contains("paths"))
    }

    private fun draft(
        version: String = "1.0.0",
        instructions: String = "Read cached content before answering.",
        allowedTools: List<String> = emptyList(),
    ) = AgentSkillDraft(
        slug = "chapter_reader",
        name = "Chapter reader",
        description = "Read a cached chapter safely",
        version = version,
        instructions = instructions,
        allowedTools = allowedTools,
    )
}
