package io.legado.app.domain.gateway

import io.legado.app.domain.model.SourceDomainIndex
import kotlinx.coroutines.flow.Flow

interface SourceDomainIndexGateway {
    val index: Flow<SourceDomainIndex>
}
