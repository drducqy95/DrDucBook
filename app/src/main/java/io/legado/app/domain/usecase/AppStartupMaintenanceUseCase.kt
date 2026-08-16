package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AppStartupGateway

class AppStartupMaintenanceUseCase(
    private val appStartupGateway: AppStartupGateway,
    private val migrateAiProviderApiKeysUseCase: MigrateAiProviderApiKeysUseCase,
    private val repairAiRouteBindingsUseCase: RepairAiRouteBindingsUseCase,
) {

    suspend fun deleteNotShelfBooks() {
        appStartupGateway.deleteNotShelfBooks()
    }

    suspend fun ensureDefaultHttpTts() {
        appStartupGateway.ensureDefaultHttpTts()
    }

    suspend fun reconcileVbookSourceTypes(): Int {
        return appStartupGateway.reconcileVbookSourceTypes()
    }

    suspend fun repairAiRouteBindings(): RepairAiRouteBindingsResult {
        return repairAiRouteBindingsUseCase()
    }

    suspend fun migrateAiProviderApiKeys(): AiProviderApiKeyMigrationResult {
        return migrateAiProviderApiKeysUseCase()
    }
}
