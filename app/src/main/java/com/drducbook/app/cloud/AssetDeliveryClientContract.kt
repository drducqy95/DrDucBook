package com.drducbook.app.cloud

import io.legado.app.domain.model.AssetDeliveryCatalog
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class AssetDeliveryReference(
    val kind: AssetDeliveryKind,
    val id: String,
)

enum class AssetDeliveryKind {
    DOWNLOAD,
    CATALOG,
}

data class AssetFunctionRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

object AssetDeliveryClientContract {

    fun parseInternalUri(rawUrl: String): AssetDeliveryReference? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != AssetDeliveryCatalog.assetUriScheme) return null
        val kind = when (uri.host) {
            "download" -> AssetDeliveryKind.DOWNLOAD
            "catalog" -> AssetDeliveryKind.CATALOG
            else -> return null
        }
        val id = uri.path.trim('/').takeIf(String::isNotBlank) ?: return null
        requireValidAssetId(id)
        return AssetDeliveryReference(kind = kind, id = id)
    }

    fun buildTicketRequest(
        config: SupabasePublicConfig,
        artifactId: String,
        supabaseAccessToken: String,
    ): AssetFunctionRequest {
        require(config.isConfigured) { "Supabase asset delivery is not configured" }
        require(supabaseAccessToken.isNotBlank()) { "Supabase session is required for asset delivery" }
        config.requireValid()
        val id = requireValidAssetId(artifactId)
        return AssetFunctionRequest(
            method = "POST",
            url = functionUrl(config, "asset-ticket"),
            headers = mapOf(
                "authorization" to "Bearer $supabaseAccessToken",
                "content-type" to "application/json",
            ),
            body = """{"artifactId":"$id"}""",
        )
    }

    fun buildDownloadRequest(
        config: SupabasePublicConfig,
        artifactId: String,
        ticket: String,
        rangeHeader: String? = null,
    ): AssetFunctionRequest {
        require(config.isConfigured) { "Supabase asset delivery is not configured" }
        require(ticket.isNotBlank()) { "Asset ticket is required" }
        config.requireValid()
        val id = requireValidAssetId(artifactId)
        val query = "artifactId=${urlEncode(id)}"
        val headers = buildMap {
            put("x-drducbook-asset-ticket", ticket)
            rangeHeader?.takeIf(String::isNotBlank)?.let { put("range", it) }
        }
        return AssetFunctionRequest(
            method = "GET",
            url = "${functionUrl(config, "asset-download")}?$query",
            headers = headers,
        )
    }

    fun containsClientSecret(request: AssetFunctionRequest): Boolean {
        val haystack = buildString {
            append(request.url).append('\n')
            request.body?.let(::append)
            request.headers.forEach { (key, value) ->
                append('\n').append(key).append(':').append(value)
            }
        }
        return secretPatterns.any { it.containsMatchIn(haystack) }
    }

    private fun functionUrl(config: SupabasePublicConfig, functionName: String): String =
        "${config.url.trimEnd('/')}/functions/v1/$functionName"

    private fun requireValidAssetId(assetId: String): String {
        val normalized = assetId.trim()
        require(assetIdRegex.matches(normalized)) { "Invalid asset id" }
        return normalized
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private val assetIdRegex = Regex("^[a-z0-9][a-z0-9._-]{1,127}$")
    private val secretPatterns = listOf(
        Regex("hf_[A-Za-z0-9]{30,}"),
        Regex("sb_secret_[A-Za-z0-9_\\-.]{10,}"),
        Regex("service_role", RegexOption.IGNORE_CASE),
    )
}
