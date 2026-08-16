package io.legado.app.domain.gateway

import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage
import io.legado.app.domain.model.AccountRole

interface AccountAccessGateway {
    val configured: Boolean

    suspend fun getAccess(userId: String): AccountAccess

    suspend fun listAccounts(): List<AccountAccess>

    suspend fun updateAccess(
        userId: String,
        role: AccountRole,
        permissions: Set<AccountPermission>,
        roleStartsAtEpochMillis: Long?,
        roleExpiresAtEpochMillis: Long?,
    ): AccountAccess

    suspend fun getDailyQuotaUsage(): List<AccountQuotaUsage>

    suspend fun consumeDailyQuota(
        kind: AccountQuotaKind,
        operationKeys: Set<String>,
    ): AccountQuotaUsage
}
