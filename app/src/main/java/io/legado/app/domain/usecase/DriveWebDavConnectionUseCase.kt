package io.legado.app.domain.usecase

import android.content.Context
import com.drducbook.app.BuildConfig
import io.legado.app.data.repository.ManagedSourceRegistry
import io.legado.app.domain.model.DriveConnectionStatus
import io.legado.app.domain.model.DriveSourceType
import io.legado.app.domain.model.ManagedDriveSource
import io.legado.app.help.drive.DriveWebDavServiceController
import io.legado.app.help.drive.DriveWebDavState
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class DriveWebDavConnectionUseCase(
    private val context: Context,
    private val registry: ManagedSourceRegistry
) {

    suspend fun connectSource(
        source: ManagedDriveSource,
        accessToken: String = ""
    ): Result<Long> = runCatching {
        registry.updateStatus(source.id, DriveConnectionStatus.CONNECTING)

        val configMap = mutableMapOf<String, Any>(
            "port" to 0,
            "read_only" to true,
            "request_timeout_sec" to 30
        )

        when (source.type) {
            DriveSourceType.GOOGLE_DRIVE_ACCOUNT -> {
                configMap["provider"] = "google_drive"
                configMap["mode"] = "authenticated"
                if (source.rootFolderId.isNotBlank()) {
                    configMap["root_folder_id"] = source.rootFolderId
                }
            }
            DriveSourceType.GOOGLE_DRIVE_PUBLIC -> {
                configMap["provider"] = "google_drive"
                configMap["mode"] = "public_link"
                configMap["public_resource_url"] = source.publicUrl
                if (source.rootFolderId.isNotBlank()) {
                    configMap["root_folder_id"] = source.rootFolderId
                }
                val apiKey = BuildConfig.GOOGLE_DRIVE_API_KEY.trim()
                if (apiKey.isNotBlank()) {
                    configMap["google_drive_api_key"] = apiKey
                }
            }
            DriveSourceType.GOOGLE_DRIVE_SERVICE_ACCOUNT -> {
                configMap["provider"] = "google_drive"
                configMap["mode"] = "service_account"
                val rawSaJson = io.legado.app.help.drive.ServiceAccountCredentialStore.decrypt(source.serviceAccountJson)
                if (rawSaJson.isNotBlank()) {
                    configMap["service_account_json"] = rawSaJson
                }
                if (source.rootFolderId.isNotBlank()) {
                    configMap["root_folder_id"] = source.rootFolderId
                }
            }
            DriveSourceType.ONEDRIVE_PUBLIC -> {
                configMap["provider"] = "onedrive"
                configMap["mode"] = "public_link"
                configMap["public_resource_url"] = source.publicUrl
            }
            DriveSourceType.DROPBOX_PUBLIC -> {
                configMap["provider"] = "dropbox"
                configMap["mode"] = "public_link"
                configMap["public_resource_url"] = source.publicUrl
            }
            DriveSourceType.HTTP_INDEX -> {
                configMap["provider"] = "http"
                configMap["mode"] = "public_link"
                configMap["public_resource_url"] = source.publicUrl
            }
        }

        val configJson = GSON.toJson(configMap)

        DriveWebDavServiceController.start(
            context = context,
            configJson = configJson,
            accessToken = accessToken
        )

        // Wait up to 10 seconds for server to start with valid port
        val serverInfo = withTimeoutOrNull(10_000L) {
            DriveWebDavServiceController.serverInfo.filter {
                (it.state == DriveWebDavState.RUNNING && it.port > 0) || it.state == DriveWebDavState.ERROR
            }.first()
        }

        if (serverInfo == null || serverInfo.state != DriveWebDavState.RUNNING) {
            val err = serverInfo?.lastError?.ifEmpty { "Connection timeout" } ?: "Connection timeout"
            registry.updateStatus(source.id, DriveConnectionStatus.ERROR)
            error(err)
        }

        val port = serverInfo.port
        val secret = serverInfo.sessionSecret

        val serverId = registry.syncServerEntity(source, port, secret)
        registry.updateStatus(source.id, DriveConnectionStatus.CONNECTED)

        // Start local OPDS server proxy chained to the WebDAV instance
        io.legado.app.help.drive.DriveOpdsServiceController.start(
            context = context,
            sourceId = source.id,
            sourceName = source.name,
            webDavBaseUrl = "http://127.0.0.1:$port/",
            webDavUsername = secret,
            webDavPassword = secret,
        )

        serverId
    }

    suspend fun disconnect() {
        io.legado.app.help.drive.DriveOpdsServiceController.stop(context)
        DriveWebDavServiceController.stop(context)
        val active = registry.getActiveSource()
        if (active != null) {
            registry.updateStatus(active.id, DriveConnectionStatus.DISCONNECTED)
        }
    }
}
