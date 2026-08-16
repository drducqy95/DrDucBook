package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiCredentialEntity
import io.legado.app.data.entities.AiRouteAttemptEntity
import io.legado.app.data.entities.AiRouteProfileEntity
import io.legado.app.data.entities.AiRouteTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiRouterDao {

    @Query("SELECT * FROM ai_credentials ORDER BY sortNumber, createdAt")
    fun observeCredentials(): Flow<List<AiCredentialEntity>>

    @Query("SELECT * FROM ai_route_profiles ORDER BY taskType, sortNumber, createdAt")
    fun observeRoutes(): Flow<List<AiRouteProfileEntity>>

    @Query("SELECT * FROM ai_route_targets ORDER BY routeProfileId, priority, sortNumber, createdAt")
    fun observeTargets(): Flow<List<AiRouteTargetEntity>>

    @Query("SELECT * FROM ai_route_attempts ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentAttempts(limit: Int = 100): Flow<List<AiRouteAttemptEntity>>

    @Query("SELECT * FROM ai_credentials WHERE id = :id")
    suspend fun getCredential(id: String): AiCredentialEntity?

    @Query(
        """
        SELECT * FROM ai_credentials
        WHERE providerId = :providerId
        ORDER BY sortNumber, createdAt
        """
    )
    suspend fun getCredentialsForProvider(providerId: String): List<AiCredentialEntity>

    @Query("SELECT * FROM ai_route_profiles WHERE id = :id")
    suspend fun getRoute(id: String): AiRouteProfileEntity?

    @Query(
        """
        SELECT * FROM ai_route_profiles
        WHERE taskType = :taskType AND enabled = 1
        ORDER BY isDefault DESC, sortNumber, createdAt
        LIMIT 1
        """
    )
    suspend fun getActiveRoute(taskType: String): AiRouteProfileEntity?

    @Query("SELECT * FROM ai_route_targets WHERE id = :id")
    suspend fun getTarget(id: String): AiRouteTargetEntity?

    @Query(
        """
        SELECT * FROM ai_route_targets
        WHERE routeProfileId = :routeProfileId AND enabled = 1
        ORDER BY priority, sortNumber, createdAt
        """
    )
    suspend fun getEnabledTargets(routeProfileId: String): List<AiRouteTargetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredential(entity: AiCredentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoute(entity: AiRouteProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTarget(entity: AiRouteTargetEntity)

    @Insert
    suspend fun insertAttempt(entity: AiRouteAttemptEntity)

    @Query(
        """
        UPDATE ai_route_attempts SET success = 0, failureKind = :failureKind
        WHERE id = (
            SELECT id FROM ai_route_attempts
            WHERE routeProfileId = :routeProfileId AND targetId = :targetId AND success = 1
            ORDER BY createdAt DESC, id DESC LIMIT 1
        )
        """
    )
    suspend fun markLatestAttemptSemanticFailure(
        routeProfileId: String,
        targetId: String,
        failureKind: String,
    )

    @Query("DELETE FROM ai_credentials WHERE id = :id")
    suspend fun deleteCredential(id: String)

    @Query("UPDATE ai_route_targets SET credentialId = NULL WHERE credentialId = :credentialId")
    suspend fun clearCredentialFromTargets(credentialId: String)

    @Query("DELETE FROM ai_route_profiles WHERE id = :id")
    suspend fun deleteRouteRow(id: String)

    @Query("DELETE FROM ai_route_targets WHERE routeProfileId = :routeProfileId")
    suspend fun deleteTargetsForRoute(routeProfileId: String)

    @Query("DELETE FROM ai_route_targets WHERE id = :id")
    suspend fun deleteTarget(id: String)

    @Query("UPDATE ai_route_profiles SET isDefault = 0 WHERE taskType = :taskType")
    suspend fun clearDefaultRoutes(taskType: String)

    @Query(
        """
        UPDATE ai_route_targets SET
            cooldownUntil = :cooldownUntil,
            consecutiveFailures = consecutiveFailures + 1,
            lastFailureKind = :failureKind,
            lastFailureAt = :now,
            lastUsedAt = :now,
            updatedAt = :now
        WHERE id = :targetId
        """
    )
    suspend fun markTargetFailure(
        targetId: String,
        failureKind: String,
        cooldownUntil: Long,
        now: Long,
    )

    @Query(
        """
        UPDATE ai_credentials SET
            cooldownUntil = :cooldownUntil,
            consecutiveFailures = consecutiveFailures + 1,
            lastFailureKind = :failureKind,
            lastFailureAt = :now,
            lastUsedAt = :now,
            updatedAt = :now
        WHERE id = :credentialId
        """
    )
    suspend fun markCredentialFailure(
        credentialId: String,
        failureKind: String,
        cooldownUntil: Long,
        now: Long,
    )

    @Query(
        """
        UPDATE ai_route_targets SET
            cooldownUntil = 0,
            consecutiveFailures = 0,
            lastFailureKind = NULL,
            lastSuccessAt = :now,
            lastUsedAt = :now,
            updatedAt = :now
        WHERE id = :targetId
        """
    )
    suspend fun markTargetSuccess(targetId: String, now: Long)

    @Query(
        """
        UPDATE ai_credentials SET
            cooldownUntil = 0,
            consecutiveFailures = 0,
            lastFailureKind = NULL,
            lastSuccessAt = :now,
            lastUsedAt = :now,
            updatedAt = :now
        WHERE id = :credentialId
        """
    )
    suspend fun markCredentialSuccess(credentialId: String, now: Long)

    @Query(
        """
        UPDATE ai_credentials SET
            secretRef = :accessTokenRef,
            refreshTokenRef = :refreshTokenRef,
            idTokenRef = :idTokenRef,
            expiresAt = :expiresAt,
            scopes = :scopes,
            status = :status,
            providerDataJson = :providerDataJson,
            cooldownUntil = 0,
            consecutiveFailures = 0,
            lastFailureKind = NULL,
            updatedAt = :now
        WHERE id = :credentialId
        """
    )
    suspend fun updateOAuthTokens(
        credentialId: String,
        accessTokenRef: String,
        refreshTokenRef: String?,
        idTokenRef: String?,
        expiresAt: Long?,
        scopes: String?,
        status: String,
        providerDataJson: String?,
        now: Long,
    )

    @Query("UPDATE ai_credentials SET status = :status, updatedAt = :now WHERE id = :credentialId")
    suspend fun updateCredentialStatus(credentialId: String, status: String, now: Long)

    @Query(
        """
        UPDATE ai_route_targets SET cooldownUntil = 0, consecutiveFailures = 0,
            lastFailureKind = NULL, updatedAt = :now
        WHERE :targetId IS NULL OR id = :targetId
        """
    )
    suspend fun resetTargetHealth(targetId: String?, now: Long)

    @Query(
        """
        UPDATE ai_credentials SET cooldownUntil = 0, consecutiveFailures = 0,
            lastFailureKind = NULL, updatedAt = :now
        WHERE :credentialId IS NULL OR id = :credentialId
        """
    )
    suspend fun resetCredentialHealth(credentialId: String?, now: Long)

    @Query(
        """
        DELETE FROM ai_route_attempts WHERE id NOT IN (
            SELECT id FROM ai_route_attempts ORDER BY createdAt DESC, id DESC LIMIT :keep
        )
        """
    )
    suspend fun trimAttempts(keep: Int = 500)

    @Transaction
    suspend fun deleteRoute(id: String) {
        deleteTargetsForRoute(id)
        deleteRouteRow(id)
    }

    @Transaction
    suspend fun removeCredential(id: String) {
        clearCredentialFromTargets(id)
        deleteCredential(id)
    }
}
