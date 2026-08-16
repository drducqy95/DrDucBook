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
    private val quickUrlPattern = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _state = MutableStateFlow(CloudflareTunnelState())
    private var process: Process? = null
    private var tokenFile: File? = null

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
        stop()
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
        stop()
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

    fun stop() {
        val stopped = synchronized(lock) {
            process.also { process = null }
        }
        stopped?.destroy()
        tokenFile?.delete()
        tokenFile = null
        WebServicePairingCenter.revokeAll()
        _state.value = CloudflareTunnelState(pairingEnabled = _state.value.pairingEnabled)
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
            val started = runCatching {
                ProcessBuilder(command(binary))
                    .redirectErrorStream(true)
                    .apply {
                        environment()["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
                        androidDnsServer(context)?.let { dns ->
                            environment()["CLOUDFLARED_ANDROID_DNS"] = dns
                        }
                    }
                    .start()
            }.getOrElse { error ->
                fail(error.localizedMessage ?: error.javaClass.simpleName)
                return@launch
            }
            synchronized(lock) { process = started }
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> handleOutput(started, line) }
                }
                val exitCode = started.waitFor()
                synchronized(lock) {
                    if (process === started) {
                        process = null
                        fail("Cloudflare Tunnel stopped (code $exitCode).")
                    }
                }
            }.onFailure { error ->
                synchronized(lock) {
                    if (process === started) {
                        process = null
                        fail(error.localizedMessage ?: "Cloudflare Tunnel stopped.")
                    }
                }
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
            _state.value = _state.value.copy(
                phase = CloudflareTunnelPhase.CONNECTED,
                publicUrl = quickUrl ?: _state.value.publicUrl,
                detail = "Connected through Cloudflare.",
            )
        }
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

    private fun androidDnsServer(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return null
        val address = connectivity.getLinkProperties(network)
            ?.dnsServers
            ?.firstOrNull()
            ?.hostAddress
            ?: return null
        return if (address.contains(':')) "[$address]:53" else "$address:53"
    }

    private fun Context.readPairingEnabled(): Boolean =
        getPrefBoolean(PREF_PAIRING_ENABLED, true)
}
