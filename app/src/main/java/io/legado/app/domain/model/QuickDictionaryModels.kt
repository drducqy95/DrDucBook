package io.legado.app.domain.model

import androidx.annotation.Keep
import io.legado.app.data.entities.Book

enum class QuickDictionaryScope {
    GLOBAL,
    UNIVERSE,
    PROJECT,
}

enum class QuickDictionaryType {
    NAME,
    VIETPHRASE,
    PHONETIC,
    PRONOUN,
    LUAT_NHAN,
    IGNORE,
    TERM,
}

const val QUICK_DICTIONARY_IGNORE_TARGET = "\uE620QT_IGNORE\uE621"

data class QuickDictionaryEntry(
    val id: Long = 0,
    val raw: String,
    val hanViet: String = "",
    val target: String = "",
    val type: QuickDictionaryType = QuickDictionaryType.VIETPHRASE,
    val scope: QuickDictionaryScope = QuickDictionaryScope.PROJECT,
    val scopeKey: String,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

fun QuickDictionaryEntry.toQuickTranslationPair(): DictPair? {
    val normalizedRaw = raw.trim()
    if (normalizedRaw.isEmpty()) return null
    return when (type) {
        QuickDictionaryType.PHONETIC -> null
        QuickDictionaryType.IGNORE -> DictPair(
            normalizedRaw,
            QUICK_DICTIONARY_IGNORE_TARGET,
            type,
        )
        QuickDictionaryType.NAME,
        QuickDictionaryType.VIETPHRASE,
        QuickDictionaryType.PRONOUN,
        QuickDictionaryType.LUAT_NHAN,
        QuickDictionaryType.TERM -> target.trim()
            .takeIf(String::isNotEmpty)
            ?.let { DictPair(normalizedRaw, it, type) }
    }
}

fun QuickDictionaryEntry.toQuickPhoneticPair(): DictPair? {
    if (type != QuickDictionaryType.PHONETIC) return null
    val normalizedRaw = raw.trim()
    if (normalizedRaw.isEmpty()) return null
    if (normalizedRaw.codePointCount(0, normalizedRaw.length) != 1) return null
    val reading = hanViet.trim().ifEmpty { target.trim() }
    return reading.takeIf(String::isNotEmpty)?.let {
        DictPair(normalizedRaw, it, QuickDictionaryType.PHONETIC)
    }
}

data class QuickDictionaryUniverse(
    val key: String,
    val name: String,
    val contextMarkers: List<String>,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Exact revision vector of the dictionary scopes that can affect one book/context. */
data class QuickDictionaryRevision(
    val global: Long = 0,
    val universeKey: String = "",
    val universe: Long = 0,
    val projectKey: String = "",
    val project: Long = 0,
) {
    val cacheToken: String
        get() = buildString {
            append("g:").append(global)
            append("|u:").append(universeKey.length).append(':').append(universeKey)
                .append(':').append(universe)
            append("|p:").append(projectKey.length).append(':').append(projectKey)
                .append(':').append(project)
        }
}

data class QuickDictionaryCatalog(
    val id: String,
    val name: String,
    val type: QuickDictionaryType,
    val entryCount: Int,
)

data class QuickDictionaryCatalogEntry(
    val catalogId: String,
    val raw: String,
    val hanViet: String = "",
    val target: String = "",
    val type: QuickDictionaryType,
)

@Keep
data class QuickDictionaryPack(
    val id: String,
    val name: String,
    val type: QuickDictionaryType,
    val scope: QuickDictionaryScope,
    val scopeKey: String,
    val entryCount: Int,
    val indexBytes: Long,
    val sourceBytes: Long,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class QuickDictionaryImportPhase {
    ANALYZING,
    INDEXING,
}

data class QuickDictionaryImportProgress(
    val phase: QuickDictionaryImportPhase,
    val processedLines: Int,
    val totalLines: Int,
    val importedEntries: Int,
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val duplicateLines: Int = 0,
)

data class QuickDictionaryImportResult(
    val pack: QuickDictionaryPack?,
    val rejectedLines: Int,
    val duplicateLines: Int = 0,
    val importedEntries: Int = pack?.entryCount ?: 0,
)

/**
 * Resolves overlapping dictionary rows by semantic lane and scope.
 *
 * Translation entries (including Ignore) compete with one another for the same source phrase,
 * while a single-character Phonetic row remains available as a fallback reading. Project wins
 * over the active Universe, which wins over Global; the newest row wins inside one scope.
 */
fun resolveQuickDictionaryScopeConflicts(
    entries: List<QuickDictionaryEntry>,
): List<QuickDictionaryEntry> {
    val seen = hashSetOf<String>()
    return entries.asSequence()
        .filter(QuickDictionaryEntry::enabled)
        .sortedWith(
            compareByDescending<QuickDictionaryEntry> { it.scope.precedence }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id }
        )
        .filter { entry ->
            val normalizedRaw = entry.raw.trim().lowercase()
            val lane = if (entry.type == QuickDictionaryType.PHONETIC) {
                "phonetic"
            } else {
                "translation"
            }
            normalizedRaw.isNotEmpty() && seen.add("$lane\u0000$normalizedRaw")
        }
        .toList()
}

private val QuickDictionaryScope.precedence: Int
    get() = when (this) {
        QuickDictionaryScope.GLOBAL -> 0
        QuickDictionaryScope.UNIVERSE -> 1
        QuickDictionaryScope.PROJECT -> 2
    }

fun QuickDictionaryScope.keyFor(book: Book, universeKey: String = ""): String = when (this) {
    QuickDictionaryScope.GLOBAL -> ""
    QuickDictionaryScope.UNIVERSE -> universeKey
    QuickDictionaryScope.PROJECT -> book.bookUrl
}

fun quickDictionaryUniverseKey(name: String): String = name
    .trim()
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')

object QuickDictionaryUniverseMatcher {

    /** Selects the universe whose marker occurs latest in the supplied translation context. */
    fun activeUniverseKey(
        universes: List<QuickDictionaryUniverse>,
        context: String,
    ): String? {
        if (context.isBlank()) return null
        return universes.asSequence()
            .filter { it.enabled && it.key.isNotBlank() }
            .mapNotNull { universe ->
                universe.contextMarkers.asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .mapNotNull { marker -> markerLastMatch(marker, context) }
                    .maxWithOrNull(compareBy<MarkerMatch> { it.index }.thenBy { it.length })
                    ?.let { match -> UniverseMatch(universe.key, match.index, match.length) }
            }
            .maxWithOrNull(
                compareBy<UniverseMatch> { it.index }
                    .thenBy { it.markerLength }
                    .thenBy { it.key }
            )
            ?.key
    }

    private fun markerLastMatch(marker: String, context: String): MarkerMatch? {
        if (marker.startsWith(REGEX_PREFIX, ignoreCase = true)) {
            val pattern = marker.substring(REGEX_PREFIX.length).trim()
            if (pattern.isEmpty()) return null
            return runCatching {
                Regex(pattern, RegexOption.IGNORE_CASE).findAll(context).lastOrNull()
                    ?.let { MarkerMatch(it.range.first, it.value.length) }
            }.getOrNull()
        }
        val index = context.lastIndexOf(marker, ignoreCase = true)
        return if (index < 0) null else MarkerMatch(index, marker.length)
    }

    private data class MarkerMatch(val index: Int, val length: Int)
    private data class UniverseMatch(val key: String, val index: Int, val markerLength: Int)
    private const val REGEX_PREFIX = "regex:"
}

fun String.usesQuickDictionaryForTranslation(): Boolean {
    return this == TranslationConstants.PROVIDER_QUICK_TRANSLATOR ||
        this == TranslationConstants.PROVIDER_NMT ||
        this == TranslationConstants.PROVIDER_APP_AI ||
        this == TranslationConstants.PROVIDER_ML_KIT ||
        this == TranslationConstants.PROVIDER_HAN_VIET
}

fun dictionaryAwareContentHash(
    originalContentHash: String,
    provider: String,
    dictionaryRevision: Long,
    quickTranslationPackVersion: String,
): String {
    if (!provider.usesQuickDictionaryForTranslation()) return originalContentHash
    return "$originalContentHash|qt-dictionary:$dictionaryRevision:$quickTranslationPackVersion"
}

fun dictionaryAwareContentHash(
    originalContentHash: String,
    provider: String,
    dictionaryRevision: QuickDictionaryRevision,
    quickTranslationPackVersion: String,
): String {
    if (!provider.usesQuickDictionaryForTranslation()) return originalContentHash
    return "$originalContentHash|qt-dictionary:${dictionaryRevision.cacheToken}:$quickTranslationPackVersion"
}

fun dictionaryAwareScopeKey(
    scopeKey: String,
    provider: String,
    dictionaryRevision: Long,
    quickTranslationPackVersion: String,
): String {
    if (!provider.usesQuickDictionaryForTranslation()) return scopeKey
    return "$scopeKey|qt-dictionary:$dictionaryRevision:$quickTranslationPackVersion"
}

fun dictionaryAwareScopeKey(
    scopeKey: String,
    provider: String,
    dictionaryRevision: QuickDictionaryRevision,
    quickTranslationPackVersion: String,
): String {
    if (!provider.usesQuickDictionaryForTranslation()) return scopeKey
    return "$scopeKey|qt-dictionary:${dictionaryRevision.cacheToken}:$quickTranslationPackVersion"
}
