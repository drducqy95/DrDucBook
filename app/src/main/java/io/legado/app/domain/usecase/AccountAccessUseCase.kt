package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AccountAccessGateway
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage
import io.legado.app.domain.model.AccountRole

class AccountAccessUseCase(
    private val gateway: AccountAccessGateway,
) {
    val configured: Boolean
        get() = gateway.configured

    suspend fun getAccess(userId: String): AccountAccess = gateway.getAccess(userId)

    suspend fun listAccounts(requester: AccountAccess): List<AccountAccess> {
        require(requester.allows(AccountPermission.MANAGE_ACCOUNTS)) {
            "Tài khoản không có quyền quản lý người dùng"
        }
        return gateway.listAccounts()
    }

    suspend fun updateAccess(
        requester: AccountAccess,
        userId: String,
        role: AccountRole,
        roleStartsAtEpochMillis: Long?,
        roleExpiresAtEpochMillis: Long?,
    ): AccountAccess {
        require(requester.allows(AccountPermission.MANAGE_ACCOUNTS)) {
            "Tài khoản không có quyền quản lý người dùng"
        }
        require(
            roleExpiresAtEpochMillis == null ||
                roleStartsAtEpochMillis != null && roleExpiresAtEpochMillis > roleStartsAtEpochMillis
        ) { "Thời hạn quyền tài khoản không hợp lệ" }
        val normalizedPermissions = role.defaultPermissions
        return gateway.updateAccess(
            userId = userId,
            role = role,
            permissions = normalizedPermissions,
            roleStartsAtEpochMillis = roleStartsAtEpochMillis,
            roleExpiresAtEpochMillis = roleExpiresAtEpochMillis,
        )
    }

    suspend fun getDailyQuotaUsage(): List<AccountQuotaUsage> =
        gateway.getDailyQuotaUsage()

    suspend fun consumeDailyQuota(
        kind: AccountQuotaKind,
        operationKeys: Set<String>,
    ): AccountQuotaUsage {
        require(operationKeys.isNotEmpty()) { "Không có tác vụ cần tính hạn mức" }
        require(operationKeys.size <= 100) { "Mỗi lần chỉ hỗ trợ tối đa 100 tác vụ" }
        require(operationKeys.all { OPERATION_KEY_REGEX.matches(it) }) {
            "Mã tác vụ hạn mức không hợp lệ"
        }
        return gateway.consumeDailyQuota(kind, operationKeys)
    }

    private companion object {
        val OPERATION_KEY_REGEX = Regex("^[a-f0-9]{64}$")
    }
}
