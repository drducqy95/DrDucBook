package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiOAuthAuthorization
import io.legado.app.domain.model.AiOAuthEvent
import io.legado.app.domain.model.AiOAuthProviderConfig
import io.legado.app.domain.model.AiAvailableModel
import kotlinx.coroutines.flow.Flow

interface AiOAuthGateway {
    val events: Flow<AiOAuthEvent>
    fun providers(): List<AiOAuthProviderConfig>
    suspend fun begin(providerId: String): AiOAuthAuthorization
    suspend fun resolveAccessToken(credentialId: String): String
    suspend fun refresh(credentialId: String): Result<Unit>
    suspend fun syncModels(credentialId: String): Result<List<AiAvailableModel>>
}
