package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.GoogleDriveBackupGateway
import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.GoogleDriveAccountLink
import io.legado.app.domain.model.GoogleDriveSnapshotObject
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountPermission
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt

class GoogleDriveBackupUseCase(
    private val googleDriveBackupGateway: GoogleDriveBackupGateway,
) {

    val configured: Boolean
        get() = googleDriveBackupGateway.configured

    val requiredScopes: Set<String>
        get() = googleDriveBackupGateway.requiredScopes

    fun validateConsentScopes(scopes: Set<String>): Result<Unit> =
        googleDriveBackupGateway.validateConsentScopes(scopes)

    fun snapshotObject(descriptor: CloudSnapshotDescriptor): GoogleDriveSnapshotObject =
        googleDriveBackupGateway.snapshotObject(descriptor)

    fun accountLink(
        supabaseUserHash: String,
        driveAccountHash: String,
    ): GoogleDriveAccountLink = googleDriveBackupGateway.accountLink(
        supabaseUserHash = supabaseUserHash,
        driveAccountHash = driveAccountHash,
    )

    suspend fun uploadLatest(
        accessToken: String,
        session: AccountSession,
        access: AccountAccess,
        password: String,
    ): CloudBackupReceipt {
        requireAccess(session, access)
        return googleDriveBackupGateway.uploadLatest(
            accessToken,
            session,
            password.requireBackupPassword(),
        )
    }

    suspend fun restoreLatest(
        accessToken: String,
        session: AccountSession,
        access: AccountAccess,
        password: String,
    ): CloudBackupReceipt? {
        requireAccess(session, access)
        return googleDriveBackupGateway.restoreLatest(
            accessToken,
            session,
            password.requireBackupPassword(),
        )
    }

    private fun requireAccess(session: AccountSession, access: AccountAccess) {
        require(access.userId == session.userId) { "Phiên đăng nhập không khớp tài khoản" }
        require(access.allows(AccountPermission.CLOUD_BACKUP)) {
            "Tài khoản không có quyền sao lưu Google Drive"
        }
    }

    private fun String.requireBackupPassword(): String {
        require(trim().length >= 8) { "Mật khẩu sao lưu phải có ít nhất 8 ký tự" }
        return this
    }
}
