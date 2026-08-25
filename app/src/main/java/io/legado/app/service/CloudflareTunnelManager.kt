package io.legado.app.service

import android.content.Context
import android.net.ConnectivityManager
import io.legado.app.domain.webservice.CloudflareTunnelCommand
import io.legado.app.domain.webservice.CloudflareTunnelMode
import io.legado.app.domain.webservice.CloudflareTunnelPhase
import io.legado.app.domain.webservice.CloudflareTunnelState
import io.legado.app.domain.webservice.WebServicePairingCenter
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object CloudflareTunnelManager {
    private const val PREF_PAIRING_ENABLED = "cloudflare_tunnel_pairing_enabled"
    private const val BINARY_NAME = "libcloudflared.so"
    private const val MAX_RETRIES = 5
    private val RETRY_DELAYS = longArrayOf(2000L, 4000L, 8000L, 16000L, 32000L)
    private val quickUrlPattern = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _state = MutableStateFlow(CloudflareTunnelState())
    private var process: Process? = null
    private var tokenFile: File? = null

    private var lastLocalPort: Int? = null
    private var lastToken: String = ""
    private var lastPublicUrl: String = ""
    private var lastContext: Context? = null
    private var retryCount = 0
    private var userStopped = false
    private var reconnectJob: kotlinx.coroutines.Job? = null

    val state = _state.asStateFlow()
    val requiresPairing: Boolean
        get() = _state.value.requiresPairing
    val publicUrl: String
        get() = _state.value.publicUrl

    fun isPairingEnabled(context: Context): Boolean =
        context.getPrefBoolean(PREF_PAIRING_ENABLED, true)

    fun setPairingEnabled(context: Context, enabled: Boolean) {
        context.putPrefBoolean(PREF_PAIRING_ENABLED, enabled)
        _state.value = _state.value.copy(pairingEnabled = enabled)
        if (enabled && _state.value.requiresPairing && _state.value.pairingCode.isBlank()) {
            refreshPairingCode()
        } else if (!enabled) {
            WebServicePairingCenter.revokeAll()
            _state.value = _state.value.copy(pairingCode = "", pairingExpiresAt = 0L)
        }
    }

    fun startQuick(context: Context, localPort: Int) {
        userStopped = false
        lastContext = context.applicationContext
        lastLocalPort = localPort
        reconnectJob?.cancel()
        reconnectJob = null
        stopInternal()
        start(
            context = context,
            mode = CloudflareTunnelMode.QUICK,
            publicUrl = "",
            command = { binary -> CloudflareTunnelCommand.quick(binary.path, localPort) },
        )
    }

    fun startNamed(context: Context, token: String, publicUrl: String) {
        val normalizedUrl = CloudflareTunnelCommand.normalizePublicUrl(publicUrl)
        if (token.isBlank() || normalizedUrl == null) {
            _state.value = CloudflareTunnelState(
                phase = CloudflareTunnelPhase.ERROR,
                detail = "Tunnel token and an https public URL are required.",
            )
            return
        }
        userStopped = false
        lastContext = context.applicationContext
        lastToken = token
        lastPublicUrl = normalizedUrl
        reconnectJob?.cancel()
        reconnectJob = null
        stopInternal()
        val privateTokenFile = File(context.noBackupFilesDir, "cloudflared-tunnel-token")
        runCatching {
            privateTokenFile.writeText(token.trim())
            privateTokenFile.setReadable(false, false)
            privateTokenFile.setReadable(true, true)
            privateTokenFile.setWritable(false, false)
            privateTokenFile.setWritable(true, true)
        }.onFailure { error ->
            _state.value = CloudflareTunnelState(
                phase = CloudflareTunnelPhase.ERROR,
                detail = error.localizedMessage ?: "Could not protect the tunnel token.",
            )
            return
        }
        tokenFile = privateTokenFile
        start(
            context = context,
            mode = CloudflareTunnelMode.NAMED,
            publicUrl = normalizedUrl,
            command = { binary ->
                CloudflareTunnelCommand.named(binary.path, privateTokenFile.path)
            },
        )
    }

    fun refreshPairingCode() {
        if (!requiresPairing) return
        val challenge = WebServicePairingCenter.createChallenge()
        _state.value = _state.value.copy(
            pairingCode = challenge.code,
            pairingExpiresAt = challenge.expiresAt,
        )
    }

    fun onPairingConsumed() {
        _state.value = _state.value.copy(pairingCode = "", pairingExpiresAt = 0L)
    }

    fun retryIfFailed(context: Context) {
        val current = _state.value
        if (current.phase != CloudflareTunnelPhase.ERROR || current.mode == CloudflareTunnelMode.OFF) return
        retryCount = 0
        reconnectJob?.cancel()
        reconnectJob = null
        when (current.mode) {
            CloudflareTunnelMode.QUICK -> {
                lastLocalPort?.let { port -> startQuick(context, port) }
            }
            CloudflareTunnelMode.NAMED -> {
                if (lastToken.isNotBlank() && lastPublicUrl.isNotBlank()) {
                    startNamed(context, lastToken, lastPublicUrl)
                }
            }
            CloudflareTunnelMode.OFF -> Unit
        }
    }

    fun stop() {
        userStopped = true
        retryCount = 0
        reconnectJob?.cancel()
        reconnectJob = null
        stopInternal()
        _state.value = CloudflareTunnelState(pairingEnabled = _state.value.pairingEnabled)
    }

    private fun stopInternal() {
        val stopped = synchronized(lock) {
            process.also { process = null }
        }
        stopped?.destroy()
        tokenFile?.delete()
        tokenFile = null
        WebServicePairingCenter.revokeAll()
    }

    private fun start(
        context: Context,
        mode: CloudflareTunnelMode,
        publicUrl: String,
        command: (File) -> List<String>,
    ) {
        val binary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!binary.isFile) {
            tokenFile?.delete()
            tokenFile = null
            _state.value = CloudflareTunnelState(
                phase = CloudflareTunnelPhase.ERROR,
                detail = "Cloudflare Tunnel is unavailable for this device architecture.",
            )
            return
        }
        val pairingEnabled = context.readPairingEnabled()
        val challenge = pairingEnabled.takeIf { it }?.let { WebServicePairingCenter.createChallenge() }
        _state.value = CloudflareTunnelState(
            mode = mode,
            phase = CloudflareTunnelPhase.STARTING,
            publicUrl = publicUrl,
            pairingEnabled = pairingEnabled,
            pairingCode = challenge?.code.orEmpty(),
            pairingExpiresAt = challenge?.expiresAt ?: 0L,
            detail = "Connecting to Cloudflare…",
        )
        scope.launch {
            val diagnosticLines = mutableListOf<String>()
            val started = runCatching {
                ProcessBuilder(command(binary))
                    .redirectErrorStream(true)
                    .directory(context.cacheDir)
                    .apply {
                        environment()["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
                        environment()["TMPDIR"] = context.cacheDir.absolutePath
                        environment()["HOME"] = context.noBackupFilesDir.absolutePath
                        environment()["CLOUDFLARED_ANDROID_DNS"] = androidDnsServer(context)
                    }
                    .start()
            }.getOrElse { error ->
                fail(error.localizedMessage ?: error.javaClass.simpleName)
                return@launch
            }
            synchronized(lock) { process = started }
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(lock) {
                            if (diagnosticLines.size >= 8) diagnosticLines.removeAt(0)
                            diagnosticLines.add(line.trim())
                        }
                        handleOutput(started, line)
                    }
                }
                val exitCode = started.waitFor()
                handleProcessExit(started, exitCode, diagnosticLines)
            }.onFailure { error ->
                handleProcessExit(started, -1, diagnosticLines, error.localizedMessage)
            }
        }
    }

    private fun handleOutput(started: Process, line: String) {
        synchronized(lock) {
            if (process !== started) return
        }
        val quickUrl = quickUrlPattern.find(line)?.value
        val connected = quickUrl != null ||
            line.contains("Registered tunnel connection", ignoreCase = true)
        if (connected) {
            retryCount = 0
            _state.value = _state.value.copy(
                phase = CloudflareTunnelPhase.CONNECTED,
                publicUrl = quickUrl ?: _state.value.publicUrl,
                detail = "Connected through Cloudflare.",
            )
        }
    }

    private fun handleProcessExit(
        started: Process,
        exitCode: Int,
        diagnosticLines: List<String>,
        errorMessage: String? = null,
    ) {
        synchronized(lock) {
            if (process !== started) return
            process = null
        }
        if (userStopped) return

        val currentMode = _state.value.mode
        val ctx = lastContext
        if (currentMode != CloudflareTunnelMode.OFF && ctx != null && retryCount < MAX_RETRIES) {
            retryCount++
            val delayMs = RETRY_DELAYS.getOrElse(retryCount - 1) { 32000L }
            _state.value = _state.value.copy(
                phase = CloudflareTunnelPhase.RECONNECTING,
                detail = "Reconnecting in ${delayMs / 1000}s (attempt $retryCount/$MAX_RETRIES)…",
            )
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                kotlinx.coroutines.delay(delayMs)
                if (!userStopped) {
                    when (currentMode) {
                        CloudflareTunnelMode.QUICK -> {
                            lastLocalPort?.let { port -> startQuick(ctx, port) }
                        }
                        CloudflareTunnelMode.NAMED -> {
                            if (lastToken.isNotBlank() && lastPublicUrl.isNotBlank()) {
                                startNamed(ctx, lastToken, lastPublicUrl)
                            }
                        }
                        CloudflareTunnelMode.OFF -> Unit
                    }
                }
            }
            return
        }

        val contextMsg = diagnosticLines.takeLast(3).joinToString(" | ")
        val detailPrefix = errorMessage ?: "Cloudflare Tunnel stopped (code $exitCode)"
        val suffix = if (contextMsg.isNotBlank()) ": $contextMsg" else ""
        fail(detailPrefix + suffix)
    }

    private fun fail(detail: String) {
        tokenFile?.delete()
        tokenFile = null
        WebServicePairingCenter.revokeAll()
        _state.value = _state.value.copy(
            mode = CloudflareTunnelMode.OFF,
            phase = CloudflareTunnelPhase.ERROR,
            pairingCode = "",
            pairingExpiresAt = 0L,
            detail = detail,
        )
    }

    private fun androidDnsServer(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val dnsServers = network?.let {
            connectivity.getLinkProperties(it)?.dnsServers
        }.orEmpty()

        // Filter out link-local, loopback, or invalid addresses (e.g. fe80::1 which causes EINVAL on UDP connect)
        val validServers = dnsServers.filter { addr ->
            !addr.isLinkLocalAddress &&
            !addr.isLoopbackAddress &&
            !addr.isAnyLocalAddress &&
            !(addr.hostAddress?.substringBefore('%')?.startsWith("fe80", ignoreCase = true) ?: false)
        }

        // Prefer IPv4 DNS (e.g. 192.168.1.1, 8.8.8.8)
        val ipv4Dns = validServers.firstOrNull { addr ->
            val host = addr.hostAddress?.substringBefore('%').orEmpty()
            host.isNotEmpty() && !host.contains(':')
        }
        if (ipv4Dns != null) {
            val clean = ipv4Dns.hostAddress?.substringBefore('%') ?: return "1.1.1.1:53"
            return "$clean:53"
        }

        // Fallback to valid global IPv6 DNS if present
        val ipv6Dns = validServers.firstOrNull { addr ->
            val host = addr.hostAddress?.substringBefore('%').orEmpty()
            host.isNotEmpty() && host.contains(':')
        }
        if (ipv6Dns != null) {
            val clean = ipv6Dns.hostAddress?.substringBefore('%') ?: return "1.1.1.1:53"
            return "[$clean]:53"
        }

        // Default fallback: Cloudflare Public DNS
        return "1.1.1.1:53"
    }

    private fun Context.readPairingEnabled(): Boolean =
        getPrefBoolean(PREF_PAIRING_ENABLED, true)
}
