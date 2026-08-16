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
class AiMemoryFtsMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate100To101BackfillsMemorySearchIndex() {
        val databaseName = "ai-memory-fts-migration"
        helper.createDatabase(databaseName, 100).apply {
            execSQL(
                """
                INSERT INTO ai_memory(conversationId, `key`, value, updatedAt)
                VALUES ('chat-a', 'hero', 'Azure Dragon Sword', 1)
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            databaseName,
        ).addMigrations(*DatabaseMigrations.migrations)
            .build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT `key` FROM ai_memory_fts WHERE ai_memory_fts MATCH 'dragon'"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("hero", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }
}
