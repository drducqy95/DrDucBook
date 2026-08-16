package io.legado.app.data.repository

import io.legado.app.data.dao.BrowserBookmarkDao
import io.legado.app.data.entities.BrowserBookmarkEntity
import io.legado.app.data.entities.SourceBookmarkPreferenceEntity
import io.legado.app.data.entities.toEntity
import io.legado.app.domain.model.BrowserBookmark
import io.legado.app.domain.model.SourceBookmarkPreference
import io.legado.app.domain.model.SourceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class BrowserBookmarkRepository(
    private val dao: BrowserBookmarkDao,
) {

    fun observeBookmarks(): Flow<List<BrowserBookmark>> =
        dao.flowBookmarks().map { bookmarks -> bookmarks.map(BrowserBookmarkEntity::toDomain) }

    fun observeSourcePreferences(): Flow<List<SourceBookmarkPreference>> =
        dao.flowSourcePreferences().map { preferences ->
            preferences.mapNotNull(SourceBookmarkPreferenceEntity::toDomain)
        }

    suspend fun saveBookmark(
        id: String?,
        title: String,
        url: String,
        folder: String,
        sortOrder: Int = 0,
    ): BrowserBookmark {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getBookmarkById(it) }
            ?: dao.getBookmarkByUrl(url)
        val bookmark = BrowserBookmark(
            id = existing?.id ?: id?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { url },
            url = url.trim(),
            folder = folder.trim().ifBlank { BrowserBookmark.DEFAULT_FOLDER },
            sortOrder = sortOrder,
            createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
        dao.upsertBookmark(bookmark.toEntity())
        return bookmark
    }

    suspend fun deleteBookmark(id: String) {
        dao.deleteBookmarkById(id)
    }

    suspend fun setSourcePinned(sourceKey: SourceKey, pinned: Boolean) {
        val current = getSourcePreference(sourceKey)
        dao.upsertSourcePreference(
            current.copy(
                pinned = pinned,
                hidden = if (pinned) false else current.hidden,
                updatedAt = System.currentTimeMillis(),
            ).toEntity()
        )
    }

    suspend fun setSourceHidden(sourceKey: SourceKey, hidden: Boolean) {
        val current = getSourcePreference(sourceKey)
        dao.upsertSourcePreference(
            current.copy(
                hidden = hidden,
                pinned = if (hidden) false else current.pinned,
                updatedAt = System.currentTimeMillis(),
            ).toEntity()
        )
    }

    private suspend fun getSourcePreference(sourceKey: SourceKey): SourceBookmarkPreference =
        dao.getSourcePreference(sourceKey.type.name, sourceKey.id)?.toDomain()
            ?: SourceBookmarkPreference(sourceKey = sourceKey)
}
