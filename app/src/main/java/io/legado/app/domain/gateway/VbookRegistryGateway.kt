package io.legado.app.domain.gateway

import io.legado.app.domain.model.VbookRegistrySnapshot

interface VbookRegistryGateway {
    suspend fun load(forceRefresh: Boolean = false): Result<VbookRegistrySnapshot>

    suspend fun loadCached(): Result<VbookRegistrySnapshot>
}
