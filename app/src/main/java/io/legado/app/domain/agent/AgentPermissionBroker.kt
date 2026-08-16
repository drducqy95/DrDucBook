package io.legado.app.domain.agent

import io.legado.app.domain.model.AiToolCall
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID

class AgentPermissionBroker(
    private val clock: Clock = Clock.systemUTC(),
    private val tokenTtlMillis: Long = DEFAULT_TOKEN_TTL.toMillis(),
    private val mutationEnabled: () -> Boolean = { true },
    private val skillEnabled: () -> Boolean = { true },
    private val pluginEnabled: () -> Boolean = { true },
) {

    private val proposals = linkedMapOf<String, StoredProposal>()
    private val reusableGrants = linkedMapOf<GrantKey, StoredGrant>()

    fun riskFor(toolName: String): AgentActionRisk {
        val canonicalName = AgentToolNameNormalizer.canonicalize(toolName)
        return defaultToolRisks[canonicalName] ?: AgentActionRisk.READ
    }

    fun capabilitiesFor(toolName: String): Set<AgentToolCapability> {
        val canonicalName = AgentToolNameNormalizer.canonicalize(toolName)
        val configured = defaultToolCapabilities[canonicalName].orEmpty()
        val risk = riskFor(canonicalName)
        return buildSet {
            add(AgentToolCapability.READ)
            addAll(configured)
            if (risk.requiresApproval) add(AgentToolCapability.WRITE)
            if (canonicalName in sourceToolNames) add(AgentToolCapability.SOURCE)
            if (canonicalName in authoringToolNames) add(AgentToolCapability.AUTHORING)
            if (canonicalName in fileToolNames) add(AgentToolCapability.FILE)
            if (canonicalName in networkToolNames) add(AgentToolCapability.NETWORK)
        }
    }

    fun requiresApproval(toolName: String): Boolean {
        return riskFor(toolName).requiresApproval
    }

    fun isToolEnabled(toolName: String): Boolean {
        val canonicalName = AgentToolNameNormalizer.canonicalize(toolName)
        val requiresMutation = riskFor(canonicalName).requiresApproval
        return when {
            canonicalName in skillToolNames ->
                skillEnabled() && (!requiresMutation || mutationEnabled())
            canonicalName in pluginToolNames ->
                pluginEnabled() && (!requiresMutation || mutationEnabled())
            requiresMutation -> mutationEnabled()
            else -> true
        }
    }

    @Synchronized
    fun hasReusableGrant(toolName: String, conversationId: String?): Boolean {
        val canonicalName = AgentToolNameNormalizer.canonicalize(toolName)
        reusableGrants[GrantKey(conversationId, canonicalName)]?.let { return true }
        reusableGrants[GrantKey(null, canonicalName)]?.let { return it.scope == AgentApprovalScope.ALWAYS }
        return false
    }

    @Synchronized
    fun createProposal(
        conversationId: String?,
        toolCalls: List<AiToolCall>,
    ): AgentActionProposal {
        val canonicalCalls = toolCalls.map { it.withCanonicalToolName() }
        canonicalCalls.firstOrNull { !isToolEnabled(it.name) }?.let {
            throw AgentPermissionException("Tool '${it.name}' is disabled by the current feature policy")
        }
        val mutationCalls = canonicalCalls.filter { requiresApproval(it.name) }
        require(mutationCalls.isNotEmpty()) { "No mutation tool call requires approval" }
        val now = clock.millis()
        val previews = mutationCalls.map { call ->
            AgentToolCallPreview(
                callId = call.id,
                toolName = call.name,
                argumentsPreview = call.arguments.sanitizeForAgentAudit(ARGUMENT_PREVIEW_CHARS),
                risk = riskFor(call.name),
                callHash = call.callHash(),
            )
        }
        val proposal = AgentActionProposal(
            id = "proposal_${UUID.randomUUID().toString().replace("-", "")}",
            conversationId = conversationId,
            toolCalls = previews,
            proposalHash = hash(
                buildString {
                    append("conversation=")
                    append(conversationId.orEmpty())
                    previews.forEach {
                        append('|')
                        append(it.callId)
                        append(':')
                        append(it.toolName)
                        append(':')
                        append(it.callHash)
                    }
                }
            ),
            argsHash = hash(previews.joinToString("|") { "${it.callId}:${it.callHash}" }),
            createdAt = now,
            expiresAt = now + tokenTtlMillis,
        )
        proposals[proposal.id] = StoredProposal(
            proposal = proposal,
            remainingCallIds = previews.mapTo(mutableSetOf()) { it.callId },
        )
        return proposal
    }

    @Synchronized
    fun approve(proposal: AgentActionProposal): AgentActionApproval {
        return approve(proposal, AgentApprovalScope.ONE_TIME)
    }

    @Synchronized
    fun approve(
        proposal: AgentActionProposal,
        scope: AgentApprovalScope,
    ): AgentActionApproval {
        val stored = proposals[proposal.id] ?: throw AgentPermissionException("Approval proposal was not found")
        ensureFresh(stored)
        if (stored.proposal.proposalHash != proposal.proposalHash || stored.proposal.argsHash != proposal.argsHash) {
            throw AgentPermissionException("Approval proposal does not match the pending action")
        }
        val token = "approval_${UUID.randomUUID().toString().replace("-", "")}"
        stored.token = token
        return AgentActionApproval(
            proposalId = stored.proposal.id,
            token = token,
            conversationId = stored.proposal.conversationId,
            proposalHash = stored.proposal.proposalHash,
            argsHash = stored.proposal.argsHash,
            callHashes = stored.proposal.toolCalls.associate { it.callId to it.callHash },
            expiresAt = stored.proposal.expiresAt,
            scope = scope,
        )
    }

    @Synchronized
    fun reject(proposalId: String) {
        proposals.remove(proposalId)
    }

    @Synchronized
    fun revokeGrants(
        conversationId: String? = null,
        toolName: String? = null,
    ) {
        reusableGrants.entries.removeAll { (_, grant) ->
            val conversationMatches = conversationId == null || grant.conversationId == conversationId
            val toolMatches = toolName == null || grant.toolName == toolName
            conversationMatches && toolMatches
        }
    }

    @Synchronized
    fun requireCanExecute(
        call: AiToolCall,
        approval: AgentActionApproval?,
        conversationId: String? = approval?.conversationId,
    ) {
        val canonicalCall = call.withCanonicalToolName()
        if (!isToolEnabled(canonicalCall.name)) {
            throw AgentPermissionException("Tool '${canonicalCall.name}' is disabled by the current feature policy")
        }
        if (!requiresApproval(canonicalCall.name)) return
        val grant = approval
            ?: if (hasReusableGrant(canonicalCall.name, conversationId)) return
            else throw AgentPermissionException("Tool '${canonicalCall.name}' requires user approval")
        val stored = proposals[grant.proposalId]
            ?: throw AgentPermissionException("Approval token is not pending or was already used")
        ensureFresh(stored)
        val token = stored.token
        if (token.isNullOrBlank() || token != grant.token) {
            throw AgentPermissionException("Approval token is invalid")
        }
        val proposal = stored.proposal
        if (
            proposal.conversationId != grant.conversationId ||
            proposal.proposalHash != grant.proposalHash ||
            proposal.argsHash != grant.argsHash
        ) {
            throw AgentPermissionException("Approved action metadata does not match")
        }
        val approvedHash = grant.callHashes[canonicalCall.id]
            ?: throw AgentPermissionException("Tool call was not included in the approved proposal")
        val expectedHash = proposal.toolCalls.firstOrNull {
            it.callId == canonicalCall.id && it.toolName == canonicalCall.name
        }?.callHash
            ?: throw AgentPermissionException("Tool call was not included in the approved proposal")
        val actualHash = canonicalCall.callHash()
        if (approvedHash != expectedHash || actualHash != expectedHash) {
            throw AgentPermissionException("Tool arguments changed after approval")
        }
        if (grant.scope != AgentApprovalScope.ONE_TIME) {
            registerReusableGrant(proposal, grant.scope)
            if (!stored.remainingCallIds.remove(canonicalCall.id)) {
                throw AgentPermissionException("Approval token was already consumed for this tool call")
            }
            if (stored.remainingCallIds.isEmpty()) {
                proposals.remove(grant.proposalId)
            }
            return
        }
        if (grant.scope == AgentApprovalScope.ONE_TIME) {
            if (!stored.remainingCallIds.remove(canonicalCall.id)) {
                throw AgentPermissionException("Approval token was already consumed for this tool call")
            }
            if (stored.remainingCallIds.isEmpty()) {
                proposals.remove(grant.proposalId)
            }
        }
    }

    private fun ensureFresh(stored: StoredProposal) {
        if (clock.millis() > stored.proposal.expiresAt) {
            proposals.remove(stored.proposal.id)
            throw AgentPermissionException("Approval proposal expired")
        }
    }

    private fun registerReusableGrant(
        proposal: AgentActionProposal,
        scope: AgentApprovalScope,
    ) {
        proposal.toolCalls.forEach { preview ->
            val key = GrantKey(
                conversationId = if (scope == AgentApprovalScope.SESSION) proposal.conversationId else null,
                toolName = preview.toolName,
            )
            reusableGrants[key] = StoredGrant(
                conversationId = key.conversationId,
                toolName = preview.toolName,
                risk = preview.risk,
                scope = scope,
                grantedAt = clock.millis(),
            )
        }
    }

    private data class StoredProposal(
        val proposal: AgentActionProposal,
        val remainingCallIds: MutableSet<String>,
        var token: String? = null,
    )

    private data class GrantKey(
        val conversationId: String?,
        val toolName: String,
    )

    private data class StoredGrant(
        val conversationId: String?,
        val toolName: String,
        val risk: AgentActionRisk,
        val scope: AgentApprovalScope,
        val grantedAt: Long,
    )

    companion object {
        private val DEFAULT_TOKEN_TTL: Duration = Duration.ofMinutes(10)
        private const val ARGUMENT_PREVIEW_CHARS = 2_000

        private val defaultToolRisks = mapOf(
            "save_ai_artifact" to AgentActionRisk.WRITE,
            "save_memory" to AgentActionRisk.WRITE,
            "delete_memory" to AgentActionRisk.DELETE,
            "update_book" to AgentActionRisk.WRITE,
            "download_book_chapters" to AgentActionRisk.WRITE,
            "add_book_to_bookshelf" to AgentActionRisk.WRITE,
            "repair_book_source" to AgentActionRisk.WRITE,
            "create_vbook_plugin_draft" to AgentActionRisk.WRITE,
            "install_vbook_plugin" to AgentActionRisk.PLUGIN_INSTALL,
            "create_legado_book_source_draft" to AgentActionRisk.WRITE,
            "install_legado_book_source" to AgentActionRisk.PLUGIN_INSTALL,
            "create_agent_skill_draft" to AgentActionRisk.WRITE,
            "set_agent_skill_enabled" to AgentActionRisk.PLUGIN_INSTALL,
            "activate_agent_skill_version" to AgentActionRisk.PLUGIN_INSTALL,
            "rollback_agent_skill" to AgentActionRisk.PLUGIN_INSTALL,
            "save_book_dictionary_term" to AgentActionRisk.WRITE,
            "delete_book_dictionary_term" to AgentActionRisk.DELETE,
            "clear_book_dictionary" to AgentActionRisk.DELETE,
            "save_dictionary_entry" to AgentActionRisk.WRITE,
            "delete_dictionary_entry" to AgentActionRisk.DELETE,
            "save_authoring_project" to AgentActionRisk.WRITE,
            "delete_authoring_project" to AgentActionRisk.DELETE,
            "set_bookshelf_automation" to AgentActionRisk.WRITE,
        )

        private val defaultToolCapabilities = mapOf(
            "search_internet" to setOf(AgentToolCapability.NETWORK),
            "fetch_internet_page" to setOf(AgentToolCapability.NETWORK),
            "search_book_sources" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.SOURCE),
            "search_online_books" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.SOURCE),
            "diagnose_book_source" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.SOURCE),
            "repair_book_source" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.SOURCE),
            "add_book_to_bookshelf" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.SOURCE),
            "create_vbook_plugin_draft" to setOf(AgentToolCapability.FILE, AgentToolCapability.SOURCE),
            "install_vbook_plugin" to setOf(AgentToolCapability.FILE, AgentToolCapability.SOURCE),
            "create_legado_book_source_draft" to setOf(AgentToolCapability.FILE, AgentToolCapability.SOURCE),
            "install_legado_book_source" to setOf(AgentToolCapability.FILE, AgentToolCapability.SOURCE),
            "download_book_chapters" to setOf(AgentToolCapability.NETWORK, AgentToolCapability.FILE),
            "save_authoring_project" to setOf(AgentToolCapability.FILE, AgentToolCapability.AUTHORING),
            "delete_authoring_project" to setOf(AgentToolCapability.FILE, AgentToolCapability.AUTHORING),
            "list_authoring_projects" to setOf(AgentToolCapability.FILE, AgentToolCapability.AUTHORING),
            "get_authoring_project" to setOf(AgentToolCapability.FILE, AgentToolCapability.AUTHORING),
            "save_ai_artifact" to setOf(AgentToolCapability.FILE),
        )

        private val skillToolNames = setOf(
            "list_agent_skills",
            "create_agent_skill_draft",
            "set_agent_skill_enabled",
            "activate_agent_skill_version",
            "rollback_agent_skill",
        )

        private val pluginToolNames = setOf(
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
        )

        private val sourceToolNames = setOf(
            "search_book_sources",
            "search_online_books",
            "diagnose_book_source",
            "repair_book_source",
            "add_book_to_bookshelf",
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
            "update_book",
            "download_book_chapters",
        )

        private val authoringToolNames = setOf(
            "list_authoring_projects",
            "get_authoring_project",
            "save_authoring_project",
            "delete_authoring_project",
        )

        private val fileToolNames = setOf(
            "save_ai_artifact",
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
            "download_book_chapters",
            "list_authoring_projects",
            "get_authoring_project",
            "save_authoring_project",
            "delete_authoring_project",
        )

        private val networkToolNames = setOf(
            "search_internet",
            "fetch_internet_page",
            "search_book_sources",
            "search_online_books",
            "diagnose_book_source",
            "repair_book_source",
            "add_book_to_bookshelf",
            "update_book",
            "download_book_chapters",
        )
    }
}

private fun AiToolCall.callHash(): String {
    return hash("$id|$name|$arguments|${metadata.orEmpty()}")
}

private fun hash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
