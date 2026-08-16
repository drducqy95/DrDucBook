package io.legado.app.domain.gateway

import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AuthoringRecoveryType {
    CORRUPT_MANIFEST,
    HASH_MISMATCH,
    UNSUPPORTED_SCHEMA,
    CORRUPT_ASSET_INDEX,
    MISSING_ASSET,
}

data class AuthoringRecoveryDiagnostic(
    val id: String,
    val projectId: String,
    val type: AuthoringRecoveryType,
    val message: String,
    val sourcePath: String,
    val recoveryPath: String?,
    val createdAt: Long,
    val sizeBytes: Long,
)

interface AuthoringProjectGateway {
    fun observeProjects(kind: AuthoringProjectKind): Flow<List<AuthoringProject>>

    suspend fun getProject(id: String): AuthoringProject?

    suspend fun saveProject(project: AuthoringProject)

    suspend fun deleteProject(id: String)

    suspend fun importImage(projectId: String, displayName: String, bytes: ByteArray): String

    suspend fun recoveryDiagnostics(): List<AuthoringRecoveryDiagnostic> = emptyList()

    suspend fun restoreLatestProjectSnapshot(projectId: String): AuthoringProject? = null

    suspend fun deleteRecoveryDiagnostic(id: String) = Unit
}
