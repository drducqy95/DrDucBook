package io.legado.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDownloadMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate103To105CreatesPersistentDownloadQueueAndResumeIdentity() {
        val databaseName = "media-download-migration"
        helper.createDatabase(databaseName, 103).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            databaseName,
        ).addMigrations(*DatabaseMigrations.migrations).build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL(
                """
                INSERT INTO media_download_tasks(
                    id, bookUrl, bookTitle, coverUrl, status, createdAt, updatedAt, errorMessage
                ) VALUES ('task-1', 'book-1', 'Book', NULL, 'PENDING', 1, 1, NULL)
                """.trimIndent()
            )
            sql.execSQL(
                """
                INSERT INTO media_download_items(
                    id, taskId, bookUrl, chapterIndex, episodeTitle, variantId, sourceUri,
                    mimeType, headersJson, protocol, expiresAt, status, bytesDownloaded,
                    totalBytes, segmentIndex, tempPath, localPath, checksum, errorMessage,
                    retryCount, sortOrder, updatedAt
                ) VALUES (
                    'item-1', 'task-1', 'book-1', 0, 'Episode', 'default',
                    'https://example.test/audio.mp3', 'audio/mpeg', '{}', 'DIRECT', NULL,
                    'PENDING', 0, 0, 0, '', '', '', NULL, 0, 0, 1
                )
                """.trimIndent()
            )
            sql.query("SELECT COUNT(*) FROM media_download_items WHERE taskId = 'task-1'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            sql.query("SELECT responseContentLength FROM media_download_items WHERE id = 'item-1'").use {
                it.moveToFirst()
                assertEquals(0L, it.getLong(0))
            }
        } finally {
            database.close()
        }
    }
}
