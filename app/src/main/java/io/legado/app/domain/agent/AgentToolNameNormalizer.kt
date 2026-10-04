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
        "save-book-source" to "save_book_source",
        "saveBookSource" to "save_book_source",
        "test-book-source-rule" to "test_book_source_rule",
        "testBookSourceRule" to "test_book_source_rule",
        "test_book_source" to "test_book_source_rule",
        "create-story-memory" to "create_story_memory",
        "createStoryMemory" to "create_story_memory",
        "analyze_story_memory" to "create_story_memory",
        "get-story-memory" to "get_story_memory",
        "getStoryMemory" to "get_story_memory",
        "retrofit-story-translations" to "retrofit_story_translations",
        "retrofitStoryTranslations" to "retrofit_story_translations",
        "get-story-chronicle" to "get_story_chronicle",
        "getStoryChronicle" to "get_story_chronicle",
        "synthesize-story-chronicle" to "synthesize_story_chronicle",
        "synthesizeStoryChronicle" to "synthesize_story_chronicle",
        "save-story-chronicle" to "save_story_chronicle",
        "saveStoryChronicle" to "save_story_chronicle",
        "upsert-story-wiki-entity" to "upsert_story_wiki_entity",
        "upsertStoryWikiEntity" to "upsert_story_wiki_entity",
        "delete-story-wiki-entity" to "delete_story_wiki_entity",
        "deleteStoryWikiEntity" to "delete_story_wiki_entity",
        "upsert-story-wiki-world" to "upsert_story_wiki_world",
        "upsertStoryWikiWorld" to "upsert_story_wiki_world",
        "delete-story-wiki-world" to "delete_story_wiki_world",
        "deleteStoryWikiWorld" to "delete_story_wiki_world",
        "upsert-story-wiki-relationship" to "upsert_story_wiki_relationship",
        "upsertStoryWikiRelationship" to "upsert_story_wiki_relationship",
        "delete-story-wiki-relationship" to "delete_story_wiki_relationship",
        "deleteStoryWikiRelationship" to "delete_story_wiki_relationship",
        "get-story-wiki" to "get_story_wiki",
        "getStoryWiki" to "get_story_wiki",
        "generate-character-image" to "generate_character_image",
        "generateCharacterImage" to "generate_character_image",
        "generate-book-cover" to "generate_book_cover",
        "generateBookCover" to "generate_book_cover",
        "generate-world-image" to "generate_world_image",
        "generateWorldImage" to "generate_world_image",
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
