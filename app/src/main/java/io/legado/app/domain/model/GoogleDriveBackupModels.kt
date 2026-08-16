package io.legado.app.domain.model

data class GoogleDriveAccountLink(
    val supabaseUserHash: String,
    val driveAccountHash: String,
) {
    val requiresAccountMismatchConfirmation: Boolean
        get() = supabaseUserHash != driveAccountHash
}

data class GoogleDriveCredentialSnapshot(
    val accessToken: String,
    val refreshToken: String?,
    val accountHash: String,
    val expiresAtEpochMillis: Long,
) {
    override fun toString(): String =
        "GoogleDriveCredentialSnapshot(accessToken=<redacted>, refreshToken=<redacted>, accountHash=$accountHash, expiresAtEpochMillis=$expiresAtEpochMillis)"
}

data class GoogleDriveSnapshotObject(
    val fileName: String,
    val appDataPath: String,
    val revision: String,
    val snapshotId: String,
    val sha256: String,
    val sizeBytes: Long,
)
