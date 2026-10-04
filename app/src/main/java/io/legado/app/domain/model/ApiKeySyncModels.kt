package io.legado.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ApiKeyBundle(
    val version: Int = 1,
    val updatedAt: String,
    val providers: List<ApiKeyEntry> = emptyList(),
)

@Serializable
@Immutable
data class ApiKeyEntry(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val apiKey: String,
    val authType: String = "bearer",
    val modelsUrl: String? = null,
    val customHeadersJson: String? = null,
    val chatPath: String? = null,
    val enabled: Boolean = true,
)

sealed interface ApiKeySyncStatus {
    data object NotSynced : ApiKeySyncStatus
    data object Syncing : ApiKeySyncStatus
    data class Synced(val timestamp: Long, val providerCount: Int) : ApiKeySyncStatus
    data class Error(val message: String) : ApiKeySyncStatus
}
