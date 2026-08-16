package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Long-term memory entries for AI conversations.
 * Stored as key-value pairs scoped to a conversation, book, project, or global memory.
 * Injected into the system prompt so the model can reference past context.
 */
@Entity(
    tableName = "ai_memory",
    primaryKeys = ["conversationId", "key"],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["scope", "scopeId"]),
        Index(value = ["type"]),
        Index(value = ["pinned"]),
        Index(value = ["updatedAt"]),
    ]
)
data class AiMemory(
    val conversationId: String,  // "" for global memories
    val key: String,
    val value: String,
    @ColumnInfo(defaultValue = "'conversation'")
    val scope: String = scopeFromConversation(conversationId),
    @ColumnInfo(defaultValue = "''")
    val scopeId: String = conversationId,
    @ColumnInfo(defaultValue = "'fact'")
    val type: String = TYPE_FACT,
    val sourceConversationId: String? = conversationId.takeIf { it.isNotBlank() },
    val sourceMessageId: String? = null,
    @ColumnInfo(defaultValue = "1.0")
    val confidence: Double = 1.0,
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_CONVERSATION = "conversation"
        const val SCOPE_BOOK = "book"
        const val SCOPE_WRITING_PROJECT = "writing_project"
        const val SCOPE_EBOOK_PROJECT = "ebook_project"

        const val TYPE_FACT = "fact"
        const val TYPE_PREFERENCE = "preference"
        const val TYPE_DECISION = "decision"
        const val TYPE_GLOSSARY = "glossary"
        const val TYPE_RELATIONSHIP = "relationship"
        const val TYPE_WORKFLOW_RESULT = "workflow_result"
        const val TYPE_SUMMARY = "summary"

        fun scopeFromConversation(conversationId: String): String {
            return if (conversationId.isBlank()) SCOPE_GLOBAL else SCOPE_CONVERSATION
        }
    }
}
