package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.legado.app.data.entities.AiCustomTool
import io.legado.app.data.entities.AiCustomToolVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCustomToolDao {

    @Query("SELECT * FROM ai_custom_tools ORDER BY updatedAt DESC")
    fun observeTools(): Flow<List<AiCustomTool>>

    @Query("SELECT * FROM ai_custom_tool_versions ORDER BY createdAt DESC")
    fun observeVersions(): Flow<List<AiCustomToolVersion>>

    @Query("SELECT * FROM ai_custom_tools WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledTools(): List<AiCustomTool>

    @Query("SELECT * FROM ai_custom_tools ORDER BY updatedAt DESC")
    suspend fun getTools(): List<AiCustomTool>

    @Query("SELECT * FROM ai_custom_tools WHERE id = :id LIMIT 1")
    suspend fun getTool(id: String): AiCustomTool?

    @Query("SELECT * FROM ai_custom_tools WHERE toolName = :toolName LIMIT 1")
    suspend fun getToolByName(toolName: String): AiCustomTool?

    @Query("SELECT * FROM ai_custom_tool_versions WHERE toolId = :toolId ORDER BY createdAt DESC")
    suspend fun getVersions(toolId: String): List<AiCustomToolVersion>

    @Query("SELECT * FROM ai_custom_tool_versions WHERE id = :versionId LIMIT 1")
    suspend fun getVersion(versionId: String): AiCustomToolVersion?

    @Upsert
    suspend fun upsertTool(tool: AiCustomTool)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(version: AiCustomToolVersion)

    @Query(
        "UPDATE ai_custom_tool_versions SET lifecycleState = :lifecycleState, " +
            "validationStatus = :validationStatus, validationMessage = :validationMessage, " +
            "validatedAt = :validatedAt WHERE id = :versionId"
    )
    suspend fun updateValidation(
        versionId: String,
        lifecycleState: String,
        validationStatus: String,
        validationMessage: String,
        validatedAt: Long?,
    ): Int

    @Query(
        "UPDATE ai_custom_tool_versions SET testStatus = :testStatus, testMessage = :testMessage, " +
            "testOutputJson = :testOutputJson, testedAt = :testedAt WHERE id = :versionId"
    )
    suspend fun updateTestResult(
        versionId: String,
        testStatus: String,
        testMessage: String,
        testOutputJson: String?,
        testedAt: Long?,
    ): Int

    @Query(
        "UPDATE ai_custom_tools SET activeVersionId = :versionId, name = :name, " +
            "description = :description, updatedAt = :updatedAt WHERE id = :toolId"
    )
    suspend fun updateActiveVersion(
        toolId: String,
        versionId: String,
        name: String,
        description: String,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE ai_custom_tool_versions SET lifecycleState = :lifecycleState, " +
            "approvedAt = :approvedAt WHERE id = :versionId"
    )
    suspend fun updateLifecycle(
        versionId: String,
        lifecycleState: String,
        approvedAt: Long? = null,
    ): Int

    @Query("UPDATE ai_custom_tools SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :toolId")
    suspend fun updateEnabled(toolId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM ai_custom_tools WHERE id = :toolId")
    suspend fun deleteTool(toolId: String): Int

    @Transaction
    suspend fun saveDraft(tool: AiCustomTool, version: AiCustomToolVersion) {
        upsertTool(tool)
        insertVersion(version)
    }
}
