package io.legado.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class DriveSourceType {
    GOOGLE_DRIVE_ACCOUNT,
    GOOGLE_DRIVE_PUBLIC,
    ONEDRIVE_PUBLIC,
    DROPBOX_PUBLIC,
    HTTP_INDEX
}

@Serializable
enum class DriveConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    AUTH_REQUIRED,
    ERROR
}

@Serializable
@Immutable
data class ManagedDriveSource(
    val id: String,
    val name: String,
    val type: DriveSourceType,
    val rootFolderId: String = "",
    val publicUrl: String = "",
    val accountEmail: String = "",
    val serverId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val status: DriveConnectionStatus = DriveConnectionStatus.DISCONNECTED,
)
