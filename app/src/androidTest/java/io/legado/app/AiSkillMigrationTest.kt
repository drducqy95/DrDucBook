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
class AiSkillMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate101To102CreatesVersionedSkillRegistry() {
        val databaseName = "ai-skill-migration"
        helper.createDatabase(databaseName, 101).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            databaseName,
        ).addMigrations(*DatabaseMigrations.migrations)
            .build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL(
                """
                INSERT INTO ai_skills(id, slug, name, description, enabled, activeVersionId, createdAt, updatedAt)
                VALUES ('skill_1', 'chapter_reader', 'Chapter reader', '', 0, 'version_1', 1, 1)
                """.trimIndent()
            )
            sql.execSQL(
                """
                INSERT INTO ai_skill_versions(
                    id, skillId, version, name, description, manifestJson, skillMarkdown,
                    allowedToolsJson, requirementsJson, validationStatus, validationMessage, createdAt
                ) VALUES (
                    'version_1', 'skill_1', '1.0.0', 'Chapter reader', '', '{}', 'Read first',
                    '[]', '[]', 'VALID', '', 1
                )
                """.trimIndent()
            )
            sql.query("SELECT COUNT(*) FROM ai_skill_versions WHERE skillId = 'skill_1'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}
