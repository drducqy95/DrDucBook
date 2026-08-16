package io.legado.app.domain.agent

import io.legado.app.domain.model.AiToolCall

object AgentToolNameNormalizer {

    private val aliases = mapOf(
        "create_vbook_plugin.draft" to "create_vbook_plugin_draft",
        "create.vbook.plugin.draft" to "create_vbook_plugin_draft",
        "create-vbook-plugin-draft" to "create_vbook_plugin_draft",
        "createVbookPluginDraft" to "create_vbook_plugin_draft",
        "install_vbook_plugin.install" to "install_vbook_plugin",
        "install.vbook.plugin" to "install_vbook_plugin",
        "install-vbook-plugin" to "install_vbook_plugin",
        "create_legado_source_draft" to "create_legado_book_source_draft",
        "create-legado-book-source-draft" to "create_legado_book_source_draft",
        "createLegadoBookSourceDraft" to "create_legado_book_source_draft",
        "install_legado_source" to "install_legado_book_source",
        "install-legado-book-source" to "install_legado_book_source",
        "installLegadoBookSource" to "install_legado_book_source",
        "create_agent_skill.draft" to "create_agent_skill_draft",
        "create.agent.skill.draft" to "create_agent_skill_draft",
        "set_agent_skill.enabled" to "set_agent_skill_enabled",
        "activate_agent_skill.version" to "activate_agent_skill_version",
        "rollback_agent_skill.rollback" to "rollback_agent_skill",
    )

    fun canonicalize(toolName: String): String {
        val trimmed = toolName.trim()
        return aliases[trimmed] ?: aliases[trimmed.lowercase()] ?: trimmed
    }
}

fun AiToolCall.withCanonicalToolName(): AiToolCall {
    val canonicalName = AgentToolNameNormalizer.canonicalize(name)
    return if (canonicalName == name) this else copy(name = canonicalName)
}
