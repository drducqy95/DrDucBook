package io.legado.app.data.repository

import io.legado.app.domain.agent.AgentPermissionBroker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolRepositoryToolCatalogTest {

    @Test
    fun catalogExposesInternetBookAddAndPluginTools() {
        val tools = AiToolRepository.toolDefinitions.associateBy { it.name }

        assertTrue(AiToolRepository.TOOL_SEARCH_INTERNET in tools)
        assertTrue(AiToolRepository.TOOL_FETCH_INTERNET_PAGE in tools)
        assertTrue(AiToolRepository.TOOL_SEARCH_BOOK_SOURCES in tools)
        assertTrue(AiToolRepository.TOOL_SEARCH_ONLINE_BOOKS in tools)
        assertTrue(AiToolRepository.TOOL_GET_AI_RUNTIME_STATUS in tools)
        assertTrue(AiToolRepository.TOOL_GET_AI_QUOTA_STATUS in tools)
        assertTrue(AiToolRepository.TOOL_DIAGNOSE_BOOK_SOURCE in tools)
        assertTrue(AiToolRepository.TOOL_REPAIR_BOOK_SOURCE in tools)
        assertTrue(AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF in tools)
        assertTrue(AiToolRepository.TOOL_CREATE_VBOOK_PLUGIN_DRAFT in tools)
        assertTrue(AiToolRepository.TOOL_INSTALL_VBOOK_PLUGIN in tools)
        assertTrue(AiToolRepository.TOOL_CREATE_LEGADO_BOOK_SOURCE_DRAFT in tools)
        assertTrue(AiToolRepository.TOOL_INSTALL_LEGADO_BOOK_SOURCE in tools)
        assertTrue(AiToolRepository.TOOL_LIST_AUTHORING_PROJECTS in tools)
        assertTrue(AiToolRepository.TOOL_GET_AUTHORING_PROJECT in tools)
        assertTrue(AiToolRepository.TOOL_SAVE_AUTHORING_PROJECT in tools)
        assertTrue(AiToolRepository.TOOL_DELETE_AUTHORING_PROJECT in tools)
        assertTrue(AiToolRepository.TOOL_SAVE_BOOK_SOURCE in tools)
        assertTrue(AiToolRepository.TOOL_TEST_BOOK_SOURCE_RULE in tools)
        assertTrue(AiToolRepository.TOOL_CREATE_STORY_MEMORY in tools)
        assertTrue(AiToolRepository.TOOL_GET_STORY_MEMORY in tools)
        assertTrue(AiToolRepository.TOOL_RETROFIT_STORY_TRANSLATIONS in tools)
        assertTrue(AiToolRepository.TOOL_GET_STORY_CHRONICLE in tools)
        assertTrue(AiToolRepository.TOOL_SYNTHESIZE_STORY_CHRONICLE in tools)
        assertTrue(AiToolRepository.TOOL_SAVE_STORY_CHRONICLE in tools)
        assertTrue(AiToolRepository.TOOL_UPSERT_STORY_WIKI_ENTITY in tools)
        assertTrue(AiToolRepository.TOOL_DELETE_STORY_WIKI_ENTITY in tools)
        assertTrue(AiToolRepository.TOOL_UPSERT_STORY_WIKI_WORLD in tools)
        assertTrue(AiToolRepository.TOOL_DELETE_STORY_WIKI_WORLD in tools)
        assertTrue(AiToolRepository.TOOL_UPSERT_STORY_WIKI_RELATIONSHIP in tools)
        assertTrue(AiToolRepository.TOOL_DELETE_STORY_WIKI_RELATIONSHIP in tools)
        assertTrue(AiToolRepository.TOOL_GET_STORY_WIKI in tools)
        assertTrue(AiToolRepository.TOOL_GENERATE_CHARACTER_IMAGE in tools)
        assertTrue(AiToolRepository.TOOL_GENERATE_BOOK_COVER in tools)
        assertTrue(AiToolRepository.TOOL_GENERATE_WORLD_IMAGE in tools)
    }

    @Test
    fun builtInToolContractSnapshotHasStableIdsAndSchemas() {
        val tools = AiToolRepository.toolDefinitions
        val names = tools.map { it.name }

        assertEquals(EXPECTED_TOOL_IDS, names.sorted())
        assertEquals(names.distinct(), names)
        tools.forEach { tool ->
            assertTrue(tool.name, tool.description.isNotBlank())
            assertEquals(tool.name, "object", tool.inputSchema["type"])
            assertTrue(tool.name, "properties" in tool.inputSchema)
            assertTrue(tool.name, "additionalProperties" in tool.inputSchema)
            val properties = tool.inputSchema["properties"] as Map<*, *>
            properties.forEach { (name, schema) ->
                assertTrue(tool.name, name is String && name.isNotBlank())
                val propertySchema = schema as Map<*, *>
                assertTrue("$tool.$name", "type" in propertySchema)
                if (propertySchema["type"] != "object") {
                    assertTrue("$tool.$name", "description" in propertySchema)
                }
            }
        }
    }

    @Test
    fun builtInToolArgumentValidatorRejectsUnknownAndMalformedArgs() {
        val definition = AiToolRepository.toolDefinitions
            .first { it.name == AiToolRepository.TOOL_SEARCH_BOOKS }

        assertTrue(
            AiToolRepository.validateToolArguments(
                definition,
                """{"query":"Book","unexpected":true}""",
            ).isFailure
        )
        assertTrue(
            AiToolRepository.validateToolArguments(
                definition,
                """["not-an-object"]""",
            ).isFailure
        )
        assertTrue(
            AiToolRepository.validateToolArguments(
                definition,
                """{"query":"Book","limit":5}""",
            ).isSuccess
        )
    }

    @Test
    fun installPluginSchemaKeepsChatbotPluginDisabledByDefault() {
        val installTool = AiToolRepository.toolDefinitions
            .first { it.name == AiToolRepository.TOOL_INSTALL_VBOOK_PLUGIN }
        val properties = installTool.inputSchema["properties"] as Map<*, *>

        assertTrue("enableAfterInstall" in properties)
        assertTrue(installTool.description.contains("disabled by default"))
        assertFalse(installTool.description.contains("enabled by default"))
    }

    @Test
    fun mutationLikeToolNamesRequireBrokerApproval() {
        val broker = AgentPermissionBroker()
        val mutationPrefixes = listOf(
            "add_",
            "clear_",
            "create_",
            "delete_",
            "repair_",
            "download_",
            "install_",
            "save_",
            "set_",
            "update_",
            "upsert_",
            "retrofit_",
            "synthesize_",
        )
        val mutationTools = AiToolRepository.toolDefinitions
            .map { it.name }
            .filter { toolName -> mutationPrefixes.any(toolName::startsWith) }

        assertTrue(mutationTools.isNotEmpty())
        mutationTools.forEach { toolName ->
            assertTrue(toolName, broker.requiresApproval(toolName))
        }
    }

    @Test
    fun allBuiltInToolIdsHavePermissionClassification() {
        val broker = AgentPermissionBroker()

        AiToolRepository.toolDefinitions.forEach { tool ->
            broker.riskFor(tool.name)
            if (tool.description.contains("Requires user confirmation", ignoreCase = true) ||
                tool.description.contains("Requires strong user confirmation", ignoreCase = true) ||
                tool.description.contains("Requires confirmation", ignoreCase = true)
            ) {
                assertTrue(tool.name, broker.requiresApproval(tool.name))
            }
        }
    }

    @Test
    fun defaultSafetyGatesExplainEnabledToolsInAppDashboard() {
        val broker = AgentPermissionBroker(
            mutationEnabled = { false },
            skillEnabled = { false },
            pluginEnabled = { false },
        )
        val enabledTools = AiToolRepository.toolDefinitions
            .filter { broker.isToolEnabled(it.name) }

        assertEquals(65, AiToolRepository.toolDefinitions.size)
        assertEquals(27, enabledTools.size)
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_SAVE_MEMORY })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_LIST_AGENT_SKILLS })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_REPAIR_BOOK_SOURCE })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_SAVE_BOOK_SOURCE })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_CREATE_STORY_MEMORY })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_GENERATE_CHARACTER_IMAGE })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_GENERATE_BOOK_COVER })
        assertFalse(enabledTools.any { it.name == AiToolRepository.TOOL_GENERATE_WORLD_IMAGE })
        assertTrue(enabledTools.any { it.name == AiToolRepository.TOOL_GET_STORY_MEMORY })
        assertTrue(enabledTools.any { it.name == AiToolRepository.TOOL_GET_STORY_CHRONICLE })
        assertTrue(enabledTools.any { it.name == AiToolRepository.TOOL_GET_STORY_WIKI })
        assertTrue(enabledTools.any { it.name == AiToolRepository.TOOL_TEST_BOOK_SOURCE_RULE })
    }

    private companion object {
        val EXPECTED_TOOL_IDS = listOf(
            "activate_agent_skill_version",
            "add_book_to_bookshelf",
            "clear_book_dictionary",
            "create_agent_skill_draft",
            "create_legado_book_source_draft",
            "create_story_memory",
            "create_vbook_plugin_draft",
            "delete_authoring_project",
            "delete_book_dictionary_term",
            "delete_dictionary_entry",
            "delete_memory",
            "delete_story_wiki_entity",
            "delete_story_wiki_relationship",
            "delete_story_wiki_world",
            "diagnose_book_source",
            "download_book_chapters",
            "fetch_internet_page",
            "generate_book_cover",
            "generate_character_image",
            "generate_world_image",
            "get_ai_artifacts",
            "get_ai_quota_status",
            "get_ai_runtime_status",
            "get_authoring_project",
            "get_book_detail",
            "get_bookshelf_automation",
            "get_chapter_content",
            "get_chapter_window",
            "get_download_status",
            "get_reading_stats",
            "get_story_chronicle",
            "get_story_memory",
            "get_story_wiki",
            "install_legado_book_source",
            "install_vbook_plugin",
            "list_agent_skills",
            "list_authoring_projects",
            "list_book_chapters",
            "list_book_dictionary_terms",
            "list_dictionary_entries",
            "recall_memory",
            "repair_book_source",
            "retrofit_story_translations",
            "rollback_agent_skill",
            "save_ai_artifact",
            "save_authoring_project",
            "save_book_dictionary_term",
            "save_book_source",
            "save_dictionary_entry",
            "save_memory",
            "save_story_chronicle",
            "search_book_sources",
            "search_bookmarks",
            "search_books",
            "search_chapter_content",
            "search_internet",
            "search_online_books",
            "set_agent_skill_enabled",
            "set_bookshelf_automation",
            "synthesize_story_chronicle",
            "test_book_source_rule",
            "update_book",
            "upsert_story_wiki_entity",
            "upsert_story_wiki_relationship",
            "upsert_story_wiki_world",
        )
    }
}
