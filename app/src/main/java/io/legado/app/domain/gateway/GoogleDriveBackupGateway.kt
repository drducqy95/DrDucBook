package io.legado.app.domain.gateway

import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.GoogleDriveAccountLink
import io.legado.app.domain.model.GoogleDriveSnapshotObject
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt

interface GoogleDriveBackupGateway {
    val configured: Boolean
    val requiredScopes: Set<String>

    fun validateConsentScopes(scopes: Set<String>): Result<Unit>

    fun snapshotObject(descriptor: CloudSnapshotDescriptor): GoogleDriveSnapshotObject

    fun accountLink(
        supabaseUserHash: String,
        driveAccountHash: String,
    ): GoogleDriveAccountLink

    suspend fun uploadLatest(
        accessToken: String,
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt

    suspend fun restoreLatest(
        accessToken: String,
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt?
}
