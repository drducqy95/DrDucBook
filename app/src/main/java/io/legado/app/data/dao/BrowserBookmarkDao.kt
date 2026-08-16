package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BrowserBookmarkEntity
import io.legado.app.data.entities.SourceBookmarkPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserBookmarkDao {

    @get:Query("SELECT * FROM browser_bookmarks ORDER BY folder COLLATE LOCALIZED, sortOrder, title COLLATE LOCALIZED")
    val allBookmarks: List<BrowserBookmarkEntity>

    @get:Query("SELECT * FROM source_bookmark_preferences ORDER BY pinned DESC, sortOrder, sourceId")
    val allSourcePreferences: List<SourceBookmarkPreferenceEntity>

    @Query("SELECT * FROM browser_bookmarks ORDER BY folder COLLATE LOCALIZED, sortOrder, title COLLATE LOCALIZED")
    fun flowBookmarks(): Flow<List<BrowserBookmarkEntity>>

    @Query("SELECT * FROM source_bookmark_preferences ORDER BY pinned DESC, sortOrder, sourceId")
    fun flowSourcePreferences(): Flow<List<SourceBookmarkPreferenceEntity>>

    @Query("SELECT * FROM browser_bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): BrowserBookmarkEntity?

    @Query("SELECT * FROM browser_bookmarks WHERE id = :id LIMIT 1")
    suspend fun getBookmarkById(id: String): BrowserBookmarkEntity?

    @Query("SELECT * FROM source_bookmark_preferences WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun getSourcePreference(
        sourceType: String,
        sourceId: String,
    ): SourceBookmarkPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BrowserBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookmarks(vararg bookmarks: BrowserBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourcePreference(preference: SourceBookmarkPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSourcePreferences(vararg preferences: SourceBookmarkPreferenceEntity)

    @Query("DELETE FROM browser_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    @Delete
    suspend fun deleteBookmark(bookmark: BrowserBookmarkEntity)
}
