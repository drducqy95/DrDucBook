package io.legado.app.domain.webservice

data class WebServiceAddressCandidate(
    val interfaceName: String,
    val hostAddress: String,
    val isSiteLocal: Boolean,
)

object WebServiceAddressPolicy {
    private val mobileInterfacePrefixes = listOf("rmnet", "ccmni", "pdp", "wwan")
    private val lanInterfacePrefixes = listOf("wlan", "swlan", "eth", "en", "ap")
    private val vpnInterfacePrefixes = listOf("tun", "tap", "wg", "tailscale")

    fun orderedHosts(candidates: List<WebServiceAddressCandidate>): List<String> =
        candidates
            .filterNot { candidate ->
                mobileInterfacePrefixes.any(candidate.interfaceName.lowercase()::startsWith)
            }
            .filter { candidate ->
                candidate.isSiteLocal || vpnInterfacePrefixes.any(
                    candidate.interfaceName.lowercase()::startsWith
                )
            }
            .sortedBy { candidate ->
                val interfaceName = candidate.interfaceName.lowercase()
                when {
                    candidate.isSiteLocal && lanInterfacePrefixes.any(interfaceName::startsWith) -> 0
                    vpnInterfacePrefixes.any(interfaceName::startsWith) -> 1
                    else -> 2
                }
            }
            .map(WebServiceAddressCandidate::hostAddress)
            .distinct()
}
