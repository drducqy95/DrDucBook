package io.legado.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.help.drive.DriveWebDavServiceController
import io.legado.app.help.drive.DriveWebDavState
import io.legado.app.help.drive.GoBridge
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveWebDavService : BaseService() {

    companion object {
        const val ACTION_START = "io.legado.app.service.DriveWebDavService.START"
        const val ACTION_STOP = "io.legado.app.service.DriveWebDavService.STOP"
        const val ACTION_UPDATE_TOKEN = "io.legado.app.service.DriveWebDavService.UPDATE_TOKEN"

        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }

    private val bridge = GoBridge()

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch {
                    bridge.stop()
                    DriveWebDavServiceController.updateState(DriveWebDavState.STOPPED)
                    stopSelf()
                }
            }

            ACTION_UPDATE_TOKEN -> {
                val token = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
                lifecycleScope.launch {
                    bridge.updateAccessToken(token)
                }
            }

            ACTION_START, null -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON).orEmpty()
                val accessToken = intent?.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
                startServer(configJson, accessToken)
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer(configJson: String, accessToken: String) {
        lifecycleScope.launch {
            DriveWebDavServiceController.updateState(DriveWebDavState.STARTING)
            val result = bridge.start(configJson, accessToken)
            result.onSuccess { startResult ->
                DriveWebDavServiceController.updateState(
                    state = DriveWebDavState.RUNNING,
                    port = startResult.port,
                    secret = startResult.sessionSecret
                )
                LogUtils.d("DriveWebDavService", "WebDAV server running on port ${startResult.port}")
                updateNotification("Đang phục vụ tại cổng ${startResult.port} (Read-Only)")
            }.onFailure { error ->
                val errorMsg = error.message ?: "Failed to start WebDAV proxy"
                LogUtils.e("DriveWebDavService", errorMsg)
                DriveWebDavServiceController.updateState(
                    state = DriveWebDavState.ERROR,
                    error = errorMsg
                )
                stopSelf()
            }
        }
    }

    override fun startForegroundNotification() {
        val notification = buildNotification("Đang khởi động dịch vụ...")
        startForeground(NotificationId.DriveWebDavService, notification)
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NotificationId.DriveWebDavService, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, DriveWebDavService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setSmallIcon(R.drawable.ic_outline_cloud_24)
            .setContentTitle("DrDucBook WebDAV")
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_stop_black_24dp,
                "Dừng",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                bridge.stop()
            }
            DriveWebDavServiceController.updateState(DriveWebDavState.STOPPED)
        }
    }
}
