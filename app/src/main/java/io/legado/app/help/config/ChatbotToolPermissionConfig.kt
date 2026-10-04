package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.domain.agent.AgentToolNameNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatbotToolCategory(
    val key: String,
    val title: String,
    val description: String,
    val tools: Set<String>,
) {
    BOOKSHELF_AND_READING(
        key = "pref_chatbot_perm_bookshelf",
        title = "Kệ sách & Tải chương",
        description = "Thêm sách vào kệ, cập nhật trạng thái đọc, tải trước chương truyện.",
        tools = setOf(
            "add_book_to_bookshelf",
            "update_book",
            "set_bookshelf_automation",
            "download_book_chapters",
        ),
    ),
    BOOK_SOURCE_AND_PLUGINS(
        key = "pref_chatbot_perm_sources",
        title = "Nguồn sách & Plugin VBook",
        description = "Tự động cài đặt nguồn sách, tạo/sửa lỗi nguồn, cài đặt plugin VBook.",
        tools = setOf(
            "repair_book_source",
            "save_book_source",
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
        ),
    ),
    DICTIONARY_AND_MEMORY(
        key = "pref_chatbot_perm_dict",
        title = "Từ điển, Wiki & Ký ức truyện",
        description = "Lưu/xóa thuật ngữ, ký ức truyện (story memory), wiki nhân vật/thế giới/quan hệ, niên biểu.",
        tools = setOf(
            "save_memory",
            "delete_memory",
            "save_book_dictionary_term",
            "delete_book_dictionary_term",
            "clear_book_dictionary",
            "save_dictionary_entry",
            "delete_dictionary_entry",
            "create_story_memory",
            "retrofit_story_translations",
            "synthesize_story_chronicle",
            "save_story_chronicle",
            "upsert_story_wiki_entity",
            "delete_story_wiki_entity",
            "upsert_story_wiki_world",
            "delete_story_wiki_world",
            "upsert_story_wiki_relationship",
            "delete_story_wiki_relationship",
        ),
    ),
    AUTHORING_AND_ARTIFACTS(
        key = "pref_chatbot_perm_authoring",
        title = "Dự án viết & Artifacts",
        description = "Lưu/xóa dự án sáng tác, ghi file markdown và tài liệu artifact.",
        tools = setOf(
            "save_authoring_project",
            "delete_authoring_project",
            "save_ai_artifact",
        ),
    ),
    AGENT_SKILLS(
        key = "pref_chatbot_perm_skills",
        title = "Kỹ năng Agent (Skills)",
        description = "Cài đặt, bật/tắt và cập nhật phiên bản kỹ năng tùy chỉnh.",
        tools = setOf(
            "create_agent_skill_draft",
            "set_agent_skill_enabled",
            "activate_agent_skill_version",
            "rollback_agent_skill",
        ),
    );

    companion object {
        fun categoryForTool(toolName: String): ChatbotToolCategory? {
            val canonical = AgentToolNameNormalizer.canonicalize(toolName)
            return entries.firstOrNull { canonical in it.tools }
        }
    }
}

class ChatbotToolPermissionConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _permissionsFlow = MutableStateFlow(loadAllPermissions())
    val permissionsFlow: StateFlow<Map<ChatbotToolCategory, Boolean>> = _permissionsFlow.asStateFlow()

    fun isCategoryAutoApproved(category: ChatbotToolCategory): Boolean {
        return prefs.getBoolean(category.key, DEFAULT_APPROVED_CATEGORIES.contains(category))
    }

    fun setCategoryAutoApproved(category: ChatbotToolCategory, approved: Boolean) {
        prefs.edit { putBoolean(category.key, approved) }
        _permissionsFlow.value = loadAllPermissions()
    }

    fun isToolAutoApproved(toolName: String): Boolean {
        val category = ChatbotToolCategory.categoryForTool(toolName) ?: return false
        return isCategoryAutoApproved(category)
    }

    private fun loadAllPermissions(): Map<ChatbotToolCategory, Boolean> {
        return ChatbotToolCategory.entries.associateWith { isCategoryAutoApproved(it) }
    }

    companion object {
        private const val PREF_NAME = "chatbot_tool_permissions"
        val DEFAULT_APPROVED_CATEGORIES = setOf(
            ChatbotToolCategory.BOOKSHELF_AND_READING,
            ChatbotToolCategory.DICTIONARY_AND_MEMORY,
            ChatbotToolCategory.AUTHORING_AND_ARTIFACTS,
        )
    }
}
