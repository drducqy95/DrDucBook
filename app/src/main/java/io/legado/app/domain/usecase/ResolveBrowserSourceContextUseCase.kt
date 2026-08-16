package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.model.SourceDomainIndex
import kotlinx.coroutines.flow.Flow

class ResolveBrowserSourceContextUseCase(
    gateway: SourceDomainIndexGateway,
) {
    val index: Flow<SourceDomainIndex> = gateway.index
}
