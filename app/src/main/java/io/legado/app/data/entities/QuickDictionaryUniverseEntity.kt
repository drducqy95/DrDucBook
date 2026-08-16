package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_dictionary_universes")
data class QuickDictionaryUniverseEntity(
    @PrimaryKey
    val universeKey: String,
    val name: String,
    /** One literal marker or `regex:` expression per line. */
    val contextMarkers: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
