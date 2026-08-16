package io.legado.app.domain.usecase

import android.net.Uri
import io.legado.app.domain.gateway.SafBackupGateway

class SafBackupUseCase(
    private val gateway: SafBackupGateway,
) {
    val configured: Boolean get() = gateway.configured
    val scheduleIntervalHours: Long? get() = gateway.scheduleIntervalHours
    fun selectedTreeUri(): Uri? = gateway.selectedTreeUri()
    suspend fun setTreeUri(uri: Uri) = gateway.setTreeUri(uri)
    suspend fun clearTreeUri() = gateway.clearTreeUri()
    suspend fun setSchedule(intervalHours: Long, password: String) {
        require(password.length >= 8) { "Backup password must contain at least 8 characters" }
        gateway.setSchedule(intervalHours, password)
    }
    suspend fun clearSchedule() = gateway.clearSchedule()
    suspend fun uploadLatest(password: String): Long {
        require(password.length >= 8) { "Mật khẩu sao lưu phải có ít nhất 8 ký tự" }
        return gateway.uploadLatest(password)
    }
    suspend fun restoreLatest(password: String): Boolean {
        require(password.length >= 8) { "Mật khẩu sao lưu phải có ít nhất 8 ký tự" }
        return gateway.restoreLatest(password)
    }
}
