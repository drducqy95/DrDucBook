package io.legado.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val databaseNames = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migrateGoldenFixture98ToLatestPreservesDataAndCreatesPhaseTables() {
        val databaseName = installGoldenFixture("phase08-golden-migration")
        val expected = fixtureExpectations()

        val database = openMigrated(databaseName)
        try {
            val sql = database.openHelper.writableDatabase
            assertEquals(110, sql.version)
            expected.getJSONObject("counts").keys().forEach { table ->
                assertEquals(
                    "Unexpected row count for $table",
                    expected.getJSONObject("counts").getInt(table),
                    sql.count(table),
                )
            }
            val preserved = expected.getJSONObject("preserved")
            assertEquals(preserved.getString("bookName"), sql.string("SELECT name FROM books WHERE bookUrl='book://text'"))
            assertEquals(
                preserved.getString("sourceName"),
                sql.string("SELECT bookSourceName FROM book_sources WHERE bookSourceUrl='https://source.test'"),
            )
            assertEquals(preserved.getString("cookie"), sql.string("SELECT cookie FROM cookies WHERE url='https://source.test'"))
            assertEquals(
                preserved.getString("legacyApiKey"),
                sql.string("SELECT apiKey FROM ai_provider_profiles WHERE id='provider-fixture'"),
            )
            assertEquals(
                preserved.getString("credentialSecretRef"),
                sql.string("SELECT secretRef FROM ai_credentials WHERE id='credential-fixture'"),
            )
            val newTables = expected.getJSONArray("newTables")
            repeat(newTables.length()) { index ->
                assertTrue("Missing table ${newTables.getString(index)}", sql.hasTable(newTables.getString(index)))
            }
            assertEquals(1, sql.countWhere("ai_memory_fts", "ai_memory_fts MATCH 'dragon'"))
            sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            database.close()
        }
    }

    @Test
    fun migratedFixtureCanBeReopenedWithoutDuplicateBackfill() {
        val databaseName = installGoldenFixture("phase08-idempotent-migration")
        openMigrated(databaseName).close()

        val database = openMigrated(databaseName)
        try {
            val sql = database.openHelper.writableDatabase
            assertEquals(2, sql.count("ai_memory"))
            assertEquals(2, sql.count("ai_memory_fts"))
            assertEquals(0, sql.count("ai_skills"))
            assertEquals(0, sql.count("media_download_tasks"))
        } finally {
            database.close()
        }
    }

    @Test
    fun freshInstallMatchesLatestRoomSchema() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sql = database.openHelper.writableDatabase
            assertEquals(110, sql.version)
            assertTrue(sql.hasTable("book_source_health"))
            assertTrue(sql.hasTable("media_download_items"))
            assertTrue(sql.hasTable("ai_skill_versions"))
            assertTrue(sql.hasTable("ai_agent_audits"))
            assertTrue(sql.hasTable("ai_custom_tools"))
            assertTrue(sql.hasTable("ai_custom_tool_versions"))
        } finally {
            database.close()
        }
    }

    private fun installGoldenFixture(databaseName: String): String {
        databaseNames += databaseName
        context.deleteDatabase(databaseName)
        val destination = context.getDatabasePath(databaseName)
        destination.parentFile?.mkdirs()
        testAssets.open("test_db_v98.db").use { input ->
            destination.outputStream().buffered().use(input::copyTo)
        }
        File(destination.path + "-wal").delete()
        File(destination.path + "-shm").delete()
        return databaseName
    }

    private fun openMigrated(databaseName: String): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName,
    )
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase }

    private fun fixtureExpectations(): JSONObject = testAssets
        .open("test_db_migration_fixture.json")
        .bufferedReader()
        .use { JSONObject(it.readText()) }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name=?",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.count(table: String): Int = query("SELECT COUNT(*) FROM `$table`").use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun SupportSQLiteDatabase.countWhere(table: String, where: String): Int =
        query("SELECT COUNT(*) FROM `$table` WHERE $where").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun SupportSQLiteDatabase.string(query: String): String = this.query(query).use {
        assertTrue(it.moveToFirst())
        it.getString(0)
    }
}
