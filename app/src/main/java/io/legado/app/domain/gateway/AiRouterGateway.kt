package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiCredentialConfig
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteTargetConfig
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiRouterSnapshot
import kotlinx.coroutines.flow.Flow

interface AiRouterGateway {
    fun observeSnapshot(): Flow<AiRouterSnapshot>

    suspend fun saveCredential(draft: AiCredentialDraft): AiCredentialConfig
    suspend fun resolveCredentialSecret(id: String): String
    suspend fun deleteCredential(id: String)

    suspend fun saveRoute(draft: AiRouteProfileDraft): AiRouteProfileConfig
    suspend fun deleteRoute(id: String)

    suspend fun saveTarget(draft: AiRouteTargetDraft): AiRouteTargetConfig
    suspend fun deleteTarget(id: String)

    suspend fun resetHealth(targetId: String? = null, credentialId: String? = null)
}
