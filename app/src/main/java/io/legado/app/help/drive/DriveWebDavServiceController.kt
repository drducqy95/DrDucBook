package io.legado.app.help.drive

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.legado.app.service.DriveWebDavService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DriveWebDavState {
    STOPPED,
    STARTING,
    RUNNING,
    AUTH_REQUIRED,
    ERROR
}

data class DriveWebDavServerInfo(
    val state: DriveWebDavState = DriveWebDavState.STOPPED,
    val port: Int = 0,
    val sessionSecret: String = "",
    val lastError: String = ""
)

object DriveWebDavServiceController {
    private val _serverInfo = MutableStateFlow(DriveWebDavServerInfo())
    val serverInfo: StateFlow<DriveWebDavServerInfo> = _serverInfo.asStateFlow()

    private val _state = MutableStateFlow(DriveWebDavState.STOPPED)
    val state: StateFlow<DriveWebDavState> = _state.asStateFlow()

    private val _port = MutableStateFlow(0)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _sessionSecret = MutableStateFlow("")
    val sessionSecret: StateFlow<String> = _sessionSecret.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    val isRunning: Boolean get() = _serverInfo.value.state == DriveWebDavState.RUNNING

    fun updateState(
        state: DriveWebDavState,
        port: Int = _serverInfo.value.port,
        secret: String = _serverInfo.value.sessionSecret,
        error: String = ""
    ) {
        val newInfo = DriveWebDavServerInfo(
            state = state,
            port = port,
            sessionSecret = secret,
            lastError = error
        )
        _serverInfo.value = newInfo
        // Also update legacy flows atomically
        _port.value = port
        _sessionSecret.value = secret
        _lastError.value = error
        _state.value = state
    }

    fun start(
        context: Context,
        configJson: String = "",
        accessToken: String = ""
    ) {
        updateState(DriveWebDavState.STARTING, port = 0, secret = "", error = "")
        val intent = Intent(context, DriveWebDavService::class.java).apply {
            action = DriveWebDavService.ACTION_START
            putExtra(DriveWebDavService.EXTRA_CONFIG_JSON, configJson)
            putExtra(DriveWebDavService.EXTRA_ACCESS_TOKEN, accessToken)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        updateState(DriveWebDavState.STOPPED, port = 0, secret = "", error = "")
        val intent = Intent(context, DriveWebDavService::class.java).apply {
            action = DriveWebDavService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateAccessToken(context: Context, token: String) {
        val intent = Intent(context, DriveWebDavService::class.java).apply {
            action = DriveWebDavService.ACTION_UPDATE_TOKEN
            putExtra(DriveWebDavService.EXTRA_ACCESS_TOKEN, token)
        }
        context.startService(intent)
    }
}
