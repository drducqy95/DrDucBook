package io.legado.app.domain.model

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Locale

@Serializable
data class SourceKey(
    val type: SourceKeyType,
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "Source key id must not be blank" }
    }
}

@Serializable
enum class SourceKeyType {
    BOOK,
    RSS,
}

enum class SourceDomainMatchReason {
    DIRECT,
    SUBDOMAIN,
    OBSERVED_REDIRECT,
}

data class BrowserSourceContext(
    val key: SourceKey,
    val name: String,
    val group: String? = null,
    val sourceUrl: String,
    val homeUrl: String?,
    val loginUrl: String?,
    val iconPath: String? = null,
    val matchedUrl: String,
    val matchReason: SourceDomainMatchReason,
    val enabled: Boolean,
    val isVbook: Boolean = false,
)

data class SourceDomainEntry(
    val key: SourceKey,
    val name: String,
    val group: String? = null,
    val sourceUrl: String,
    val homeUrl: String? = null,
    val loginUrl: String? = null,
    val iconPath: String? = null,
    val enabled: Boolean = true,
    val isVbook: Boolean = false,
    val order: Int = 0,
    val matchUrls: List<String> = emptyList(),
    val observedRedirectUrls: List<String> = emptyList(),
) {
    fun preferredHomeUrl(): String? =
        sequenceOf(homeUrl, loginUrl, sourceUrl)
            .firstOrNull { it.toHttpUrlPartsOrNull() != null }
}

class SourceDomainIndex(
    entries: List<SourceDomainEntry> = emptyList(),
) {
    val entries: List<SourceDomainEntry> = entries.sortedWith(
        compareBy<SourceDomainEntry> { it.order }
            .thenBy { it.key.type.name }
            .thenBy { it.key.id }
    )

    private val rules: List<SourceDomainRule> = this.entries.flatMapIndexed { entryIndex, entry ->
        buildRules(entry, entryIndex)
    }

    fun match(
        url: String,
        preferredKey: SourceKey? = null,
        preferredSourceId: String? = null,
        enabledOnly: Boolean = true,
    ): BrowserSourceContext? {
        val target = url.toHttpUrlPartsOrNull() ?: return null
        return rules.asSequence()
            .filter { !enabledOnly || it.entry.enabled }
            .mapNotNull { rule ->
                val score = rule.score(target) ?: return@mapNotNull null
                val preferredBoost = when {
                    preferredKey != null && rule.entry.key == preferredKey -> PREFERRED_KEY_BOOST
                    preferredSourceId != null && rule.entry.sourceUrl == preferredSourceId ->
                        PREFERRED_KEY_BOOST
                    else -> 0
                }
                SourceDomainCandidate(
                    rule = rule,
                    score = score + preferredBoost,
                )
            }
            .maxWithOrNull(
                compareBy<SourceDomainCandidate> { it.score }
                    .thenBy { it.rule.pathPrefix.length }
                    .thenBy { -it.rule.entry.order }
                    .thenBy { -it.rule.entryIndex }
            )
            ?.let { candidate ->
                val entry = candidate.rule.entry
                BrowserSourceContext(
                    key = entry.key,
                    name = entry.name,
                    group = entry.group,
                    sourceUrl = entry.sourceUrl,
                    homeUrl = entry.preferredHomeUrl(),
                    loginUrl = entry.loginUrl,
                    iconPath = entry.iconPath,
                    matchedUrl = url,
                    matchReason = candidate.rule.reasonFor(target),
                    enabled = entry.enabled,
                    isVbook = entry.isVbook,
                )
            }
    }

    fun normalizedRuleKeys(): List<String> = rules.map {
        "${it.entry.key.type}:${it.entry.key.id}:${it.host}:${it.pathPrefix}:${it.redirect}"
    }

    companion object {
        fun empty(): SourceDomainIndex = SourceDomainIndex()
    }
}

private data class SourceDomainCandidate(
    val rule: SourceDomainRule,
    val score: Int,
)

private data class SourceDomainRule(
    val entry: SourceDomainEntry,
    val entryIndex: Int,
    val host: String,
    val pathPrefix: String,
    val redirect: Boolean,
) {
    fun score(target: HttpUrlParts): Int? {
        val hostScore = when {
            target.host == host -> EXACT_HOST_SCORE
            target.host.endsWith(".$host") -> SUBDOMAIN_SCORE
            else -> return null
        }
        val pathScore = when {
            pathPrefix == "/" -> 0
            target.path == pathPrefix -> pathPrefix.length + EXACT_PATH_BONUS
            target.path.startsWith(pathPrefix.ensureTrailingSlash()) -> pathPrefix.length
            else -> return null
        }
        val redirectScore = if (redirect) OBSERVED_REDIRECT_BONUS else DIRECT_RULE_BONUS
        return hostScore + pathScore + redirectScore
    }

    fun reasonFor(target: HttpUrlParts): SourceDomainMatchReason = when {
        redirect -> SourceDomainMatchReason.OBSERVED_REDIRECT
        target.host == host -> SourceDomainMatchReason.DIRECT
        else -> SourceDomainMatchReason.SUBDOMAIN
    }
}

private data class HttpUrlParts(
    val host: String,
    val path: String,
)

private fun buildRules(
    entry: SourceDomainEntry,
    entryIndex: Int,
): List<SourceDomainRule> {
    val directUrls = listOf(
        entry.sourceUrl,
        entry.homeUrl,
        entry.loginUrl,
    ) + entry.matchUrls
    val directRules = directUrls
        .distinct()
        .mapNotNull { it.toRule(entry, entryIndex, redirect = false) }
    val redirectRules = entry.observedRedirectUrls
        .distinct()
        .mapNotNull { it.toRule(entry, entryIndex, redirect = true) }
    return (directRules + redirectRules).distinctBy {
        "${it.entry.key}:${it.host}:${it.pathPrefix}:${it.redirect}"
    }
}

private fun String?.toRule(
    entry: SourceDomainEntry,
    entryIndex: Int,
    redirect: Boolean,
): SourceDomainRule? {
    val parts = toHttpUrlPartsOrNull() ?: return null
    return SourceDomainRule(
        entry = entry,
        entryIndex = entryIndex,
        host = parts.host,
        pathPrefix = parts.path,
        redirect = redirect,
    )
}

private fun String?.toHttpUrlPartsOrNull(): HttpUrlParts? {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme !in setOf("http", "https")) return null
    val host = uri.host?.lowercase(Locale.ROOT)?.trim('.')?.takeIf(String::isNotBlank)
        ?: return null
    val path = uri.rawPath
        ?.takeIf(String::isNotBlank)
        ?.let { if (it.startsWith('/')) it else "/$it" }
        ?: "/"
    return HttpUrlParts(host = host, path = path.removeSuffix("/").ifBlank { "/" })
}

private fun String.ensureTrailingSlash(): String =
    if (endsWith('/')) this else "$this/"

private const val EXACT_HOST_SCORE = 10_000
private const val SUBDOMAIN_SCORE = 8_000
private const val DIRECT_RULE_BONUS = 800
private const val OBSERVED_REDIRECT_BONUS = 200
private const val EXACT_PATH_BONUS = 500
private const val PREFERRED_KEY_BOOST = 50
