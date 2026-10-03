package io.legado.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.help.drive.DriveOpdsServiceController
import io.legado.app.help.drive.DriveOpdsState
import io.legado.app.lib.webdav.Authorization
import io.legado.app.utils.LogUtils
import io.legado.app.web.CloudDriveListingProvider
import io.legado.app.web.DriveOpdsProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveOpdsService : BaseService() {

    companion object {
        const val ACTION_START = "io.legado.app.service.DriveOpdsService.START"
        const val ACTION_STOP = "io.legado.app.service.DriveOpdsService.STOP"

        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_SOURCE_NAME = "source_name"
        const val EXTRA_WEBDAV_BASE_URL = "webdav_base_url"
        const val EXTRA_WEBDAV_USERNAME = "webdav_username"
        const val EXTRA_WEBDAV_PASSWORD = "webdav_password"
    }

    private var proxyServer: DriveOpdsProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch {
                    stopProxyServer()
                    DriveOpdsServiceController.updateState(DriveOpdsState.STOPPED)
                    stopSelf()
                }
            }

            ACTION_START, null -> {
                val sourceId = intent?.getStringExtra(EXTRA_SOURCE_ID).orEmpty()
                val sourceName = intent?.getStringExtra(EXTRA_SOURCE_NAME).orEmpty().ifEmpty { "Drive Library" }
                val webDavUrl = intent?.getStringExtra(EXTRA_WEBDAV_BASE_URL).orEmpty()
                val username = intent?.getStringExtra(EXTRA_WEBDAV_USERNAME).orEmpty()
                val password = intent?.getStringExtra(EXTRA_WEBDAV_PASSWORD).orEmpty()

                if (webDavUrl.isNotBlank()) {
                    startProxyServer(sourceId, sourceName, webDavUrl, username, password)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startProxyServer(
        sourceId: String,
        sourceName: String,
        webDavUrl: String,
        username: String,
        password: String
    ) {
        lifecycleScope.launch {
            DriveOpdsServiceController.updateState(DriveOpdsState.STARTING)
            try {
                stopProxyServer()

                val auth = Authorization(username, password)
                val listingProvider = CloudDriveListingProvider(webDavUrl, auth)
                val server = DriveOpdsProxyServer(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    listingProvider = listingProvider,
                    requestedPort = 0,
                )
                val port = server.start()
                proxyServer = server

                val opdsUrl = "http://127.0.0.1:$port/opds/catalog"
                DriveOpdsServiceController.updateState(
                    state = DriveOpdsState.RUNNING,
                    port = port,
                    opdsUrl = opdsUrl,
                )
                updateNotification("OPDS Catalog: 127.0.0.1:$port")
            } catch (e: Throwable) {
                LogUtils.e("DriveOpdsService", "Failed to start OPDS server: ${e.message}")
                DriveOpdsServiceController.updateState(
                    state = DriveOpdsState.ERROR,
                    error = e.message ?: "Failed to start OPDS server"
                )
                stopSelf()
            }
        }
    }

    private suspend fun stopProxyServer() = withContext(Dispatchers.IO) {
        proxyServer?.stop()
        proxyServer = null
    }

    override fun startForegroundNotification() {
        val notification = buildNotification("Đang khởi động OPDS server...")
        startForeground(NotificationId.DriveOpdsService, notification)
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NotificationId.DriveOpdsService, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, DriveOpdsService::class.java).apply {
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
            .setContentTitle("DrDucBook OPDS Server")
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
            stopProxyServer()
            DriveOpdsServiceController.updateState(DriveOpdsState.STOPPED)
        }
    }
}
