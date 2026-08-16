package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import io.legado.app.domain.model.SourceBookmarkPreference
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType

@Entity(
    tableName = "source_bookmark_preferences",
    primaryKeys = ["sourceType", "sourceId"],
    indices = [
        Index(value = ["hidden"]),
        Index(value = ["pinned", "sortOrder"]),
        Index(value = ["updatedAt"]),
    ],
)
data class SourceBookmarkPreferenceEntity(
    val sourceType: String,
    val sourceId: String,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
) {
    fun toDomain(): SourceBookmarkPreference? {
        val type = runCatching { SourceKeyType.valueOf(sourceType) }.getOrNull() ?: return null
        return SourceBookmarkPreference(
            sourceKey = SourceKey(type, sourceId),
            pinned = pinned,
            hidden = hidden,
            sortOrder = sortOrder,
            updatedAt = updatedAt,
        )
    }
}

fun SourceBookmarkPreference.toEntity(): SourceBookmarkPreferenceEntity =
    SourceBookmarkPreferenceEntity(
        sourceType = sourceKey.type.name,
        sourceId = sourceKey.id,
        pinned = pinned,
        hidden = hidden,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
    )
