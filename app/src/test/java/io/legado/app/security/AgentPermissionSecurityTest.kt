package io.legado.app.security

import io.legado.app.data.repository.isUnsafeAgentVbookPluginPath
import io.legado.app.data.repository.validateAgentVbookPluginInstallFilePath
import io.legado.app.data.repository.validateInternetFetchUrl
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentPermissionException
import io.legado.app.domain.model.AiToolCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AgentPermissionSecurityTest {

    @Test
    fun disabledMutationSkillAndPluginToolsCannotExecute() {
        val broker = AgentPermissionBroker(
            mutationEnabled = { false },
            skillEnabled = { false },
            pluginEnabled = { false },
        )

        listOf(
            call("save_memory"),
            call("list_agent_skills"),
            call("install_vbook_plugin"),
            call("install_legado_book_source"),
        ).forEach { toolCall ->
            assertFalse(toolCall.name, broker.isToolEnabled(toolCall.name))
            assertThrows(AgentPermissionException::class.java) {
                broker.requireCanExecute(toolCall, approval = null)
            }
        }
        assertTrue(broker.isToolEnabled("search_books"))
    }

    @Test
    fun enablingSkillOrPluginDoesNotBypassGlobalMutationGate() {
        val broker = AgentPermissionBroker(
            mutationEnabled = { false },
            skillEnabled = { true },
            pluginEnabled = { true },
        )

        assertTrue(broker.isToolEnabled("list_agent_skills"))
        assertFalse(broker.isToolEnabled("create_agent_skill_draft"))
        assertFalse(broker.isToolEnabled("install_vbook_plugin"))
        assertFalse(broker.isToolEnabled("install_legado_book_source"))
    }

    @Test
    fun pluginInstallPathsRejectTraversalAbsoluteAndUriInputs() {
        listOf(
            "../outside.zip",
            "folder/../../outside.zip",
            "C:\\temp\\plugin.zip",
            "https://example.com/plugin.zip",
        ).forEach { unsafe ->
            assertTrue(unsafe, runCatching { validateAgentVbookPluginInstallFilePath(unsafe) }.isFailure)
        }
        assertTrue(isUnsafeAgentVbookPluginPath("../outside.zip"))
    }

    @Test
    fun internetFetchRejectsLoopbackPrivateAndCredentialedUrls() {
        assertTrue(validateInternetFetchUrl("http://localhost/private").isFailure)
        assertTrue(validateInternetFetchUrl("https://user:pass@example.com/private").isFailure)

        val privateResult = validateInternetFetchUrl("https://example.com/private") {
            arrayOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1)))
        }
        val loopbackResult = validateInternetFetchUrl("https://example.com/private") {
            arrayOf(InetAddress.getLoopbackAddress())
        }
        assertTrue(privateResult.isFailure)
        assertTrue(loopbackResult.isFailure)
    }

    private fun call(name: String) = AiToolCall(
        id = "call-$name",
        name = name,
        arguments = "{}",
    )
}
