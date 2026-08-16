package io.legado.app.service

// ——————【新增引用】——————
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.domain.webservice.WebServicePorts
import io.legado.app.domain.usecase.WebServiceAccessUseCase
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.LogUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.sendToClip
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.startService
import io.legado.app.utils.stopService
import io.legado.app.utils.toastOnUi
import io.legado.app.web.KtorServer
import splitties.init.appCtx
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext

class WebService : BaseService() {

    companion object {
        private const val ACCESS_RECHECK_INTERVAL_MILLIS = 60_000L
        private const val SERVER_START_TIMEOUT_MILLIS = 15_000L
        private const val BATTERY_OPTIMIZATION_PROMPT_INTERVAL_MILLIS = 10 * 60 * 1000L

        var isRun = false
        var hostAddress = ""
        var activeHttpPort = WebServicePorts.DEFAULT_HTTP_PORT
        var activeWebSocketPort = WebServicePorts.DEFAULT_WEB_SOCKET_PORT

        fun start(context: Context) {
            startForeground(context)
        }

        fun startForeground(context: Context) {
            val intent = Intent(context, WebService::class.java)
            context.startForegroundServiceCompat(intent)
        }

        fun stop(context: Context) {
            context.stopService<WebService>()
        }

        fun serve() {
            // Requests are served only by an already-running instance. Starting a
            // foreground service from every request races with shutdown/background limits.
        }
    }

    // Huawei/EMUI may freeze the entire application UID when the UI moves to
    // the background, even while the HTTP service is foreground. Keep a
    // partial wake lock for the lifetime of the web service so local and
    // Cloudflare requests continue to be accepted reliably.
    private val useWakeLock = appCtx.getPrefBoolean(PreferKey.webServiceWakeLock, true)
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:WebService")
            .apply {
                setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private var ktorServer: KtorServer? = null
    private var notificationList = mutableListOf(appCtx.getString(R.string.service_starting))
    private var runtimeInitialized = false
    private val accessCheckRunning = AtomicBoolean(false)
    private var accessMonitorStarted = false
    private var lastBatteryOptimizationPromptAt = 0L
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
    }

    @SuppressLint("WakelockTimeout")
    private fun initializeRuntime() {
        if (runtimeInitialized) return
        if (useWakeLock) {
            if (!wakeLock.isHeld) wakeLock.acquire()
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        }
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            lifecycleScope.launch {
                val addressList = webServiceAddresses()
                notificationList.clear()
                if (addressList.any()) {
                    notificationList.addAll(addressList.map { address ->
                        getString(
                            R.string.http_ip,
                            address.hostAddress,
                            activeHttpPort
                        )
                    })
                    hostAddress = notificationList.first()
                } else {
                    hostAddress = getString(R.string.network_connection_unavailable)
                    notificationList.add(hostAddress)
                }
                startForegroundNotification()
                if (isRun) {
                    postEvent(EventBus.WEB_SERVICE, hostAddress)
                    FlowEventBus.post(EventBus.WEB_SERVICE, hostAddress)
                }
            }
        }
        runtimeInitialized = true
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        requestBatteryOptimizationExemption()
        when (intent?.action) {
            IntentAction.stop -> {
                // ——————【修改开始】通知栏点击停止时，也记录关闭状态——————
                OtherConfig.webServiceAutoStart = false
                stopSelf()
                // ——————【修改结束】——————
            }
            "copyHostAddress" -> sendToClip(hostAddress)
            else -> authorizeAndStart(intent)
        }
        return Service.START_STICKY
    }

    /**
     * EMUI's PowerGenie can freeze an entire app UID when an external browser
     * becomes foreground, even if the service is marked foreground. The Android
     * battery-optimization exemption is the only user-consented API available
     * to keep a long-lived local HTTP server reachable in that state.
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            powerManager.isIgnoringBatteryOptimizations(appCtx.packageName)
        ) return
        val now = System.currentTimeMillis()
        if (now - lastBatteryOptimizationPromptAt < BATTERY_OPTIMIZATION_PROMPT_INTERVAL_MILLIS) return
        lastBatteryOptimizationPromptAt = now
        runCatching {
            appCtx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${appCtx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            LogUtils.e("WebService", "Unable to open battery optimization settings: ${it.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The foreground web server is intentionally independent of the app task.
        // Swiping the UI away must not tear down local or Cloudflare sessions.
    }

    private fun authorizeAndStart(intent: Intent?) {
        if (!accessCheckRunning.compareAndSet(false, true)) return
        lifecycleScope.launch {
            try {
                val access = GlobalContext.get().get<WebServiceAccessUseCase>()
                val accessError = runCatching { access.requireAllowed() }.exceptionOrNull()
                if (accessError != null) {
                    OtherConfig.webServiceAutoStart = false
                    isRun = false
                    upTile(false)
                    toastOnUi(
                        accessError.localizedMessage
                            ?: getString(R.string.web_service_premium_required)
                    )
                    stopSelf()
                    return@launch
                }
                initializeRuntime()
                startAccessMonitor(access)
                if (isRun && ktorServer != null) {
                    startForegroundNotification()
                    return@launch
                }
                upWebServer()
            } finally {
                accessCheckRunning.set(false)
            }
        }
    }

    private fun startAccessMonitor(access: WebServiceAccessUseCase) {
        if (accessMonitorStarted) return
        accessMonitorStarted = true
        lifecycleScope.launch {
            while (isActive) {
                delay(ACCESS_RECHECK_INTERVAL_MILLIS)
                val allowed = runCatching { access.isAllowed() }.getOrNull()
                if (allowed == false) {
                    OtherConfig.webServiceAutoStart = false
                    stopSelf()
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CloudflareTunnelManager.stop()
        if (useWakeLock) {
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        if (runtimeInitialized) {
            networkChangedListener.unRegister()
        }
        isRun = false
        ktorServer?.stop()
        postEvent(EventBus.WEB_SERVICE, "")
        FlowEventBus.post(EventBus.WEB_SERVICE, "")
        upTile(false)
    }

    private suspend fun upWebServer() {
        val previousServer = ktorServer
        ktorServer = null
        withContext(Dispatchers.IO) {
            previousServer?.stop()
        }
        val addressList = webServiceAddresses()
        if (addressList.any()) {
            val port = resolveHttpPort()
            val wsPort = WebServicePorts.webSocketPortFor(port)
            activeHttpPort = port
            activeWebSocketPort = wsPort
            ktorServer = KtorServer(port, wsPort)
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(SERVER_START_TIMEOUT_MILLIS) {
                        ktorServer?.start()
                        ktorServer?.startWebSocket(wsPort)
                    }
                }
                notificationList.clear()
                notificationList.addAll(addressList.map { address ->
                    getString(
                        R.string.http_ip,
                        address.hostAddress,
                        port
                    )
                })
                hostAddress = notificationList.first()
                isRun = true
                upTile(true)
                postEvent(EventBus.WEB_SERVICE, hostAddress)
                FlowEventBus.post(EventBus.WEB_SERVICE, hostAddress)
                startForegroundNotification()
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    ktorServer?.stop()
                }
                ktorServer = null
                toastOnUi(getString(R.string.web_service_start_failed, e.localizedMessage.orEmpty()))
                LogUtils.e("WebService", e.stackTraceStr)
                stopSelf()
            }
        }
    }

    private fun webServiceAddresses(): List<InetAddress> =
        NetworkUtils.getLocalIPAddress().ifEmpty {
            listOf(InetAddress.getByName("127.0.0.1"))
        }

    private fun getConfiguredPort(): Int {
        val savedPort = getPrefInt(PreferKey.webPort, WebServicePorts.DEFAULT_HTTP_PORT)
        val normalizedPort = WebServicePorts.normalizeHttpPort(savedPort)
        if (savedPort != normalizedPort) {
            putPrefInt(PreferKey.webPort, normalizedPort)
        }
        return normalizedPort
    }

    private fun resolveHttpPort(): Int {
        val preferredPort = getConfiguredPort()
        val availablePort = WebServicePorts.suggestHttpPort(
            preferredPort = preferredPort,
            isAvailable = ::isTcpPortAvailable,
        )
        if (availablePort != preferredPort) {
            putPrefInt(PreferKey.webPort, availablePort)
        }
        return availablePort
    }

    private fun isTcpPortAvailable(port: Int): Boolean {
        return runCatching {
            ServerSocket(port).use { true }
        }.getOrDefault(false)
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setContentTitle(getString(R.string.web_service))
            .setContentText(notificationList.joinToString("\n"))
            .setContentIntent(
                servicePendingIntent<WebService>("copyHostAddress")
            )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<WebService>(IntentAction.stop)
        )
        val notification = builder.build()
        startForeground(NotificationId.WebService, notification)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun upTile(active: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            kotlin.runCatching {
                startService<WebTileService> {
                    action = if (active) {
                        IntentAction.start
                    } else {
                        IntentAction.stop
                    }
                }
            }

        }
    }
}
