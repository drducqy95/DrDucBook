package io.legado.app.domain.gateway

import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt

interface AccountCloudBackupGateway {
    val configured: Boolean

    suspend fun uploadLatest(session: AccountSession, password: String): CloudBackupReceipt

    suspend fun restoreLatest(session: AccountSession, password: String): CloudBackupReceipt?
}
