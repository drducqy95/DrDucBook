package io.legado.app

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomAgentToolMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate109To110CreatesCustomToolLifecycleTables() {
        helper.createDatabase(DATABASE_NAME, 109).close()

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            110,
            true,
            *DatabaseMigrations.migrations,
        )
        try {
            assertTrue(database.hasTable("ai_custom_tools"))
            assertTrue(database.hasTable("ai_custom_tool_versions"))
            assertTrue(database.hasIndex("index_ai_custom_tools_toolName"))
            assertTrue(database.hasIndex("index_ai_custom_tools_enabled"))
            assertTrue(database.hasIndex("index_ai_custom_tools_updatedAt"))
            assertTrue(database.hasIndex("index_ai_custom_tool_versions_toolId"))
            assertTrue(database.hasIndex("index_ai_custom_tool_versions_toolId_version"))
            assertTrue(database.hasIndex("index_ai_custom_tool_versions_toolName"))
            assertTrue(database.hasIndex("index_ai_custom_tool_versions_lifecycleState"))
            assertTrue(database.hasIndex("index_ai_custom_tool_versions_createdAt"))
            database.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.hasIndex(name: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type='index' AND name=?",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private companion object {
        const val DATABASE_NAME = "custom-agent-tool-migration"
    }
}
