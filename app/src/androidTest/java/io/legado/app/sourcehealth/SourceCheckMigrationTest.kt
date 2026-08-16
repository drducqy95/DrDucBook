package io.legado.app.sourcehealth

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
class SourceCheckMigrationTest {

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
    fun migrate107To108CreatesRunAndStageTablesWithoutChangingHealthSummary() {
        helper.createDatabase(DATABASE_NAME, 107).apply {
            execSQL(
                """
                INSERT INTO book_source_health(
                    sourceUrl,
                    status,
                    lastChecked,
                    latencyMs,
                    httpStatus,
                    failureStep,
                    messageRedacted,
                    consecutiveFailures
                ) VALUES(
                    'https://source.example',
                    'HEALTHY',
                    1000,
                    120,
                    200,
                    NULL,
                    NULL,
                    0
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            108,
            true,
            *DatabaseMigrations.migrations,
        )
        try {
            assertTrue(database.hasTable("source_check_runs"))
            assertTrue(database.hasTable("source_check_stage_results"))
            assertTrue(database.hasIndex("index_source_check_runs_sourceUrl_startedAt"))
            assertTrue(database.hasIndex("index_source_check_stage_results_runId"))
            assertEquals(
                "HEALTHY",
                database.string("SELECT status FROM book_source_health WHERE sourceUrl='https://source.example'"),
            )
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

    private fun SupportSQLiteDatabase.string(sql: String): String = query(sql).use {
        assertTrue(it.moveToFirst())
        it.getString(0)
    }

    private companion object {
        const val DATABASE_NAME = "source-check-migration"
    }
}
