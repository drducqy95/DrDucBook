package io.legado.app.domain.agent

import io.legado.app.domain.model.AiToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AgentPermissionBrokerTest {

    @Test
    fun readToolDoesNotRequireApproval() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = AiToolCall(
            id = "call_read",
            name = "fetch_internet_page",
            arguments = """{"url":"https://example.com"}""",
        )

        assertFalse(broker.requiresApproval(call.name))
        broker.requireCanExecute(call, approval = null)
    }

    @Test
    fun bookDictionaryReadToolDoesNotRequireApproval() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = AiToolCall(
            id = "call_read_dictionary",
            name = "list_book_dictionary_terms",
            arguments = """{"bookUrl":"book_1"}""",
        )

        assertFalse(broker.requiresApproval(call.name))
        broker.requireCanExecute(call, approval = null)
    }

    @Test
    fun toolCapabilitiesCoverNetworkSourceFileAndAuthoringLevels() {
        val broker = AgentPermissionBroker(fixedClock())

        assertEquals(
            setOf(
                AgentToolCapability.READ,
                AgentToolCapability.NETWORK,
                AgentToolCapability.SOURCE,
            ),
            broker.capabilitiesFor("search_book_sources"),
        )
        assertTrue(AgentToolCapability.SOURCE in broker.capabilitiesFor("diagnose_book_source"))
        assertTrue(AgentToolCapability.WRITE in broker.capabilitiesFor("repair_book_source"))
        assertTrue(AgentToolCapability.FILE in broker.capabilitiesFor("install_vbook_plugin"))
        assertTrue(AgentToolCapability.AUTHORING in broker.capabilitiesFor("save_authoring_project"))
        assertTrue(AgentToolCapability.WRITE in broker.capabilitiesFor("delete_authoring_project"))
    }

    @Test(expected = AgentPermissionException::class)
    fun mutationWithoutApprovalIsRejected() {
        val broker = AgentPermissionBroker(fixedClock())

        broker.requireCanExecute(
            call = mutationCall(),
            approval = null,
        )
    }

    @Test
    fun approvedMutationCanExecuteOnce() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall()
        val proposal = broker.createProposal(
            conversationId = "chat_1",
            toolCalls = listOf(call),
        )
        val approval = broker.approve(proposal)

        assertTrue(broker.requiresApproval(call.name))
        broker.requireCanExecute(call, approval)
    }

    @Test
    fun sessionGrantCanBeRevoked() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall()
        val proposal = broker.createProposal("chat_1", listOf(call))
        val approval = broker.approve(proposal, AgentApprovalScope.SESSION)

        broker.requireCanExecute(call, approval)
        assertTrue(broker.hasReusableGrant(call.name, "chat_1"))
        broker.requireCanExecute(
            mutationCall(id = "call_write_session", arguments = """{"key":"session","value":"ok"}"""),
            approval = null,
            conversationId = "chat_1",
        )

        broker.revokeGrants(conversationId = "chat_1", toolName = call.name)

        assertFalse(broker.hasReusableGrant(call.name, "chat_1"))
        assertThrows(AgentPermissionException::class.java) {
            broker.requireCanExecute(
                mutationCall(id = "call_write_after_session_revoke"),
                approval = null,
                conversationId = "chat_1",
            )
        }
    }

    @Test
    fun alwaysGrantExecutesWithoutApprovalUntilRevoked() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall()
        val proposal = broker.createProposal("chat_1", listOf(call))
        val approval = broker.approve(proposal, AgentApprovalScope.ALWAYS)

        broker.requireCanExecute(call, approval)
        broker.requireCanExecute(
            mutationCall(id = "call_write_later", arguments = """{"key":"later","value":"ok"}"""),
            approval = null,
        )

        broker.revokeGrants(toolName = call.name)

        assertThrows(AgentPermissionException::class.java) {
            broker.requireCanExecute(
                mutationCall(id = "call_write_after_revoke", arguments = """{"key":"later","value":"blocked"}"""),
                approval = null,
            )
        }
    }

    @Test
    fun processRestartClearsReusableGrantsByDefault() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall()
        val approval = broker.approve(
            broker.createProposal("chat_1", listOf(call)),
            AgentApprovalScope.ALWAYS,
        )
        broker.requireCanExecute(call, approval)

        val restartedBroker = AgentPermissionBroker(fixedClock())

        assertFalse(restartedBroker.hasReusableGrant(call.name, conversationId = null))
        assertThrows(AgentPermissionException::class.java) {
            restartedBroker.requireCanExecute(call.copy(id = "call_restart"), approval = null)
        }
    }

    @Test
    fun bookDictionaryMutationToolsRequireApproval() {
        val broker = AgentPermissionBroker(fixedClock())

        listOf(
            "save_book_dictionary_term",
            "delete_book_dictionary_term",
            "clear_book_dictionary",
            "add_book_to_bookshelf",
            "repair_book_source",
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
        ).forEach { toolName ->
            val call = mutationCall(name = toolName)

            assertTrue(toolName, broker.requiresApproval(call.name))
            assertTrue(
                toolName,
                runCatching { broker.requireCanExecute(call, approval = null) }
                    .exceptionOrNull() is AgentPermissionException,
            )
        }
    }

    @Test
    fun pluginToolAliasUsesCanonicalPolicyAndApproval() {
        val broker = AgentPermissionBroker(fixedClock())
        val aliasCall = mutationCall(
            id = "call_plugin_alias",
            name = "create_vbook_plugin.draft",
            arguments = """{"name":"Demo","source":"https://books.example","files":[]}""",
        )

        assertEquals("create_vbook_plugin_draft", AgentToolNameNormalizer.canonicalize(aliasCall.name))
        assertTrue(broker.requiresApproval(aliasCall.name))
        assertTrue(AgentToolCapability.FILE in broker.capabilitiesFor(aliasCall.name))

        val proposal = broker.createProposal("chat_1", listOf(aliasCall))
        assertEquals("create_vbook_plugin_draft", proposal.toolCalls.single().toolName)

        broker.requireCanExecute(aliasCall, broker.approve(proposal))
    }

    @Test
    fun disabledPluginPolicyAlsoAppliesToPluginToolAlias() {
        val broker = AgentPermissionBroker(
            clock = fixedClock(),
            mutationEnabled = { true },
            pluginEnabled = { false },
        )

        assertFalse(broker.isToolEnabled("create_vbook_plugin.draft"))
    }

    @Test(expected = AgentPermissionException::class)
    fun approvalCannotBeReusedForSameToolCall() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall()
        val approval = broker.approve(
            broker.createProposal("chat_1", listOf(call)),
        )

        broker.requireCanExecute(call, approval)
        broker.requireCanExecute(call, approval)
    }

    @Test(expected = AgentPermissionException::class)
    fun changedArgumentsAfterApprovalAreRejected() {
        val broker = AgentPermissionBroker(fixedClock())
        val call = mutationCall(arguments = """{"key":"genre","value":"xianxia"}""")
        val approval = broker.approve(
            broker.createProposal("chat_1", listOf(call)),
        )

        broker.requireCanExecute(
            call = call.copy(arguments = """{"key":"genre","value":"romance"}"""),
            approval = approval,
        )
    }

    @Test(expected = AgentPermissionException::class)
    fun batchApprovalRejectsToolCallAddedAfterApproval() {
        val broker = AgentPermissionBroker(fixedClock())
        val approvedCall = mutationCall(
            id = "call_write_1",
            arguments = """{"key":"genre","value":"xianxia"}""",
        )
        val approval = broker.approve(
            broker.createProposal("chat_1", listOf(approvedCall)),
        )

        broker.requireCanExecute(
            call = mutationCall(
                id = "call_write_2",
                arguments = """{"key":"extra","value":"unapproved"}""",
            ),
            approval = approval,
        )
    }

    @Test(expected = AgentPermissionException::class)
    fun expiredProposalCannotBeApproved() {
        val clock = MutableClock(Instant.parse("2026-07-20T00:00:00Z"))
        val broker = AgentPermissionBroker(
            clock = clock,
            tokenTtlMillis = Duration.ofMinutes(1).toMillis(),
        )
        val proposal = broker.createProposal("chat_1", listOf(mutationCall()))

        clock.instant = Instant.parse("2026-07-20T00:02:00Z")
        broker.approve(proposal)
    }

    private fun mutationCall(
        id: String = "call_write",
        name: String = "save_memory",
        arguments: String = """{"key":"genre","value":"xianxia"}""",
    ): AiToolCall =
        AiToolCall(
            id = id,
            name = name,
            arguments = arguments,
        )

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC)

    private class MutableClock(
        var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
