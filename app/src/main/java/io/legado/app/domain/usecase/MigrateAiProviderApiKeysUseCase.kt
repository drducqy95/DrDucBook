package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiProviderDraft
import kotlinx.coroutines.flow.first
import java.util.UUID

class MigrateAiProviderApiKeysUseCase(
    private val profileGateway: AiProfileGateway,
    private val routerGateway: AiRouterGateway,
) {

    suspend operator fun invoke(): AiProviderApiKeyMigrationResult {
        val providers = profileGateway.observeProviders().first()
        var migrated = 0
        providers.filter { it.apiKey.isNotBlank() }.forEach { provider ->
            routerGateway.saveCredential(
                AiCredentialDraft(
                    id = stableCredentialId(provider.id),
                    providerId = provider.id,
                    label = "${provider.name} API key",
                    kind = AiCredentialKind.API_KEY,
                    secret = provider.apiKey,
                    enabled = provider.enabled,
                )
            )
            profileGateway.saveProvider(provider.toRedactedDraft())
            migrated += 1
        }
        return AiProviderApiKeyMigrationResult(migratedProviderCount = migrated)
    }

    private fun AiProviderProfile.toRedactedDraft(): AiProviderDraft =
        AiProviderDraft(
            providerId = id,
            providerName = name,
            protocol = protocol,
            baseUrl = baseUrl,
            modelsUrl = modelsUrl,
            apiKey = "",
            authType = authType,
            headers = null,
            chatPath = chatPath,
            responsesPath = responsesPath,
            messagesPath = messagesPath,
            modelsPath = modelsPath,
            customHeaders = null,
        )

    private fun stableCredentialId(providerId: String): String {
        val uuid = UUID.nameUUIDFromBytes("legacy-api-key:$providerId".toByteArray())
            .toString()
            .replace("-", "")
        return "credential_$uuid"
    }
}

data class AiProviderApiKeyMigrationResult(
    val migratedProviderCount: Int,
)
