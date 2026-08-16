package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookSourceHealth
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSourceHealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(health: BookSourceHealth)

    @Query("SELECT * FROM book_source_health WHERE sourceUrl = :sourceUrl")
    suspend fun getBySourceUrl(sourceUrl: String): BookSourceHealth?

    @Query("SELECT * FROM book_source_health ORDER BY lastChecked DESC, sourceUrl ASC")
    fun flowAll(): Flow<List<BookSourceHealth>>

    @Query("SELECT * FROM book_source_health WHERE status = :status ORDER BY lastChecked DESC")
    fun flowByStatus(status: String): Flow<List<BookSourceHealth>>

    @Query("DELETE FROM book_source_health WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySourceUrl(sourceUrl: String)

    @Query("DELETE FROM book_source_health WHERE sourceUrl = :sourceUrl")
    fun deleteBySourceUrlBlocking(sourceUrl: String): Int

    @Query("SELECT COUNT(*) FROM book_source_health WHERE status NOT IN ('HEALTHY', 'DEGRADED', 'UNKNOWN_OFFLINE', 'STALE')")
    suspend fun getErrorCount(): Int

    @Query("SELECT COUNT(*) FROM book_source_health WHERE status = 'AUTH_REQUIRED'")
    suspend fun getAuthRequiredCount(): Int

    @Query("SELECT COUNT(*) FROM book_source_health WHERE status = 'CAPTCHA_REQUIRED'")
    suspend fun getCaptchaRequiredCount(): Int
}
