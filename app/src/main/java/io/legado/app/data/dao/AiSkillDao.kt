package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.legado.app.data.entities.AiSkill
import io.legado.app.data.entities.AiSkillVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSkillDao {

    @Query("SELECT * FROM ai_skills ORDER BY updatedAt DESC")
    fun observeSkills(): Flow<List<AiSkill>>

    @Query("SELECT * FROM ai_skill_versions ORDER BY createdAt DESC")
    fun observeVersions(): Flow<List<AiSkillVersion>>

    @Query("SELECT * FROM ai_skills WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledSkills(): List<AiSkill>

    @Query("SELECT * FROM ai_skills WHERE id = :id LIMIT 1")
    suspend fun getSkill(id: String): AiSkill?

    @Query("SELECT * FROM ai_skills WHERE slug = :slug LIMIT 1")
    suspend fun getSkillBySlug(slug: String): AiSkill?

    @Query("SELECT * FROM ai_skill_versions WHERE skillId = :skillId ORDER BY createdAt DESC")
    suspend fun getVersions(skillId: String): List<AiSkillVersion>

    @Upsert
    suspend fun upsertSkill(skill: AiSkill)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(version: AiSkillVersion)

    @Query("UPDATE ai_skills SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :skillId")
    suspend fun updateEnabled(skillId: String, enabled: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE ai_skills SET activeVersionId = :versionId, name = :name, " +
            "description = :description, updatedAt = :updatedAt WHERE id = :skillId"
    )
    suspend fun updateActiveVersion(
        skillId: String,
        versionId: String,
        name: String,
        description: String,
        updatedAt: Long,
    ): Int

    @Transaction
    suspend fun saveDraft(skill: AiSkill, version: AiSkillVersion) {
        upsertSkill(skill)
        insertVersion(version)
    }
}
