package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "ai_memory_fts")
data class AiMemoryFts(
    val conversationId: String,
    val key: String,
    val value: String,
    val scope: String,
    val scopeId: String,
    val type: String,
) {
    companion object {
        fun from(memory: AiMemory): AiMemoryFts = AiMemoryFts(
            conversationId = memory.conversationId,
            key = memory.key,
            value = memory.value,
            scope = memory.scope,
            scopeId = memory.scopeId,
            type = memory.type,
        )
    }
}
