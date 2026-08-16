package io.legado.app.domain.webservice

enum class CloudflareTunnelMode {
    OFF,
    QUICK,
    NAMED,
}

enum class CloudflareTunnelPhase {
    STOPPED,
    STARTING,
    CONNECTED,
    ERROR,
}

data class CloudflareTunnelState(
    val mode: CloudflareTunnelMode = CloudflareTunnelMode.OFF,
    val phase: CloudflareTunnelPhase = CloudflareTunnelPhase.STOPPED,
    val publicUrl: String = "",
    val pairingEnabled: Boolean = true,
    val pairingCode: String = "",
    val pairingExpiresAt: Long = 0L,
    val detail: String = "",
) {
    val requiresPairing: Boolean
        get() = mode != CloudflareTunnelMode.OFF &&
            pairingEnabled &&
            phase != CloudflareTunnelPhase.STOPPED &&
            phase != CloudflareTunnelPhase.ERROR
}

object CloudflareTunnelCommand {
    fun quick(binaryPath: String, localPort: Int): List<String> = listOf(
        binaryPath,
        "tunnel",
        "--no-autoupdate",
        "--protocol",
        "http2",
        "--edge-ip-version",
        "4",
        "--url",
        "http://127.0.0.1:$localPort",
    )

    fun named(binaryPath: String, tokenFilePath: String): List<String> = listOf(
        binaryPath,
        "tunnel",
        "--no-autoupdate",
        "--protocol",
        "http2",
        "--edge-ip-version",
        "4",
        "run",
        "--token-file",
        tokenFilePath,
    )

    fun normalizePublicUrl(value: String): String? {
        val normalized = value.trim().trimEnd('/')
        return normalized.takeIf {
            it.startsWith("https://") && it.length > "https://".length
        }
    }
}
