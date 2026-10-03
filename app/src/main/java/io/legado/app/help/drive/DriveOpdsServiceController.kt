package io.legado.app.help.drive

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.legado.app.service.DriveOpdsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DriveOpdsState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class DriveOpdsServerInfo(
    val state: DriveOpdsState = DriveOpdsState.STOPPED,
    val port: Int = 0,
    val opdsUrl: String = "",
    val lastError: String = ""
)

object DriveOpdsServiceController {
    private val _serverInfo = MutableStateFlow(DriveOpdsServerInfo())
    val serverInfo: StateFlow<DriveOpdsServerInfo> = _serverInfo.asStateFlow()

    val isRunning: Boolean get() = _serverInfo.value.state == DriveOpdsState.RUNNING

    fun updateState(
        state: DriveOpdsState,
        port: Int = _serverInfo.value.port,
        opdsUrl: String = _serverInfo.value.opdsUrl,
        error: String = ""
    ) {
        _serverInfo.value = DriveOpdsServerInfo(
            state = state,
            port = port,
            opdsUrl = opdsUrl,
            lastError = error
        )
    }

    fun start(
        context: Context,
        sourceId: String,
        sourceName: String,
        webDavBaseUrl: String,
        webDavUsername: String = "",
        webDavPassword: String = "",
    ) {
        updateState(DriveOpdsState.STARTING, port = 0, opdsUrl = "", error = "")
        val intent = Intent(context, DriveOpdsService::class.java).apply {
            action = DriveOpdsService.ACTION_START
            putExtra(DriveOpdsService.EXTRA_SOURCE_ID, sourceId)
            putExtra(DriveOpdsService.EXTRA_SOURCE_NAME, sourceName)
            putExtra(DriveOpdsService.EXTRA_WEBDAV_BASE_URL, webDavBaseUrl)
            putExtra(DriveOpdsService.EXTRA_WEBDAV_USERNAME, webDavUsername)
            putExtra(DriveOpdsService.EXTRA_WEBDAV_PASSWORD, webDavPassword)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, DriveOpdsService::class.java).apply {
            action = DriveOpdsService.ACTION_STOP
        }
        context.startService(intent)
        updateState(DriveOpdsState.STOPPED, port = 0, opdsUrl = "")
    }
}
