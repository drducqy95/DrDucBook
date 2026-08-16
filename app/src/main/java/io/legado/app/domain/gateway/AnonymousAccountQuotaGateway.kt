package io.legado.app.domain.gateway

import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage

interface AnonymousAccountQuotaGateway {
    suspend fun consumeDailyQuota(
        kind: AccountQuotaKind,
        operationKeys: Set<String>,
    ): AccountQuotaUsage
}
