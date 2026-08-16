package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AccountCloudBackupGateway
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt

class AccountCloudBackupUseCase(
    private val gateway: AccountCloudBackupGateway,
) {
    val configured: Boolean
        get() = gateway.configured

    suspend fun uploadLatest(
        session: AccountSession,
        access: AccountAccess,
        password: String,
    ): CloudBackupReceipt {
        require(access.userId == session.userId) { "Phiên đăng nhập không khớp tài khoản" }
        require(access.allows(AccountPermission.CLOUD_BACKUP)) {
            "Tài khoản không có quyền sao lưu đám mây"
        }
        return gateway.uploadLatest(session, password.requireBackupPassword())
    }

    suspend fun restoreLatest(
        session: AccountSession,
        access: AccountAccess,
        password: String,
    ): CloudBackupReceipt? {
        require(access.userId == session.userId) { "Phiên đăng nhập không khớp tài khoản" }
        require(access.allows(AccountPermission.CLOUD_BACKUP)) {
            "Tài khoản không có quyền khôi phục dữ liệu đám mây"
        }
        return gateway.restoreLatest(session, password.requireBackupPassword())
    }

    private fun String.requireBackupPassword(): String {
        require(trim().length >= 8) { "Mật khẩu sao lưu phải có ít nhất 8 ký tự" }
        return this
    }
}
