package io.legado.app.domain.model

enum class BookSourceHealthStatus {
    HEALTHY,
    DEGRADED,
    AUTH_REQUIRED,
    CAPTCHA_REQUIRED,
    RATE_LIMITED,
    BROKEN_RULE,
    NETWORK_ERROR,
    TLS_ERROR,
    CONTENT_EMPTY,
    MEDIA_ERROR,
    UNSUPPORTED,
    STALE,
    HTTP_ERROR,
    UNKNOWN_OFFLINE,
}

data class BookSourceHealthRow(
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val sourceType: SourceKeyType = SourceKeyType.BOOK,
    val homeUrl: String? = null,
    val loginUrl: String? = null,
    val iconPath: String? = null,
    val isVbook: Boolean = false,
    val enabled: Boolean,
    val enabledExplore: Boolean,
    val hasLoginUrl: Boolean = !loginUrl.isNullOrBlank(),
    val health: io.legado.app.data.entities.BookSourceHealth?,
)

data class BookSourceProbeEvidence(
    val status: BookSourceHealthStatus,
    val latencyMs: Long,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
)

internal fun redactBookSourceDiagnostic(message: String?): String? {
    val normalized = message
        ?.replace(Regex("(?i)bearer\\s+[a-z0-9._~+\\-/]+=*"), "Bearer <redacted>")
        ?.replace(
            Regex("(?i)(authorization|cookie|token|api[-_ ]?key)\\s*[:=]\\s*[^\\s,;]+")
        ) { match -> "${match.groupValues[1]}=<redacted>" }
        ?.trim()
        ?.take(512)
    return normalized?.takeIf(String::isNotBlank)
}

fun nextBookSourceFailureCount(
    previousFailures: Int,
    status: BookSourceHealthStatus,
): Int = if (
    status in setOf(
        BookSourceHealthStatus.HEALTHY,
        BookSourceHealthStatus.DEGRADED,
        BookSourceHealthStatus.UNKNOWN_OFFLINE,
        BookSourceHealthStatus.STALE,
    )
) {
    0
} else {
    (previousFailures + 1).coerceAtMost(999)
}

internal fun classifyBookSourceProbeFailure(
    message: String?,
    latencyMs: Long,
): BookSourceProbeEvidence {
    val value = message.orEmpty().lowercase()
    val httpStatus = Regex("(?:http|status)\\s*[:=]?\\s*(\\d{3})")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val status = when {
        listOf("captcha", "cloudflare", "challenge", "verify code", "verification code")
            .any(value::contains) -> BookSourceHealthStatus.CAPTCHA_REQUIRED
        httpStatus in setOf(401, 403) ||
            listOf("unauthorized", "forbidden", "login", "dang nhap", "signin", "sign in")
                .any(value::contains) -> BookSourceHealthStatus.AUTH_REQUIRED
        httpStatus == 429 ||
            listOf("rate limit", "too many requests", "quota exceeded", "throttle")
                .any(value::contains) -> BookSourceHealthStatus.RATE_LIMITED
        listOf("ssl", "tls", "certificate", "certpath", "handshake", "trust anchor")
            .any(value::contains) -> BookSourceHealthStatus.TLS_ERROR
        listOf("offline", "no network", "airplane mode")
            .any(value::contains) -> BookSourceHealthStatus.UNKNOWN_OFFLINE
        listOf("unknownhost", "dns", "failed to connect", "connection reset", "timeout", "network")
            .any(value::contains) -> BookSourceHealthStatus.NETWORK_ERROR
        listOf("rule", "parse", "selector", "xpath", "js failed", "script")
            .any(value::contains) -> BookSourceHealthStatus.BROKEN_RULE
        listOf("empty", "no items", "no articles", "no chapters", "content")
            .any(value::contains) -> BookSourceHealthStatus.CONTENT_EMPTY
        listOf("media", "track", "variant", "m3u8", "mpd")
            .any(value::contains) -> BookSourceHealthStatus.MEDIA_ERROR
        listOf("unsupported", "not supported")
            .any(value::contains) -> BookSourceHealthStatus.UNSUPPORTED
        listOf("stale", "outdated")
            .any(value::contains) -> BookSourceHealthStatus.STALE
        else -> BookSourceHealthStatus.HTTP_ERROR
    }
    return BookSourceProbeEvidence(
        status = status,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = "search_or_explore",
        messageRedacted = redactBookSourceDiagnostic(message),
    )
}
