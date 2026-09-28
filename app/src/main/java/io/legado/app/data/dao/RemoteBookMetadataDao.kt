package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.RemoteBookMetadata

@Dao
interface RemoteBookMetadataDao {
    @Query("SELECT * FROM remote_book_metadata WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): RemoteBookMetadata?

    @Query("SELECT * FROM remote_book_metadata WHERE path LIKE :folderPrefix || '%'")
    suspend fun getAllInFolder(folderPrefix: String): List<RemoteBookMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RemoteBookMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RemoteBookMetadata>)

    @Query("DELETE FROM remote_book_metadata WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
