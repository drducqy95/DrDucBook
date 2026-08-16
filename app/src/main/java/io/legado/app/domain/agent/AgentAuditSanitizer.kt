package io.legado.app.domain.agent

fun String.sanitizeForAgentAudit(maxChars: Int = DEFAULT_AGENT_AUDIT_CHARS): String {
    return replace(JSON_SECRET_FIELD) { match ->
        match.groupValues[1] + REDACTED + match.groupValues[2]
    }
        .replace(AUTHORIZATION_SECRET) { match -> "${match.groupValues[1]} $REDACTED" }
        .replace(QUERY_SECRET) { match -> "${match.groupValues[1]}=$REDACTED" }
        .replace(KNOWN_TOKEN_SECRET, REDACTED)
        .take(maxChars.coerceAtLeast(0))
}

private const val DEFAULT_AGENT_AUDIT_CHARS = 4_000
private const val REDACTED = "[REDACTED]"
private val JSON_SECRET_FIELD = Regex(
    "(?i)(\"(?:api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|cookie|password|secret)\"\\s*:\\s*\")[^\"]*(\")"
)
private val AUTHORIZATION_SECRET = Regex("(?i)\\b(Bearer|Token)\\s+[A-Za-z0-9._~+/-]+=*")
private val QUERY_SECRET = Regex(
    "(?i)\\b(key|api[_-]?key|token|access[_-]?token|cookie|password|secret)=([^&\\s]+)"
)
private val KNOWN_TOKEN_SECRET = Regex(
    "(?i)\\b(?:sk-[a-z0-9_-]{16,}|ghp_[a-z0-9]{16,}|AIza[a-z0-9_-]{20,})"
)
