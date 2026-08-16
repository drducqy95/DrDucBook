package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_dictionary_entries",
    indices = [
        Index(value = ["scope", "scopeKey", "enabled"]),
        Index(value = ["raw", "type", "scope", "scopeKey"], unique = true),
    ],
)
data class QuickDictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val raw: String,
    val hanViet: String = "",
    val target: String = "",
    val type: String,
    val scope: String,
    val scopeKey: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
