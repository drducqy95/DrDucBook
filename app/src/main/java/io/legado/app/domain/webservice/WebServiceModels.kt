package io.legado.app.domain.webservice

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.net.URI

object WebServicePorts {
    const val DEFAULT_HTTP_PORT = 1124
    const val DEFAULT_WEB_SOCKET_PORT = 1125
    const val LEGACY_HTTP_PORT = 1122
    const val LEGACY_WEB_SOCKET_PORT = 1123
    const val MIN_HTTP_PORT = 1024
    const val MAX_HTTP_PORT = 65534

    fun normalizeHttpPort(port: Int): Int =
        if (port in MIN_HTTP_PORT..MAX_HTTP_PORT) port else DEFAULT_HTTP_PORT

    fun webSocketPortFor(httpPort: Int): Int =
        normalizeHttpPort(httpPort) + 1

    fun suggestHttpPort(
        preferredPort: Int,
        isAvailable: (Int) -> Boolean,
    ): Int {
        val normalized = normalizeHttpPort(preferredPort)
        if (isPortPairAvailable(normalized, isAvailable)) return normalized

        var candidate = normalized + 2
        while (candidate <= MAX_HTTP_PORT) {
            if (isPortPairAvailable(candidate, isAvailable)) return candidate
            candidate += 2
        }

        candidate = MIN_HTTP_PORT
        while (candidate < normalized) {
            if (isPortPairAvailable(candidate, isAvailable)) return candidate
            candidate += 2
        }

        return normalized
    }

    private fun isPortPairAvailable(
        httpPort: Int,
        isAvailable: (Int) -> Boolean,
    ): Boolean = httpPort in MIN_HTTP_PORT..MAX_HTTP_PORT &&
        isAvailable(httpPort) &&
        isAvailable(httpPort + 1)
}

object WebServiceLegacyContract {
    object Http {
        const val SAVE_BOOK_SOURCE = "/saveBookSource"
        const val SAVE_BOOK_SOURCES = "/saveBookSources"
        const val DELETE_BOOK_SOURCES = "/deleteBookSources"
        const val SAVE_BOOK = "/saveBook"
        const val DELETE_BOOK = "/deleteBook"
        const val SAVE_BOOK_PROGRESS = "/saveBookProgress"
        const val ADD_LOCAL_BOOK = "/addLocalBook"
        const val SAVE_READ_CONFIG = "/saveReadConfig"
        const val SAVE_RSS_SOURCE = "/saveRssSource"
        const val SAVE_RSS_SOURCES = "/saveRssSources"
        const val DELETE_RSS_SOURCES = "/deleteRssSources"
        const val SAVE_REPLACE_RULE = "/saveReplaceRule"
        const val DELETE_REPLACE_RULE = "/deleteReplaceRule"
        const val TEST_REPLACE_RULE = "/testReplaceRule"
        const val GET_BOOK_SOURCE = "/getBookSource"
        const val GET_BOOK_SOURCES = "/getBookSources"
        const val GET_BOOKSHELF = "/getBookshelf"
        const val GET_CHAPTER_LIST = "/getChapterList"
        const val REFRESH_TOC = "/refreshToc"
        const val GET_BOOK_CONTENT = "/getBookContent"
        const val COVER = "/cover"
        const val IMAGE = "/image"
        const val GET_READ_CONFIG = "/getReadConfig"
        const val GET_RSS_SOURCE = "/getRssSource"
        const val GET_RSS_SOURCES = "/getRssSources"
        const val GET_REPLACE_RULES = "/getReplaceRules"
    }

    object WebSocket {
        const val BOOK_SOURCE_DEBUG = "/bookSourceDebug"
        const val RSS_SOURCE_DEBUG = "/rssSourceDebug"
        const val SEARCH_BOOK = "/searchBook"
    }

    data class Route(
        val method: String,
        val path: String,
    )

    val postRoutes = listOf(
        Http.SAVE_BOOK_SOURCE,
        Http.SAVE_BOOK_SOURCES,
        Http.DELETE_BOOK_SOURCES,
        Http.SAVE_BOOK,
        Http.DELETE_BOOK,
        Http.SAVE_BOOK_PROGRESS,
        Http.ADD_LOCAL_BOOK,
        Http.SAVE_READ_CONFIG,
        Http.SAVE_RSS_SOURCE,
        Http.SAVE_RSS_SOURCES,
        Http.DELETE_RSS_SOURCES,
        Http.SAVE_REPLACE_RULE,
        Http.DELETE_REPLACE_RULE,
        Http.TEST_REPLACE_RULE,
    )

    val getRoutes = listOf(
        Http.GET_BOOK_SOURCE,
        Http.GET_BOOK_SOURCES,
        Http.GET_BOOKSHELF,
        Http.GET_CHAPTER_LIST,
        Http.REFRESH_TOC,
        Http.GET_BOOK_CONTENT,
        Http.COVER,
        Http.IMAGE,
        Http.GET_READ_CONFIG,
        Http.GET_RSS_SOURCE,
        Http.GET_RSS_SOURCES,
        Http.GET_REPLACE_RULES,
    )

    val webSocketRoutes = listOf(
        WebSocket.BOOK_SOURCE_DEBUG,
        WebSocket.RSS_SOURCE_DEBUG,
        WebSocket.SEARCH_BOOK,
    )

    val httpRoutes: List<Route> =
        postRoutes.map { Route("POST", it) } + getRoutes.map { Route("GET", it) }

    val returnDataKeys = listOf("isSuccess", "errorMsg", "data")

    fun hasNoV2Collision(v2Paths: Iterable<String>): Boolean {
        val legacyPaths = (postRoutes + getRoutes + webSocketRoutes).toSet()
        return v2Paths.none { it in legacyPaths }
    }
}

data class WebServiceInstanceResponse(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val instanceId: String,
    val apiVersion: Int,
    val legacyApiVersion: Int,
    val httpPort: Int,
    val webSocketPort: Int,
    val legacyHttpPort: Int,
    val legacyWebSocketPort: Int,
    val requiresPairing: Boolean,
    val pairingCodeTtlMillis: Long,
    val sessionTtlMillis: Long,
)

data class WebServiceDiscoveryBook(
    val bookUrl: String,
    val origin: String,
    val originName: String,
    val type: Int,
    val name: String,
    val author: String,
    val kind: String? = null,
    val coverUrl: String? = null,
    val intro: String? = null,
    val latestChapterTitle: String? = null,
    val tocUrl: String = "",
    val time: Long = 0,
    val originOrder: Int = 0,
    val chapterWordCountText: String? = null,
    val chapterWordCount: Int = -1,
    val respondTime: Int = -1,
)

data class WebServiceDiscoveryResponse(
    val items: List<WebServiceDiscoveryBook>,
    val sourceErrors: Int,
    val refreshedAt: Long,
)

data class WebServiceBookImportResponse(
    val fileName: String,
    val imported: Boolean,
)

data class WebServiceMediaSessionRequest(
    val bookUrl: String = "",
    val chapterIndex: Int? = null,
)

data class WebServiceMediaVariant(
    val id: String,
    val title: String,
    val contentKind: String,
    val protocol: String,
    val mimeType: String,
    val playbackUrl: String,
    val externalPlayerRequired: Boolean,
    val drmUnsupported: Boolean,
    val durationMs: Long? = null,
)

data class WebServiceMediaTrack(
    val id: String,
    val label: String,
    val language: String,
    val mimeType: String,
    val playbackUrl: String,
    val isDefault: Boolean,
)

data class WebServiceMediaChapter(
    val index: Int,
    val title: String,
    val isOffline: Boolean,
)

data class WebServiceMediaSessionResponse(
    val sessionId: String,
    val expiresAt: Long,
    val bookUrl: String,
    val bookTitle: String,
    val coverUrl: String?,
    val chapterIndex: Int,
    val chapterCount: Int,
    val previousChapterIndex: Int?,
    val nextChapterIndex: Int?,
    val isVideo: Boolean,
    val chapters: List<WebServiceMediaChapter>,
    val variants: List<WebServiceMediaVariant>,
    val subtitles: List<WebServiceMediaTrack>,
    val audioTracks: List<WebServiceMediaTrack>,
)

class WebServicePairingChallenge(
    val code: String,
    val expiresAt: Long,
) {
    override fun toString(): String =
        "WebServicePairingChallenge(code=<redacted>, expiresAt=$expiresAt)"
}

class WebServiceSession(
    val token: String,
    val createdAt: Long,
    val expiresAt: Long,
) {
    override fun toString(): String =
        "WebServiceSession(token=<redacted>, createdAt=$createdAt, expiresAt=$expiresAt)"
}

data class WebServicePairingExchangeRequest(
    val code: String? = null,
    val pairingCode: String? = null,
)

data class WebServicePairingExchangeResponse(
    val tokenType: String = WebServicePairingBroker.AUTH_SCHEME,
    val sessionToken: String,
    val expiresAt: Long,
)

data class WebServiceSessionStatusResponse(
    val active: Boolean,
    val expiresAt: Long? = null,
)

data class WebServiceErrorResponse(
    val error: String,
)

data class WebServicePolicy(
    val exportEnabled: Boolean = false,
    val autoTranslationEnabled: Boolean = true,
    val backgroundAssetId: String? = null,
    val backgroundFit: String = WebServiceBackgroundPolicy.DEFAULT_FIT,
    val backgroundPosition: String = WebServiceBackgroundPolicy.DEFAULT_POSITION,
    val backgroundDim: Float = WebServiceBackgroundPolicy.DEFAULT_DIM,
    val backgroundBlur: Int = WebServiceBackgroundPolicy.DEFAULT_BLUR,
    val revision: Long = 1L,
    val updatedAt: Long = 0L,
) {
    val etag: String
        get() = WebServicePolicyRevision.etag(revision)

    fun sanitized(): WebServicePolicy =
        copy(
            backgroundAssetId = WebServiceBackgroundPolicy.normalizeAssetId(backgroundAssetId),
            backgroundFit = WebServiceBackgroundPolicy.normalizeFit(backgroundFit),
            backgroundPosition = WebServiceBackgroundPolicy.normalizePosition(backgroundPosition),
            backgroundDim = WebServiceBackgroundPolicy.normalizeDim(backgroundDim),
            backgroundBlur = WebServiceBackgroundPolicy.normalizeBlur(backgroundBlur),
            revision = revision.takeIf { it > 0L } ?: 1L,
        )

    fun toResponse(): WebServicePolicyResponse {
        val clean = sanitized()
        return WebServicePolicyResponse(
            exportEnabled = clean.exportEnabled,
            autoTranslationEnabled = clean.autoTranslationEnabled,
            backgroundAssetId = clean.backgroundAssetId,
            backgroundFit = clean.backgroundFit,
            backgroundPosition = clean.backgroundPosition,
            backgroundDim = clean.backgroundDim,
            backgroundBlur = clean.backgroundBlur,
            revision = clean.revision,
            updatedAt = clean.updatedAt,
            etag = clean.etag,
        )
    }
}

data class WebServicePolicyResponse(
    val exportEnabled: Boolean,
    val autoTranslationEnabled: Boolean,
    val backgroundAssetId: String?,
    val backgroundFit: String,
    val backgroundPosition: String,
    val backgroundDim: Float,
    val backgroundBlur: Int,
    val revision: Long,
    val updatedAt: Long,
    val etag: String,
)

data class WebServicePolicyPatchRequest(
    val exportEnabled: Boolean? = null,
    val autoTranslationEnabled: Boolean? = null,
    val backgroundAssetId: String? = null,
    val clearBackgroundAsset: Boolean? = null,
    val backgroundFit: String? = null,
    val backgroundPosition: String? = null,
    val backgroundDim: Float? = null,
    val backgroundBlur: Int? = null,
)

data class WebServiceBackgroundAssetResponse(
    val assetId: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val etag: String,
)

data class WebServiceBackgroundUploadResponse(
    val asset: WebServiceBackgroundAssetResponse,
    val policy: WebServicePolicyResponse,
)

data class WebServiceExportSourcesRequest(
    val sourceType: String? = null,
    val sourceKeys: List<String>? = null,
    val payloadJson: String? = null,
)

data class WebServiceExportBookshelfRequest(
    val bookUrls: List<String>? = null,
)

data class WebServiceExportChapterRequest(
    val bookUrl: String? = null,
    val chapterIndex: Int? = null,
)

data class WebServiceExportBookTextRequest(
    val bookUrl: String? = null,
    val chapterIndices: List<Int>? = null,
)

data class WebServiceTranslationJobRequest(
    val bookUrl: String? = null,
    val chapterIndex: Int? = null,
    val forceRetranslate: Boolean = false,
    val provider: String? = null,
    val targetLanguage: String? = null,
)

data class WebServiceTranslationProviderResponse(
    val id: String,
    val name: String,
    val targetLanguages: List<String>,
)

data class WebServiceTranslationProviderListResponse(
    val providers: List<WebServiceTranslationProviderResponse>,
    val defaultProvider: String,
    val defaultTargetLanguage: String,
)

fun buildWebServiceTranslationProviderList(
    providerValues: List<String>,
    providerDisplayNames: List<String>,
    defaultProvider: String,
    defaultTargetLanguage: String,
    targetLanguagesForProvider: (String) -> List<String>,
): WebServiceTranslationProviderListResponse = WebServiceTranslationProviderListResponse(
    providers = providerValues.distinct().mapIndexed { index, provider ->
        WebServiceTranslationProviderResponse(
            id = provider,
            name = providerDisplayNames.getOrElse(index) { provider },
            targetLanguages = targetLanguagesForProvider(provider).distinct(),
        )
    },
    defaultProvider = defaultProvider,
    defaultTargetLanguage = defaultTargetLanguage,
)

data class WebServiceTranslationContentResponse(
    val bookUrl: String,
    val chapterIndex: Int,
    val content: String? = null,
    val provider: String? = null,
    val targetLanguage: String,
    val updatedAt: Long = 0L,
)

data class WebServiceTranslationJobResponse(
    val jobId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val provider: String,
    val targetLanguage: String,
    val status: String,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val progress: Float = 0f,
    val content: String? = null,
    val preview: String? = null,
    val error: String? = null,
    val updatedAt: Long,
)

data class WebServiceTranslationJobListResponse(
    val jobs: List<WebServiceTranslationJobResponse>,
)

data class WebServiceTtsSynthesisRequest(
    val text: String = "",
    val language: String? = null,
)

data class WebServiceTtsCapabilitiesResponse(
    val enabled: Boolean,
    val engine: String,
    val language: String,
)

data class WebServiceTtsSynthesisResponse(
    val audioUrl: String,
    val engine: String,
    val language: String,
    val expiresAt: Long,
)

object WebServiceExportRequests {
    const val SOURCE_TYPE_BOOK = "book"
    const val SOURCE_TYPE_RSS = "rss"

    fun normalizeSourceType(sourceType: String?): String =
        when (sourceType?.trim()?.lowercase()) {
            SOURCE_TYPE_RSS, "rsssource", "rss_source" -> SOURCE_TYPE_RSS
            else -> SOURCE_TYPE_BOOK
        }

    fun normalizedKeys(keys: List<String>?): Set<String> =
        keys
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    fun normalizedChapterIndices(indices: List<Int>?): Set<Int> =
        indices
            .orEmpty()
            .filter { it >= 0 }
            .toSet()
}

object WebServiceTranslationJobs {
    const val STATUS_IDLE = "idle"
    const val STATUS_TRANSLATING = "translating"
    const val STATUS_TRANSLATED = "translated"
    const val STATUS_FAILED = "failed"
    const val STATUS_CANCELLED = "cancelled"

    fun progress(
        currentChunk: Int,
        totalChunks: Int,
    ): Float {
        if (totalChunks <= 0) return 0f
        return currentChunk
            .coerceIn(0, totalChunks)
            .toFloat() / totalChunks.toFloat()
    }

    fun normalizedOptionalText(value: String?): String? =
        value
            ?.trim()
            ?.takeIf(String::isNotBlank)

    fun normalizedChapterIndex(value: String?): Int? =
        value
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }
}

sealed interface WebServicePolicyPatchResult {
    data class Success(val policy: WebServicePolicy) : WebServicePolicyPatchResult
    data class Conflict(val current: WebServicePolicy) : WebServicePolicyPatchResult
    data object PreconditionRequired : WebServicePolicyPatchResult
}

object WebServicePolicyRevision {
    fun etag(revision: Long): String =
        "\"web-policy-$revision\""

    fun applyPatch(
        current: WebServicePolicy,
        request: WebServicePolicyPatchRequest,
        ifMatch: String?,
        now: Long,
    ): WebServicePolicyPatchResult {
        val cleanCurrent = current.sanitized()
        if (ifMatch.isNullOrBlank()) return WebServicePolicyPatchResult.PreconditionRequired
        if (!matches(ifMatch, cleanCurrent.revision)) {
            return WebServicePolicyPatchResult.Conflict(cleanCurrent)
        }
        return WebServicePolicyPatchResult.Success(
            cleanCurrent.copy(
                exportEnabled = request.exportEnabled ?: cleanCurrent.exportEnabled,
                autoTranslationEnabled = request.autoTranslationEnabled ?: cleanCurrent.autoTranslationEnabled,
                backgroundAssetId = when {
                    request.clearBackgroundAsset == true -> null
                    request.backgroundAssetId != null ->
                        WebServiceBackgroundPolicy.normalizeAssetId(request.backgroundAssetId)
                            ?: cleanCurrent.backgroundAssetId
                    else -> cleanCurrent.backgroundAssetId
                },
                backgroundFit = request.backgroundFit
                    ?.let { WebServiceBackgroundPolicy.normalizeFitOrNull(it) ?: cleanCurrent.backgroundFit }
                    ?: cleanCurrent.backgroundFit,
                backgroundPosition = request.backgroundPosition
                    ?.let {
                        WebServiceBackgroundPolicy.normalizePositionOrNull(it)
                            ?: cleanCurrent.backgroundPosition
                    }
                    ?: cleanCurrent.backgroundPosition,
                backgroundDim = request.backgroundDim
                    ?.let(WebServiceBackgroundPolicy::normalizeDim)
                    ?: cleanCurrent.backgroundDim,
                backgroundBlur = request.backgroundBlur
                    ?.let(WebServiceBackgroundPolicy::normalizeBlur)
                    ?: cleanCurrent.backgroundBlur,
                revision = cleanCurrent.revision + 1L,
                updatedAt = now,
            )
        )
    }

    fun matches(ifMatch: String, revision: Long): Boolean {
        val expected = etag(revision)
        return ifMatch
            .split(',')
            .map(String::trim)
            .any { tag -> tag == expected || tag == "*" }
    }
}

object WebServiceBackgroundPolicy {
    const val DEFAULT_FIT = "cover"
    const val DEFAULT_POSITION = "center"
    const val DEFAULT_DIM = 0.22f
    const val DEFAULT_BLUR = 0
    const val MAX_DIM = 0.75f
    const val MAX_BLUR = 24

    private val assetIdRegex = Regex("^[a-f0-9]{64}\\.png$")
    private val supportedFit = setOf("cover", "contain")
    private val supportedPosition = setOf(
        "center",
        "top",
        "bottom",
        "left",
        "right",
        "left top",
        "left bottom",
        "right top",
        "right bottom",
    )

    fun normalizeAssetId(assetId: String?): String? =
        assetId
            ?.trim()
            ?.lowercase()
            ?.takeIf(assetIdRegex::matches)

    fun normalizeFit(fit: String?): String =
        normalizeFitOrNull(fit) ?: DEFAULT_FIT

    fun normalizeFitOrNull(fit: String?): String? =
        fit
            ?.trim()
            ?.lowercase()
            ?.takeIf(supportedFit::contains)

    fun normalizePosition(position: String?): String =
        normalizePositionOrNull(position) ?: DEFAULT_POSITION

    fun normalizePositionOrNull(position: String?): String? =
        position
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf(supportedPosition::contains)

    fun normalizeDim(dim: Float): Float =
        if (dim.isNaN()) DEFAULT_DIM else dim.coerceIn(0f, MAX_DIM)

    fun normalizeBlur(blur: Int): Int =
        blur.coerceIn(0, MAX_BLUR)
}

object WebServiceOriginPolicy {
    fun isSameOrigin(
        origin: String?,
        hostHeader: String?,
        trustedExternalUrl: String? = null,
    ): Boolean {
        if (origin.isNullOrBlank()) return true
        val originAuthority = urlAuthority(origin) ?: return false
        val requestAuthority = hostAuthority(
            hostHeader = hostHeader,
            defaultPort = originAuthority.defaultPort,
        )
        if (requestAuthority == originAuthority.hostAndPort) return true
        return trustedExternalUrl
            ?.let(::urlAuthority)
            ?.hostAndPort == originAuthority.hostAndPort
    }

    fun isExternalRequest(
        origin: String?,
        hostHeader: String?,
        externalUrl: String?,
    ): Boolean {
        val externalAuthority = externalUrl?.let(::urlAuthority) ?: return false
        val originMatches = origin
            ?.let(::urlAuthority)
            ?.hostAndPort == externalAuthority.hostAndPort
        val hostMatches = hostAuthority(
            hostHeader = hostHeader,
            defaultPort = externalAuthority.defaultPort,
        ) == externalAuthority.hostAndPort
        return originMatches || hostMatches
    }

    private fun urlAuthority(value: String): UrlAuthority? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        return UrlAuthority(
            hostAndPort = HostAndPort(
                host = host,
                port = if (uri.port >= 0) uri.port else defaultPort,
            ),
            defaultPort = defaultPort,
        )
    }

    private fun hostAuthority(
        hostHeader: String?,
        defaultPort: Int,
    ): HostAndPort? {
        val host = hostHeader?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { URI("//$host") }.getOrNull() ?: return null
        return HostAndPort(
            host = uri.host?.lowercase() ?: return null,
            port = if (uri.port >= 0) uri.port else defaultPort,
        )
    }

    private data class UrlAuthority(
        val hostAndPort: HostAndPort,
        val defaultPort: Int,
    )

    private data class HostAndPort(
        val host: String,
        val port: Int,
    )
}

object WebServiceRequestPolicy {
    fun requiresPairing(
        tunnelRequiresPairing: Boolean,
        origin: String?,
        hostHeader: String?,
        cloudflareRay: String?,
        cloudflareConnectingIp: String?,
        publicUrl: String?,
    ): Boolean {
        if (!tunnelRequiresPairing) return false
        if (!cloudflareRay.isNullOrBlank() || !cloudflareConnectingIp.isNullOrBlank()) {
            return true
        }
        return WebServiceOriginPolicy.isExternalRequest(
            origin = origin,
            hostHeader = hostHeader,
            externalUrl = publicUrl,
        )
    }
}

sealed interface WebServicePairingExchangeResult {
    data class Success(val session: WebServiceSession) : WebServicePairingExchangeResult
    data object MissingCode : WebServicePairingExchangeResult
    data object InvalidCode : WebServicePairingExchangeResult
    data object Expired : WebServicePairingExchangeResult
}

class WebServicePairingBroker(
    private val now: () -> Long = System::currentTimeMillis,
    private val codeGenerator: () -> String = ::generatePairingCode,
    private val tokenGenerator: () -> String = ::generateSessionToken,
    private val challengeTtlMillis: Long = DEFAULT_CHALLENGE_TTL_MILLIS,
    private val sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MILLIS,
) {
    private var challenge: WebServicePairingChallenge? = null
    private val sessions = linkedMapOf<String, WebServiceSession>()

    @Synchronized
    fun createChallenge(): WebServicePairingChallenge {
        currentChallenge()?.let { return it }
        val createdAt = now()
        return WebServicePairingChallenge(
            code = normalizeCode(codeGenerator()) ?: generatePairingCode(),
            expiresAt = createdAt + challengeTtlMillis,
        ).also { challenge = it }
    }

    @Synchronized
    fun currentChallenge(): WebServicePairingChallenge? {
        val current = challenge ?: return null
        return if (current.expiresAt > now()) {
            current
        } else {
            challenge = null
            null
        }
    }

    @Synchronized
    fun exchange(code: String?): WebServicePairingExchangeResult {
        val normalized = normalizeCode(code) ?: return WebServicePairingExchangeResult.MissingCode
        val current = challenge ?: return WebServicePairingExchangeResult.InvalidCode
        if (current.expiresAt <= now()) {
            challenge = null
            return WebServicePairingExchangeResult.Expired
        }
        if (current.code != normalized) {
            return WebServicePairingExchangeResult.InvalidCode
        }
        challenge = null
        pruneExpiredSessions()
        val createdAt = now()
        val session = WebServiceSession(
            token = tokenGenerator(),
            createdAt = createdAt,
            expiresAt = createdAt + sessionTtlMillis,
        )
        sessions[session.token] = session
        return WebServicePairingExchangeResult.Success(session)
    }

    @Synchronized
    fun sessionFromAuthorization(authorization: String?): WebServiceSession? {
        val token = bearerToken(authorization) ?: return null
        pruneExpiredSessions()
        return sessions[token]?.takeIf { it.expiresAt > now() }
    }

    @Synchronized
    fun revoke(authorization: String?): Boolean {
        val token = bearerToken(authorization) ?: return false
        return sessions.remove(token) != null
    }

    @Synchronized
    fun revokeAll() {
        challenge = null
        sessions.clear()
    }

    private fun pruneExpiredSessions() {
        val timestamp = now()
        sessions.entries.removeAll { it.value.expiresAt <= timestamp }
    }

    private fun normalizeCode(code: String?): String? =
        code
            ?.filter(Char::isDigit)
            ?.takeIf { it.length == PAIRING_CODE_LENGTH }

    private fun bearerToken(authorization: String?): String? {
        val value = authorization?.trim().orEmpty()
        if (!value.startsWith("$AUTH_SCHEME ", ignoreCase = true)) return null
        return value.substringAfter(' ').takeIf(String::isNotBlank)
    }

    companion object {
        const val AUTH_SCHEME = "Bearer"
        const val DEFAULT_CHALLENGE_TTL_MILLIS = 5L * 60L * 1000L
        const val DEFAULT_SESSION_TTL_MILLIS = 12L * 60L * 60L * 1000L
        private const val PAIRING_CODE_LENGTH = 6
        private val random = SecureRandom()

        private fun generatePairingCode(): String =
            random.nextInt(1_000_000).toString().padStart(PAIRING_CODE_LENGTH, '0')

        private fun generateSessionToken(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

object WebServicePairingCenter {
    private val broker = WebServicePairingBroker()

    fun createChallenge(): WebServicePairingChallenge =
        broker.createChallenge()

    fun currentChallenge(): WebServicePairingChallenge? =
        broker.currentChallenge()

    fun exchange(code: String?): WebServicePairingExchangeResult =
        broker.exchange(code)

    fun sessionFromAuthorization(authorization: String?): WebServiceSession? =
        broker.sessionFromAuthorization(authorization)

    fun revoke(authorization: String?): Boolean =
        broker.revoke(authorization)

    fun revokeAll() =
        broker.revokeAll()
}

fun newWebServiceInstanceId(): String =
    UUID.randomUUID().toString()
