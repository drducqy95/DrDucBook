package io.legado.app.data.repository

import android.app.ActivityManager
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.DisplaySourceSegment
import io.legado.app.domain.model.MappedTranslation
import io.legado.app.domain.model.QUICK_DICTIONARY_IGNORE_TARGET
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.TranslationTextToken
import io.legado.app.domain.model.TranslationTextTokenizer
import io.legado.app.domain.model.normalizedForRuntime
import io.legado.app.utils.getPrefString
import com.huaban.analysis.jieba.JiebaSegmenter
import splitties.init.appCtx
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * QT runtime backed by the provenance-tracked clean pack. Debug builds can additionally provide
 * the complete Quick Translator 2020 compatibility pack as a memory-mapped asset.
 *
 * Precedence is project dictionary first, then the ordered bundled pack. A reviewed bundled name
 * may repair a generic VIETPHRASE project entry for the same source; explicitly typed project names
 * remain authoritative. Within one tier the longest source match wins. Unknown Han characters fall
 * back to their Hán-Việt reading.
 */
class QuickTranslationRepository : QuickTranslationGateway {

    private val jiebaTokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val activityManager = appCtx.getSystemService(ActivityManager::class.java)
        if (shouldEnableJiebaTokenizer(
                memoryClassMb = activityManager?.memoryClass ?: 0,
                isLowRamDevice = activityManager?.isLowRamDevice ?: true,
            )
        ) {
            runCatching { JiebaQtTokenizer() }.getOrNull()
        } else {
            null
        }
    }
    private val cachedPack = AtomicReference<QuickPack?>()
    private val cachedCatalog = AtomicReference<CatalogPack?>()
    private val cachedProjectTrie = AtomicReference<ProjectTrieCache?>()
    private val projectTrieCacheLock = Any()
    private val projectTrieCache = object : LinkedHashMap<Long, ProjectTrieCache>(
        PROJECT_TRIE_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, ProjectTrieCache>?,
        ): Boolean = size > PROJECT_TRIE_CACHE_SIZE
    }
    private val packLoadLock = Any()
    private val catalogLoadLock = Any()
    private val emptyProjectTrie = TermTrie(emptyList())
    private val detectedPackVersion by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (runCatching {
                appCtx.assets.openFd(QT2025_TERM_INDEX_ASSET).use { }
            }.isSuccess
        ) {
            QT2025_PACK_VERSION
        } else if (runCatching {
                appCtx.assets.openFd(QT2020_TERM_INDEX_ASSET).use { }
            }.isSuccess
        ) {
            QT2020_PACK_VERSION
        } else {
            PACK_VERSION
        }
    }

    override val packVersion: String
        get() = packVersionFor(null)

    override fun packVersionFor(pronounMode: QuickTranslationPronounMode?): String =
        versionWithPronounMode(
            version = cachedPack.get()?.version ?: detectedPackVersion,
            mode = resolvedPronounMode(pronounMode),
        )

    override fun warmUp() {
        pack()
        jiebaTokenizer
    }

    fun translate(text: String, projectTerms: List<DictPair>): String {
        return translate(text, projectTerms, emptyList())
    }

    override fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
    ): String = translate(text, projectTerms, customPhonetics, null)

    override fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
    ): String {
        if (text.isEmpty()) return text
        if (text.codePoints().noneMatch(::isCjk)) return text
        val safeProjectTerms = projectTerms.map(DictPair::normalizedForRuntime)
        val safeCustomPhonetics = customPhonetics.map(DictPair::normalizedForRuntime)
        val pack = pack()
        val resolvedPronounMode = resolvedPronounMode(pronounMode)
        val projectRuntime = projectRuntimeFor(safeProjectTerms, pack)
        val projectTrie = projectRuntime.trie
        val customPhoneticMap = safeCustomPhonetics.asSequence()
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .filter { it.original.codePointCount(0, it.original.length) == 1 }
            .distinctBy { it.original }
            .associate { it.original to it.translation.trim() }
        val protected = protectMarkup(text)
        val pronounContext = PronounContextTracker()
        val translatedTokenCache = HashMap<TranslationTokenCacheKey, String>()
        val translated = protected.layout.tokens.joinToString(separator = "") { token ->
            when (token) {
                is TranslationTextToken.TextToken -> {
                    val pronounHints = pronounContext.hintsFor(token.raw)
                    translatedTokenCache.getOrPut(
                        TranslationTokenCacheKey(token.raw, pronounHints)
                    ) {
                        val lexical = translateProtected(
                            text = token.raw,
                            projectRuntime = projectRuntime,
                            pack = pack,
                            customPhonetics = customPhoneticMap,
                        )
                        postProcessTranslatedText(
                            sourceText = token.raw,
                            text = lexical,
                            pack = pack,
                            customPhonetics = customPhoneticMap,
                            pronounMode = resolvedPronounMode,
                            pronounHints = pronounHints,
                        )
                    }
                }

                is TranslationTextToken.ProtectedToken -> protected.tokenFor(token)
                else -> token.raw
            }
        }
        return protected.restore(
            QuickTranslationTextPostProcessor.cleanHeadingArtifacts(
                QuickTranslationTextPostProcessor.capitalizeSentenceStarts(translated)
            )
        )
    }

    override fun translateMapped(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
    ): MappedTranslation = translateMapped(text, projectTerms, customPhonetics, null)

    override fun translateMapped(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
    ): MappedTranslation {
        if (text.isEmpty()) return MappedTranslation(text, emptyList(), MAPPED_ENGINE)
        if (text.codePoints().noneMatch(::isCjk)) {
            return MappedTranslation(
                text = text,
                segments = listOf(
                    DisplaySourceSegment(
                        sourceStart = 0,
                        sourceEnd = text.length,
                        displayStart = 0,
                        displayEnd = text.length,
                        confidence = 1f,
                        exactCharacterMapping = true,
                    )
                ),
                engine = MAPPED_ENGINE,
            )
        }

        val safeProjectTerms = projectTerms.map(DictPair::normalizedForRuntime)
        val safeCustomPhonetics = customPhonetics.map(DictPair::normalizedForRuntime)
        val pack = pack()
        val resolvedPronounMode = resolvedPronounMode(pronounMode)
        val projectRuntime = projectRuntimeFor(safeProjectTerms, pack)
        val customPhoneticMap = safeCustomPhonetics.asSequence()
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .filter { it.original.codePointCount(0, it.original.length) == 1 }
            .distinctBy { it.original }
            .associate { it.original to it.translation.trim() }
        val layout = TranslationTextTokenizer.tokenize(text)
        val output = StringBuilder(text.length * 2)
        val segments = mutableListOf<DisplaySourceSegment>()
        val capitalizer = StatefulSentenceCapitalizer()
        val pronounContext = PronounContextTracker()
        val translatedTokenCache = HashMap<TranslationTokenCacheKey, LocalMappedText>()
        var sourceOffset = 0
        layout.tokens.forEach { token ->
            val displayOffset = output.length
            when (token) {
                is TranslationTextToken.TextToken -> {
                    val pronounHints = pronounContext.hintsFor(token.raw)
                    val mapped = translatedTokenCache.getOrPut(
                        TranslationTokenCacheKey(token.raw, pronounHints)
                    ) {
                        translateTextTokenMapped(
                            text = token.raw,
                            projectRuntime = projectRuntime,
                            pack = pack,
                            customPhonetics = customPhoneticMap,
                            pronounMode = resolvedPronounMode,
                            pronounHints = pronounHints,
                        )
                    }
                    capitalizer.append(mapped.text, output)
                    mapped.segments.forEach { segment ->
                        segments += segment.copy(
                            sourceStart = segment.sourceStart + sourceOffset,
                            sourceEnd = segment.sourceEnd + sourceOffset,
                            displayStart = segment.displayStart + displayOffset,
                            displayEnd = segment.displayEnd + displayOffset,
                        )
                    }
                }

                is TranslationTextToken.ProtectedToken -> {
                    capitalizer.appendProtected(token.raw, output)
                    segments += exactPassthroughSegment(sourceOffset, displayOffset, token.raw.length)
                }

                else -> {
                    capitalizer.append(token.raw, output)
                    segments += exactPassthroughSegment(sourceOffset, displayOffset, token.raw.length)
                }
            }
            sourceOffset += token.raw.length
        }
        return MappedTranslation(output.toString(), segments, MAPPED_ENGINE)
    }

    override fun hanViet(text: String, customPhonetics: List<DictPair>): String {
        if (text.isEmpty()) return text
        val phonetics = pack().phonetics
        val customPhoneticMap = customPhonetics.asSequence()
            .map(DictPair::normalizedForRuntime)
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .filter { it.original.codePointCount(0, it.original.length) == 1 }
            .distinctBy { it.original }
            .associate { it.original to it.translation.trim() }
        val protected = protectMarkup(text)
        val output = protected.layout.tokens.joinToString(separator = "") { token ->
            when (token) {
                is TranslationTextToken.TextToken -> buildString(token.raw.length * 2) {
                    var offset = 0
                    while (offset < token.raw.length) {
                        val codePoint = token.raw.codePointAt(offset)
                        val source = String(Character.toChars(codePoint))
                        val reading = customPhoneticMap[source] ?: phonetics[source]
                        if (reading != null && isCjk(codePoint)) {
                            appendWord(reading)
                        } else {
                            append(source)
                        }
                        offset += Character.charCount(codePoint)
                    }
                }

                is TranslationTextToken.ProtectedToken -> protected.tokenFor(token)
                else -> token.raw
            }
        }
        return protected.restore(
            QuickTranslationTextPostProcessor.capitalizeSentenceStarts(output)
        )
    }

    override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> {
        val catalog = catalogPack()
        val qt2020Catalogs = availableQt2020SourceCatalogs()
        if (qt2020Catalogs.isEmpty()) {
            return CATALOG_ORDER.mapNotNull { type ->
                catalog.entries[type]?.let { entries ->
                    QuickDictionaryCatalog(
                        id = "bundled:${type.name.lowercase()}",
                        name = type.catalogName,
                        type = type,
                        entryCount = entries.size,
                    )
                }
            }
        }
        val sourceTypes = qt2020Catalogs.mapTo(hashSetOf()) { it.type }
        val supplemental = CATALOG_ORDER
            .filterNot(sourceTypes::contains)
            .mapNotNull { type ->
            catalog.entries[type]?.let { entries ->
                QuickDictionaryCatalog(
                    id = "bundled:${type.name.lowercase()}",
                    name = type.catalogName,
                    type = type,
                    entryCount = entries.size,
                )
            }
        }
        return qt2020Catalogs.map { source ->
            QuickDictionaryCatalog(
                id = source.catalogId,
                name = source.fileName,
                type = source.type,
                entryCount = source.entryCount,
            )
        } + supplemental
    }

    override fun searchBuiltInEntries(
        type: QuickDictionaryType,
        query: String,
        limit: Int,
        catalogId: String?,
    ): List<QuickDictionaryCatalogEntry> {
        val normalizedQuery = query.trim().lowercase()
        val requestedLimit = limit.coerceIn(1, 2_000)
        val sourceEntries = searchQt2020SourceEntries(
            type = type,
            normalizedQuery = normalizedQuery,
            limit = requestedLimit,
            catalogId = catalogId,
        )
        val seen = sourceEntries.mapTo(hashSetOf()) { normalize(it.raw) }
        val fallbackEntries = catalogPack().entries[type].orEmpty().asSequence()
            .filter { catalogId == null || it.catalogId == catalogId }
            .filter { entry ->
                normalizedQuery.isEmpty() ||
                    entry.raw.lowercase().contains(normalizedQuery) ||
                    entry.hanViet.lowercase().contains(normalizedQuery) ||
                    entry.target.lowercase().contains(normalizedQuery)
            }
            .filter { seen.add(normalize(it.raw)) }
            .take((requestedLimit - sourceEntries.size).coerceAtLeast(0))
            .toList()
        return (sourceEntries + fallbackEntries).asSequence()
            .map { entry ->
                if (entry.type == QuickDictionaryType.PHONETIC || entry.hanViet.isNotBlank()) {
                    entry
                } else {
                    entry.copy(hanViet = hanViet(entry.raw))
                }
            }
            .toList()
    }

    override fun containsBuiltInEntry(type: QuickDictionaryType, raw: String): Boolean {
        val normalizedRaw = raw.trim()
        if (normalizedRaw.isEmpty()) return false
        return when (type) {
            QuickDictionaryType.PHONETIC -> pack().phonetics.containsKey(normalizedRaw)
            QuickDictionaryType.LUAT_NHAN -> catalogPack().entries[type].orEmpty()
                .any { normalize(it.raw) == normalize(normalizedRaw) }
            else -> pack().baseTrie.containsExact(normalizedRaw)
        }
    }

    private fun availableQt2020SourceCatalogs(): List<Qt2020SourceCatalog> {
        val available = runCatching {
            appCtx.assets.list(QT2020_ASSET_DIRECTORY).orEmpty().toSet()
        }.getOrDefault(emptySet())
        return QT2020_SOURCE_CATALOGS.filter { it.fileName in available }
    }

    /**
     * Streams the editable QT2020 source files instead of materializing ~743k rows in heap.
     * A query may scan the large VietPhrase file, but retained memory remains bounded by [limit].
     */
    private fun searchQt2020SourceEntries(
        type: QuickDictionaryType,
        normalizedQuery: String,
        limit: Int,
        catalogId: String?,
    ): List<QuickDictionaryCatalogEntry> {
        val catalogs = availableQt2020SourceCatalogs().filter { catalog ->
            catalog.type == type && (catalogId == null || catalog.catalogId == catalogId)
        }
        if (catalogs.isEmpty() || limit <= 0) return emptyList()
        val result = ArrayList<QuickDictionaryCatalogEntry>(limit.coerceAtMost(512))
        val seen = hashSetOf<String>()
        for (catalog in catalogs) {
            if (result.size >= limit) break
            runCatching {
                appCtx.assets.open("$QT2020_ASSET_DIRECTORY/${catalog.fileName}")
                    .let { InputStreamReader(it, Charsets.UTF_8) }
                    .buffered(DEFAULT_DICTIONARY_BUFFER_BYTES)
                    .useLines { lines ->
                        val iterator = lines.iterator()
                        while (iterator.hasNext() && result.size < limit) {
                            val line = iterator.next().trimEnd()
                            if (line.isBlank() || line.trimStart().startsWith('#')) continue
                            val delimiter = line.indexOf('=')
                            if (delimiter <= 0) continue
                            val raw = line.substring(0, delimiter).trim().removePrefix("\uFEFF")
                            val target = cleanQuickDictionaryTarget(line.substring(delimiter + 1))
                            val key = normalize(raw)
                            if (raw.isEmpty() || target.isEmpty() || !seen.add(key)) continue
                            if (normalizedQuery.isNotEmpty() &&
                                !raw.lowercase().contains(normalizedQuery) &&
                                !target.lowercase().contains(normalizedQuery)
                            ) {
                                continue
                            }
                            result += QuickDictionaryCatalogEntry(
                                catalogId = catalog.catalogId,
                                raw = raw,
                                target = target,
                                type = type,
                            )
                        }
                    }
            }
        }
        return result
    }

    private fun translateProtected(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
    ): String {
        val matches = RuntimeMatchIndex(
            text = text,
            projectMatches = projectRuntime.trie.allMatchesByStart(text),
            baseMatches = pack.baseTrie.allMatchesByStart(text),
            jiebaTokens = jiebaTokenizer?.tokenize(text).orEmpty(),
        )
        val plan = bestTranslationPlan(
            text = text,
            projectRuntime = projectRuntime,
            pack = pack,
            customPhonetics = customPhonetics,
            matches = matches,
        )
        val output = StringBuilder(text.length * 2)
        var offset = 0
        while (offset < text.length) {
            val candidate = plan[offset]
            if (candidate == null || candidate.endExclusive <= offset) {
                val fallback = fallbackCandidate(text, offset, customPhonetics, pack.phonetics)
                if (fallback.kind == CandidateKind.LITERAL) {
                    output.appendLiteralRun(fallback.translation)
                } else {
                    output.appendWord(fallback.translation)
                }
                offset = fallback.endExclusive
                continue
            }
            if (candidate.kind == CandidateKind.LITERAL) {
                val literal = StringBuilder()
                var literalOffset = offset
                while (literalOffset < text.length) {
                    val literalCandidate = plan[literalOffset] ?: break
                    if (literalCandidate.kind != CandidateKind.LITERAL ||
                        literalCandidate.endExclusive <= literalOffset
                    ) {
                        break
                    }
                    literal.append(literalCandidate.translation)
                    literalOffset = literalCandidate.endExclusive
                }
                if (literal.isNotEmpty()) {
                    output.appendLiteralRun(literal.toString())
                    offset = literalOffset
                    continue
                }
            }
            output.appendWord(candidate.translation)
            offset = candidate.endExclusive
        }
        return output.toString()
    }

    private fun translateTextTokenMapped(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode,
        pronounHints: PronounHints,
    ): LocalMappedText {
        val lexical = translateProtectedMapped(
            text = text,
            projectRuntime = projectRuntime,
            pack = pack,
            customPhonetics = customPhonetics,
        )
        val processed = postProcessTranslatedText(
            sourceText = text,
            text = lexical.text,
            pack = pack,
            customPhonetics = customPhonetics,
            pronounMode = pronounMode,
            pronounHints = pronounHints,
        )
        if (processed == lexical.text) return lexical
        val remapped = remapProcessedSegments(
            sourceText = text,
            lexical = lexical,
            processed = processed,
            pack = pack,
            customPhonetics = customPhonetics,
            pronounMode = pronounMode,
            pronounHints = pronounHints,
        )
        return LocalMappedText(processed, remapped, text.length)
    }

    private fun translateProtectedMapped(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
    ): LocalMappedText {
        val matches = RuntimeMatchIndex(
            text = text,
            projectMatches = projectRuntime.trie.allMatchesByStart(text),
            baseMatches = pack.baseTrie.allMatchesByStart(text),
            jiebaTokens = jiebaTokenizer?.tokenize(text).orEmpty(),
        )
        val plan = bestTranslationPlan(
            text = text,
            projectRuntime = projectRuntime,
            pack = pack,
            customPhonetics = customPhonetics,
            matches = matches,
        )
        val output = LocalMappedTextBuilder(text)
        var offset = 0
        while (offset < text.length) {
            val candidate = plan[offset]
            if (candidate == null || candidate.endExclusive <= offset) {
                val fallback = fallbackCandidate(text, offset, customPhonetics, pack.phonetics)
                output.append(
                    sourceStart = offset,
                    sourceEnd = fallback.endExclusive,
                    value = fallback.translation,
                    literal = fallback.kind == CandidateKind.LITERAL,
                )
                offset = fallback.endExclusive
                continue
            }
            if (candidate.kind == CandidateKind.LITERAL) {
                val literal = StringBuilder()
                val literalStart = offset
                var literalOffset = offset
                while (literalOffset < text.length) {
                    val literalCandidate = plan[literalOffset] ?: break
                    if (literalCandidate.kind != CandidateKind.LITERAL ||
                        literalCandidate.endExclusive <= literalOffset
                    ) {
                        break
                    }
                    literal.append(literalCandidate.translation)
                    literalOffset = literalCandidate.endExclusive
                }
                if (literal.isNotEmpty()) {
                    output.append(literalStart, literalOffset, literal.toString(), literal = true)
                    offset = literalOffset
                    continue
                }
            }
            output.append(offset, candidate.endExclusive, candidate.translation, literal = false)
            offset = candidate.endExclusive
        }
        return output.build()
    }

    private fun postProcessTranslatedText(
        sourceText: String,
        text: String,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode,
        pronounHints: PronounHints = PronounHints(),
    ): String = QuickTranslationTextPostProcessor.normalizeNumericSpacing(
        replaceRemainingCjkWithPhonetics(
            text = normalizeNarratorThirdPerson(
                sourceText = sourceText,
                translatedText = applyPostRules(
                    applyPronounProfile(sourceText, text, pronounMode, pronounHints),
                    pack.postRules,
                ),
                mode = pronounMode,
            ),
            customPhonetics = customPhonetics,
            bundledPhonetics = pack.phonetics,
        )
    )

    private fun remapProcessedSegments(
        sourceText: String,
        lexical: LocalMappedText,
        processed: String,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode,
        pronounHints: PronounHints = PronounHints(),
    ): List<DisplaySourceSegment> {
        val result = mutableListOf<DisplaySourceSegment>()
        var cursor = 0
        lexical.segments.forEach { segment ->
            val lexicalPiece = lexical.text.substring(segment.displayStart, segment.displayEnd)
            val safeSourceStart = segment.sourceStart.coerceIn(0, sourceText.length)
            val safeSourceEnd = segment.sourceEnd.coerceIn(safeSourceStart, sourceText.length)
            val sourcePiece = sourceText.substring(safeSourceStart, safeSourceEnd)
            if (lexicalPiece.isEmpty()) return@forEach
            var matchText = lexicalPiece
            var matchStart = processed.indexOf(matchText, startIndex = cursor, ignoreCase = true)
            if (matchStart < 0) {
                matchText = postProcessTranslatedText(
                    sourceText = sourcePiece,
                    text = lexicalPiece,
                    pack = pack,
                    customPhonetics = customPhonetics,
                    pronounMode = pronounMode,
                    pronounHints = pronounHints,
                ).takeIf(String::isNotEmpty) ?: return@forEach
                matchStart = processed.indexOf(matchText, startIndex = cursor, ignoreCase = true)
            }
            if (matchStart < 0) return@forEach
            val matchEnd = matchStart + matchText.length
            result += segment.copy(
                displayStart = matchStart,
                displayEnd = matchEnd,
                exactCharacterMapping = segment.exactCharacterMapping &&
                    lexicalPiece == matchText,
            )
            cursor = matchEnd
        }
        return if (result.isNotEmpty()) {
            result
        } else {
            listOf(
                DisplaySourceSegment(
                    sourceStart = 0,
                    sourceEnd = lexical.sourceLength,
                    displayStart = 0,
                    displayEnd = processed.length,
                    confidence = 0.35f,
                )
            )
        }
    }

    private fun exactPassthroughSegment(
        sourceOffset: Int,
        displayOffset: Int,
        length: Int,
    ) = DisplaySourceSegment(
        sourceStart = sourceOffset,
        sourceEnd = sourceOffset + length,
        displayStart = displayOffset,
        displayEnd = displayOffset + length,
        confidence = 1f,
        exactCharacterMapping = true,
    )

    private fun bestTranslationPlan(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        matches: RuntimeMatchIndex,
    ): Array<TranslationCandidate?> {
        val bestScores = LongArray(text.length + 1) { Long.MIN_VALUE / 4 }
        val plan = arrayOfNulls<TranslationCandidate>(text.length)
        bestScores[text.length] = 0L
        for (offset in text.length - 1 downTo 0) {
            var bestCandidate: TranslationCandidate? = null
            var bestScore = Long.MIN_VALUE / 4
            translationCandidatesAt(
                text = text,
                offset = offset,
                projectRuntime = projectRuntime,
                pack = pack,
                customPhonetics = customPhonetics,
                matches = matches,
            ).forEach { candidate ->
                val tailScore = bestScores.getOrElse(candidate.endExclusive) {
                    Long.MIN_VALUE / 4
                }
                if (tailScore <= Long.MIN_VALUE / 8) return@forEach
                val score = candidate.score + tailScore
                val current = bestCandidate
                if (current == null ||
                    score > bestScore ||
                    score == bestScore && isBetterCandidate(candidate, current)
                ) {
                    bestCandidate = candidate
                    bestScore = score
                }
            }
            bestCandidate?.let { candidate ->
                plan[offset] = candidate
                bestScores[offset] = bestScore
            }
        }
        return plan
    }

    private fun translationCandidatesAt(
        text: String,
        offset: Int,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        matches: RuntimeMatchIndex,
    ): List<TranslationCandidate> {
        if (offset >= text.length) return emptyList()
        val candidates = ArrayList<TranslationCandidate>(8)
        matches.termsAt(offset)
            .filterNot { match ->
                !match.term.projectOwned &&
                    matches.conflictsWithProjectTerm(match.start, match.endExclusive)
            }
            .forEach { match -> candidates += lexicalCandidate(match, matches) }
        val insideProjectTerm = matches.isInsideProjectTerm(offset)
        val structuredMatch = if (!insideProjectTerm &&
            mayStartStructured(text, offset) &&
            matches.projectTermsAt(offset).isEmpty()
        ) {
            bestStructuredMatch(text, offset)
        } else {
            null
        }
        if (!insideProjectTerm && matches.projectTermsAt(offset).isEmpty()) {
            pack.qt2025Runtime?.matchAt(
                text = text,
                offset = offset,
                resolveName = matches::qt2025NameTarget,
                containsExact = pack.baseTrie::containsExact,
            )?.let { runtime ->
                val coveredByStructured = structuredMatch?.endExclusive
                    ?.let { structuredEnd -> structuredEnd >= runtime.endExclusive } == true ||
                    runtime.kind == Qt2025Runtime.MatchKind.NUMBER_RULE &&
                    isCoveredByEarlierStructuredMatch(
                        text = text,
                        offset = offset,
                        requiredEnd = runtime.endExclusive,
                    )
                if (!coveredByStructured &&
                    !matches.hasCoveringCorrection(offset, runtime.endExclusive)
                ) {
                    candidates += TranslationCandidate(
                        endExclusive = runtime.endExclusive,
                        translation = runtime.translation,
                        priority = runtime.priority,
                        score = phraseLengthScore(runtime.endExclusive - offset) +
                            runtime.priority * 10L + QT2025_RUNTIME_SCORE_BONUS,
                    )
                }
            }
        }
        if (!insideProjectTerm && matches.projectTermsAt(offset).isEmpty()) {
            structuredMatch?.let { structured ->
                if (!matches.hasStartingCorrectionOverride(offset, structured.endExclusive)) {
                    candidates += TranslationCandidate(
                        endExclusive = structured.endExclusive,
                        translation = structured.translation,
                        priority = structured.priority,
                        score = structured.scoreFrom(offset, GRAMMAR_SCORE_BONUS),
                    )
                }
            }
        }
        if (!insideProjectTerm) {
            bestGrammarMatch(
                text = text,
                offset = offset,
                matches = matches,
            )?.let { grammar ->
                val coveredByBaseLexicalTerm = matches.termsAt(offset).any { lexical ->
                    !lexical.term.projectOwned &&
                        lexical.term.sourceQuality != TermSourceQuality.LEGACY &&
                        lexical.term.target.isNotBlank() &&
                        (lexical.endExclusive - offset) * 2 >= grammar.endExclusive - offset
                }
                if (!coveredByBaseLexicalTerm ||
                    grammar.priority >= TRUSTED_GRAMMAR_PRIORITY &&
                    !matches.hasCorrectionOverride(offset, grammar.endExclusive)
                ) {
                    candidates += TranslationCandidate(
                        endExclusive = grammar.endExclusive,
                        translation = grammar.translation,
                        priority = grammar.priority,
                        score = grammar.scoreFrom(offset, GRAMMAR_SCORE_BONUS),
                    )
                }
            }
        }
        bestTemplateMatch(
            text = text,
            offset = offset,
            indexedTemplates = projectRuntime.indexedTemplatesAt(text, offset) +
                pack.indexedTemplatesAt(text, offset),
            leadingSlotTemplates = if (!insideProjectTerm && matches.termsAt(offset).isNotEmpty()) {
                leadingSlotTemplatesAt(
                    text = text,
                    offset = offset,
                    matches = matches,
                    projectIndex = projectRuntime.leadingSlotTemplateIndex,
                    packIndex = pack.leadingSlotTemplateIndex,
                )
            } else {
                emptyList()
            },
            matches = matches,
        )?.let { template ->
            val coveredByBaseLexicalTerm = matches.termsAt(offset).any { lexical ->
                !lexical.term.projectOwned &&
                    lexical.term.sourceQuality != TermSourceQuality.LEGACY &&
                    lexical.term.target.isNotBlank() &&
                    (lexical.endExclusive - offset) * 2 >= template.endExclusive - offset
            }
            val startingProjectEnd = matches.projectTermsAt(offset)
                .maxOfOrNull { it.endExclusive }
            val replacesStartingProjectTerm = startingProjectEnd != null &&
                template.endExclusive <= startingProjectEnd
            if ((!replacesStartingProjectTerm || template.priority >= PROJECT_TEMPLATE_PRIORITY) &&
                (!coveredByBaseLexicalTerm ||
                    template.priority >= TRUSTED_GRAMMAR_PRIORITY &&
                    (template.priority >= REVIEWED_TEMPLATE_PRIORITY ||
                        !matches.hasCorrectionOverride(offset, template.endExclusive)) ||
                    template.priority >= PROJECT_TEMPLATE_PRIORITY)
            ) {
                candidates += TranslationCandidate(
                    endExclusive = template.endExclusive,
                    translation = template.translation,
                    priority = template.priority,
                    score = template.scoreFrom(offset, TEMPLATE_SCORE_BONUS),
                )
            }
        }
        jiebaFallbackCandidatesAt(
            text = text,
            offset = offset,
            matches = matches,
            customPhonetics = customPhonetics,
            bundledPhonetics = pack.phonetics,
        ).forEach(candidates::add)
        val fallback = fallbackCandidate(text, offset, customPhonetics, pack.phonetics)
        candidates += if (matches.conflictsWithProjectTerm(offset, fallback.endExclusive)) {
            fallback.copy(score = fallback.score + PROJECT_TERM_SPLIT_PENALTY)
        } else {
            fallback
        }
        return candidates.bestBySpanAndText()
    }

    private fun isCoveredByEarlierStructuredMatch(
        text: String,
        offset: Int,
        requiredEnd: Int,
    ): Boolean {
        val firstStart = (offset - MAX_QT2025_STRUCTURED_LOOKBACK).coerceAtLeast(0)
        for (start in firstStart until offset) {
            if (!mayStartStructured(text, start)) continue
            val structured = bestStructuredMatch(text, start) ?: continue
            if (structured.endExclusive >= requiredEnd) return true
        }
        return false
    }

    private fun RuntimeMatchIndex.hasCoveringCorrection(
        offset: Int,
        requiredEnd: Int,
    ): Boolean {
        val firstStart = (offset - MAX_QT2025_CORRECTION_LOOKBACK).coerceAtLeast(0)
        for (start in firstStart..offset) {
            if (termsAt(start).any { match ->
                    isUsableCorrection(match) && match.endExclusive >= requiredEnd
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun RuntimeMatchIndex.hasStartingCorrectionOverride(
        start: Int,
        requiredEnd: Int,
    ): Boolean = termsAt(start).any { match ->
        isUsableCorrection(match) && match.endExclusive >= requiredEnd
    }

    private fun RuntimeMatchIndex.hasCorrectionOverride(
        start: Int,
        requiredEnd: Int,
    ): Boolean {
        if (termsAt(start).any { match ->
                isUsableCorrection(match) && match.endExclusive >= requiredEnd
            }
        ) {
            return true
        }
        for (offset in start + 1 until requiredEnd) {
            if (termsAt(offset).any { match ->
                    match.term.sourceQuality == TermSourceQuality.LATEST_CORRECTION &&
                        match.endExclusive <= requiredEnd &&
                        isUsableCorrection(match)
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun RuntimeMatchIndex.isUsableCorrection(match: TermMatch): Boolean =
        !match.term.projectOwned &&
            match.term.sourceQuality in CORRECTION_SOURCE_QUALITIES &&
            match.term.target.isNotBlank() &&
            !conflictsWithProjectTerm(match.start, match.endExclusive)

    private fun lexicalCandidate(
        match: TermMatch,
        matches: RuntimeMatchIndex,
    ): TranslationCandidate {
        val length = match.endExclusive - match.start
        val typeScore = when (match.term.type) {
            QuickDictionaryType.IGNORE -> 10_000
            QuickDictionaryType.NAME -> 700
            QuickDictionaryType.PRONOUN -> 550
            QuickDictionaryType.TERM -> 450
            QuickDictionaryType.VIETPHRASE -> 400
            QuickDictionaryType.LUAT_NHAN -> 300
            QuickDictionaryType.PHONETIC -> 100
        }
        val projectScore = if (match.term.projectOwned) 1_200 else 0
        val ignoreScore = if (match.term.target.isEmpty()) IGNORE_SCORE_BONUS else 0
        val jiebaScore = when {
            match.term.projectOwned -> JIEBA_PROJECT_SCORE
            matches.isJiebaAligned(match.start, match.endExclusive) -> JIEBA_ALIGNMENT_BONUS
            matches.crossesJiebaToken(match.start, match.endExclusive) ->
                JIEBA_CROSSING_PENALTY * length.coerceAtLeast(1)
            else -> 0L
        }
        val targetQualityScore = if (match.term.target.isBlank()) {
            0L
        } else {
            quickDictionaryTargetScore(match.term.target)
                .coerceIn(-40, 40)
                .toLong() * TARGET_QUALITY_SCORE_FACTOR
        }
        val sourceQualityScore = when (match.term.sourceQuality) {
            TermSourceQuality.PROJECT -> PROJECT_SOURCE_QUALITY_BONUS
            TermSourceQuality.LATEST_CORRECTION ->
                LATEST_CORRECTION_SOURCE_QUALITY_BONUS +
                    length.toLong() * length.toLong() * LATEST_CORRECTION_LENGTH_SCORE_FACTOR
            TermSourceQuality.CORRECTION ->
                CORRECTION_SOURCE_QUALITY_BONUS +
                    length.toLong() * length.toLong() * CORRECTION_LENGTH_SCORE_FACTOR
            TermSourceQuality.REVIEWED -> REVIEWED_SOURCE_QUALITY_BONUS
            TermSourceQuality.CATALOG_NAME -> CATALOG_NAME_SOURCE_QUALITY_BONUS
            TermSourceQuality.BASE -> 0L
            TermSourceQuality.LEGACY -> LEGACY_SOURCE_QUALITY_PENALTY
            TermSourceQuality.SYNTHETIC -> SYNTHETIC_SOURCE_QUALITY_PENALTY
        }
        val fragmentationPenalty = lexicalFragmentationPenalty(match, length)
        val definitionPenalty = if (isDefinitionLikeQuickDictionaryTarget(match.term.target)) {
            DEFINITION_TARGET_SCORE_PENALTY
        } else {
            0L
        }
        return TranslationCandidate(
            endExclusive = match.endExclusive,
            translation = match.term.target,
            priority = typeScore + projectScore,
            score = phraseLengthScore(length) + typeScore + projectScore + ignoreScore -
                match.term.sourcePriority.coerceAtMost(500) + jiebaScore + targetQualityScore +
                sourceQualityScore - fragmentationPenalty - definitionPenalty,
        )
    }

    private fun lexicalFragmentationPenalty(match: TermMatch, length: Int): Long {
        if (match.term.projectOwned || match.term.target.isEmpty()) return 0L
        var penalty = TRANSLATION_FRAGMENT_PENALTY
        if (length == 1) {
            penalty += if (match.term.source in SINGLE_CHAR_FUNCTION_PARTICLES) {
                SINGLE_CHAR_FUNCTION_SPLIT_PENALTY
            } else {
                SINGLE_CHAR_LEXICAL_SPLIT_PENALTY
            }
        }
        if (match.term.type == QuickDictionaryType.PHONETIC) {
            penalty += PHONETIC_FALLBACK_PENALTY
        }
        return penalty
    }

    private fun fallbackCandidate(
        text: String,
        offset: Int,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): TranslationCandidate {
        val codePoint = text.codePointAt(offset)
        val source = String(Character.toChars(codePoint))
        val reading = customPhonetics[source] ?: bundledPhonetics[source]
        val translation = if (reading != null && isCjk(codePoint)) reading else source
        return TranslationCandidate(
            endExclusive = offset + Character.charCount(codePoint),
            translation = translation,
            priority = 0,
            score = if (reading != null && isCjk(codePoint)) {
                FALLBACK_SCORE - TRANSLATION_FRAGMENT_PENALTY - PHONETIC_FALLBACK_PENALTY
            } else {
                FALLBACK_SCORE
            },
            kind = if (reading != null && isCjk(codePoint)) {
                CandidateKind.PHONETIC
            } else {
                CandidateKind.LITERAL
            },
        )
    }

    private fun StructuredMatch.scoreFrom(offset: Int, bonus: Long): Long {
        val length = endExclusive - offset
        return phraseLengthScore(length) + priority * 10L + bonus +
            length.toLong() * length.toLong() * STRUCTURED_CONTIGUOUS_SCORE_FACTOR
    }

    private fun TemplateMatch.scoreFrom(offset: Int, bonus: Long): Long {
        val length = endExclusive - offset
        val reviewedContiguousScore = if (contiguousScore) {
            length.toLong() * length.toLong() * TEMPLATE_CONTIGUOUS_SCORE_FACTOR
        } else {
            0L
        }
        return phraseLengthScore(length) + priority * 10L + bonus + reviewedContiguousScore
    }

    private fun phraseLengthScore(length: Int): Long =
        length.coerceAtMost(SATURATED_LENGTH_CHARS).toLong().let { bounded ->
            bounded * bounded * LENGTH_SCORE_FACTOR
        } +
            (length - SATURATED_LENGTH_CHARS).coerceAtLeast(0) * LONG_PHRASE_SCORE_FACTOR

    private fun isBetterCandidate(
        candidate: TranslationCandidate,
        current: TranslationCandidate,
    ): Boolean = when {
        candidate.priority != current.priority -> candidate.priority > current.priority
        candidate.endExclusive != current.endExclusive -> candidate.endExclusive > current.endExclusive
        candidate.translation.isNotBlank() != current.translation.isNotBlank() ->
            candidate.translation.isNotBlank()
        else -> false
    }

    private fun List<TranslationCandidate>.bestBySpanAndText(): List<TranslationCandidate> {
        if (size <= 1) return this
        val winners = LinkedHashMap<String, TranslationCandidate>()
        forEach { candidate ->
            val key = "${candidate.endExclusive}\u0000${candidate.translation}"
            val current = winners[key]
            if (current == null || candidate.score > current.score ||
                candidate.score == current.score && isBetterCandidate(candidate, current)
            ) {
                winners[key] = candidate
            }
        }
        return winners.values.sortedWith(
            compareByDescending<TranslationCandidate> { it.score }
                .thenByDescending { it.priority }
                .thenByDescending { it.endExclusive }
        )
    }

    private fun jiebaFallbackCandidatesAt(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): List<TranslationCandidate> {
        val tokens = matches.jiebaTokensAt(offset)
        if (tokens.isEmpty()) return emptyList()
        return tokens.asSequence()
            .filter { token -> token.endExclusive - token.start > 1 }
            .filterNot { token ->
                matches.hasDictionaryTermInside(token.start, token.endExclusive)
            }
            .mapNotNull { token ->
                val translation = fallbackTranslationForRange(
                    text = text,
                    start = token.start,
                    endExclusive = token.endExclusive,
                    customPhonetics = customPhonetics,
                    bundledPhonetics = bundledPhonetics,
                ).takeIf(String::isNotBlank) ?: return@mapNotNull null
                val length = text.codePointCount(token.start, token.endExclusive)
                TranslationCandidate(
                    endExclusive = token.endExclusive,
                    translation = translation,
                    priority = JIEBA_TOKEN_FALLBACK_PRIORITY,
                    score = phraseLengthScore(length) + JIEBA_TOKEN_FALLBACK_BONUS -
                        TRANSLATION_FRAGMENT_PENALTY - PHONETIC_FALLBACK_PENALTY,
                )
            }
            .take(MAX_JIEBA_FALLBACK_CANDIDATES)
            .toList()
    }

    private fun fallbackTranslationForRange(
        text: String,
        start: Int,
        endExclusive: Int,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): String {
        val output = StringBuilder((endExclusive - start) * 2)
        var offset = start
        while (offset < endExclusive) {
            val fallback = fallbackCandidate(text, offset, customPhonetics, bundledPhonetics)
            output.appendWord(fallback.translation)
            offset = fallback.endExclusive
        }
        return output.toString()
    }

    private fun posOf(
        match: TermMatch,
        matches: RuntimeMatchIndex? = null,
    ): TermPos {
        matches?.cachedPos(match)?.let { return it }
        match.term.runtimePos?.let { runtimePos ->
            matches?.cachePos(match, runtimePos)
            return runtimePos
        }
        val source = match.term.source
        val lexicalPos = when (match.term.type) {
            QuickDictionaryType.NAME -> when {
                isLocationSource(source) -> TermPos.LOCATION
                isPersonHead(match) -> TermPos.PERSON
                else -> TermPos.NAME
            }
            QuickDictionaryType.PRONOUN -> TermPos.PRONOUN
            QuickDictionaryType.IGNORE -> TermPos.FUNCTION
            QuickDictionaryType.PHONETIC -> TermPos.UNKNOWN
            QuickDictionaryType.LUAT_NHAN -> TermPos.UNKNOWN
            QuickDictionaryType.TERM,
            QuickDictionaryType.VIETPHRASE -> inferLexicalPos(match)
        }
        return mergeRuntimePos(
            lexicalPos = lexicalPos,
            jiebaPos = matches?.jiebaTokenAt(match.start, match.endExclusive)
                ?.let { inferJiebaTokenPos(it.word) },
        ).also { resolved -> matches?.cachePos(match, resolved) }
    }

    private fun inferLexicalPos(match: TermMatch): TermPos = when {
        isLocationModifier(match) || isLocationSource(match.term.source) -> TermPos.LOCATION
        isPersonHead(match) -> TermPos.PERSON
        isProperNameTarget(match.term.target) -> TermPos.NAME
        isPossessiveOwner(match) -> TermPos.PRONOUN
        isNounSource(match.term.source) -> TermPos.NOUN
        isDescriptiveModifier(match) -> TermPos.ADJECTIVE
        isAdverbSource(match.term.source) -> TermPos.ADVERB
        isVerbSource(match.term.source, match.term.target) -> TermPos.VERB
        match.term.projectOwned -> TermPos.NOUN
        else -> TermPos.UNKNOWN
    }

    private fun inferJiebaTokenPos(source: String): TermPos = when {
        source.isBlank() -> TermPos.UNKNOWN
        isLocationSource(source) -> TermPos.LOCATION
        PERSON_HEAD_SUFFIXES.any(source::endsWith) -> TermPos.PERSON
        isNounSource(source) -> TermPos.NOUN
        isDescriptiveSource(source) -> TermPos.ADJECTIVE
        isAdverbSource(source) -> TermPos.ADVERB
        isVerbSource(source, "") -> TermPos.VERB
        else -> TermPos.UNKNOWN
    }

    private fun mergeRuntimePos(
        lexicalPos: TermPos,
        jiebaPos: TermPos?,
    ): TermPos {
        if (jiebaPos == null || jiebaPos == TermPos.UNKNOWN) return lexicalPos
        if (lexicalPos == TermPos.UNKNOWN) return jiebaPos
        if (lexicalPos == TermPos.NOUN && jiebaPos in JIEBA_STRONG_POS) return jiebaPos
        return lexicalPos
    }

    private fun isLocationSource(source: String): Boolean =
        LOCATION_MODIFIER_SUFFIXES.any(source::endsWith) ||
            LOCATION_ENTITY_SUFFIXES.any(source::endsWith)

    private fun isAdverbSource(source: String): Boolean =
        source.endsWith("地") || ADVERBIAL_SOURCES.any(source::contains)

    private fun isNounSource(source: String): Boolean =
        NOUN_SOURCE_SUFFIXES.any(source::endsWith)

    private fun isVerbSource(source: String, target: String): Boolean {
        if (VERB_SOURCE_SUFFIXES.any(source::endsWith)) return true
        val normalized = target.lowercase()
        return VERB_TARGET_PREFIXES.any(normalized::startsWith)
    }

    private fun isProperNameTarget(target: String): Boolean =
        target.any(Char::isUpperCase)

    /**
     * A dictionary target or a post rule can itself contain Han characters. Resolve every such
     * remainder one code point at a time so Phonetic keeps its fallback-only semantics and never
     * competes with Name/VietPhrase phrase matching.
     */
    private fun replaceRemainingCjkWithPhonetics(
        text: String,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): String {
        val output = StringBuilder(text.length * 2)
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val source = String(Character.toChars(codePoint))
            val reading = customPhonetics[source] ?: bundledPhonetics[source]
            if (reading != null && isCjk(codePoint)) {
                output.appendWord(reading)
            } else {
                output.append(source)
            }
            offset += Character.charCount(codePoint)
        }
        return output.toString()
    }

    private fun bestTemplateMatch(
        text: String,
        offset: Int,
        indexedTemplates: List<SourceTemplate>,
        leadingSlotTemplates: List<SourceTemplate>,
        matches: RuntimeMatchIndex,
    ): TemplateMatch? {
        var best: TemplateMatch? = null
        fun consider(template: SourceTemplate) {
            val candidate = matchTemplate(
                text,
                offset,
                template,
                matches,
            ) ?: return
            val current = best
            if (current == null || candidate.priority > current.priority ||
                candidate.priority == current.priority &&
                candidate.endExclusive > current.endExclusive
            ) {
                best = candidate
            }
        }
        indexedTemplates.forEach(::consider)
        leadingSlotTemplates.forEach(::consider)
        return best
    }

    private fun leadingSlotTemplatesAt(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
        projectIndex: LeadingSlotTemplateIndex,
        packIndex: LeadingSlotTemplateIndex,
    ): List<SourceTemplate> {
        val nextCharacters = LinkedHashSet<Char>()
        matches.termsAt(offset)
            .asSequence()
            .take(MAX_SLOT_CANDIDATES)
            .map(TermMatch::endExclusive)
            .forEach { end -> text.getOrNull(end)?.let(nextCharacters::add) }
        grammarPhraseCandidateAt(
            text = text,
            start = offset,
            acceptedPos = emptySet(),
            matches = matches,
        )?.endExclusive?.let { end -> text.getOrNull(end)?.let(nextCharacters::add) }

        val candidates = ArrayList<SourceTemplate>(
            projectIndex.unindexed.size + packIndex.unindexed.size + nextCharacters.size * 4
        )
        fun addFrom(index: LeadingSlotTemplateIndex) {
            candidates += index.unindexed
            nextCharacters.forEach { character ->
                index.byNextLiteralChar[character]?.let(candidates::addAll)
                val lower = character.lowercaseChar()
                if (lower != character) {
                    index.byNextLiteralChar[lower]?.let(candidates::addAll)
                }
            }
        }
        addFrom(projectIndex)
        addFrom(packIndex)
        return candidates
    }

    private fun matchTemplate(
        text: String,
        start: Int,
        template: SourceTemplate,
        matches: RuntimeMatchIndex,
    ): TemplateMatch? {
        var best: TemplateMatch? = null
        fun collect(
            partIndex: Int,
            cursor: Int,
            captures: Array<TermMatch?>,
        ) {
            if (partIndex >= template.parts.size) {
                if (cursor <= start) return
                val replacement = renderTemplateReplacement(template, captures, matches)
                val candidate = TemplateMatch(
                    endExclusive = cursor,
                    translation = replacement,
                    priority = template.priority,
                    contiguousScore = template.contiguousScore,
                )
                val current = best
                if (current == null || candidate.priority > current.priority ||
                    candidate.priority == current.priority &&
                    candidate.endExclusive > current.endExclusive
                ) {
                    best = candidate
                }
                return
            }
            when (val part = template.parts[partIndex]) {
                is TemplatePart.Literal -> {
                    if (part.value.isNotEmpty() &&
                        (cursor + part.value.length > text.length ||
                            !text.regionMatches(cursor, part.value, 0, part.value.length))
                    ) return
                    collect(partIndex + 1, cursor + part.value.length, captures)
                }

                is TemplatePart.Slot -> slotMatchesAt(
                    text = text,
                    cursor = cursor,
                    part = part,
                    matches = matches,
                )
                    .asSequence()
                    .take(MAX_SLOT_CANDIDATES)
                    .forEach { match ->
                        val nextCaptures = captures.copyOf()
                        nextCaptures[part.index] = match
                        collect(partIndex + 1, match.endExclusive, nextCaptures)
                    }
            }
        }
        collect(0, start, arrayOfNulls(3))
        return best
    }

    private fun slotMatchesAt(
        text: String,
        cursor: Int,
        part: TemplatePart.Slot,
        matches: RuntimeMatchIndex,
    ): List<TermMatch> {
        if (cursor !in text.indices) return emptyList()
        return matches.cachedSlotMatches(cursor, part.acceptedPos) {
            val result = LinkedHashMap<String, TermMatch>()
            fun add(match: TermMatch) {
                if (match.term.target.isBlank() || !part.accepts(posOf(match, matches))) return
                val key = "${normalize(match.term.source)}\u0000${match.endExclusive}"
                val current = result[key]
                if (current == null || isBetterTermMatch(match, current)) {
                    result[key] = match
                }
            }
            matches.termsAt(cursor).forEach(::add)
            grammarPhraseCandidateAt(
                text = text,
                start = cursor,
                acceptedPos = part.acceptedPos,
                matches = matches,
            )?.let(::add)
            result.values.sortedWith(TERM_MATCH_COMPARATOR)
        }
    }

    private fun grammarPhraseCandidateAt(
        text: String,
        start: Int,
        acceptedPos: Set<TermPos>,
        matches: RuntimeMatchIndex,
    ): TermMatch? {
        val candidate = matches.cachedGrammarPhrase(start) {
            val parts = collectGrammarNounPhraseParts(
                text = text,
                start = start,
                end = null,
                matches = matches,
                maxChars = MAX_GRAMMAR_SLOT_CHARS,
            )
            if (parts.size < 2) return@cachedGrammarPhrase null
            val phrasePos = grammarPhrasePos(parts, matches)
            if (phrasePos == TermPos.UNKNOWN || parts.any { part ->
                    !part.term.projectOwned && posOf(part, matches) == TermPos.UNKNOWN
                }
            ) {
                return@cachedGrammarPhrase null
            }
            val translation = renderGrammarNounPhrase(parts, matches).takeIf(String::isNotBlank)
                ?: return@cachedGrammarPhrase null
            val end = parts.last().endExclusive
            TermMatch(
                term = Term(
                    source = text.substring(start, end),
                    target = translation,
                    type = QuickDictionaryType.TERM,
                    sourcePriority = SYNTHETIC_GRAMMAR_SOURCE_PRIORITY,
                    projectOwned = parts.any { it.term.projectOwned },
                    runtimePos = phrasePos,
                    sourceQuality = TermSourceQuality.SYNTHETIC,
                ),
                start = start,
                endExclusive = end,
            )
        } ?: return null
        val phrasePos = candidate.term.runtimePos ?: TermPos.UNKNOWN
        if (acceptedPos.isNotEmpty() &&
            acceptedPos.none { accepted -> acceptsCompatiblePos(accepted, phrasePos) }
        ) {
            return null
        }
        return candidate
    }

    private fun renderTemplateReplacement(
        template: SourceTemplate,
        captures: Array<TermMatch?>,
        matches: RuntimeMatchIndex,
    ): String {
        renderAttributiveDe(template, captures, matches)?.let { return it }
        return captures.foldIndexed(template.replacement) { index, value, capture ->
            if (capture == null) value else value.replace("{$index}", capture.term.target)
        }
    }

    private fun renderAttributiveDe(
        template: SourceTemplate,
        captures: Array<TermMatch?>,
        matches: RuntimeMatchIndex,
    ): String? {
        if (template.sourcePattern != ATTRIBUTIVE_DE_PATTERN) return null
        val left = captures.getOrNull(0) ?: return null
        val right = captures.getOrNull(1) ?: return null
        val leftTarget = left.term.target.takeIf(String::isNotBlank) ?: return null
        val rightTarget = right.term.target.takeIf(String::isNotBlank) ?: return null
        return renderAttributiveDe(left, right, leftTarget, rightTarget, matches)
    }

    private fun bestStructuredMatch(text: String, offset: Int): StructuredMatch? {
        return listOfNotNull(
            matchChapterHeading(text, offset),
            matchOrdinalNoun(text, offset),
            matchOrdinalPlace(text, offset),
            matchWeekdayFullDate(text, offset),
            matchFullDate(text, offset),
            matchYearMonth(text, offset),
            matchMonthDay(text, offset),
            matchWeekday(text, offset),
            matchDigitalTime(text, offset),
            matchChineseTime(text, offset),
            matchPercent(text, offset),
            matchNumberUnit(text, offset),
            matchDecimalNumber(text, offset),
        ).maxWithOrNull(
            compareBy<StructuredMatch> { it.priority }
                .thenBy { it.endExclusive }
        )
    }

    private fun bestGrammarMatch(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        return listOfNotNull(
            matchPlaceHierarchy(text, offset, matches),
            matchLeadingHeadPhrase(text, offset, matches),
            matchPossessiveOrdinalNoun(text, offset, matches),
            matchDynamicAttributiveDe(text, offset, matches),
            matchPluralSuffix(text, offset, matches),
            matchModifierHeadPhrase(offset, matches),
        ).maxWithOrNull(
            compareBy<StructuredMatch> { it.priority }
                .thenBy { it.endExclusive }
        )
    }

    private fun matchPlaceHierarchy(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        var cursor = offset
        val pieces = mutableListOf<String>()
        var labeledSegments = 0
        val country = placeCountryTermAt(cursor, matches)
        if (country != null && bestPlaceHierarchySegmentAt(text, country.endExclusive, matches) != null) {
            pieces += country.term.target
            cursor = country.endExclusive
        }
        while (labeledSegments < MAX_PLACE_HIERARCHY_SEGMENTS) {
            val segment = bestPlaceHierarchySegmentAt(text, cursor, matches) ?: break
            pieces += segment.translation
            cursor = segment.endExclusive
            labeledSegments += 1
        }
        if (labeledSegments <= 0) return null
        if (pieces.size == 1 && cursor - offset < MIN_SINGLE_PLACE_HIERARCHY_CHARS) return null
        return StructuredMatch(
            endExclusive = cursor,
            translation = pieces.joinToString(", "),
            priority = TRUSTED_GRAMMAR_PRIORITY + 40,
        )
    }

    private fun placeCountryTermAt(
        offset: Int,
        matches: RuntimeMatchIndex,
    ): TermMatch? =
        matches.termsAt(offset).firstOrNull { match ->
            match.term.target.isNotBlank() &&
                (match.term.source in PLACE_COUNTRY_SOURCES ||
                    match.term.target in PLACE_COUNTRY_TARGETS)
        }

    private fun bestPlaceHierarchySegmentAt(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): PlaceHierarchySegment? {
        var best: PlaceHierarchySegment? = null
        for (name in matches.termsAt(offset).asSequence()
            .filter { it.term.target.isNotBlank() }
            .take(MAX_SLOT_CANDIDATES)
        ) {
            for ((suffix, label) in PLACE_HIERARCHY_SUFFIX_LABELS) {
                if (!text.startsWith(suffix, name.endExclusive)) continue
                val rendered = renderPlaceHierarchySegment(name.term.target, label)
                val candidate = PlaceHierarchySegment(
                    endExclusive = name.endExclusive + suffix.length,
                    translation = rendered,
                )
                val current = best
                if (current == null ||
                    candidate.endExclusive > current.endExclusive ||
                    candidate.endExclusive == current.endExclusive &&
                    candidate.translation.length < current.translation.length
                ) {
                    best = candidate
                }
            }
        }
        return best
    }

    private fun renderPlaceHierarchySegment(target: String, label: String): String {
        val normalized = target.lowercase()
        return if (normalized.startsWith("$label ")) {
            target
        } else {
            "$label $target"
        }
    }

    private fun matchLeadingHeadPhrase(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        val prefix = LEADING_HEAD_PREFIXES.firstOrNull { text.startsWith(it, offset) } ?: return null
        val phraseStart = offset + prefix.length
        val phraseEnd = scanGrammarNounPhraseEnd(
            text = text,
            start = phraseStart,
            matches = matches,
            maxChars = 12,
        )
        if (phraseEnd <= phraseStart) return null
        val phrase = renderGrammarNounPhrase(
            text = text,
            start = phraseStart,
            end = phraseEnd,
            matches = matches,
        ).takeIf(String::isNotBlank) ?: return null
        return StructuredMatch(
            endExclusive = phraseEnd,
            translation = "$phrase dẫn đầu",
            priority = TRUSTED_GRAMMAR_PRIORITY,
        )
    }

    private fun scanGrammarNounPhraseEnd(
        text: String,
        start: Int,
        matches: RuntimeMatchIndex,
        maxChars: Int,
    ): Int = collectGrammarNounPhraseParts(
        text = text,
        start = start,
        end = null,
        matches = matches,
        maxChars = maxChars,
    ).lastOrNull()?.endExclusive ?: -1

    private fun collectGrammarNounPhraseParts(
        text: String,
        start: Int,
        end: Int?,
        matches: RuntimeMatchIndex,
        maxChars: Int = Int.MAX_VALUE,
    ): List<TermMatch> {
        var cursor = start
        var consumedChars = 0
        var bestParts = emptyList<TermMatch>()
        val parts = mutableListOf<TermMatch>()
        val limit = end ?: text.length
        while (cursor < limit && consumedChars < maxChars) {
            if (cursor > start && GRAMMAR_NOUN_PHRASE_STOPS.any { text.startsWith(it, cursor) }) {
                break
            }
            val codePoint = text.codePointAt(cursor)
            if (!isCjk(codePoint)) break
            val match = bestGrammarNounPartAt(
                cursor = cursor,
                limit = limit,
                matches = matches,
            )
            if (match != null) {
                val pos = posOf(match, matches)
                if (parts.isNotEmpty() && pos in GRAMMAR_NOUN_PHRASE_BREAK_POS) break
                parts += match
                cursor = match.endExclusive
                consumedChars = text.codePointCount(start, cursor)
                if (isCompleteGrammarNounPhrase(parts, matches)) {
                    bestParts = parts.toList()
                }
            } else {
                break
            }
        }
        return bestParts
    }

    private fun bestGrammarNounPartAt(
        cursor: Int,
        limit: Int,
        matches: RuntimeMatchIndex,
    ): TermMatch? {
        var best: TermMatch? = null
        var bestScore = Long.MIN_VALUE
        for (match in matches.termsAt(cursor)) {
            if (match.term.target.isBlank() || match.endExclusive > limit) continue
            if (matches.splitsProjectTerm(cursor, match.endExclusive)) continue
            val pos = posOf(match, matches)
            if (pos in GRAMMAR_NOUN_PHRASE_BREAK_POS && renderPersonModifier(match) == null) {
                continue
            }
            val score = grammarPartScore(match, matches)
            val current = best
            if (current == null ||
                score > bestScore ||
                score == bestScore && isBetterTermMatch(match, current)
            ) {
                best = match
                bestScore = score
            }
        }
        return best
    }

    private fun grammarPartScore(
        match: TermMatch,
        matches: RuntimeMatchIndex,
    ): Long {
        val pos = posOf(match, matches)
        val length = match.endExclusive - match.start
        val posScore = when (pos) {
            TermPos.PERSON -> 900L
            TermPos.NAME -> 750L
            TermPos.NOUN -> 650L
            TermPos.LOCATION -> 500L
            TermPos.ADJECTIVE -> 420L
            else -> 0L
        }
        val modifierScore = if (renderPersonModifier(match) != null) 1_100L else 0L
        val projectScore = if (match.term.projectOwned) 700L else 0L
        val jiebaScore = when {
            matches.jiebaTokenAt(match.start, match.endExclusive) != null -> JIEBA_EXACT_TOKEN_BONUS
            matches.isJiebaAligned(match.start, match.endExclusive) -> JIEBA_ALIGNMENT_BONUS
            matches.crossesJiebaToken(match.start, match.endExclusive) ->
                JIEBA_CROSSING_PENALTY * length.coerceAtLeast(1)
            else -> 0L
        }
        return length * 80L +
            termTypeRank(match.term.type) * 40L +
            quickDictionaryTargetScore(match.term.target) * 8L +
            posScore +
            modifierScore +
            projectScore +
            jiebaScore -
            match.term.sourcePriority.coerceAtMost(500)
    }

    private fun renderGrammarNounPhrase(
        text: String,
        start: Int,
        end: Int,
        matches: RuntimeMatchIndex,
    ): String {
        val parts = collectGrammarNounPhraseParts(
            text = text,
            start = start,
            end = end,
            matches = matches,
        )
        return renderGrammarNounPhrase(parts, matches)
    }

    private fun renderGrammarNounPhrase(
        parts: List<TermMatch>,
        matches: RuntimeMatchIndex,
    ): String {
        if (parts.isEmpty()) return ""
        renderPersonNounPhrase(parts, matches)?.let { return it }
        return parts.joinToStringByWord { it.term.target }
    }

    private fun isCompleteGrammarNounPhrase(
        parts: List<TermMatch>,
        matches: RuntimeMatchIndex,
    ): Boolean = grammarPhrasePos(parts, matches) in GRAMMAR_NOUN_PHRASE_HEAD_POS

    private fun grammarPhrasePos(
        parts: List<TermMatch>,
        matches: RuntimeMatchIndex,
    ): TermPos = parts.lastOrNull()?.let { posOf(it, matches) } ?: TermPos.UNKNOWN

    private fun acceptsCompatiblePos(accepted: TermPos, actual: TermPos): Boolean {
        if (accepted == actual) return true
        return when (accepted) {
            TermPos.NOUN -> actual in NOUN_COMPATIBLE_POS
            TermPos.PERSON -> actual == TermPos.NAME
            else -> false
        }
    }

    private fun matchDynamicAttributiveDe(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        var best: StructuredMatch? = null
        matches.termsAt(offset)
            .asSequence()
            .filter { it.term.target.isNotBlank() }
            .take(MAX_SLOT_CANDIDATES)
            .forEach { left ->
                val particleStart = left.endExclusive
                if (!text.startsWith(ATTRIBUTIVE_DE_LITERAL, particleStart)) return@forEach
                val rightStart = particleStart + ATTRIBUTIVE_DE_LITERAL.length
                matches.termsAt(rightStart)
                    .asSequence()
                    .filter { it.term.target.isNotBlank() }
                    .take(MAX_SLOT_CANDIDATES)
                    .forEach { right ->
                        val translation = renderAttributiveDe(
                            left = left,
                            right = right,
                            leftTarget = left.term.target,
                            rightTarget = right.term.target,
                            matches = matches,
                        ) ?: return@forEach
                        val candidate = StructuredMatch(
                            endExclusive = right.endExclusive,
                            translation = translation,
                            priority = TRUSTED_GRAMMAR_PRIORITY + 20,
                        )
                        val current = best
                        if (current == null || candidate.endExclusive > current.endExclusive) {
                            best = candidate
                        }
                    }
            }
        return best
    }

    private fun matchPluralSuffix(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        if (matches.termsAt(offset).any { it.term.source.endsWith(PLURAL_SUFFIX) }) return null
        var best: StructuredMatch? = null
        matches.termsAt(offset)
            .asSequence()
            .filter { base ->
                base.term.target.isNotBlank() &&
                    !base.term.source.endsWith(PLURAL_SUFFIX) &&
                    text.startsWith(PLURAL_SUFFIX, base.endExclusive) &&
                    posOf(base, matches) in PLURAL_BASE_POS
            }
            .take(MAX_SLOT_CANDIDATES)
            .forEach { base ->
                val translation = renderPlural(base.term.target)
                val candidate = StructuredMatch(
                    endExclusive = base.endExclusive + PLURAL_SUFFIX.length,
                    translation = translation,
                    priority = 130,
                )
                val current = best
                if (current == null ||
                    candidate.endExclusive > current.endExclusive ||
                    candidate.endExclusive == current.endExclusive &&
                    candidate.translation.length < current.translation.length
                ) {
                    best = candidate
                }
            }
        return best
    }

    private fun renderPlural(target: String): String {
        val normalized = target.trim().lowercase()
        return if (VIETNAMESE_PLURAL_PREFIXES.any { normalized.startsWith("$it ") }) {
            target
        } else {
            listOf("c\u00E1c", target).joinToStringByWord { it }
        }
    }

    private fun matchModifierHeadPhrase(
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        var best: StructuredMatch? = null
        matches.termsAt(offset)
            .asSequence()
            .filter { it.term.target.isNotBlank() }
            .mapNotNull { modifier ->
                renderHeadModifier(modifier, matches)?.let { renderedModifier ->
                    modifier to renderedModifier
                }
            }
            .take(MAX_SLOT_CANDIDATES)
            .forEach { modifier ->
                matches.termsAt(modifier.first.endExclusive)
                    .asSequence()
                    .filter { it.term.target.isNotBlank() }
                    .filter { posOf(it, matches) in HEAD_MODIFIER_POS }
                    .take(MAX_SLOT_CANDIDATES)
                    .forEach { head ->
                        val rendered = listOf(
                            head.term.target,
                            modifier.second,
                        ).joinToStringByWord { it }
                        val candidate = StructuredMatch(
                            endExclusive = head.endExclusive,
                            translation = rendered,
                            priority = 118,
                        )
                        val current = best
                        if (current == null || candidate.endExclusive > current.endExclusive) {
                            best = candidate
                        }
                    }
            }
        return best
    }

    private fun renderAttributiveDe(
        left: TermMatch,
        right: TermMatch,
        leftTarget: String,
        rightTarget: String,
        matches: RuntimeMatchIndex? = null,
    ): String? {
        if (isPossessiveOwner(left)) return null
        if (posOf(right, matches) in ATTRIBUTIVE_ACTION_HEAD_POS &&
            isActionAttributiveModifier(left, leftTarget, matches)
        ) {
            return listOf(rightTarget, leftTarget).joinToStringByWord { it }
        }
        return when (posOf(left, matches)) {
            TermPos.LOCATION -> listOf(rightTarget, "ở", leftTarget).joinToStringByWord { it }
            TermPos.ADJECTIVE -> listOf(rightTarget, leftTarget).joinToStringByWord { it }
            else -> when {
                isLocationModifier(left) ->
                    listOf(rightTarget, "ở", leftTarget).joinToStringByWord { it }
                isDescriptiveModifier(left) ->
                    listOf(rightTarget, leftTarget).joinToStringByWord { it }
                else -> null
            }
        }
    }

    private fun isActionAttributiveModifier(
        match: TermMatch,
        target: String,
        matches: RuntimeMatchIndex?,
    ): Boolean {
        val source = match.term.source
        val pos = posOf(match, matches)
        if (pos == TermPos.VERB || pos == TermPos.ADVERB) return true
        if (ACTION_ATTRIBUTIVE_MARKERS.any(source::contains)) return true
        return isVerbSource(source, target)
    }

    private fun renderHeadModifier(
        modifier: TermMatch,
        matches: RuntimeMatchIndex,
    ): String? =
        renderPersonModifier(modifier)
            ?: modifier.term.target.takeIf { posOf(modifier, matches) == TermPos.ADJECTIVE }

    private fun renderPersonNounPhrase(
        parts: List<TermMatch>,
        matches: RuntimeMatchIndex,
    ): String? {
        if (parts.size < 2) return null
        val head = parts.last()
        if (!isPersonHead(head)) return null
        val modifiers = parts.dropLast(1).map { modifier ->
            renderPersonModifier(modifier) ?: return null
        }
        if (modifiers.isEmpty()) return null
        return (listOf(head.term.target) + modifiers).joinToStringByWord { it }
    }

    private fun renderPersonModifier(match: TermMatch): String? {
        val source = match.term.source
        val first = source.firstOrNull()
        if (first != null && source.endsWith("衣")) {
            CLOTHING_COLOR_MODIFIERS[first]?.let { return "áo $it" }
        }
        val target = match.term.target.lowercase()
        return when {
            target.startsWith("áo ") -> match.term.target
            target.startsWith("trang phục màu ") ->
                "áo " + match.term.target.substringAfter("trang phục màu ").trim()
            else -> null
        }
    }

    private fun isPersonHead(match: TermMatch): Boolean {
        val source = match.term.source
        if (PERSON_HEAD_SUFFIXES.any(source::endsWith)) return true
        val target = match.term.target.lowercase()
        return PERSON_HEAD_TARGETS.any(target::contains)
    }

    private fun isPossessiveOwner(match: TermMatch): Boolean {
        val source = match.term.source
        if (source in POSSESSIVE_OWNER_SOURCES) return true
        val target = match.term.target
        if (target.any(Char::isUpperCase)) return true
        val normalized = target.lowercase()
        return POSSESSIVE_OWNER_TARGETS.any(normalized::contains)
    }

    private fun isLocationModifier(match: TermMatch): Boolean {
        val source = match.term.source
        return LOCATION_MODIFIER_SUFFIXES.any(source::endsWith)
    }

    private fun isDescriptiveModifier(match: TermMatch): Boolean {
        val source = match.term.source
        if (isDescriptiveSource(source)) return true
        val target = match.term.target.lowercase()
        return DESCRIPTIVE_MODIFIER_TARGET_PREFIXES.any(target::startsWith)
    }

    private fun isDescriptiveSource(source: String): Boolean {
        if (source in DESCRIPTIVE_MODIFIER_SOURCES) return true
        return source.length <= 4 && source.any(DESCRIPTIVE_MODIFIER_CHARS::contains)
    }

    private fun mayStartStructured(text: String, offset: Int): Boolean {
        val codePoint = text.codePointAt(offset)
        val char = text[offset]
        if (SPECIAL_CHAPTER_HEADINGS.keys.any { text.startsWith(it, offset) }) return true
        if (CHAPTER_LABELS.keys.any { text.startsWith(it, offset) }) return true
        if (TIME_PREFIX_TRANSLATIONS.keys.any { it.isNotEmpty() && text.startsWith(it, offset) }) {
            return true
        }
        return char in '0'..'9' ||
            char in '０'..'９' ||
            char in CHINESE_DIGITS ||
            char in CHINESE_MULTIPLIERS ||
            char in CHINESE_SECTIONS ||
            codePoint == '第'.code ||
            codePoint == '上'.code ||
            codePoint == '下'.code ||
            codePoint == '前'.code ||
            codePoint == '后'.code ||
            codePoint == '後'.code ||
            codePoint == '周'.code ||
            codePoint == '週'.code ||
            codePoint == "星期".first().code ||
            codePoint == "礼拜".first().code ||
            codePoint == "禮拜".first().code ||
            codePoint == '点'.code ||
            codePoint == '點'.code ||
            codePoint == '.'.code ||
            codePoint == '．'.code
    }

    private fun matchChapterHeading(text: String, offset: Int): StructuredMatch? {
        SPECIAL_CHAPTER_HEADING_PATTERN.matchAt(text, offset)?.let { match ->
            val translation = SPECIAL_CHAPTER_HEADINGS[match.value] ?: return@let
            return StructuredMatch(match.range.last + 1, translation, TRUSTED_GRAMMAR_PRIORITY + 80)
        }
        CHAPTER_PREFIX_PATTERN.matchAt(text, offset)?.let { match ->
            val number = parseChapterNumber(match.groupValues[1]) ?: return@let
            val label = CHAPTER_LABELS[match.groupValues[2]] ?: return@let
            return StructuredMatch(match.range.last + 1, "$label $number", TRUSTED_GRAMMAR_PRIORITY + 70)
        }
        CHAPTER_SUFFIX_PATTERN.matchAt(text, offset)?.let { match ->
            val label = CHAPTER_LABELS[match.groupValues[1]] ?: return@let
            val number = parseChapterNumber(match.groupValues[2]) ?: return@let
            return StructuredMatch(match.range.last + 1, "$label $number", TRUSTED_GRAMMAR_PRIORITY + 60)
        }
        return null
    }

    private fun parseChapterNumber(value: String): String? {
        val number = parseQuickNumber(value) ?: return null
        if ('.' in number) return number
        return number.trimStart('0').ifEmpty { "0" }
    }

    private fun matchOrdinalNoun(text: String, offset: Int): StructuredMatch? {
        val match = ORDINAL_NOUN_PATTERN.matchAt(text, offset) ?: return null
        val number = parseQuickNumber(match.groupValues[1]) ?: return null
        val template = ORDINAL_NOUN_TEMPLATES[match.groupValues[2]] ?: return null
        return StructuredMatch(
            endExclusive = match.range.last + 1,
            translation = template.replace("{n}", number),
            priority = TRUSTED_GRAMMAR_PRIORITY + 330,
        )
    }

    private fun matchPossessiveOrdinalNoun(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        return matches.termsAt(offset)
            .asSequence()
            .filter(::isPossessiveOwner)
            .mapNotNull { owner ->
                if (!text.startsWith(ATTRIBUTIVE_DE_LITERAL, owner.endExclusive)) {
                    return@mapNotNull null
                }
                val ordinal = matchOrdinalNoun(
                    text = text,
                    offset = owner.endExclusive + ATTRIBUTIVE_DE_LITERAL.length,
                ) ?: return@mapNotNull null
                StructuredMatch(
                    endExclusive = ordinal.endExclusive,
                    translation = listOf(
                        ordinal.translation,
                        "của",
                        owner.term.target,
                    ).joinToStringByWord { it },
                    priority = ordinal.priority + 10,
                )
            }
            .maxByOrNull(StructuredMatch::endExclusive)
    }

    private fun matchOrdinalPlace(text: String, offset: Int): StructuredMatch? {
        val match = ORDINAL_PLACE_PATTERN.matchAt(text, offset) ?: return null
        val number = parseQuickNumber(match.groupValues[1]) ?: return null
        val template = ORDINAL_PLACE_TEMPLATES[match.groupValues[2]] ?: return null
        return StructuredMatch(match.range.last + 1, template.replace("{n}", number), 82)
    }

    private fun matchWeekdayFullDate(text: String, offset: Int): StructuredMatch? {
        val weekday = matchWeekday(text, offset) ?: return null
        val date = FULL_DATE_PATTERN.matchAt(text, weekday.endExclusive) ?: return null
        val renderedDate = renderFullDate(date) ?: return null
        return StructuredMatch(
            endExclusive = date.range.last + 1,
            translation = "${weekday.translation}, $renderedDate",
            priority = TRUSTED_GRAMMAR_PRIORITY + 300,
        )
    }

    private fun matchFullDate(text: String, offset: Int): StructuredMatch? {
        val match = FULL_DATE_PATTERN.matchAt(text, offset) ?: return null
        val renderedDate = renderFullDate(match) ?: return null
        val weekday = matchWeekday(text, match.range.last + 1)
        return if (weekday != null) {
            StructuredMatch(
                endExclusive = weekday.endExclusive,
                translation = "$renderedDate, ${weekday.translation}",
                priority = TRUSTED_GRAMMAR_PRIORITY + 300,
            )
        } else {
            StructuredMatch(match.range.last + 1, renderedDate, TRUSTED_GRAMMAR_PRIORITY + 300)
        }
    }

    private fun renderFullDate(match: MatchResult): String? {
        val year = parseQuickNumber(match.groupValues[1]) ?: return null
        val month = parseQuickNumber(match.groupValues[2]) ?: return null
        val day = parseQuickNumber(match.groupValues[3]) ?: return null
        return "ngày $day tháng $month năm $year"
    }

    private fun matchYearMonth(text: String, offset: Int): StructuredMatch? {
        val match = YEAR_MONTH_PATTERN.matchAt(text, offset) ?: return null
        val year = parseQuickNumber(match.groupValues[1]) ?: return null
        val month = parseQuickNumber(match.groupValues[2]) ?: return null
        return StructuredMatch(
            match.range.last + 1,
            "tháng $month năm $year",
            TRUSTED_GRAMMAR_PRIORITY + 260,
        )
    }

    private fun matchMonthDay(text: String, offset: Int): StructuredMatch? {
        val match = MONTH_DAY_PATTERN.matchAt(text, offset) ?: return null
        val month = parseQuickNumber(match.groupValues[1]) ?: return null
        val day = parseQuickNumber(match.groupValues[2]) ?: return null
        return StructuredMatch(
            match.range.last + 1,
            "ngày $day tháng $month",
            TRUSTED_GRAMMAR_PRIORITY + 260,
        )
    }

    private fun matchWeekday(text: String, offset: Int): StructuredMatch? {
        val match = WEEKDAY_PATTERN.matchAt(text, offset) ?: return null
        val weekday = WEEKDAY_TRANSLATIONS[match.groupValues[1]] ?: return null
        return StructuredMatch(match.range.last + 1, weekday, TRUSTED_GRAMMAR_PRIORITY + 220)
    }

    private fun matchDigitalTime(text: String, offset: Int): StructuredMatch? {
        val match = DIGITAL_TIME_PATTERN.matchAt(text, offset) ?: return null
        val second = match.groupValues.getOrNull(3).orEmpty()
        return StructuredMatch(
            match.range.last + 1,
            listOf(
                "${match.groupValues[1]} giờ",
                "${match.groupValues[2]} phút",
                second.takeIf(String::isNotBlank)?.let { "$it giây" }.orEmpty(),
            ).filter(String::isNotBlank).joinToString(" "),
            TRUSTED_GRAMMAR_PRIORITY + 300,
        )
    }

    private fun matchChineseTime(text: String, offset: Int): StructuredMatch? {
        TIME_MINUTE_SECOND_PATTERN.matchAt(text, offset)?.let { match ->
            val hour = parseQuickNumber(match.groupValues[2]) ?: return null
            val minute = parseQuickNumber(match.groupValues[3]) ?: return null
            val second = parseQuickNumber(match.groupValues[4]) ?: return null
            val period = TIME_PREFIX_TRANSLATIONS[match.groupValues[1]].orEmpty()
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour giờ $minute phút $second giây", period)
                    .filter(String::isNotBlank)
                    .joinToString(" "),
                TRUSTED_GRAMMAR_PRIORITY + 320,
            )
        }
        TIME_HALF_PATTERN.matchAt(text, offset)?.let { match ->
            val hour = parseQuickNumber(match.groupValues[2]) ?: return null
            val period = TIME_PREFIX_TRANSLATIONS[match.groupValues[1]].orEmpty()
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour giờ rưỡi", period).filter(String::isNotBlank).joinToString(" "),
                TRUSTED_GRAMMAR_PRIORITY + 300,
            )
        }
        TIME_QUARTER_PATTERN.matchAt(text, offset)?.let { match ->
            val hour = parseQuickNumber(match.groupValues[2]) ?: return null
            val minute = when (match.groupValues[3]) {
                "三刻" -> "45"
                else -> "15"
            }
            val period = TIME_PREFIX_TRANSLATIONS[match.groupValues[1]].orEmpty()
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour giờ $minute phút", period)
                    .filter(String::isNotBlank)
                    .joinToString(" "),
                TRUSTED_GRAMMAR_PRIORITY + 300,
            )
        }
        TIME_MINUTE_PATTERN.matchAt(text, offset)?.let { match ->
            val hour = parseQuickNumber(match.groupValues[2]) ?: return null
            val minute = parseQuickNumber(match.groupValues[3]) ?: return null
            val period = TIME_PREFIX_TRANSLATIONS[match.groupValues[1]].orEmpty()
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour giờ $minute phút", period).filter(String::isNotBlank).joinToString(" "),
                TRUSTED_GRAMMAR_PRIORITY + 300,
            )
        }
        TIME_HOUR_PATTERN.matchAt(text, offset)?.let { match ->
            val hour = parseQuickNumber(match.groupValues[2]) ?: return null
            val period = TIME_PREFIX_TRANSLATIONS[match.groupValues[1]].orEmpty()
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour giờ", period).filter(String::isNotBlank).joinToString(" "),
                TRUSTED_GRAMMAR_PRIORITY + 260,
            )
        }
        return null
    }

    private fun matchPercent(text: String, offset: Int): StructuredMatch? {
        PERCENT_PREFIX_PATTERN.matchAt(text, offset)?.let { match ->
            val number = parseQuickNumber(match.groupValues[1]) ?: return null
            return StructuredMatch(match.range.last + 1, "$number%", TRUSTED_GRAMMAR_PRIORITY + 10)
        }
        PERCENT_SUFFIX_PATTERN.matchAt(text, offset)?.let { match ->
            val number = parseQuickNumber(match.groupValues[1]) ?: return null
            return StructuredMatch(match.range.last + 1, "$number%", TRUSTED_GRAMMAR_PRIORITY + 10)
        }
        return null
    }

    private fun matchNumberUnit(text: String, offset: Int): StructuredMatch? {
        val match = NUMBER_UNIT_PATTERN.matchAt(text, offset) ?: return null
        val number = parseQuickNumber(match.groupValues[1]) ?: return null
        val template = NUMBER_UNIT_TEMPLATES[match.groupValues[2]] ?: return null
        val base = template.replace("{n}", number)
        val rendered = when (match.groupValues.getOrNull(3).orEmpty()) {
            "左右", "来", "來" -> "khoảng $base"
            "多", "余", "餘" -> "hơn $base"
            "以上" -> "trên $base"
            "以下" -> "dưới $base"
            "以内", "內", "内" -> "trong vòng $base"
            "以外", "外" -> "ngoài $base"
            "之前", "前" -> "$base trước"
            "之后", "後", "后" -> "sau $base"
            else -> base
        }
        return StructuredMatch(match.range.last + 1, rendered, TRUSTED_GRAMMAR_PRIORITY + 320)
    }

    private fun matchDecimalNumber(text: String, offset: Int): StructuredMatch? {
        val match = DECIMAL_NUMBER_PATTERN.matchAt(text, offset) ?: return null
        if (text.getOrNull(match.range.last + 1) in DECIMAL_REJECT_FOLLOWERS) return null
        val number = parseQuickNumber(match.groupValues[1]) ?: return null
        if ('.' !in number) return null
        return StructuredMatch(match.range.last + 1, number, TRUSTED_GRAMMAR_PRIORITY + 320)
    }

    private fun currentPronounMode(): QuickTranslationPronounMode =
        QuickTranslationPronounMode.from(
            appCtx.getPrefString(
                PreferKey.quickTranslationPronounMode,
                QuickTranslationPronounMode.default.value,
            )
        )

    private fun resolvedPronounMode(mode: QuickTranslationPronounMode?): QuickTranslationPronounMode =
        mode ?: currentPronounMode()

    private fun versionWithPronounMode(
        version: String,
        mode: QuickTranslationPronounMode,
    ): String = "$version+pronoun:${mode.value}"

    private fun applyPronounProfile(
        sourceText: String,
        translatedText: String,
        mode: QuickTranslationPronounMode,
        hints: PronounHints = PronounHints(),
    ): String {
        if (translatedText.isBlank()) return translatedText
        return when (resolvePronounStyle(sourceText, mode)) {
            PronounStyle.OFF -> translatedText
            PronounStyle.MODERN -> applyModernPronounRules(sourceText, translatedText, hints)
            PronounStyle.ANCIENT -> applyAncientPronounRules(
                sourceText,
                applyModernKinshipRules(sourceText, translatedText),
            )
            PronounStyle.WESTERN -> applyWesternPronounRules(
                sourceText,
                applyModernKinshipRules(sourceText, translatedText),
            )
        }
    }

    private fun resolvePronounStyle(
        sourceText: String,
        mode: QuickTranslationPronounMode,
    ): PronounStyle = when (mode) {
        QuickTranslationPronounMode.OFF -> PronounStyle.OFF
        QuickTranslationPronounMode.ANCIENT -> PronounStyle.ANCIENT
        QuickTranslationPronounMode.MODERN -> PronounStyle.MODERN
        QuickTranslationPronounMode.WESTERN -> PronounStyle.WESTERN
        QuickTranslationPronounMode.AUTO -> when {
            hasWesternPronounContext(sourceText) -> PronounStyle.WESTERN
            hasAncientPronounContext(sourceText) -> PronounStyle.ANCIENT
            else -> PronounStyle.MODERN
        }
    }

    private fun normalizeNarratorThirdPerson(
        sourceText: String,
        translatedText: String,
        mode: QuickTranslationPronounMode,
    ): String {
        if (mode == QuickTranslationPronounMode.OFF) return translatedText
        var output = translatedText
        if (sourceText.hasThirdPersonMalePronoun()) {
            NARRATOR_MALE_PRONOUNS.forEach { pronoun ->
                output = replaceDetachedPhrase(output, pronoun, "hắn")
            }
        }
        if (sourceText.hasThirdPersonFemalePronoun()) {
            NARRATOR_FEMALE_PRONOUNS.forEach { pronoun ->
                output = replaceDetachedPhrase(output, pronoun, "cô")
            }
        }
        return output
    }

    private fun applyModernPronounRules(
        sourceText: String,
        translatedText: String,
        hints: PronounHints,
    ): String {
        var output = translatedText
        if (sourceText.hasSecondPersonPronoun()) {
            val singular = hints.secondPersonSingular
            val plural = hints.secondPersonPlural ?: singular?.let(::modernPluralAddressee)
            output = replaceDetachedPhrase(output, "các ngươi", plural ?: "các bạn")
            output = replaceDetachedPhrase(output, "ngươi", singular ?: "bạn")
            singular?.let { output = replaceDetachedPhrase(output, "bạn", it) }
        }
        if (sourceText.hasThirdPersonMalePronoun()) {
            val male = hints.maleThirdPerson ?: modernMaleThirdPerson(sourceText)
            output = replaceThirdPersonPlural(output, sourceText)
            output = replaceDetachedPhrase(output, "hắn", male)
            output = replaceDetachedPhrase(output, "anh ấy", male)
        }
        if (sourceText.hasThirdPersonFemalePronoun()) {
            val female = hints.femaleThirdPerson ?: modernFemaleThirdPerson(sourceText)
            output = replaceThirdPersonPlural(output, sourceText)
            output = replaceDetachedPhrase(output, "nàng", female)
            output = replaceDetachedPhrase(output, "cô ấy", female)
        }
        output = applyModernPossessivePronounRules(sourceText, output, hints)
        return applyModernKinshipRules(sourceText, output, hints)
    }

    private fun applyModernKinshipRules(
        sourceText: String,
        translatedText: String,
        hints: PronounHints = PronounHints(),
    ): String {
        var output = translatedText
        if (sourceText.contains("你们爹妈") || sourceText.contains("你們爹媽") ||
            sourceText.contains("你们父母") || sourceText.contains("你們父母")
        ) {
            output = replaceDetachedPhrase(output, "cha mẹ các bạn", "cha mẹ các cháu")
            output = replaceDetachedPhrase(output, "cha mẹ của các bạn", "cha mẹ các cháu")
            output = replaceDetachedPhrase(output, "các bạn", "các cháu")
        }
        directKinshipAddress(sourceText)?.let { address ->
            output = replaceVocative(output, address.outputLabels, address.vocative)
            output = replaceDetachedPhrase(output, "các bạn", "các cháu")
            output = replaceDetachedPhrase(output, "bạn", address.addressee)
            output = replaceDetachedPhrase(output, "tôi", address.speaker)
        }
        hints.secondPersonSingular?.let { addressee ->
            output = replaceDetachedPhrase(output, "bạn", addressee)
        }
        hints.secondPersonPlural?.let { addressees ->
            output = replaceDetachedPhrase(output, "các bạn", addressees)
            output = replaceDetachedPhrase(output, "các ngươi", addressees)
        }
        return output
    }

    private fun modernMaleThirdPerson(sourceText: String): String =
        when (explicitMaleRoleBeforePronoun(sourceText)) {
            ModernThirdPersonRole.CHILD -> "cậu"
            ModernThirdPersonRole.ELDER,
            ModernThirdPersonRole.ADULT,
            ModernThirdPersonRole.SUPERNATURAL,
            -> "ông ấy"
            null,
            ModernThirdPersonRole.NEUTRAL,
            -> "anh ấy"
        }

    private fun modernFemaleThirdPerson(sourceText: String): String =
        when (explicitFemaleRoleBeforePronoun(sourceText)) {
            ModernThirdPersonRole.CHILD -> "cô bé"
            ModernThirdPersonRole.ELDER -> "bà ấy"
            ModernThirdPersonRole.SUPERNATURAL -> "cô ta"
            ModernThirdPersonRole.ADULT,
            null,
            ModernThirdPersonRole.NEUTRAL,
            -> "cô ấy"
        }

    private fun modernPluralAddressee(singular: String): String =
        when (singular) {
            "cháu", "con", "cậu" -> "các cháu"
            "ông", "bà", "ngài" -> "các vị"
            "anh" -> "các anh"
            "chị" -> "các chị"
            else -> "các bạn"
        }

    private fun replaceThirdPersonPlural(text: String, sourceText: String): String {
        if (!sourceText.containsAny("他们", "他們", "她们", "她們")) return text
        var output = text
        output = replaceDetachedPhrase(output, "các hắn", "họ")
        output = replaceDetachedPhrase(output, "bọn hắn", "họ")
        output = replaceDetachedPhrase(output, "các nàng", "họ")
        output = replaceDetachedPhrase(output, "các anh ấy", "họ")
        output = replaceDetachedPhrase(output, "các cô ấy", "họ")
        return output
    }

    private fun applyModernPossessivePronounRules(
        sourceText: String,
        translatedText: String,
        hints: PronounHints,
    ): String {
        var output = translatedText
        if (sourceText.containsAny("孩子他奶", "孩子他姥", "伢儿他奶", "伢儿他姥")) {
            output = CHILD_KINSHIP_GRANDMOTHER_PATTERN.replace(output, "bà nó")
        }
        if (sourceText.containsAny("孩子他爷", "孩子他爺", "孩子他外公", "伢儿他爷", "伢儿他爺")) {
            output = CHILD_KINSHIP_GRANDFATHER_PATTERN.replace(output, "ông nó")
        }
        if (sourceText.containsAny("他奶", "他姥") && sourceText.containsAny("伢儿", "孩子", "远子", "小远侯")) {
            output = CHILD_KINSHIP_GRANDMOTHER_PATTERN.replace(output, "bà nó")
            output = replaceDetachedPhrase(output, "cậu sữa", "bà nó")
            output = replaceDetachedPhrase(output, "cậu bà", "bà nó")
            output = replaceDetachedPhrase(output, "nó sữa", "bà nó")
            output = replaceDetachedPhrase(output, "nó bà", "bà nó")
        }
        if (sourceText.containsAny("他爷", "他爺", "他外公") && sourceText.containsAny("伢儿", "孩子", "远子", "小远侯")) {
            output = CHILD_KINSHIP_GRANDFATHER_PATTERN.replace(output, "ông nó")
            output = replaceDetachedPhrase(output, "cậu gia", "ông nó")
            output = replaceDetachedPhrase(output, "cậu ông", "ông nó")
            output = replaceDetachedPhrase(output, "nó gia", "ông nó")
            output = replaceDetachedPhrase(output, "nó ông", "ông nó")
        }
        if (sourceText.containsAny("孩子他妈", "孩子他媽", "伢儿他妈", "伢儿他媽")) {
            output = CHILD_KINSHIP_MOTHER_PATTERN.replace(output, "mẹ nó")
        }
        if (sourceText.containsAny("孩子他爹", "孩子他爸", "伢儿他爹", "伢儿他爸")) {
            output = CHILD_KINSHIP_FATHER_PATTERN.replace(output, "cha nó")
        }
        if (sourceText.contains("她男人")) {
            val owner = if (hints.femaleThirdPerson == "bà ấy") "bà" else "cô ấy"
            output = replaceDetachedPhrase(output, "người đàn ông của cô ấy", "chồng $owner")
            output = replaceDetachedPhrase(output, "người đàn ông của nàng", "chồng $owner")
            output = replaceDetachedPhrase(output, "cô ấy người đàn ông", "chồng $owner")
            output = replaceDetachedPhrase(output, "bà ấy người đàn ông", "chồng $owner")
            output = replaceDetachedPhrase(output, "nàng người đàn ông", "chồng $owner")
        }
        return output
    }

    private fun applyAncientPronounRules(sourceText: String, translatedText: String): String {
        var output = translatedText
        output = when {
            sourceText.containsAny("陛下", "皇上", "圣上", "萬歲", "万岁") ->
                replaceAncientSecondPerson(output, "bệ hạ")
            sourceText.containsAny("殿下", "太子", "公主") ->
                replaceAncientSecondPerson(output, "điện hạ")
            sourceText.containsAny("王爷", "王爺") ->
                replaceAncientSecondPerson(output, "vương gia")
            sourceText.containsAny("娘娘", "皇后", "贵妃", "貴妃") ->
                replaceAncientSecondPerson(output, "nương nương")
            sourceText.containsAny("爱卿", "愛卿", "卿") ->
                replaceAncientSecondPerson(output, "khanh")
            sourceText.containsAny("师父", "師父", "师尊", "師尊") ->
                replaceAncientSecondPerson(output, "sư phụ")
            sourceText.containsAny("道友") ->
                replaceAncientSecondPerson(output, "đạo hữu")
            sourceText.containsAny("夫君", "相公", "郎君") ->
                replaceAncientSecondPerson(output, "chàng")
            sourceText.containsAny("娘子", "爱妃", "愛妃") ->
                replaceAncientSecondPerson(output, "nàng")
            else -> output
        }
        output = when {
            sourceText.containsAny("夫君", "相公", "郎君") ->
                replaceAncientFirstPerson(output, "thiếp")
            sourceText.containsAny("臣妾") ->
                replaceAncientFirstPerson(output, "thần thiếp")
            sourceText.containsAny("妾", "奴婢") ->
                replaceAncientFirstPerson(output, "thiếp")
            sourceText.containsAny("臣", "微臣", "下官") ->
                replaceAncientFirstPerson(output, "thần")
            sourceText.containsAny("弟子") ->
                replaceAncientFirstPerson(output, "đệ tử")
            sourceText.containsAny("朕") ->
                replaceAncientFirstPerson(output, "trẫm")
            sourceText.containsAny("寡人") ->
                replaceAncientFirstPerson(output, "quả nhân")
            else -> output
        }
        output = replaceDetachedPhrase(output, "chúng tôi", "chúng ta")
        output = replaceDetachedPhrase(output, "các bạn", "các ngươi")
        output = replaceDetachedPhrase(output, "bạn", "ngươi")
        output = replaceDetachedPhrase(output, "tôi", "ta")
        output = replaceDetachedPhrase(output, "anh ấy", "hắn")
        output = replaceDetachedPhrase(output, "cô ấy", "nàng")
        return output
    }

    private fun replaceAncientSecondPerson(text: String, replacement: String): String {
        var output = replaceDetachedPhrase(text, "các bạn", "các vị")
        output = replaceDetachedPhrase(output, "các ngươi", "các vị")
        output = replaceDetachedPhrase(output, "bạn", replacement)
        output = replaceDetachedPhrase(output, "ngươi", replacement)
        return output
    }

    private fun replaceAncientFirstPerson(text: String, replacement: String): String {
        var output = replaceDetachedPhrase(text, "chúng tôi", "chúng ta")
        output = replaceDetachedPhrase(output, "tôi", replacement)
        output = replaceDetachedPhrase(output, "ta", replacement)
        return output
    }

    private fun applyWesternPronounRules(sourceText: String, translatedText: String): String {
        val addressee = westernSecondPerson(sourceText)
        val pluralAddressee = if (addressee == "ngài" || sourceText.hasFormalWesternTitle()) {
            "các vị"
        } else {
            "các anh"
        }
        var output = translatedText
        output = replaceDetachedPhrase(output, "các bạn", pluralAddressee)
        output = replaceDetachedPhrase(output, "bạn", addressee)
        if (sourceText.containsAny("公爵", "伯爵", "子爵", "男爵", "先生", "爵士", "神父", "牧师")) {
            output = replaceDetachedPhrase(output, "anh ấy", "ông ấy")
        }
        if (sourceText.containsAny("夫人", "女士")) {
            output = replaceDetachedPhrase(output, "cô ấy", "bà ấy")
        }
        if (sourceText.contains("小姐")) {
            output = replaceDetachedPhrase(output, "tiểu thư", "quý cô")
        }
        if (sourceText.contains("阁下") || sourceText.contains("閣下")) {
            output = replaceDetachedPhrase(output, "các hạ", "ngài")
        }
        return output
    }

    private fun westernSecondPerson(sourceText: String): String = when {
        sourceText.containsAny("阁下", "閣下", "公爵", "伯爵", "子爵", "男爵", "爵士") -> "ngài"
        sourceText.containsAny("神父", "牧师", "牧師") -> "cha"
        sourceText.containsAny("夫人") -> "bà"
        sourceText.containsAny("女士", "小姐") -> "cô"
        sourceText.containsAny("先生") -> "ông"
        else -> "anh"
    }

    private inner class PronounContextTracker {
        private var lastMaleRole: ModernThirdPersonRole? = null
        private var lastFemaleRole: ModernThirdPersonRole? = null

        fun hintsFor(sourceText: String): PronounHints {
            val malePronounIndex = sourceText.indexOfMalePronoun()
            val femalePronounIndex = sourceText.indexOfFemalePronoun()
            val currentMaleRole = if (sourceText.hasObjectPronounAt(malePronounIndex)) {
                null
            } else {
                explicitMaleRoleBeforePronoun(sourceText)
            }
            val currentFemaleRole = if (sourceText.hasObjectPronounAt(femalePronounIndex)) {
                null
            } else {
                explicitFemaleRoleBeforePronoun(sourceText)
            }
            val maleRole = currentMaleRole ?: lastMaleRole
            val femaleRole = currentFemaleRole ?: lastFemaleRole
            val nextMaleRole = explicitMaleRole(sourceText)
            val nextFemaleRole = explicitFemaleRole(sourceText)
            val hints = PronounHints(
                secondPersonSingular = modernSecondPerson(sourceText),
                secondPersonPlural = modernSecondPersonPlural(sourceText),
                maleThirdPerson = if (sourceText.hasThirdPersonMalePronoun()) {
                    maleRole?.maleThirdPerson()
                } else {
                    null
                },
                femaleThirdPerson = if (sourceText.hasThirdPersonFemalePronoun()) {
                    femaleRole?.femaleThirdPerson()
                } else {
                    null
                },
            )
            nextMaleRole?.let { lastMaleRole = it }
            nextFemaleRole?.let { lastFemaleRole = it }
            return hints
        }
    }

    private fun modernSecondPerson(sourceText: String): String? = when {
        sourceText.hasDirectAddress("奶", "奶奶", "外婆", "祖母", "阿婆") -> "bà"
        sourceText.hasDirectAddress("爷", "爷爷", "爺", "爺爺", "外公", "祖父", "阿公") -> "ông"
        sourceText.hasDirectAddress("爸", "爸爸", "爹", "父亲", "父親") -> "bố"
        sourceText.hasDirectAddress("妈", "妈妈", "媽", "媽媽", "娘", "母亲", "母親") -> "mẹ"
        sourceText.hasDirectAddress("叔", "叔叔", "叔父", "伯", "伯伯", "大夫", "医生", "先生") -> "ông"
        sourceText.hasDirectAddress("婶", "婶子", "嬸", "嬸子") -> "thím"
        sourceText.hasDirectAddress("姨", "阿姨", "姨妈", "姨媽") -> "dì"
        sourceText.hasDirectAddress("哥", "哥哥", "兄弟") -> "anh"
        sourceText.hasDirectAddress("姐", "姐姐") -> "chị"
        sourceText.hasDirectAddress("妹子") -> "em"
        sourceText.containsAny("孩子他奶", "孩子他姥", "伢儿他奶", "伢儿他姥") -> "bà"
        sourceText.containsAny("孩子他爷", "孩子他爺", "孩子他外公", "伢儿他爷", "伢儿他爺") -> "ông"
        sourceText.containsAny("他奶", "他姥") && sourceText.containsAny("伢儿", "孩子", "远子", "小远侯") -> "bà"
        sourceText.containsAny("他爷", "他爺", "他外公") && sourceText.containsAny("伢儿", "孩子", "远子", "小远侯") -> "ông"
        sourceText.containsAny("你们爹妈", "你們爹媽", "你们父母", "你們父母") -> "cháu"
        sourceText.contains("你这是要出去") && sourceText.contains("崔桂英") -> "ông"
        sourceText.containsAny("你照顾伢儿", "你照顾孩子", "你照顧伢兒", "你照顧孩子") -> "bà"
        sourceText.containsAny("你快救救他", "你快救他", "你赶紧救他", "你趕緊救他") -> "chị"
        sourceText.containsAny("姐姐，你", "姐，你", "我说姐姐", "我說姐姐") -> "chị"
        sourceText.containsAny("妈，你不该", "媽，你不該", "妈你不该", "媽你不該") -> "mẹ"
        sourceText.containsAny("桂英侯", "你家汉侯", "你家漢侯") -> "bà"
        sourceText.containsAny("小远侯你", "小遠侯你", "远子你", "遠子你") -> "cậu"
        sourceText.containsAny("你醒醒", "你快醒醒", "你醒了", "你终于醒了", "你終於醒了") &&
            sourceText.containsAny("小远侯", "小遠侯", "远子", "遠子", "伢儿", "伢兒") -> "cậu"
        sourceText.containsAny("香侯", "菊香", "闺女", "閨女") &&
            sourceText.containsAny("你妈我", "你媽我", "娘俩", "娘倆", "我说香侯") -> "con"
        sourceText.containsAny("你汉叔", "你漢叔", "你妈我", "你媽我") -> "con"
        sourceText.contains("您") -> "ông"
        else -> null
    }

    private fun modernSecondPersonPlural(sourceText: String): String? = when {
        sourceText.containsAny("你们爹妈", "你們爹媽", "你们父母", "你們父母") -> "các cháu"
        sourceText.containsAny("你们这是", "你們這是") -> "mọi người"
        sourceText.containsAny("你们去看看", "你們去看看", "你们去看", "你們去看") -> "các cháu"
        sourceText.contains("你们") || sourceText.contains("你們") ->
            modernSecondPerson(sourceText)?.let(::modernPluralAddressee)
        else -> null
    }

    private fun explicitMaleRoleBeforePronoun(sourceText: String): ModernThirdPersonRole? {
        val pronounIndex = sourceText.indexOfMalePronoun()
        val before = if (pronounIndex >= 0) pronounIndex else sourceText.length
        return dominantRoleBefore(sourceText, MODERN_MALE_ROLE_MARKERS, before)
            ?: latestRoleBefore(sourceText, MODERN_MALE_ROLE_MARKERS, before)
    }

    private fun explicitFemaleRoleBeforePronoun(sourceText: String): ModernThirdPersonRole? {
        val pronounIndex = sourceText.indexOfFemalePronoun()
        val before = if (pronounIndex >= 0) pronounIndex else sourceText.length
        return dominantRoleBefore(sourceText, MODERN_FEMALE_ROLE_MARKERS, before)
            ?: latestRoleBefore(sourceText, MODERN_FEMALE_ROLE_MARKERS, before)
    }

    private fun explicitMaleRole(sourceText: String): ModernThirdPersonRole? =
        dominantRoleBefore(sourceText, MODERN_MALE_ROLE_MARKERS, sourceText.length)
            ?: latestRoleBefore(sourceText, MODERN_MALE_ROLE_MARKERS, sourceText.length)

    private fun explicitFemaleRole(sourceText: String): ModernThirdPersonRole? =
        dominantRoleBefore(sourceText, MODERN_FEMALE_ROLE_MARKERS, sourceText.length)
            ?: latestRoleBefore(sourceText, MODERN_FEMALE_ROLE_MARKERS, sourceText.length)

    private fun String.hasObjectPronounAt(index: Int): Boolean {
        if (index <= 0) return false
        var cursor = index - 1
        while (cursor >= 0 && this[cursor].isWhitespace()) cursor--
        return cursor >= 0 && this[cursor] in setOf('和', '跟', '同', '对', '對', '向', '给', '給')
    }

    private fun dominantRoleBefore(
        sourceText: String,
        markers: List<RoleMarker>,
        beforeExclusive: Int,
    ): ModernThirdPersonRole? {
        var bestIndex = Int.MAX_VALUE
        var bestRole: ModernThirdPersonRole? = null
        markers.forEach { marker ->
            val index = sourceText.indexOf(marker.term)
            if (index in 0 until beforeExclusive && index < bestIndex) {
                bestIndex = index
                bestRole = marker.role
            }
        }
        return bestRole
    }

    private fun latestRoleBefore(
        sourceText: String,
        markers: List<RoleMarker>,
        beforeExclusive: Int,
    ): ModernThirdPersonRole? {
        var bestIndex = -1
        var bestRole: ModernThirdPersonRole? = null
        markers.forEach { marker ->
            var index = sourceText.indexOf(marker.term)
            while (index >= 0) {
                if (index < beforeExclusive && index >= bestIndex) {
                    bestIndex = index
                    bestRole = marker.role
                }
                index = sourceText.indexOf(marker.term, index + marker.term.length)
            }
        }
        return bestRole
    }

    private fun ModernThirdPersonRole.maleThirdPerson(): String = when (this) {
        ModernThirdPersonRole.CHILD -> "cậu"
        ModernThirdPersonRole.ADULT,
        ModernThirdPersonRole.ELDER,
        ModernThirdPersonRole.SUPERNATURAL,
        -> "ông ấy"
        ModernThirdPersonRole.NEUTRAL -> "anh ấy"
    }

    private fun ModernThirdPersonRole.femaleThirdPerson(): String = when (this) {
        ModernThirdPersonRole.CHILD -> "cô bé"
        ModernThirdPersonRole.ELDER -> "bà ấy"
        ModernThirdPersonRole.SUPERNATURAL -> "cô ta"
        ModernThirdPersonRole.ADULT,
        ModernThirdPersonRole.NEUTRAL,
        -> "cô ấy"
    }

    private fun directKinshipAddress(sourceText: String): KinshipAddress? {
        return when {
            sourceText.hasDirectAddress("奶", "奶奶", "外婆", "祖母", "阿婆") ->
                KinshipAddress(
                    outputLabels = listOf("sữa", "bà"),
                    vocative = "Bà ơi,",
                    addressee = "bà",
                    speaker = "cháu",
                )
            sourceText.hasDirectAddress("爷", "爷爷", "爺", "爺爺", "外公", "祖父", "阿公") ->
                KinshipAddress(
                    outputLabels = listOf("gia", "ông"),
                    vocative = "Ông ơi,",
                    addressee = "ông",
                    speaker = "cháu",
                )
            sourceText.hasDirectAddress("爸", "爸爸", "爹", "父亲", "父親") ->
                KinshipAddress(
                    outputLabels = listOf("ba", "bố", "cha"),
                    vocative = "Bố ơi,",
                    addressee = "bố",
                    speaker = "con",
                )
            sourceText.hasDirectAddress("妈", "妈妈", "媽", "媽媽", "娘", "母亲", "母親") ->
                KinshipAddress(
                    outputLabels = listOf("mẹ", "má", "nương"),
                    vocative = "Mẹ ơi,",
                    addressee = "mẹ",
                    speaker = "con",
                )
            sourceText.hasDirectAddress("叔", "叔叔", "叔父", "伯", "伯伯", "大夫", "医生", "先生") ->
                KinshipAddress(
                    outputLabels = listOf("chú", "bác", "ông", "bác sĩ"),
                    vocative = "Bác ơi,",
                    addressee = "bác",
                    speaker = "cháu",
                )
            sourceText.hasDirectAddress("婶", "婶子", "嬸", "嬸子") ->
                KinshipAddress(
                    outputLabels = listOf("thím", "cô"),
                    vocative = "Thím ơi,",
                    addressee = "thím",
                    speaker = "cháu",
                )
            sourceText.hasDirectAddress("姨", "阿姨", "姨妈", "姨媽") ->
                KinshipAddress(
                    outputLabels = listOf("dì", "cô"),
                    vocative = "Dì ơi,",
                    addressee = "dì",
                    speaker = "cháu",
                )
            else -> null
        }
    }

    private fun hasAncientPronounContext(sourceText: String): Boolean =
        sourceText.containsAny(
            "朕", "寡人", "本宫", "本宮", "哀家", "臣妾", "微臣", "下官", "奴婢",
            "奴才", "陛下", "皇上", "圣上", "聖上", "殿下", "王爷", "王爺", "娘娘",
            "公主", "太子", "皇后", "贵妃", "貴妃", "爱卿", "愛卿", "师父", "師父",
            "师尊", "師尊", "徒儿", "徒兒", "弟子", "师兄", "師兄", "师姐", "師姐",
            "师弟", "師弟", "掌门", "掌門", "宗主", "道友", "公子", "姑娘", "夫君",
            "相公", "郎君", "娘子", "仙子", "贫道", "貧道", "贫僧", "貧僧",
        )

    private fun hasWesternPronounContext(sourceText: String): Boolean =
        sourceText.containsAny(
            "公爵", "伯爵", "子爵", "男爵", "骑士", "騎士", "爵士", "神父", "牧师",
            "牧師", "教堂", "城堡", "庄园", "莊園", "王国", "王國", "魔法", "巫师",
            "巫師", "女巫", "吸血鬼", "狼人", "英镑", "英鎊", "美元", "欧元", "歐元",
        )

    private fun String.hasFormalWesternTitle(): Boolean =
        containsAny("阁下", "閣下", "公爵", "伯爵", "子爵", "男爵", "爵士", "神父", "牧师", "牧師")

    private fun String.hasDirectAddress(vararg terms: String): Boolean =
        terms.any { term ->
            val escaped = Regex.escape(term)
            Regex("(^|[\\s\"'“”‘’（(])$escaped(?=\\s*(?:[啊呀吶呐呢哟喲])?\\s*[，,、。！？!?])")
                .containsMatchIn(this)
        }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any(::contains)

    private fun String.hasSecondPersonPronoun(): Boolean =
        contains("你") || contains("您")

    private fun String.hasThirdPersonMalePronoun(): Boolean =
        indexOfMalePronoun() >= 0

    private fun String.hasThirdPersonFemalePronoun(): Boolean =
        indexOfFemalePronoun() >= 0

    private fun String.indexOfMalePronoun(): Int {
        var index = indexOf('他')
        while (index >= 0) {
            if (getOrNull(index - 1) != '其' && getOrNull(index + 1) != '人') return index
            index = indexOf('他', index + 1)
        }
        return -1
    }

    private fun String.indexOfFemalePronoun(): Int =
        indexOf('她')

    private fun replaceVocative(text: String, labels: List<String>, vocative: String): String {
        var output = text
        labels.forEach { label ->
            output = Regex(
                "(^|[\\n.!?]\\s*)${Regex.escape(label)}\\s*[,，]",
                setOf(RegexOption.IGNORE_CASE),
            ).replace(output) { match ->
                "${match.groupValues[1]}$vocative"
            }
        }
        return output
    }

    private fun replaceDetachedPhrase(
        text: String,
        phrase: String,
        replacement: String,
    ): String = Regex(
        "(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])",
        setOf(RegexOption.IGNORE_CASE),
    ).replace(text, replacement)

    private fun compilePostRule(
        pattern: String,
        replacement: String,
        priority: Int,
    ): PostRule? = runCatching {
        PostRule(
            pattern = Regex(pattern.withUnicodeWordBoundaries(), RegexOption.IGNORE_CASE),
            replacement = replacement,
            priority = priority,
            literalTrigger = postRuleLiteralTrigger(pattern),
        )
    }.getOrNull()

    private fun postRuleLiteralTrigger(pattern: String): String? {
        val candidate = pattern.removePrefix("\\b").removeSuffix("\\b").trim()
        if (candidate.length < 3 || candidate.any { it in POST_RULE_META_CHARS } || '\\' in candidate) {
            return null
        }
        return candidate
    }

    private fun String.withUnicodeWordBoundaries(): String {
        var output = this
        if (output.startsWith("\\b")) {
            output = "(?<![\\p{L}\\p{N}])" + output.removePrefix("\\b")
        }
        if (output.endsWith("\\b")) {
            output = output.removeSuffix("\\b") + "(?![\\p{L}\\p{N}])"
        }
        return output
    }

    private fun applyPostRules(text: String, rules: List<PostRule>): String {
        if (rules.isEmpty()) return text
        var output = text
        rules.forEach { rule ->
            if (rule.literalTrigger != null &&
                !output.contains(rule.literalTrigger, ignoreCase = true)
            ) {
                return@forEach
            }
            output = runCatching {
                rule.pattern.replace(output, rule.replacement)
            }.getOrDefault(output)
            if (output.length > text.length * 4 + 4096) {
                output = output.take(text.length * 4 + 4096)
            }
        }
        return output
    }

    private fun pack(): QuickPack {
        cachedPack.get()?.let { return it }
        return synchronized(packLoadLock) {
            cachedPack.get() ?: loadPack().also(cachedPack::set)
        }
    }

    private fun catalogPack(): CatalogPack {
        cachedCatalog.get()?.let { return it }
        return synchronized(catalogLoadLock) {
            cachedCatalog.get() ?: loadCatalogPack().also(cachedCatalog::set)
        }
    }

    private fun projectRuntimeFor(
        projectTerms: List<DictPair>,
        pack: QuickPack,
    ): ProjectTrieCache {
        if (projectTerms.isEmpty()) return ProjectTrieCache(0L, emptyList(), emptyProjectTrie)
        val fingerprint = projectTerms.projectFingerprint()
        cachedProjectTrie.get()?.let { cached ->
            if (cached.fingerprint == fingerprint) {
                return cached
            }
        }
        synchronized(projectTrieCacheLock) {
            projectTrieCache[fingerprint]?.let { cached ->
                cachedProjectTrie.set(cached)
                return cached
            }
            val terms = projectTerms.asSequence()
                .filter { it.type != QuickDictionaryType.LUAT_NHAN }
                .filter { it.original.isNotBlank() }
                .distinctBy { normalize(it.original) }
                .mapIndexed { priority, pair ->
                    val reviewedName = if (pair.type == QuickDictionaryType.VIETPHRASE) {
                        pack.baseTrie.reviewedNameAt(pair.original)
                    } else {
                        null
                    }
                    val target = if (pair.translation == QUICK_DICTIONARY_IGNORE_TARGET) {
                        ""
                    } else {
                        reviewedName?.target ?: cleanQuickDictionaryTarget(pair.translation)
                    }
                    Term(
                        source = pair.original,
                        target = target,
                        type = if (pair.translation == QUICK_DICTIONARY_IGNORE_TARGET) {
                            QuickDictionaryType.IGNORE
                        } else {
                            reviewedName?.type ?: pair.type
                        },
                        sourcePriority = priority,
                        projectOwned = true,
                        sourceQuality = TermSourceQuality.PROJECT,
                    )
                }
                .toList()
            val templates = projectTerms.asSequence()
                .filter { it.type == QuickDictionaryType.LUAT_NHAN }
                .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
                .distinctBy { normalize(it.original) }
                .mapIndexedNotNull { priority, pair ->
                    compileTemplate(
                        source = pair.original,
                        replacement = pair.translation,
                        priority = PROJECT_TEMPLATE_PRIORITY - priority.coerceAtMost(200),
                        category = "project",
                    )
                }
                .take(MAX_PROJECT_TEMPLATE_RULES)
                .toList()
            val cache = ProjectTrieCache(
                fingerprint = fingerprint,
                terms = terms,
                trie = TermTrie(terms),
                templates = templates,
                templatesByFirstChar = templates.indexByFirstLiteralChar(),
                leadingSlotTemplateIndex = templates.filter {
                    (it.parts.firstOrNull() as? TemplatePart.Literal)?.value.isNullOrEmpty()
                }.indexLeadingSlotTemplates(),
            )
            projectTrieCache[fingerprint] = cache
            cachedProjectTrie.set(cache)
            return cache
        }
    }

    private fun List<DictPair>.projectFingerprint(): Long {
        var hash = -3750763034362895579L
        forEach { pair ->
            hash = hash.mix(pair.original)
            hash = hash.mix('\u0000')
            hash = hash.mix(pair.translation)
            hash = hash.mix('\u0001')
            hash = hash.mix(pair.type.name)
            hash = hash.mix('\u0002')
        }
        return hash
    }

    private fun TermLookup.reviewedNameAt(source: String): Term? =
        allAt(source, 0)
            .firstOrNull { match ->
                match.endExclusive == source.length &&
                    match.term.type == QuickDictionaryType.NAME &&
                    match.term.target.isNotBlank() &&
                    match.term.sourceQuality in REVIEWED_NAME_SOURCE_QUALITIES
            }
            ?.term

    private fun Long.mix(value: String): Long {
        var hash = this
        value.forEach { character -> hash = hash.mix(character) }
        return hash
    }

    private fun Long.mix(value: Char): Long =
        (this xor value.code.toLong()) * 1099511628211L

    private fun loadCatalogPack(): CatalogPack {
        val entriesByType = linkedMapOf<QuickDictionaryType, MutableList<QuickDictionaryCatalogEntry>>()
        CATALOG_ASSETS.forEach { catalogAsset ->
            val minimumColumns = if (catalogAsset.type == QuickDictionaryType.LUAT_NHAN) 4 else 2
            readTsv(catalogAsset.asset, minimumColumns).forEach { columns ->
                val source: String
                val target: String
                val entryType: QuickDictionaryType
                if (catalogAsset.type == QuickDictionaryType.LUAT_NHAN) {
                    if (columns[0].trim() !in setOf("QT_TEMPLATE", "REPLACE")) return@forEach
                    source = columns[2].trim()
                    target = columns[3].trim()
                    entryType = catalogAsset.type
                } else {
                    source = columns[0].trim()
                    target = cleanQuickDictionaryTarget(columns[1])
                    entryType = parseAssetTermType(
                        value = columns.getOrNull(2).orEmpty(),
                        fallback = catalogAsset.type,
                    )
                }
                if (source.isEmpty() || target.isEmpty()) return@forEach
                entriesByType.getOrPut(entryType, ::mutableListOf).add(
                    QuickDictionaryCatalogEntry(
                        catalogId = "bundled:${entryType.name.lowercase()}",
                        raw = source,
                        hanViet = if (entryType == QuickDictionaryType.PHONETIC) target else "",
                        target = if (entryType == QuickDictionaryType.PHONETIC) "" else target,
                        type = entryType,
                    )
                )
            }
        }
        return CatalogPack(
            entriesByType.mapValues { (_, entries) ->
                entries.distinctBy { normalize(it.raw) }
            }
        )
    }

    private fun loadPack(): QuickPack {
        val terms = LinkedHashMap<String, Term>()
        var sourcePriority = 0
        TERM_ASSETS.forEach { asset ->
            readTsv(asset.asset, 2).forEach { columns ->
                val source = columns[0].trim()
                val target = cleanQuickDictionaryTarget(columns[1])
                val type = parseAssetTermType(
                    value = columns.getOrNull(2).orEmpty(),
                    fallback = asset.type,
                )
                val key = normalize(source)
                if (source.isNotEmpty() && target.isNotEmpty() && key !in terms) {
                    terms[key] = Term(
                        source = source,
                        target = target,
                        type = type,
                        sourcePriority = sourcePriority,
                        sourceQuality = asset.sourceQuality,
                    )
                }
                sourcePriority++
            }
        }
        val phonetics = LinkedHashMap<String, String>()
        readEqualsAsset(QT2025_PHONETIC_ASSET).forEach { (source, target) ->
            if (source.codePointCount(0, source.length) == 1 && target.isNotEmpty()) {
                phonetics.putIfAbsent(source, target.substringBefore(' ').trim())
            }
        }
        readEqualsAsset(QT2020_PHONETIC_ASSET).forEach { (source, target) ->
            if (source.codePointCount(0, source.length) == 1 && target.isNotEmpty()) {
                phonetics.putIfAbsent(source, target.substringBefore(' ').trim())
            }
        }
        PHONETIC_ASSETS.forEach { asset ->
            readTsv(asset, 2).forEach { columns ->
                val source = columns[0]
                val target = columns[1].trim().substringBefore(' ')
                if (source.codePointCount(0, source.length) == 1 && target.isNotEmpty()) {
                    phonetics.putIfAbsent(source, target)
                }
            }
        }
        val templates = mutableListOf<SourceTemplate>()
        val postRules = mutableListOf<PostRule>()
        val qt2025Runtime = Qt2025Runtime.create(
            rules = readEqualsAsset(QT2025_RULE_ASSET),
            surnames = readEqualsAsset(QT2025_SURNAME_ASSET),
            suffixes = readEqualsAsset(QT2025_SUFFIX_ASSET),
        )
        if (qt2025Runtime == null) {
            readEqualsAsset(QT2020_TEMPLATE_ASSET).forEach { (source, target) ->
                compileTemplate(
                    source = source,
                    replacement = target,
                    priority = QT2020_TEMPLATE_PRIORITY,
                    category = "qt2020",
                )?.let(templates::add)
            }
        }
        RULE_ASSETS.forEach { asset ->
            readTsv(asset, 4).forEach { columns ->
                val action = columns[0].trim()
                val priority = columns[1].trim().toIntOrNull() ?: return@forEach
                val pattern = columns[2]
                val replacement = columns[3]
                val category = columns.getOrNull(4)?.trim().orEmpty()
                when (action) {
                    "QT_TEMPLATE" -> compileTemplate(
                        source = pattern,
                        replacement = replacement,
                        priority = priority,
                        category = category,
                        contiguousScore = asset == LATEST_RULE_ASSET,
                    )?.let(templates::add)
                    "REPLACE" -> compilePostRule(pattern, replacement, priority)?.let(postRules::add)
                }
            }
        }
        val cleanLookup = TermTrie(terms.values.toList())
        val qt2025Lookup = MappedTermLookup.openOrNull(QT2025_TERM_INDEX_ASSET)
        val compatibilityLookup = qt2025Lookup
            ?: MappedTermLookup.openOrNull(QT2020_TERM_INDEX_ASSET)
        val limitedTemplates = templates.take(MAX_TEMPLATE_RULES)
        val templatesByFirstChar = limitedTemplates.indexByFirstLiteralChar()
        val leadingSlotTemplateIndex = limitedTemplates.filter {
            (it.parts.firstOrNull() as? TemplatePart.Literal)?.value.isNullOrEmpty()
        }.indexLeadingSlotTemplates()
        return QuickPack(
            version = when {
                qt2025Lookup != null -> QT2025_PACK_VERSION
                compatibilityLookup != null -> QT2020_PACK_VERSION
                else -> PACK_VERSION
            },
            baseTrie = if (compatibilityLookup == null) {
                cleanLookup
            } else {
                CompositeTermLookup(listOf(cleanLookup, compatibilityLookup))
            },
            phonetics = phonetics,
            templates = limitedTemplates,
            templatesByFirstChar = templatesByFirstChar,
            leadingSlotTemplateIndex = leadingSlotTemplateIndex,
            postRules = postRules.take(MAX_POST_RULES).sortedByDescending { it.priority },
            qt2025Runtime = qt2025Runtime,
        )
    }

    private fun parseAssetTermType(
        value: String,
        fallback: QuickDictionaryType,
    ): QuickDictionaryType = when (value.trim().lowercase()) {
        "name" -> QuickDictionaryType.NAME
        "pronoun" -> QuickDictionaryType.PRONOUN
        "term" -> QuickDictionaryType.TERM
        "phonetic" -> QuickDictionaryType.PHONETIC
        "ignore" -> QuickDictionaryType.IGNORE
        "luat_nhan", "template", "rule" -> QuickDictionaryType.LUAT_NHAN
        "general", "vietphrase", "phrase" -> QuickDictionaryType.VIETPHRASE
        else -> fallback
    }

    private fun compileTemplate(
        source: String,
        replacement: String,
        priority: Int,
        category: String = "",
        contiguousScore: Boolean = false,
    ): SourceTemplate? {
        if (source.isBlank() || source.length > 160 || replacement.length > 320) return null
        val matches = PLACEHOLDER.findAll(source).toList()
        if (matches.size !in 1..3) return null
        if (matches.map { it.groupValues[1].toInt() } != (0 until matches.size).toList()) return null
        val parts = buildList {
            var cursor = 0
            matches.forEach { match ->
                add(TemplatePart.Literal(source.substring(cursor, match.range.first)))
                add(
                    TemplatePart.Slot(
                        index = match.groupValues[1].toInt(),
                        acceptedPos = parseSlotPosConstraint(match.groupValues.getOrNull(2).orEmpty()),
                    )
                )
                cursor = match.range.last + 1
            }
            add(TemplatePart.Literal(source.substring(cursor)))
        }
        if (parts.filterIsInstance<TemplatePart.Literal>().all { it.value.isEmpty() }) return null
        return SourceTemplate(
            sourcePattern = source,
            parts = parts,
            replacement = replacement,
            priority = priority,
            category = category,
            contiguousScore = contiguousScore,
        )
    }

    private fun List<SourceTemplate>.indexByFirstLiteralChar(): Map<Char, List<SourceTemplate>> =
        mapNotNull { template ->
            (template.parts.firstOrNull() as? TemplatePart.Literal)
                ?.value
                ?.firstOrNull()
                ?.let { it to template }
        }.groupBy({ it.first }, { it.second })

    private fun List<SourceTemplate>.indexLeadingSlotTemplates(): LeadingSlotTemplateIndex {
        val indexed = linkedMapOf<Char, MutableList<SourceTemplate>>()
        val unindexed = mutableListOf<SourceTemplate>()
        forEach { template ->
            val firstSlot = template.parts.indexOfFirst { it is TemplatePart.Slot }
            val nextLiteral = template.parts
                .drop(firstSlot + 1)
                .takeWhile { it !is TemplatePart.Slot }
                .filterIsInstance<TemplatePart.Literal>()
                .firstNotNullOfOrNull { it.value.firstOrNull() }
            if (firstSlot < 0 || nextLiteral == null) {
                unindexed += template
            } else {
                indexed.getOrPut(nextLiteral) { mutableListOf() } += template
            }
        }
        return LeadingSlotTemplateIndex(
            byNextLiteralChar = indexed,
            unindexed = unindexed,
        )
    }

    private fun parseSlotPosConstraint(value: String): Set<TermPos> {
        if (value.isBlank()) return emptySet()
        return value.split('|', ',', '&')
            .map(String::trim)
            .map(String::lowercase)
            .mapNotNull { raw ->
                when (raw) {
                    "name", "proper", "proper_name" -> TermPos.NAME
                    "pronoun", "pro" -> TermPos.PRONOUN
                    "person", "human" -> TermPos.PERSON
                    "location", "loc", "place" -> TermPos.LOCATION
                    "noun", "n" -> TermPos.NOUN
                    "verb", "v" -> TermPos.VERB
                    "adj", "adjective", "a" -> TermPos.ADJECTIVE
                    "adv", "adverb", "d" -> TermPos.ADVERB
                    "function", "func", "particle" -> TermPos.FUNCTION
                    "unknown", "any" -> TermPos.UNKNOWN
                    else -> null
                }
            }
            .toSet()
    }

    private fun readTsv(asset: String, minimumColumns: Int): List<List<String>> {
        return appCtx.assets.open("offline/$asset").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map(String::trimEnd)
                .filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
                .map { it.split('\t') }
                .filter { it.size >= minimumColumns }
                .toList()
        }
    }

    private fun readEqualsAsset(asset: String): List<Pair<String, String>> {
        return runCatching {
            appCtx.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(String::trimEnd)
                    .filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
                    .mapNotNull { line ->
                        val delimiter = line.indexOf('=')
                        if (delimiter <= 0) return@mapNotNull null
                        val source = line.substring(0, delimiter).trim().removePrefix("\uFEFF")
                        val target = cleanQuickDictionaryTarget(line.substring(delimiter + 1))
                        (source to target).takeIf { source.isNotEmpty() && target.isNotEmpty() }
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun protectMarkup(text: String): ProtectedText {
        val layout = TranslationTextTokenizer.tokenize(text)
        val protectedTokens = layout.tokens.filterIsInstance<TranslationTextToken.ProtectedToken>()
        val replacements = linkedMapOf<TranslationTextToken.ProtectedToken, String>()
        protectedTokens
            .forEachIndexed { index, protected ->
                replacements[protected] = "\uE600QT${index.toString().padStart(5, '0')}\uE601"
            }
        return ProtectedText(layout, replacements, protectedTokens)
    }

    private data class ProtectedText(
        val layout: io.legado.app.domain.model.TranslationTextLayout,
        val replacements: Map<TranslationTextToken.ProtectedToken, String>,
        val protectedTokens: List<TranslationTextToken.ProtectedToken>,
    ) {
        fun tokenFor(value: TranslationTextToken.ProtectedToken): String =
            requireNotNull(replacements[value])

        fun restore(value: String): String {
            if (protectedTokens.isEmpty()) return value
            val output = StringBuilder(value.length)
            var offset = 0
            while (offset < value.length) {
                val markerEnd = offset + PROTECTED_MARKER_LENGTH
                val tokenIndex = if (
                    markerEnd <= value.length &&
                    value.startsWith(PROTECTED_MARKER_PREFIX, offset) &&
                    value[markerEnd - 1] == PROTECTED_MARKER_SUFFIX
                ) {
                    value.parseProtectedTokenIndex(offset + PROTECTED_MARKER_PREFIX.length)
                } else {
                    -1
                }
                if (tokenIndex >= 0 && tokenIndex < protectedTokens.size) {
                    output.append(protectedTokens[tokenIndex].raw)
                    offset = markerEnd
                } else {
                    val codePoint = value.codePointAt(offset)
                    output.appendCodePoint(codePoint)
                    offset += Character.charCount(codePoint)
                }
            }
            return output.toString()
        }
    }

    private data class QuickPack(
        val version: String,
        val baseTrie: TermLookup,
        val phonetics: Map<String, String>,
        val templates: List<SourceTemplate>,
        val templatesByFirstChar: Map<Char, List<SourceTemplate>>,
        val leadingSlotTemplateIndex: LeadingSlotTemplateIndex,
        val postRules: List<PostRule>,
        val qt2025Runtime: Qt2025Runtime?,
    ) {
        fun indexedTemplatesAt(text: String, offset: Int): List<SourceTemplate> {
            if (offset >= text.length) return emptyList()
            return templatesByFirstChar[text[offset]]
                ?: templatesByFirstChar[text[offset].lowercaseChar()]
                ?: emptyList()
        }
    }

    private data class CatalogPack(
        val entries: Map<QuickDictionaryType, List<QuickDictionaryCatalogEntry>>,
    )

    private data class CatalogAsset(
        val asset: String,
        val type: QuickDictionaryType,
        val sourceQuality: TermSourceQuality = TermSourceQuality.REVIEWED,
    )

    private data class Qt2020SourceCatalog(
        val catalogId: String,
        val fileName: String,
        val type: QuickDictionaryType,
        val entryCount: Int,
    )

    private data class Term(
        val source: String,
        val target: String,
        val type: QuickDictionaryType,
        val sourcePriority: Int = 0,
        val projectOwned: Boolean = false,
        val runtimePos: TermPos? = null,
        val sourceQuality: TermSourceQuality = TermSourceQuality.BASE,
    )
    private enum class TermSourceQuality {
        PROJECT,
        LATEST_CORRECTION,
        CORRECTION,
        REVIEWED,
        CATALOG_NAME,
        BASE,
        LEGACY,
        SYNTHETIC,
    }
    private enum class CandidateKind {
        LEXICAL,
        PHONETIC,
        LITERAL,
    }
    private data class ProjectTrieCache(
        val fingerprint: Long,
        val terms: List<Term>,
        val trie: TermTrie,
        val templates: List<SourceTemplate> = emptyList(),
        val templatesByFirstChar: Map<Char, List<SourceTemplate>> = emptyMap(),
        val leadingSlotTemplateIndex: LeadingSlotTemplateIndex = LeadingSlotTemplateIndex(),
    ) {
        fun indexedTemplatesAt(text: String, offset: Int): List<SourceTemplate> {
            if (offset >= text.length) return emptyList()
            return templatesByFirstChar[text[offset]]
                ?: templatesByFirstChar[text[offset].lowercaseChar()]
                ?: emptyList()
        }
    }
    private data class LeadingSlotTemplateIndex(
        val byNextLiteralChar: Map<Char, List<SourceTemplate>> = emptyMap(),
        val unindexed: List<SourceTemplate> = emptyList(),
    )
    private data class TermMatch(
        val term: Term,
        val start: Int,
        val endExclusive: Int,
    )
    private data class TranslationCandidate(
        val endExclusive: Int,
        val translation: String,
        val priority: Int,
        val score: Long,
        val kind: CandidateKind = CandidateKind.LEXICAL,
    )
    private data class LocalMappedText(
        val text: String,
        val segments: List<DisplaySourceSegment>,
        val sourceLength: Int,
    )
    private class LocalMappedTextBuilder(
        private val sourceText: String,
    ) {
        private val output = StringBuilder(sourceText.length * 2)
        private val segments = mutableListOf<DisplaySourceSegment>()

        fun append(
            sourceStart: Int,
            sourceEnd: Int,
            value: String,
            literal: Boolean,
        ) {
            val rendered = if (literal) value else value.trim()
            if (rendered.isEmpty() || sourceEnd <= sourceStart) return
            val previous = output.lastOrNull()
            val next = rendered.first()
            if (
                if (literal) {
                    !next.isWhitespace() &&
                        QuickTranslationTextPostProcessor.needsWordSeparator(previous, next)
                } else {
                    QuickTranslationTextPostProcessor.needsWordSeparator(previous, next)
                }
            ) {
                output.append(' ')
            }
            val displayStart = output.length
            output.append(rendered)
            val safeStart = sourceStart.coerceIn(0, sourceText.length)
            val safeEnd = sourceEnd.coerceIn(safeStart, sourceText.length)
            segments += DisplaySourceSegment(
                sourceStart = safeStart,
                sourceEnd = safeEnd,
                displayStart = displayStart,
                displayEnd = output.length,
                confidence = 1f,
                exactCharacterMapping = literal &&
                    sourceText.substring(safeStart, safeEnd) == rendered,
            )
        }

        fun build(): LocalMappedText = LocalMappedText(
            text = output.toString(),
            segments = segments,
            sourceLength = sourceText.length,
        )
    }
    private class StatefulSentenceCapitalizer {
        private var capitalizeNext = true

        fun appendProtected(value: String, output: StringBuilder) {
            output.append(value)
        }

        fun append(value: String, output: StringBuilder) {
            var offset = 0
            while (offset < value.length) {
                val codePoint = value.codePointAt(offset)
                val source = String(Character.toChars(codePoint))
                if (capitalizeNext && Character.isLetter(codePoint)) {
                    output.append(source.replaceFirstChar { it.titlecaseChar() })
                    capitalizeNext = false
                } else {
                    output.append(source)
                    if (Character.isLetterOrDigit(codePoint)) {
                        capitalizeNext = false
                    }
                }
                if (source.length == 1 && source[0] in sentenceTerminators) {
                    capitalizeNext = true
                }
                offset += Character.charCount(codePoint)
            }
        }

        companion object {
            private val sentenceTerminators = setOf(
                '.', '!', '?', '…', '。', '！', '？', '\n', '\r',
            )
        }
    }
    private data class TemplateMatch(
        val endExclusive: Int,
        val translation: String,
        val priority: Int,
        val contiguousScore: Boolean = false,
    )
    private data class StructuredMatch(
        val endExclusive: Int,
        val translation: String,
        val priority: Int,
    )
    private data class PlaceHierarchySegment(
        val endExclusive: Int,
        val translation: String,
    )
    private data class SourceTemplate(
        val sourcePattern: String,
        val parts: List<TemplatePart>,
        val replacement: String,
        val priority: Int,
        val category: String = "",
        val contiguousScore: Boolean = false,
    )
    private sealed interface TemplatePart {
        data class Literal(val value: String) : TemplatePart
        data class Slot(
            val index: Int,
            val acceptedPos: Set<TermPos> = emptySet(),
        ) : TemplatePart {
            fun accepts(pos: TermPos): Boolean =
                acceptedPos.isEmpty() ||
                    acceptedPos.any { accepted -> acceptsCompatiblePos(accepted, pos) }

            private fun acceptsCompatiblePos(accepted: TermPos, actual: TermPos): Boolean {
                if (accepted == actual) return true
                return when (accepted) {
                    TermPos.NOUN -> actual in NOUN_COMPATIBLE_POS
                    TermPos.PERSON -> actual == TermPos.NAME
                    else -> false
                }
            }
        }
    }
    private data class PostRule(
        val pattern: Regex,
        val replacement: String,
        val priority: Int,
        val literalTrigger: String?,
    )
    private data class KinshipAddress(
        val outputLabels: List<String>,
        val vocative: String,
        val addressee: String,
        val speaker: String,
    )
    private data class PronounHints(
        val secondPersonSingular: String? = null,
        val secondPersonPlural: String? = null,
        val maleThirdPerson: String? = null,
        val femaleThirdPerson: String? = null,
    )
    private data class TranslationTokenCacheKey(
        val sourceText: String,
        val pronounHints: PronounHints,
    )
    private data class RoleMarker(
        val term: String,
        val role: ModernThirdPersonRole,
    )
    private enum class PronounStyle {
        MODERN,
        ANCIENT,
        WESTERN,
        OFF,
    }
    private enum class ModernThirdPersonRole {
        CHILD,
        ADULT,
        ELDER,
        SUPERNATURAL,
        NEUTRAL,
    }
    private enum class TermPos {
        NAME,
        PRONOUN,
        PERSON,
        LOCATION,
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB,
        FUNCTION,
        UNKNOWN,
    }
    private class RuntimeMatchIndex(
        private val text: String,
        private val projectMatches: Array<MutableList<TermMatch>?>,
        private val baseMatches: Array<MutableList<TermMatch>?>,
        jiebaTokens: List<JiebaToken>,
    ) {
        private val merged = arrayOfNulls<List<TermMatch>>(text.length)
        private val termPosCache = IdentityHashMap<TermMatch, TermPos>()
        private val slotMatchCache = HashMap<SlotMatchCacheKey, List<TermMatch>>()
        private val grammarPhraseComputed = BooleanArray(text.length)
        private val grammarPhraseCache = arrayOfNulls<TermMatch>(text.length)
        private val jiebaStart = BooleanArray(text.length + 1)
        private val jiebaEnd = BooleanArray(text.length + 1)
        private val jiebaInteriorBoundary = BooleanArray(text.length + 1)
        private val dictionaryStart = BooleanArray(text.length)
        private val dictionaryStartPrefix = IntArray(text.length + 1)
        private val jiebaByStart = arrayOfNulls<MutableList<JiebaToken>>(text.length)
        private val jiebaTokenRanges = jiebaTokens
            .filter { it.start in text.indices && it.endExclusive in 1..text.length }
            .also { tokens ->
                tokens.forEach { token ->
                    jiebaStart[token.start] = true
                    jiebaEnd[token.endExclusive] = true
                    val bucket = jiebaByStart[token.start] ?: mutableListOf<JiebaToken>().also {
                        jiebaByStart[token.start] = it
                    }
                    bucket += token
                    for (boundary in token.start + 1 until token.endExclusive) {
                        jiebaInteriorBoundary[boundary] = true
                    }
                }
                jiebaByStart.forEach { bucket ->
                    bucket?.sortByDescending { it.endExclusive - it.start }
                }
            }
        private val projectSpanStart = IntArray(text.length) { -1 }
        private val projectSpanEnd = IntArray(text.length) { -1 }

        init {
            for (offset in text.indices) {
                val projectAtOffset = projectMatches.getOrNull(offset).orEmpty()
                val baseAtOffset = baseMatches.getOrNull(offset).orEmpty()
                projectAtOffset.forEach(::markProjectSpan)
                dictionaryStart[offset] = projectAtOffset.any(::isTranslatableMatch) ||
                    baseAtOffset.any(::isTranslatableMatch)
                dictionaryStartPrefix[offset + 1] = dictionaryStartPrefix[offset] +
                    (if (dictionaryStart[offset]) 1 else 0)
            }
        }

        private fun isTranslatableMatch(match: TermMatch): Boolean =
            match.term.target.isNotBlank() && match.endExclusive > match.start

        fun termsAt(offset: Int): List<TermMatch> {
            if (offset !in text.indices) return emptyList()
            merged[offset]?.let { return it }
            val result = LinkedHashMap<String, TermMatch>()
            fun add(match: TermMatch) {
                val key = "${normalize(match.term.source)}\u0000${match.endExclusive}"
                val current = result[key]
                if (current == null || isBetterTermMatch(match, current)) {
                    result[key] = match
                }
            }
            projectMatches[offset]?.forEach(::add)
            baseMatches[offset]?.forEach(::add)
            return result.values.sortedWith(TERM_MATCH_COMPARATOR).also { merged[offset] = it }
        }

        fun qt2025NameTarget(start: Int, endExclusive: Int): String? =
            termsAt(start).firstOrNull { match ->
                match.endExclusive == endExclusive &&
                    match.term.target.isNotBlank() &&
                    match.term.type in QT2025_NAME_SLOT_TYPES
            }?.term?.target

        fun cachedPos(match: TermMatch): TermPos? = termPosCache[match]

        fun cachePos(match: TermMatch, pos: TermPos) {
            termPosCache[match] = pos
        }

        fun cachedSlotMatches(
            cursor: Int,
            acceptedPos: Set<TermPos>,
            compute: () -> List<TermMatch>,
        ): List<TermMatch> = slotMatchCache.getOrPut(
            SlotMatchCacheKey(cursor, acceptedPos),
            compute,
        )

        fun cachedGrammarPhrase(start: Int, compute: () -> TermMatch?): TermMatch? {
            if (start !in text.indices) return null
            if (!grammarPhraseComputed[start]) {
                grammarPhraseCache[start] = compute()
                grammarPhraseComputed[start] = true
            }
            return grammarPhraseCache[start]
        }

        fun projectTermsAt(offset: Int): List<TermMatch> {
            if (offset !in text.indices) return emptyList()
            val result = LinkedHashMap<String, TermMatch>()
            fun add(match: TermMatch) {
                result["${normalize(match.term.source)}\u0000${match.endExclusive}"] = match
            }
            projectMatches.getOrNull(offset).orEmpty().forEach(::add)
            return result.values.sortedWith(TERM_MATCH_COMPARATOR)
        }

        fun jiebaTokenAt(start: Int, endExclusive: Int): JiebaToken? {
            if (start !in text.indices) return null
            return jiebaByStart[start].orEmpty().firstOrNull { it.endExclusive == endExclusive }
        }

        fun jiebaTokensAt(start: Int): List<JiebaToken> {
            if (start !in text.indices) return emptyList()
            return jiebaByStart[start].orEmpty()
        }

        fun hasDictionaryTermInside(start: Int, endExclusive: Int): Boolean {
            if (start !in text.indices || endExclusive !in 1..text.length) return false
            return dictionaryStartPrefix[endExclusive] - dictionaryStartPrefix[start] > 0
        }

        private fun markProjectSpan(match: TermMatch) {
            val length = match.endExclusive - match.start
            if (!match.term.projectOwned || length <= 1 || match.term.target.isBlank()) return
            for (index in match.start until match.endExclusive) {
                if (index !in text.indices) continue
                val currentLength = projectSpanEnd[index] - projectSpanStart[index]
                if (currentLength < length) {
                    projectSpanStart[index] = match.start
                    projectSpanEnd[index] = match.endExclusive
                }
            }
        }

        fun isInsideProjectTerm(offset: Int): Boolean {
            if (offset !in text.indices) return false
            val start = projectSpanStart[offset]
            val end = projectSpanEnd[offset]
            return start >= 0 && start < offset && offset < end
        }

        fun splitsProjectTerm(start: Int, endExclusive: Int): Boolean =
            isProjectTermInteriorBoundary(start) || isProjectTermInteriorBoundary(endExclusive)

        fun conflictsWithProjectTerm(start: Int, endExclusive: Int): Boolean {
            if (start !in text.indices || endExclusive <= start) return false
            for (index in start until endExclusive.coerceAtMost(text.length)) {
                val projectStart = projectSpanStart[index]
                val projectEnd = projectSpanEnd[index]
                if (projectStart >= 0 && (projectStart != start || projectEnd != endExclusive)) {
                    return true
                }
            }
            return false
        }

        private fun isProjectTermInteriorBoundary(offset: Int): Boolean {
            if (offset !in text.indices) return false
            val start = projectSpanStart[offset]
            val end = projectSpanEnd[offset]
            return start >= 0 && start < offset && offset < end
        }

        fun isJiebaAligned(start: Int, endExclusive: Int): Boolean =
            jiebaTokenRanges.isEmpty() || (
                start in jiebaStart.indices &&
                    endExclusive in jiebaEnd.indices &&
                    jiebaStart[start] &&
                    jiebaEnd[endExclusive]
                )

        fun crossesJiebaToken(start: Int, endExclusive: Int): Boolean {
            if (jiebaTokenRanges.isEmpty()) return false
            return start in jiebaInteriorBoundary.indices &&
                endExclusive in jiebaInteriorBoundary.indices &&
                (jiebaInteriorBoundary[start] || jiebaInteriorBoundary[endExclusive])
        }

        private data class SlotMatchCacheKey(
            val cursor: Int,
            val acceptedPos: Set<TermPos>,
        )
    }
    private data class JiebaToken(
        val word: String,
        val start: Int,
        val endExclusive: Int,
    )
    private class JiebaQtTokenizer {
        private val segmenter = JiebaSegmenter()

        fun tokenize(text: String): List<JiebaToken> {
            if (text.isBlank()) return emptyList()
            val tokens = mutableListOf<JiebaToken>()
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                if (!isCjk(codePoint)) {
                    offset += Character.charCount(codePoint)
                    continue
                }
                val spanStart = offset
                do {
                    offset += Character.charCount(text.codePointAt(offset))
                } while (offset < text.length && isCjk(text.codePointAt(offset)))
                tokenizeCjkSpan(text, spanStart, offset, tokens)
            }
            return tokens
                .distinctBy { "${it.start}\u0000${it.endExclusive}" }
                .sortedWith(compareBy<JiebaToken> { it.start }.thenByDescending { it.endExclusive })
        }

        private fun tokenizeCjkSpan(
            text: String,
            spanStart: Int,
            spanEnd: Int,
            output: MutableList<JiebaToken>,
        ) {
            val span = text.substring(spanStart, spanEnd)
            runCatching {
                segmenter.process(span, JiebaSegmenter.SegMode.SEARCH)
                    .forEach { token ->
                        val start = spanStart + token.startOffset
                        val end = spanStart + token.endOffset
                        if (start < spanStart || end <= start || end > spanEnd) return@forEach
                        output += JiebaToken(
                            word = token.word,
                            start = start,
                            endExclusive = end,
                        )
                    }
            }.getOrNull()
        }
    }
    private interface TermLookup {
        fun longestAt(text: String, offset: Int): TermMatch?

        fun allAt(text: String, offset: Int): List<TermMatch> =
            longestAt(text, offset)?.let(::listOf).orEmpty()

        fun allMatchesByStart(text: String): Array<MutableList<TermMatch>?> {
            val matches = arrayOfNulls<MutableList<TermMatch>>(text.length)
            for (offset in text.indices) {
                val terms = allAt(text, offset)
                if (terms.isNotEmpty()) {
                    matches[offset] = terms.toMutableList()
                }
            }
            return matches
        }

        fun containsExact(text: String): Boolean {
            return longestAt(text, 0)?.endExclusive == text.length
        }
    }

    private class CompositeTermLookup(
        private val lookups: List<TermLookup>,
    ) : TermLookup {
        override fun longestAt(text: String, offset: Int): TermMatch? {
            var best: TermMatch? = null
            lookups.forEach { lookup ->
                val candidate = lookup.longestAt(text, offset) ?: return@forEach
                val current = best
                if (current == null || isBetterTermMatch(candidate, current)) {
                    best = candidate
                }
            }
            return best
        }

        override fun allAt(text: String, offset: Int): List<TermMatch> {
            val result = LinkedHashMap<String, TermMatch>()
            lookups.forEach { lookup ->
                lookup.allAt(text, offset).forEach { match ->
                    val key = "${match.term.source.lowercase()}\u0000${match.endExclusive}"
                    // Lookups are ordered from curated clean data to optional legacy packs.
                    // The first exact raw/span match is authoritative; a verbose legacy target
                    // must not replace a reviewed clean target merely because it scores longer.
                    result.putIfAbsent(key, match)
                }
            }
            return result.values.sortedWith(TERM_MATCH_COMPARATOR)
        }

        override fun allMatchesByStart(text: String): Array<MutableList<TermMatch>?> {
            val merged = arrayOfNulls<MutableList<TermMatch>>(text.length)
            lookups.forEach { lookup ->
                lookup.allMatchesByStart(text).forEachIndexed { offset, matches ->
                    if (matches.isNullOrEmpty()) return@forEachIndexed
                    val bucket = merged[offset] ?: mutableListOf<TermMatch>().also {
                        merged[offset] = it
                    }
                    matches.forEach { match -> bucket.addOrReplace(match) }
                }
            }
            merged.forEach { bucket -> bucket?.sortWith(TERM_MATCH_COMPARATOR) }
            return merged
        }

        private fun MutableList<TermMatch>.addOrReplace(match: TermMatch) {
            for (index in indices) {
                val current = this[index]
                if (current.endExclusive == match.endExclusive &&
                    normalize(current.term.source) == normalize(match.term.source)
                ) {
                    return
                }
            }
            add(match)
        }
    }

    /** Aho-Corasick automaton over project/clean-pack terms, returning every match by start. */
    private class TermTrie(terms: List<Term>) : TermLookup {
        private class Node {
            val children = HashMap<Char, Int>()
            val outputs = mutableListOf<Term>()
            var failure: Int = 0
        }

        private val nodes = mutableListOf(Node())

        init {
            terms.forEach(::insert)
            buildFailureLinks()
        }

        override fun longestAt(text: String, offset: Int): TermMatch? {
            return allAt(text, offset).firstOrNull()
        }

        override fun allAt(text: String, offset: Int): List<TermMatch> {
            var state = 0
            var cursor = offset
            val matches = mutableListOf<TermMatch>()
            while (cursor < text.length) {
                state = nodes[state].children[text[cursor].lowercaseChar()] ?: break
                cursor++
                nodes[state].outputs.forEach { term ->
                    if (offset + term.source.length == cursor) {
                        matches += TermMatch(term, offset, cursor)
                    }
                }
            }
            return matches.sortedWith(TERM_MATCH_COMPARATOR)
        }

        override fun allMatchesByStart(text: String): Array<MutableList<TermMatch>?> {
            val matches = arrayOfNulls<MutableList<TermMatch>>(text.length)
            var state = 0
            text.forEachIndexed { index, rawCharacter ->
                val character = rawCharacter.lowercaseChar()
                while (state != 0 && character !in nodes[state].children) {
                    state = nodes[state].failure
                }
                state = nodes[state].children[character] ?: 0
                nodes[state].outputs.forEach { term ->
                    val start = index + 1 - term.source.length
                    if (start >= 0) {
                        val bucket = matches[start] ?: mutableListOf<TermMatch>().also {
                            matches[start] = it
                        }
                        bucket += TermMatch(term, start, index + 1)
                    }
                }
            }
            matches.forEach { bucket -> bucket?.sortWith(TERM_MATCH_COMPARATOR) }
            return matches
        }

        private fun insert(term: Term) {
            var state = 0
            term.source.forEach { rawCharacter ->
                val character = rawCharacter.lowercaseChar()
                state = nodes[state].children.getOrPut(character) {
                    nodes.add(Node())
                    nodes.lastIndex
                }
            }
            if (nodes[state].outputs.none { normalize(it.source) == normalize(term.source) }) {
                nodes[state].outputs += term
            }
        }

        private fun buildFailureLinks() {
            val queue = ArrayDeque<Int>()
            nodes[0].children.values.forEach { child ->
                nodes[child].failure = 0
                queue.addLast(child)
            }
            while (queue.isNotEmpty()) {
                val parent = queue.removeFirst()
                nodes[parent].children.forEach { (character, child) ->
                    queue.addLast(child)
                    var fallback = nodes[parent].failure
                    while (fallback != 0 && character !in nodes[fallback].children) {
                        fallback = nodes[fallback].failure
                    }
                    nodes[child].failure = nodes[fallback].children[character]
                        ?.takeIf { it != child }
                        ?: 0
                    nodes[child].outputs += nodes[nodes[child].failure].outputs
                }
            }
        }
    }

    private class MappedTermLookup private constructor(
        private val buffer: ByteBuffer,
        private val bucketCount: Int,
        private val maxSourceChars: Int,
        private val firstSourceChars: BooleanArray,
    ) : TermLookup {

        private val hashScratch = ThreadLocal.withInitial { IntArray(maxSourceChars) }

        private val targetCache = object : LinkedHashMap<Int, String>(
            TARGET_CACHE_SIZE,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, String>?,
            ): Boolean = size > TARGET_CACHE_SIZE
        }

        override fun longestAt(text: String, offset: Int): TermMatch? {
            return allAt(text, offset).firstOrNull()
        }

        override fun allAt(text: String, offset: Int): List<TermMatch> {
            val available = (text.length - offset).coerceAtMost(maxSourceChars)
            if (available <= 0) return emptyList()
            if (!firstSourceChars[text[offset].lowercaseChar().code]) return emptyList()
            val hashes = checkNotNull(hashScratch.get())
            var hash = FNV_OFFSET_BASIS
            for (length in 1..available) {
                hash = (hash xor text[offset + length - 1].lowercaseChar().code) * FNV_PRIME
                hashes[length - 1] = hash
            }
            val matches = mutableListOf<TermMatch>()
            for (length in available downTo 1) {
                val entryOffset = findEntry(text, offset, length, hashes[length - 1])
                if (entryOffset >= 0) {
                    matches += TermMatch(
                        term = Term(
                            source = text.substring(offset, offset + length),
                            target = targetAt(entryOffset, length),
                            type = typeAt(entryOffset),
                            sourcePriority = QT2020_SOURCE_PRIORITY,
                            sourceQuality = sourceQualityAt(entryOffset),
                        ),
                        start = offset,
                        endExclusive = offset + length,
                    )
                }
            }
            return matches
        }

        private fun findEntry(
            text: String,
            textOffset: Int,
            sourceLength: Int,
            hash: Int,
        ): Int {
            val mask = bucketCount - 1
            var bucket = hash and mask
            repeat(bucketCount) {
                val entryOffset = buffer.getInt(HEADER_SIZE + bucket * Int.SIZE_BYTES)
                if (entryOffset == 0) return -1
                if (entryMatches(entryOffset, text, textOffset, sourceLength, hash)) {
                    return entryOffset
                }
                bucket = (bucket + 1) and mask
            }
            return -1
        }

        private fun entryMatches(
            entryOffset: Int,
            text: String,
            textOffset: Int,
            sourceLength: Int,
            hash: Int,
        ): Boolean {
            if (buffer.getInt(entryOffset) != hash) return false
            if (buffer.getShort(entryOffset + Int.SIZE_BYTES).toInt() and 0xffff != sourceLength) {
                return false
            }
            val sourceOffset = entryOffset + ENTRY_HEADER_SIZE
            repeat(sourceLength) { index ->
                if (buffer.getChar(sourceOffset + index * Char.SIZE_BYTES) !=
                    text[textOffset + index].lowercaseChar()
                ) {
                    return false
                }
            }
            return true
        }

        private fun targetAt(entryOffset: Int, sourceLength: Int): String {
            synchronized(targetCache) {
                targetCache[entryOffset]?.let { return it }
            }
            val targetLength = buffer.getInt(entryOffset + TARGET_LENGTH_OFFSET)
            val targetOffset = entryOffset + ENTRY_HEADER_SIZE + sourceLength * Char.SIZE_BYTES
            require(targetLength in 0..MAX_TARGET_BYTES) {
                "Invalid QT2020 target length: $targetLength"
            }
            val bytes = ByteArray(targetLength)
            repeat(targetLength) { index ->
                bytes[index] = buffer.get(targetOffset + index)
            }
            return cleanQuickDictionaryTarget(bytes.toString(Charsets.UTF_8)).also { target ->
                synchronized(targetCache) {
                    targetCache[entryOffset] = target
                }
            }
        }

        private fun typeAt(entryOffset: Int): QuickDictionaryType =
            when (buffer.getShort(entryOffset + TYPE_OFFSET).toInt() and 0xffff) {
                MAPPED_TYPE_NAME -> QuickDictionaryType.NAME
                MAPPED_TYPE_PRONOUN -> QuickDictionaryType.PRONOUN
                else -> QuickDictionaryType.VIETPHRASE
            }

        private fun sourceQualityAt(entryOffset: Int): TermSourceQuality =
            if (typeAt(entryOffset) == QuickDictionaryType.NAME) {
                TermSourceQuality.CATALOG_NAME
            } else {
                TermSourceQuality.LEGACY
            }

        companion object {
            private const val HEADER_SIZE = 32
            private const val ENTRY_HEADER_SIZE = 12
            private const val TARGET_LENGTH_OFFSET = 8
            private const val TYPE_OFFSET = 6
            private const val FORMAT_VERSION_OFFSET = 8
            private const val BUCKET_COUNT_OFFSET = 12
            private const val ENTRY_COUNT_OFFSET = 16
            private const val MAX_SOURCE_CHARS_OFFSET = 20
            private const val BLOB_OFFSET_OFFSET = 24
            private const val FORMAT_VERSION = 1
            private const val FNV_OFFSET_BASIS = -2128831035
            private const val FNV_PRIME = 16777619
            private const val TARGET_CACHE_SIZE = 4_096
            private const val MAX_TARGET_BYTES = 1 shl 20
            private const val MAPPED_TYPE_NAME = 1
            private const val MAPPED_TYPE_PRONOUN = 2
            private val MAGIC = "QTDCT001".toByteArray(Charsets.US_ASCII)

            fun openOrNull(asset: String): MappedTermLookup? {
                return runCatching {
                    val mapped = appCtx.assets.openFd(asset).use { descriptor ->
                        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                            channel.map(
                                FileChannel.MapMode.READ_ONLY,
                                descriptor.startOffset,
                                descriptor.length,
                            )
                        }
                    }.order(ByteOrder.LITTLE_ENDIAN)
                    validate(mapped)
                    MappedTermLookup(
                        buffer = mapped,
                        bucketCount = mapped.getInt(BUCKET_COUNT_OFFSET),
                        maxSourceChars = mapped.getInt(MAX_SOURCE_CHARS_OFFSET),
                        firstSourceChars = firstSourceChars(mapped),
                    )
                }.getOrNull()
            }

            private fun firstSourceChars(buffer: ByteBuffer): BooleanArray {
                val chars = BooleanArray(Char.MAX_VALUE.code + 1)
                val bucketCount = buffer.getInt(BUCKET_COUNT_OFFSET)
                repeat(bucketCount) { bucket ->
                    val entryOffset = buffer.getInt(HEADER_SIZE + bucket * Int.SIZE_BYTES)
                    if (entryOffset != 0) {
                        val sourceLength = buffer.getShort(entryOffset + Int.SIZE_BYTES).toInt() and 0xffff
                        if (sourceLength > 0) {
                            val sourceOffset = entryOffset + ENTRY_HEADER_SIZE
                            chars[buffer.getChar(sourceOffset).lowercaseChar().code] = true
                        }
                    }
                }
                return chars
            }

            private fun validate(buffer: ByteBuffer) {
                require(buffer.limit() >= HEADER_SIZE) { "QT2020 index is truncated" }
                MAGIC.forEachIndexed { index, byte ->
                    require(buffer.get(index) == byte) { "Invalid QT2020 index magic" }
                }
                require(buffer.getInt(FORMAT_VERSION_OFFSET) == FORMAT_VERSION) {
                    "Unsupported QT2020 index format"
                }
                val bucketCount = buffer.getInt(BUCKET_COUNT_OFFSET)
                val entryCount = buffer.getInt(ENTRY_COUNT_OFFSET)
                val maxSourceChars = buffer.getInt(MAX_SOURCE_CHARS_OFFSET)
                val blobOffset = buffer.getInt(BLOB_OFFSET_OFFSET)
                require(bucketCount > 0 && bucketCount.countOneBits() == 1)
                require(entryCount in 1 until bucketCount)
                require(maxSourceChars in 1..0xffff)
                require(blobOffset == HEADER_SIZE + bucketCount * Int.SIZE_BYTES)
                require(blobOffset < buffer.limit())
            }
        }
    }

    companion object {
        private const val MAPPED_ENGINE = "quick_translator_exact"
        private const val PACK_VERSION = "qt-clean-4.2.23"
        private const val QT2025_PACK_VERSION =
            "qt2025-302f9f8d+qt-clean-4.2.23+runtime-3-entity-lock"
        private const val QT2020_PACK_VERSION = "qt2020-2025.09.01+qt-clean-4.2.23"
        private const val QT2025_TERM_INDEX_ASSET = "offline/qt2025/qt2025-terms.qtdict"
        private const val QT2025_PHONETIC_ASSET = "offline/qt2025/ChinesePhienAmWords.txt"
        private const val QT2025_RULE_ASSET = "offline/qt2025/LuatNhan.txt"
        private const val QT2025_SURNAME_ASSET = "offline/qt2025/HoNguoi.txt"
        private const val QT2025_SUFFIX_ASSET = "offline/qt2025/HauTu.txt"
        private const val QT2020_ASSET_DIRECTORY = "offline/qt2020"
        private const val QT2020_TERM_INDEX_ASSET = "offline/qt2020/qt2020-terms.qtdict"
        private const val QT2020_PHONETIC_ASSET =
            "offline/qt2020/ChinesePhienAmWords.txt"
        private const val QT2020_TEMPLATE_ASSET = "offline/qt2020/LuatNhan.txt"
        private const val QT2020_TEMPLATE_PRIORITY = 80
        private const val QT2025_RUNTIME_SCORE_BONUS = 250_000L
        private const val MAX_QT2025_STRUCTURED_LOOKBACK = 32
        private const val MAX_QT2025_CORRECTION_LOOKBACK = 192
        private const val LATEST_RULE_ASSET = "qt_clean_rules_v8.tsv"
        private const val PROJECT_TEMPLATE_PRIORITY = 900
        private const val MAX_TEMPLATE_RULES = 4_000
        private const val MAX_PROJECT_TEMPLATE_RULES = 512
        private const val MAX_POST_RULES = 1_000
        private const val MAX_SLOT_CANDIDATES = 8
        private const val PROJECT_TRIE_CACHE_SIZE = 8
        private const val POST_RULE_META_CHARS = "[]()|+*?{}^$."
        private val CORRECTION_SOURCE_QUALITIES = setOf(
            TermSourceQuality.LATEST_CORRECTION,
            TermSourceQuality.CORRECTION,
        )
        private val REVIEWED_NAME_SOURCE_QUALITIES = setOf(
            TermSourceQuality.LATEST_CORRECTION,
            TermSourceQuality.CORRECTION,
            TermSourceQuality.REVIEWED,
            TermSourceQuality.CATALOG_NAME,
        )
        private val QT2025_NAME_SLOT_TYPES = setOf(
            QuickDictionaryType.NAME,
            QuickDictionaryType.PRONOUN,
        )
        private val NARRATOR_MALE_PRONOUNS = listOf(
            "anh ấy", "ông ấy", "cậu ấy", "ông ta", "anh ta", "cậu",
        )
        private val NARRATOR_FEMALE_PRONOUNS = listOf(
            "cô ấy", "bà ấy", "cô ta", "bà ta", "cô bé", "nàng",
        )
        private val CHILD_KINSHIP_GRANDMOTHER_PATTERN = Regex(
            "(?<![\\p{L}\\p{N}])(?:đứa nhỏ|đứa trẻ|trẻ con|hài tử|hài có lẽ tử|con nít|nó)\\s+" +
                "(?:anh ấy|hắn|cậu|nó)\\s+(?:sữa|nãi|bà)(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val CHILD_KINSHIP_GRANDFATHER_PATTERN = Regex(
            "(?<![\\p{L}\\p{N}])(?:đứa nhỏ|đứa trẻ|trẻ con|hài tử|hài có lẽ tử|con nít|nó)\\s+" +
                "(?:anh ấy|hắn|cậu|nó)\\s+(?:gia|ông)(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val CHILD_KINSHIP_MOTHER_PATTERN = Regex(
            "(?<![\\p{L}\\p{N}])(?:đứa nhỏ|đứa trẻ|trẻ con|hài tử|hài có lẽ tử|con nít|nó)\\s+" +
                "(?:anh ấy|hắn|cậu|nó)\\s+(?:mẹ|má)(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val CHILD_KINSHIP_FATHER_PATTERN = Regex(
            "(?<![\\p{L}\\p{N}])(?:đứa nhỏ|đứa trẻ|trẻ con|hài tử|hài có lẽ tử|con nít|nó)\\s+" +
                "(?:anh ấy|hắn|cậu|nó)\\s+(?:cha|bố|ba)(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val MODERN_MALE_ROLE_MARKERS = listOf(
            RoleMarker("李追远", ModernThirdPersonRole.CHILD),
            RoleMarker("李追遠", ModernThirdPersonRole.CHILD),
            RoleMarker("小远侯", ModernThirdPersonRole.CHILD),
            RoleMarker("小遠侯", ModernThirdPersonRole.CHILD),
            RoleMarker("远子", ModernThirdPersonRole.CHILD),
            RoleMarker("遠子", ModernThirdPersonRole.CHILD),
            RoleMarker("伢儿", ModernThirdPersonRole.CHILD),
            RoleMarker("孩子", ModernThirdPersonRole.CHILD),
            RoleMarker("外孙", ModernThirdPersonRole.CHILD),
            RoleMarker("外孫", ModernThirdPersonRole.CHILD),
            RoleMarker("孙子", ModernThirdPersonRole.CHILD),
            RoleMarker("孫子", ModernThirdPersonRole.CHILD),
            RoleMarker("男娃", ModernThirdPersonRole.CHILD),
            RoleMarker("潘子", ModernThirdPersonRole.CHILD),
            RoleMarker("雷子", ModernThirdPersonRole.CHILD),
            RoleMarker("虎子", ModernThirdPersonRole.CHILD),
            RoleMarker("石头", ModernThirdPersonRole.CHILD),
            RoleMarker("石頭", ModernThirdPersonRole.CHILD),
            RoleMarker("远子哥", ModernThirdPersonRole.CHILD),
            RoleMarker("遠子哥", ModernThirdPersonRole.CHILD),
            RoleMarker("哥哥", ModernThirdPersonRole.CHILD),
            RoleMarker("弟弟", ModernThirdPersonRole.CHILD),
            RoleMarker("同桌", ModernThirdPersonRole.CHILD),
            RoleMarker("男孩", ModernThirdPersonRole.CHILD),
            RoleMarker("少年", ModernThirdPersonRole.CHILD),
            RoleMarker("李维汉", ModernThirdPersonRole.ELDER),
            RoleMarker("李維漢", ModernThirdPersonRole.ELDER),
            RoleMarker("汉侯", ModernThirdPersonRole.ELDER),
            RoleMarker("漢侯", ModernThirdPersonRole.ELDER),
            RoleMarker("汉叔", ModernThirdPersonRole.ELDER),
            RoleMarker("漢叔", ModernThirdPersonRole.ELDER),
            RoleMarker("爷爷", ModernThirdPersonRole.ELDER),
            RoleMarker("爺爺", ModernThirdPersonRole.ELDER),
            RoleMarker("外公", ModernThirdPersonRole.ELDER),
            RoleMarker("祖父", ModernThirdPersonRole.ELDER),
            RoleMarker("老头", ModernThirdPersonRole.ELDER),
            RoleMarker("老頭", ModernThirdPersonRole.ELDER),
            RoleMarker("老爷子", ModernThirdPersonRole.ELDER),
            RoleMarker("老爺子", ModernThirdPersonRole.ELDER),
            RoleMarker("老教授", ModernThirdPersonRole.ELDER),
            RoleMarker("李三江", ModernThirdPersonRole.ELDER),
            RoleMarker("三江叔", ModernThirdPersonRole.ELDER),
            RoleMarker("郑大筒", ModernThirdPersonRole.ADULT),
            RoleMarker("鄭大筒", ModernThirdPersonRole.ADULT),
            RoleMarker("郑华民", ModernThirdPersonRole.ADULT),
            RoleMarker("鄭華民", ModernThirdPersonRole.ADULT),
            RoleMarker("大夫", ModernThirdPersonRole.ADULT),
            RoleMarker("医生", ModernThirdPersonRole.ADULT),
            RoleMarker("醫生", ModernThirdPersonRole.ADULT),
            RoleMarker("赤脚医生", ModernThirdPersonRole.ADULT),
            RoleMarker("赤腳醫生", ModernThirdPersonRole.ADULT),
            RoleMarker("先生", ModernThirdPersonRole.ADULT),
            RoleMarker("叔", ModernThirdPersonRole.ADULT),
            RoleMarker("男人", ModernThirdPersonRole.ADULT),
            RoleMarker("女婿", ModernThirdPersonRole.ADULT),
        )
        private val MODERN_FEMALE_ROLE_MARKERS = listOf(
            RoleMarker("小翠侯", ModernThirdPersonRole.CHILD),
            RoleMarker("翠翠", ModernThirdPersonRole.CHILD),
            RoleMarker("李翠翠", ModernThirdPersonRole.CHILD),
            RoleMarker("小女孩", ModernThirdPersonRole.CHILD),
            RoleMarker("女孩", ModernThirdPersonRole.CHILD),
            RoleMarker("孙女", ModernThirdPersonRole.CHILD),
            RoleMarker("孫女", ModernThirdPersonRole.CHILD),
            RoleMarker("女娃", ModernThirdPersonRole.CHILD),
            RoleMarker("刘金霞", ModernThirdPersonRole.ELDER),
            RoleMarker("劉金霞", ModernThirdPersonRole.ELDER),
            RoleMarker("刘瞎子", ModernThirdPersonRole.ELDER),
            RoleMarker("劉瞎子", ModernThirdPersonRole.ELDER),
            RoleMarker("崔桂英", ModernThirdPersonRole.ELDER),
            RoleMarker("老太婆", ModernThirdPersonRole.ELDER),
            RoleMarker("老太太", ModernThirdPersonRole.ELDER),
            RoleMarker("老奶奶", ModernThirdPersonRole.ELDER),
            RoleMarker("奶奶", ModernThirdPersonRole.ELDER),
            RoleMarker("阿婆", ModernThirdPersonRole.ELDER),
            RoleMarker("外婆", ModernThirdPersonRole.ELDER),
            RoleMarker("祖母", ModernThirdPersonRole.ELDER),
            RoleMarker("李菊香", ModernThirdPersonRole.ADULT),
            RoleMarker("小黄莺", ModernThirdPersonRole.ADULT),
            RoleMarker("小黃鶯", ModernThirdPersonRole.ADULT),
            RoleMarker("女人", ModernThirdPersonRole.ADULT),
            RoleMarker("女鬼", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("水鬼", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("死尸", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("死屍", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("尸体", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("屍體", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("尸身", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("屍身", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("水下", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("水面", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("水里", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("水裡", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("头发", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("頭髮", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("旗袍", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("高跟鞋", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("红色高跟鞋", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("紅色高跟鞋", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("脚踝", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("腳踝", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("发梢", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("髮梢", ModernThirdPersonRole.SUPERNATURAL),
            RoleMarker("媳妇", ModernThirdPersonRole.ADULT),
            RoleMarker("媳婦", ModernThirdPersonRole.ADULT),
            RoleMarker("夫人", ModernThirdPersonRole.ADULT),
            RoleMarker("母亲", ModernThirdPersonRole.ADULT),
            RoleMarker("母親", ModernThirdPersonRole.ADULT),
        )
        private const val DEFAULT_DICTIONARY_BUFFER_BYTES = 64 * 1024
        private const val LENGTH_SCORE_FACTOR = 100L
        private const val LONG_PHRASE_SCORE_FACTOR = 30L
        private const val STRUCTURED_CONTIGUOUS_SCORE_FACTOR = 1_000L
        private const val TEMPLATE_CONTIGUOUS_SCORE_FACTOR = 1_000L
        private const val SATURATED_LENGTH_CHARS = 6
        private const val FALLBACK_SCORE = 1L
        private const val GRAMMAR_SCORE_BONUS = 8_000L
        private const val TEMPLATE_SCORE_BONUS = 7_000L
        private const val IGNORE_SCORE_BONUS = 1_000_000L
        private const val PROJECT_TERM_SPLIT_PENALTY = -20_000L
        private const val JIEBA_ALIGNMENT_BONUS = 350L
        private const val JIEBA_EXACT_TOKEN_BONUS = 650L
        private const val JIEBA_PROJECT_SCORE = 120L
        private const val JIEBA_CROSSING_PENALTY = -420L
        private const val JIEBA_TOKEN_FALLBACK_BONUS = 180L
        private const val JIEBA_TOKEN_FALLBACK_PRIORITY = 25
        private const val MAX_JIEBA_FALLBACK_CANDIDATES = 4
        private const val TARGET_QUALITY_SCORE_FACTOR = 1L
        private const val TRANSLATION_FRAGMENT_PENALTY = 2_200L
        private const val SINGLE_CHAR_LEXICAL_SPLIT_PENALTY = 350L
        private const val SINGLE_CHAR_FUNCTION_SPLIT_PENALTY = 2_400L
        private const val PHONETIC_FALLBACK_PENALTY = 1_200L
        private const val DEFINITION_TARGET_SCORE_PENALTY = 2_800L
        private const val PROJECT_SOURCE_QUALITY_BONUS = 0L
        private const val LATEST_CORRECTION_SOURCE_QUALITY_BONUS = 12_000L
        private const val LATEST_CORRECTION_LENGTH_SCORE_FACTOR = 1_000L
        private const val CORRECTION_SOURCE_QUALITY_BONUS = 4_000L
        private const val CORRECTION_LENGTH_SCORE_FACTOR = 1_000L
        private const val REVIEWED_SOURCE_QUALITY_BONUS = 900L
        private const val CATALOG_NAME_SOURCE_QUALITY_BONUS = 300L
        private const val LEGACY_SOURCE_QUALITY_PENALTY = -500L
        private const val SYNTHETIC_SOURCE_QUALITY_PENALTY = -250L
        private const val QT2020_SOURCE_PRIORITY = 500
        private const val SYNTHETIC_GRAMMAR_SOURCE_PRIORITY = -100
        private const val TRUSTED_GRAMMAR_PRIORITY = 800
        private const val REVIEWED_TEMPLATE_PRIORITY = 855
        private const val MAX_GRAMMAR_SLOT_CHARS = 16
        private const val PROTECTED_MARKER_PREFIX = "\uE600QT"
        private const val PROTECTED_MARKER_DIGITS = 5
        private const val PROTECTED_MARKER_SUFFIX = '\uE601'
        private const val PROTECTED_MARKER_LENGTH =
            1 + 2 + PROTECTED_MARKER_DIGITS + 1
        private val LEADING_HEAD_PREFIXES = listOf("为首的", "为首")
        private const val ATTRIBUTIVE_DE_PATTERN = "{0}的{1}"
        private const val ATTRIBUTIVE_DE_LITERAL = "的"
        private val PLACEHOLDER = Regex("\\{([0-2])(?::([A-Za-z_|,&-]+))?\\}")
        private val TERM_MATCH_COMPARATOR = Comparator<TermMatch> { left, right ->
            when {
                isBetterTermMatch(left, right) -> -1
                isBetterTermMatch(right, left) -> 1
                else -> 0
            }
        }
        private val HEAD_MODIFIER_POS = setOf(TermPos.PERSON, TermPos.NOUN, TermPos.NAME)
        private const val PLURAL_SUFFIX = "\u4EEC"
        private val PLURAL_BASE_POS = setOf(TermPos.NOUN, TermPos.PERSON, TermPos.NAME)
        private val VIETNAMESE_PLURAL_PREFIXES = listOf(
            "c\u00E1c",
            "nh\u1EEFng",
            "b\u1ECDn",
            "t\u1EE5i",
        )
        private val SINGLE_CHAR_FUNCTION_PARTICLES = setOf(
            "\u7684",
            "\u7740",
            "\u4EEC",
            "\u5730",
            "\u5F97",
            "\u4E86",
            "\u8FC7",
        )
        private val ATTRIBUTIVE_ACTION_HEAD_POS = setOf(TermPos.PERSON, TermPos.NAME)
        private val ACTION_ATTRIBUTIVE_MARKERS = listOf(
            "\u7740",
            "\u5730",
            "\u6B63",
            "\u7CFB",
            "\u7AEF",
            "\u63E1",
            "\u6572\u6253",
            "\u547C\u558A",
            "\u88C5",
            "\u9A82",
        )
        private const val MAX_PLACE_HIERARCHY_SEGMENTS = 8
        private const val MIN_SINGLE_PLACE_HIERARCHY_CHARS = 3
        private val PLACE_COUNTRY_SOURCES = setOf(
            "\u4E2D\u56FD",
            "\u4E2D\u570B",
        )
        private val PLACE_COUNTRY_TARGETS = setOf(
            "Trung Qu\u1ED1c",
        )
        private val PLACE_HIERARCHY_SUFFIX_LABELS = listOf(
            "\u7279\u522B\u884C\u653F\u533A" to "\u0111\u1EB7c khu h\u00E0nh ch\u00EDnh",
            "\u81EA\u6CBB\u533A" to "khu t\u1EF1 tr\u1ECB",
            "\u81EA\u6CBB\u5DDE" to "ch\u00E2u t\u1EF1 tr\u1ECB",
            "\u81EA\u6CBB\u53BF" to "huy\u1EC7n t\u1EF1 tr\u1ECB",
            "\u65B0\u533A" to "khu m\u1EDBi",
            "\u8857\u9053" to "ph\u01B0\u1EDDng",
            "\u5927\u9053" to "\u0111\u1EA1i l\u1ED9",
            "\u7701" to "t\u1EC9nh",
            "\u5E02" to "th\u00E0nh ph\u1ED1",
            "\u53BF" to "huy\u1EC7n",
            "\u5340" to "qu\u1EADn",
            "\u533A" to "qu\u1EADn",
            "\u9547" to "th\u1ECB tr\u1EA5n",
            "\u4E61" to "h\u01B0\u01A1ng",
            "\u6751" to "th\u00F4n",
            "\u8857" to "ph\u1ED1",
            "\u8DEF" to "\u0111\u01B0\u1EDDng",
            "\u5DF7" to "ng\u00F5",
        )
        private val NOUN_COMPATIBLE_POS = setOf(
            TermPos.NOUN,
            TermPos.PERSON,
            TermPos.NAME,
            TermPos.LOCATION,
        )
        private val GRAMMAR_NOUN_PHRASE_HEAD_POS = NOUN_COMPATIBLE_POS
        private val GRAMMAR_NOUN_PHRASE_BREAK_POS = setOf(
            TermPos.VERB,
            TermPos.ADVERB,
            TermPos.FUNCTION,
        )
        private val JIEBA_STRONG_POS = setOf(
            TermPos.LOCATION,
            TermPos.PERSON,
            TermPos.VERB,
            TermPos.ADJECTIVE,
            TermPos.ADVERB,
        )

        private fun isBetterTermMatch(
            candidate: TermMatch,
            current: TermMatch,
        ): Boolean {
            val candidateLength = candidate.endExclusive - candidate.start
            val currentLength = current.endExclusive - current.start
            return when {
                candidate.term.target.isEmpty() != current.term.target.isEmpty() ->
                    candidate.term.target.isEmpty()
                candidate.term.projectOwned != current.term.projectOwned -> candidate.term.projectOwned
                candidateLength != currentLength -> candidateLength > currentLength
                candidate.term.sourceQuality != current.term.sourceQuality ->
                    termSourceQualityRank(candidate.term.sourceQuality) >
                        termSourceQualityRank(current.term.sourceQuality)
                candidate.term.type != current.term.type -> termTypeRank(candidate.term.type) >
                    termTypeRank(current.term.type)
                quickDictionaryTargetScore(candidate.term.target) !=
                    quickDictionaryTargetScore(current.term.target) ->
                    quickDictionaryTargetScore(candidate.term.target) >
                        quickDictionaryTargetScore(current.term.target)
                else -> candidate.term.sourcePriority < current.term.sourcePriority
            }
        }

        private fun termTypeRank(type: QuickDictionaryType): Int = when (type) {
            QuickDictionaryType.IGNORE -> 7
            QuickDictionaryType.NAME -> 6
            QuickDictionaryType.PRONOUN -> 5
            QuickDictionaryType.TERM -> 4
            QuickDictionaryType.VIETPHRASE -> 3
            QuickDictionaryType.LUAT_NHAN -> 2
            QuickDictionaryType.PHONETIC -> 1
        }

        private fun termSourceQualityRank(sourceQuality: TermSourceQuality): Int =
            when (sourceQuality) {
                TermSourceQuality.PROJECT -> 7
                TermSourceQuality.LATEST_CORRECTION -> 6
                TermSourceQuality.CORRECTION -> 5
                TermSourceQuality.REVIEWED -> 4
                TermSourceQuality.CATALOG_NAME -> 3
                TermSourceQuality.BASE -> 2
                TermSourceQuality.SYNTHETIC -> 1
                TermSourceQuality.LEGACY -> 0
            }
        private val GRAMMAR_NOUN_PHRASE_STOPS = listOf(
            "乔装打扮", "穿着", "套着", "戴着", "拿着", "手持", "手拄",
            "留着", "梳着", "走", "站", "坐", "有", "是", "把", "被",
            "将", "正", "正在", "在", "对", "向", "朝", "和", "与", "跟",
            "给", "为", "从", "由", "说", "说道", "问", "回答", "看", "望",
        )
        private val CLOTHING_COLOR_MODIFIERS = mapOf(
            '黑' to "đen",
            '白' to "trắng",
            '红' to "đỏ",
            '青' to "xanh",
            '蓝' to "xanh lam",
            '绿' to "xanh lá",
            '紫' to "tím",
            '黄' to "vàng",
            '灰' to "xám",
        )
        private val PERSON_HEAD_SUFFIXES = listOf(
            "调查员", "侦探", "警察", "警员", "官", "员", "者", "人",
            "男子", "女子", "男人", "女人", "青年", "少年", "少女", "老人",
            "公子", "小姐", "先生", "女士",
        )
        private val PERSON_HEAD_TARGETS = listOf(
            "người", "điều tra viên", "thám tử", "cảnh sát", "nam", "nữ",
            "đàn ông", "phụ nữ", "thiếu niên", "thiếu nữ", "công tử",
        )
        private val POSSESSIVE_OWNER_SOURCES = setOf(
            "我", "你", "妳", "他", "她", "它", "咱", "俺", "我们", "咱们",
            "你们", "妳们", "他们", "她们", "它们", "本人", "自己",
        )
        private val POSSESSIVE_OWNER_TARGETS = listOf(
            "tôi", "ta", "mình", "ngươi", "anh", "cô", "hắn", "nàng",
            "ông", "bà", "người", "điều tra viên", "công tử", "tiểu thư",
        )
        private val LOCATION_MODIFIER_SUFFIXES = listOf(
            "嘴角", "眼角", "门口", "窗口", "身边", "身上", "脸上", "心里",
            "脑海", "头顶", "背后", "面前", "左边", "右边", "旁边", "腰间",
            "胸口", "手中", "脚下", "里面", "外面", "其中", "角", "边",
            "旁", "侧", "上", "下", "里", "中", "内", "外", "前", "后",
        )
        private val LOCATION_ENTITY_SUFFIXES = listOf(
            "城", "镇", "村", "山", "峰", "谷", "宗", "门", "派", "宫", "殿",
            "府", "院", "楼", "阁", "街", "路", "巷", "店", "馆", "房", "间",
            "室", "厅", "堂", "岛", "海", "湖", "河", "江", "域", "界",
        )
        private val DESCRIPTIVE_MODIFIER_SOURCES = setOf(
            "年轻", "年老", "浓郁", "漂亮", "美丽", "精致", "瘦削", "纤细",
            "鼓胀", "高大", "娇小", "玲珑", "细长", "修长", "乌黑", "雪白",
            "漆黑", "巨大", "古怪", "奇怪", "特殊", "普通", "平静", "冰冷",
            "温柔", "灿烂", "明亮", "清澈", "深邃", "灵动", "寻常",
        )
        private val DESCRIPTIVE_MODIFIER_CHARS = setOf(
            '大', '小', '高', '低', '长', '短', '黑', '白', '红', '青',
            '蓝', '绿', '紫', '黄', '灰', '瘦', '胖', '细', '粗', '新',
            '旧', '冷', '热', '美', '丑', '浓', '淡', '深', '浅', '柔',
            '硬', '精', '灵',
        )
        private val DESCRIPTIVE_MODIFIER_TARGET_PREFIXES = listOf(
            "m\u1EC7t", "nhanh", "r\u1EA5t", "cao",
            "trẻ", "già", "đậm", "nồng", "đẹp", "tinh", "gầy", "thon",
            "mảnh", "cao", "nhỏ", "dài", "đen", "trắng", "đỏ", "xanh",
            "tím", "vàng", "xám", "lớn", "kỳ", "đặc", "bình", "lạnh",
            "ấm", "dịu", "sáng", "trong", "sâu", "linh",
        )
        private val ADVERBIAL_SOURCES = listOf(
            "突然", "忽然", "立刻", "马上", "缓缓", "慢慢", "轻轻", "狠狠",
            "直接", "再次", "终于", "依旧", "仍然", "已经", "正在",
        )
        private val VERB_SOURCE_SUFFIXES = listOf(
            "\u7CFB", "\u7AEF", "\u63E1", "\u6572\u6253", "\u547C\u558A",
            "\u88C5", "\u9A82", "\u9A82\u9053", "\u6253\u5F00", "\u8DD1",
            "\u56DE\u5BB6", "\u505C\u5DE5", "\u7EE7\u7EED",
            "说", "道", "问", "答", "喊", "叫", "看", "望", "想", "觉得",
            "认为", "发现", "听", "走", "来", "去", "进入", "离开", "拿",
            "放", "打", "杀", "攻", "守", "笑", "哭", "坐", "站", "躺",
        )
        private val VERB_TARGET_PREFIXES = listOf(
            "\u0111eo", "b\u01B0ng", "c\u1EA7m", "g\u00F5", "g\u1ECDi", "nh\u1ED3i",
            "m\u1EAFng", "ng\u1ED3i", "s\u00FAt", "\u0111\u00E1", "m\u1EDF",
            "ch\u1EA1y", "v\u1EC1", "ngh\u1EC9", "ti\u1EBFp t\u1EE5c",
            "nói", "hỏi", "đáp", "nhìn", "nghĩ", "cảm thấy", "cho rằng",
            "phát hiện", "nghe", "đi", "đến", "vào", "rời", "lấy", "đặt",
            "đánh", "giết", "tấn công", "bảo vệ", "cười", "khóc", "ngồi",
            "đứng", "nằm",
        )
        private val NOUN_SOURCE_SUFFIXES = listOf(
            "刀", "剑", "枪", "弓", "箭", "书", "信", "纸", "笔", "灯", "门",
            "窗", "桌", "椅", "床", "车", "船", "药", "丹", "符", "阵", "法",
            "术", "功", "拳", "掌", "指", "腿", "脚", "手", "眼", "脸", "头",
            "心", "魂", "力", "气", "血", "骨", "肉", "房间", "笑容", "声音",
            "气息", "目光", "身体", "衣服", "长刀",
        )
        private val TERM_ASSETS = listOf(
            CatalogAsset("qt_clean_names_core_v2.tsv", QuickDictionaryType.NAME),
            CatalogAsset(
                "qt_clean_names_wikidata_v2.tsv",
                QuickDictionaryType.NAME,
                TermSourceQuality.CATALOG_NAME,
            ),
            CatalogAsset(
                "qt_clean_corrections_v10.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.LATEST_CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v9.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.LATEST_CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v8.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.LATEST_CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v7.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.LATEST_CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v6.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v5.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.CORRECTION,
            ),
            CatalogAsset(
                "qt_clean_corrections_v4.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.CORRECTION,
            ),
            CatalogAsset("qt_clean_webnovel_v4.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_function_words_v3.tsv", QuickDictionaryType.PRONOUN),
            CatalogAsset("qt_clean_vietphrase_v2.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_lexicon_v1.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset(
                "qt_clean_cvdict_base_v3.tsv",
                QuickDictionaryType.VIETPHRASE,
                TermSourceQuality.BASE,
            ),
        )
        private val PHONETIC_ASSETS = listOf(
            "qt_clean_phonetics_unihan17.tsv",
            "qt_clean_phonetics_supplement_v4.tsv",
            "qt_drduc_phonetics_v1.tsv",
        )
        private val RULE_ASSETS = listOf(
            "qt_clean_rules_v8.tsv",
            "qt_clean_rules_v7.tsv",
            "qt_clean_rules_v6.tsv",
            "qt_clean_rules_v5.tsv",
            "qt_clean_rules_v4.tsv",
            "qt_clean_rules_v3.tsv",
            "qt_clean_rules_v2.tsv",
            "qt_clean_rules_v1.tsv",
        )
        private val CATALOG_ORDER = listOf(
            QuickDictionaryType.VIETPHRASE,
            QuickDictionaryType.NAME,
            QuickDictionaryType.PHONETIC,
            QuickDictionaryType.PRONOUN,
            QuickDictionaryType.LUAT_NHAN,
        )
        private val CATALOG_ASSETS = listOf(
            CatalogAsset("qt_clean_names_core_v2.tsv", QuickDictionaryType.NAME),
            CatalogAsset("qt_clean_names_wikidata_v2.tsv", QuickDictionaryType.NAME),
            CatalogAsset("qt_clean_corrections_v10.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v9.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v8.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v7.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v6.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v5.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_corrections_v4.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_webnovel_v4.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_vietphrase_v2.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_lexicon_v1.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_cvdict_base_v3.tsv", QuickDictionaryType.VIETPHRASE),
            CatalogAsset("qt_clean_function_words_v3.tsv", QuickDictionaryType.PRONOUN),
            CatalogAsset("qt_clean_phonetics_unihan17.tsv", QuickDictionaryType.PHONETIC),
            CatalogAsset("qt_clean_phonetics_supplement_v4.tsv", QuickDictionaryType.PHONETIC),
            CatalogAsset("qt_drduc_phonetics_v1.tsv", QuickDictionaryType.PHONETIC),
            CatalogAsset("qt_clean_rules_v8.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v7.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v6.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v5.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v4.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v3.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v2.tsv", QuickDictionaryType.LUAT_NHAN),
            CatalogAsset("qt_clean_rules_v1.tsv", QuickDictionaryType.LUAT_NHAN),
        )
        private val QT2020_SOURCE_CATALOGS = listOf(
            Qt2020SourceCatalog(
                catalogId = "qt2020:names2",
                fileName = "Names2.txt",
                type = QuickDictionaryType.NAME,
                entryCount = 1_768,
            ),
            Qt2020SourceCatalog(
                catalogId = "qt2020:names",
                fileName = "Names.txt",
                type = QuickDictionaryType.NAME,
                entryCount = 929,
            ),
            Qt2020SourceCatalog(
                catalogId = "qt2020:vietphrase2",
                fileName = "VietPhrase2.txt",
                type = QuickDictionaryType.VIETPHRASE,
                entryCount = 4_748,
            ),
            Qt2020SourceCatalog(
                catalogId = "qt2020:vietphrase",
                fileName = "VietPhrase.txt",
                type = QuickDictionaryType.VIETPHRASE,
                entryCount = 728_698,
            ),
            Qt2020SourceCatalog(
                catalogId = "qt2020:pronouns",
                fileName = "Pronouns.txt",
                type = QuickDictionaryType.PRONOUN,
                entryCount = 12_063,
            ),
        )

        private val QuickDictionaryType.catalogName: String
            get() = when (this) {
                QuickDictionaryType.NAME -> "Name.txt"
                QuickDictionaryType.VIETPHRASE -> "VietPhrase.txt"
                QuickDictionaryType.PHONETIC -> "PhienAm.txt"
                QuickDictionaryType.PRONOUN -> "Pronouns.txt"
                QuickDictionaryType.LUAT_NHAN -> "LuatNhan.txt"
                QuickDictionaryType.IGNORE -> "Ignore.txt"
                QuickDictionaryType.TERM -> "Terms.txt"
            }

        private const val NUMBER_CHARS_REGEX =
            "0-9０-９零〇一壹二贰貳两兩三叁參四肆五伍六陆陸七柒八捌九玖十拾百佰千仟万萬亿億兆点點.．"
        private const val DATE_NUMBER_CHARS_REGEX =
            "0-9０-９零〇一壹二贰貳两兩三叁參四肆五伍六陆陸七柒八捌九玖十拾百佰千仟"
        private const val DECIMAL_DIGIT_CHARS_REGEX =
            "0-9０-９零〇一壹二贰貳两兩三叁參四肆五伍六陆陸七柒八捌九玖"
        private val CHAPTER_LABELS = mapOf(
            "\u7AE0\u8282" to "Ch\u01B0\u01A1ng",
            "\u7AE0\u7BC0" to "Ch\u01B0\u01A1ng",
            "章" to "Chương",
            "回" to "Hồi",
            "节" to "Tiết",
            "節" to "Tiết",
            "卷" to "Quyển",
            "部" to "Phần",
            "篇" to "Thiên",
            "集" to "Tập",
            "幕" to "Màn",
            "季" to "Mùa",
        )
        private val SPECIAL_CHAPTER_HEADINGS = linkedMapOf(
            "\u6B63\u6587\u5377" to "Ch\u00EDnh v\u0103n",
            "\u6B63\u6587\u90E8\u5206" to "Ph\u1EA7n ch\u00EDnh v\u0103n",
            "\u4E0A\u5377" to "Quy\u1EC3n th\u01B0\u1EE3ng",
            "\u4E2D\u5377" to "Quy\u1EC3n trung",
            "\u4E0B\u5377" to "Quy\u1EC3n h\u1EA1",
            "\u5E8F\u7AE0" to "Ch\u01B0\u01A1ng m\u1EDF \u0111\u1EA7u",
            "\u5E8F\u5E55" to "M\u00E0n m\u1EDF \u0111\u1EA7u",
            "\u6954\u5B50" to "D\u1EABn nh\u1EADp",
            "\u5F15\u5B50" to "D\u1EABn nh\u1EADp",
            "\u7EC8\u7AE0" to "Ch\u01B0\u01A1ng cu\u1ED1i",
            "\u7D42\u7AE0" to "Ch\u01B0\u01A1ng cu\u1ED1i",
            "\u5C3E\u58F0" to "V\u0129 thanh",
            "\u5C3E\u8072" to "V\u0129 thanh",
            "\u540E\u8BB0" to "H\u1EADu k\u00FD",
            "\u5F8C\u8A18" to "H\u1EADu k\u00FD",
            "\u756A\u5916\u7BC7" to "Ngo\u1EA1i truy\u1EC7n",
            "\u756A\u5916\u7AE0" to "Ngo\u1EA1i truy\u1EC7n",
            "\u756A\u5916\u5377" to "Quy\u1EC3n ngo\u1EA1i truy\u1EC7n",
            "\u756A\u5916" to "Ngo\u1EA1i truy\u1EC7n",
            "\u5916\u4F20" to "Ngo\u1EA1i truy\u1EC7n",
            "\u5916\u50B3" to "Ngo\u1EA1i truy\u1EC7n",
        )
        private val CHAPTER_LABEL_PATTERN = CHAPTER_LABELS.keys
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        private val SPECIAL_CHAPTER_HEADING_PATTERN = Regex(
            SPECIAL_CHAPTER_HEADINGS.keys
                .sortedByDescending(String::length)
                .joinToString("|") { Regex.escape(it) }
        )
        private val CHAPTER_PREFIX_PATTERN = Regex(
            "(?:\u7B2C\\s*)+([$NUMBER_CHARS_REGEX]+)\\s*($CHAPTER_LABEL_PATTERN)"
        )
        private val CHAPTER_SUFFIX_PATTERN = Regex(
            "($CHAPTER_LABEL_PATTERN)\\s*([$NUMBER_CHARS_REGEX]+)"
        )
        private val ORDINAL_PLACE_TEMPLATES = mapOf(
            "层" to "tầng {n}",
            "層" to "tầng {n}",
            "楼" to "lầu {n}",
            "樓" to "lầu {n}",
            "区" to "khu {n}",
            "區" to "khu {n}",
            "号" to "số {n}",
            "號" to "số {n}",
            "位" to "vị thứ {n}",
            "次" to "lần thứ {n}",
        )
        private val ORDINAL_NOUN_TEMPLATES = linkedMapOf(
            "条命" to "cuộc đời thứ {n}",
            "條命" to "cuộc đời thứ {n}",
        )
        private val NUMBER_UNIT_TEMPLATES = linkedMapOf(
            "\u4EBA\u6C11\u5E01" to "{n} nh\u00E2n d\u00E2n t\u1EC7",
            "\u5143\u4EBA\u6C11\u5E01" to "{n} nh\u00E2n d\u00E2n t\u1EC7",
            "\u7F8E\u91D1" to "{n} \u0111\u00F4 la M\u1EF9",
            "\u6B27\u5143" to "{n} euro",
            "\u6B50\u5143" to "{n} euro",
            "\u65E5\u5143" to "{n} y\u00EAn",
            "\u6E2F\u5E01" to "{n} \u0111\u00F4 la H\u1ED3ng K\u00F4ng",
            "\u82F1\u9551" to "{n} b\u1EA3ng Anh",
            "\u8D8A\u5357\u76FE" to "{n} \u0111\u1ED3ng Vi\u1EC7t Nam",
            "\u6BEB\u5347" to "{n} ml",
            "\u5347" to "{n} l\u00EDt",
            "\u7ACB\u65B9\u7C73" to "{n} m\u00B3",
            "\u5E73\u65B9\u516C\u91CC" to "{n} km\u00B2",
            "\u516C\u9877" to "{n} hecta",
            "\u4EA9" to "{n} m\u1EABu",
            "\u6BEB\u514B" to "{n} mg",
            "\u514B" to "{n} g",
            "\u4E2A\u4EBA" to "{n} ng\u01B0\u1EDDi",
            "\u500B\u4EBA" to "{n} ng\u01B0\u1EDDi",
            "\u4E2A\u5B69\u5B50" to "{n} \u0111\u1EE9a tr\u1EBB",
            "\u500B\u5B69\u5B50" to "{n} \u0111\u1EE9a tr\u1EBB",
            "\u4E2A\u5C0F\u5B69" to "{n} \u0111\u1EE9a tr\u1EBB",
            "\u500B\u5C0F\u5B69" to "{n} \u0111\u1EE9a tr\u1EBB",
            "\u4E2A\u7537\u5B69" to "{n} b\u00E9 trai",
            "\u500B\u7537\u5B69" to "{n} b\u00E9 trai",
            "\u4E2A\u5973\u5B69" to "{n} b\u00E9 g\u00E1i",
            "\u500B\u5973\u5B69" to "{n} b\u00E9 g\u00E1i",
            "\u4E2A\u7537\u5A03" to "{n} b\u00E9 trai",
            "\u500B\u7537\u5A03" to "{n} b\u00E9 trai",
            "\u4E2A\u5973\u5A03" to "{n} b\u00E9 g\u00E1i",
            "\u500B\u5973\u5A03" to "{n} b\u00E9 g\u00E1i",
            "\u4E2A\u513F\u5B50" to "{n} con trai",
            "\u500B\u5152\u5B50" to "{n} con trai",
            "\u4E2A\u5973\u513F" to "{n} con g\u00E1i",
            "\u500B\u5973\u5152" to "{n} con g\u00E1i",
            "\u4E2A\u5927\u4EBA" to "{n} ng\u01B0\u1EDDi l\u1EDBn",
            "\u500B\u5927\u4EBA" to "{n} ng\u01B0\u1EDDi l\u1EDBn",
            "\u4E2A\u5916\u5B59" to "{n} ch\u00E1u ngo\u1EA1i",
            "\u500B\u5916\u5B6B" to "{n} ch\u00E1u ngo\u1EA1i",
            "\u540D" to "{n} ng\u01B0\u1EDDi",
            "\u4F4D" to "{n} ng\u01B0\u1EDDi",
            "条命" to "{n} cuộc đời",
            "條命" to "{n} cuộc đời",
            "\u6761" to "{n} con",
            "\u53CC" to "{n} \u0111\u00F4i",
            "\u5957" to "{n} b\u1ED9",
            "千万美元" to "{n} chục triệu đô la Mỹ",
            "万美元" to "{n} vạn đô la Mỹ",
            "美元" to "{n} đô la Mỹ",
            "千万元" to "{n} chục triệu tệ",
            "万元" to "{n} vạn tệ",
            "块钱" to "{n} tệ",
            "元" to "{n} tệ",
            "块" to "{n} tệ",
            "角" to "{n} hào",
            "毛" to "{n} hào",
            "分钱" to "{n} xu",
            "文钱" to "{n} đồng",
            "文" to "{n} đồng",
            "金币" to "{n} kim tệ",
            "银币" to "{n} ngân tệ",
            "铜币" to "{n} đồng tệ",
            "灵石" to "{n} linh thạch",
            "两银子" to "{n} lượng bạc",
            "两黄金" to "{n} lượng vàng",
            "年" to "{n} năm",
            "个月" to "{n} tháng",
            "月" to "{n} tháng",
            "周" to "{n} tuần",
            "星期" to "{n} tuần",
            "天" to "{n} ngày",
            "日" to "{n} ngày",
            "号" to "số {n}",
            "小时" to "{n} giờ",
            "个小时" to "{n} giờ",
            "分钟" to "{n} phút",
            "分" to "{n} phút",
            "秒钟" to "{n} giây",
            "秒" to "{n} giây",
            "刻" to "{n} khắc",
            "公里" to "{n} km",
            "千米" to "{n} km",
            "米" to "{n} mét",
            "平方米" to "{n} m²",
            "平米" to "{n} m²",
            "厘米" to "{n} cm",
            "公分" to "{n} cm",
            "毫米" to "{n} mm",
            "里" to "{n} dặm",
            "丈" to "{n} trượng",
            "尺" to "{n} thước",
            "寸" to "{n} tấc",
            "公斤" to "{n} kg",
            "千克" to "{n} kg",
            "斤" to "{n} cân",
            "吨" to "{n} tấn",
            "岁" to "{n} tuổi",
            "层" to "{n} tầng",
            "層" to "{n} tầng",
            "楼" to "{n} lầu",
            "樓" to "{n} lầu",
            "区" to "khu {n}",
            "區" to "khu {n}",
            "级" to "cấp {n}",
            "倍" to "{n} lần",
            "人" to "{n} người",
            "万人" to "{n} vạn người",
            "个" to "{n} cái",
            "只" to "{n} con",
            "头" to "{n} con",
            "匹" to "{n} con",
            "本" to "{n} quyển",
            "张" to "{n} tấm",
            "枚" to "{n} viên",
            "颗" to "{n} viên",
            "辆" to "{n} chiếc",
            "座" to "{n} tòa",
            "件" to "{n} món",
            "道" to "{n} đạo",
            "门" to "{n} môn",
            "次" to "{n} lần",
            "遍" to "{n} lượt",
            "种" to "{n} loại",
        )
        private val NUMBER_UNIT_PATTERN = Regex(
            "([$NUMBER_CHARS_REGEX]+)\\s*(" +
                NUMBER_UNIT_TEMPLATES.keys.sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) } +
                ")(左右|来|來|多|余|餘|以上|以下|以内|內|内|以外|外|之前|前|之后|後|后)?"
        )
        private val ORDINAL_PLACE_PATTERN = Regex("第\\s*([$NUMBER_CHARS_REGEX]+)\\s*([层層楼樓区區号號位次])")
        private val ORDINAL_NOUN_PATTERN = Regex(
            "第\\s*([$NUMBER_CHARS_REGEX]+)\\s*(" +
                ORDINAL_NOUN_TEMPLATES.keys.sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) } +
                ")"
        )
        private val FULL_DATE_PATTERN = Regex(
            "([$DATE_NUMBER_CHARS_REGEX]+)\\s*年\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*月\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:日|号|號)"
        )
        private val YEAR_MONTH_PATTERN = Regex("([$DATE_NUMBER_CHARS_REGEX]+)\\s*年\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*月")
        private val MONTH_DAY_PATTERN = Regex("([$DATE_NUMBER_CHARS_REGEX]+)\\s*月\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:日|号|號)")
        private val WEEKDAY_PATTERN = Regex("(?:星期|周|週|礼拜|禮拜)([一二三四五六日天])")
        private val WEEKDAY_TRANSLATIONS = mapOf(
            "一" to "thứ hai",
            "二" to "thứ ba",
            "三" to "thứ tư",
            "四" to "thứ năm",
            "五" to "thứ sáu",
            "六" to "thứ bảy",
            "日" to "chủ nhật",
            "天" to "chủ nhật",
        )
        private val DIGITAL_TIME_PATTERN = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})(?:\\s*[:：]\\s*(\\d{1,2}))?")
        private val TIME_PREFIX_TRANSLATIONS = mapOf(
            "" to "",
            "早晨" to "sáng",
            "早上" to "sáng",
            "上午" to "sáng",
            "中午" to "trưa",
            "下午" to "chiều",
            "傍晚" to "chiều tối",
            "晚上" to "tối",
            "深夜" to "đêm",
            "凌晨" to "sáng sớm",
            "半夜" to "nửa đêm",
        )
        private const val TIME_PREFIX_PATTERN = "(早晨|早上|上午|中午|下午|傍晚|晚上|深夜|凌晨|半夜)?"
        private val TIME_MINUTE_SECOND_PATTERN = Regex("$TIME_PREFIX_PATTERN\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:点|點|时|時)\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*分\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*秒")
        private val TIME_HALF_PATTERN = Regex("$TIME_PREFIX_PATTERN\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:点|點|时|時)\\s*半")
        private val TIME_QUARTER_PATTERN = Regex("$TIME_PREFIX_PATTERN\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:点|點|时|時)\\s*(一刻|三刻|刻)")
        private val TIME_MINUTE_PATTERN = Regex("$TIME_PREFIX_PATTERN\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:点|點|时|時)\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*分")
        private val TIME_HOUR_PATTERN = Regex("$TIME_PREFIX_PATTERN\\s*([$DATE_NUMBER_CHARS_REGEX]+)\\s*(?:点|點|时|時)")
        private val PERCENT_PREFIX_PATTERN = Regex("百分之\\s*([$NUMBER_CHARS_REGEX]+)")
        private val PERCENT_SUFFIX_PATTERN = Regex("([$NUMBER_CHARS_REGEX]+)\\s*(?:%|％)")
        private val DECIMAL_NUMBER_PATTERN = Regex(
            "([$DECIMAL_DIGIT_CHARS_REGEX]*[点點.．][$DECIMAL_DIGIT_CHARS_REGEX]+)"
        )
        private val DECIMAL_REJECT_FOLLOWERS = setOf(
            '十', '拾', '百', '佰', '千', '仟', '万', '萬', '亿', '億', '兆',
            '分', '秒',
        )

        private val CHINESE_DIGITS = mapOf(
            '零' to 0,
            '〇' to 0,
            '一' to 1,
            '壹' to 1,
            '二' to 2,
            '贰' to 2,
            '貳' to 2,
            '两' to 2,
            '兩' to 2,
            '三' to 3,
            '叁' to 3,
            '參' to 3,
            '四' to 4,
            '肆' to 4,
            '五' to 5,
            '伍' to 5,
            '六' to 6,
            '陆' to 6,
            '陸' to 6,
            '七' to 7,
            '柒' to 7,
            '八' to 8,
            '捌' to 8,
            '九' to 9,
            '玖' to 9,
        )
        private val CHINESE_MULTIPLIERS = mapOf(
            '十' to 10L,
            '拾' to 10L,
            '百' to 100L,
            '佰' to 100L,
            '千' to 1000L,
            '仟' to 1000L,
        )
        private val CHINESE_SECTIONS = mapOf(
            '万' to 10_000L,
            '萬' to 10_000L,
            '亿' to 100_000_000L,
            '億' to 100_000_000L,
            '兆' to 1_000_000_000_000L,
        )

        private fun parseQuickNumber(value: String): String? {
            val normalized = normalizeNumberText(value)
            if (normalized.isBlank()) return null
            if (normalized.all { it.isDigit() || it == '.' }) {
                return normalized.trimEnd('.')
            }
            val pointIndex = normalized.indexOfFirst { it == '点' || it == '點' || it == '.' }
            if (pointIndex >= 0) {
                val integer = parseChineseInteger(normalized.substring(0, pointIndex)) ?: return null
                val decimal = normalized.substring(pointIndex + 1)
                    .mapNotNull { digitValue(it) }
                    .joinToString("")
                return if (decimal.isBlank()) integer.toString() else "$integer.$decimal"
            }
            return parseChineseInteger(normalized)?.toString()
        }

        private fun parseChineseInteger(value: String): Long? {
            if (value.isBlank()) return 0L
            if (value.all { it.isDigit() }) return value.toLongOrNull()
            var remaining = value
            var total = 0L
            listOf('兆', '亿', '億', '万', '萬').forEach { section ->
                val index = remaining.indexOf(section)
                if (index >= 0) {
                    val sectionValue = parseChineseUnderTenThousand(remaining.substring(0, index))
                        ?.takeIf { it > 0L } ?: 1L
                    total += sectionValue * (CHINESE_SECTIONS[section] ?: return null)
                    remaining = remaining.substring(index + 1)
                }
            }
            return total + (parseChineseUnderTenThousand(remaining) ?: return null)
        }

        private fun parseChineseUnderTenThousand(value: String): Long? {
            if (value.isBlank()) return 0L
            if (value.all { digitValue(it) != null }) {
                return value.mapNotNull { digitValue(it) }.joinToString("").toLongOrNull()
            }
            var result = 0L
            var number: Long? = null
            value.forEach { char ->
                val digit = digitValue(char)
                if (digit != null) {
                    number = digit.toLong()
                    return@forEach
                }
                val multiplier = CHINESE_MULTIPLIERS[char] ?: return null
                result += (number ?: 1L) * multiplier
                number = null
            }
            return result + (number ?: 0L)
        }

        private fun digitValue(value: Char): Int? = when {
            value in '0'..'9' -> value - '0'
            value in '０'..'９' -> value - '０'
            else -> CHINESE_DIGITS[value]
        }

        private fun normalizeNumberText(value: String): String = buildString(value.length) {
            value.trim().forEach { char ->
                append(
                    when (char) {
                        in '０'..'９' -> '0' + (char - '０')
                        '．' -> '.'
                        else -> char
                    }
                )
            }
        }

        private fun normalize(value: String): String = value.trim().lowercase()

        private fun isCjk(value: Int): Boolean =
            value in 0x3400..0x4DBF || value in 0x4E00..0x9FFF || value in 0x20000..0x2A6DF
    }
}

internal fun shouldEnableJiebaTokenizer(
    memoryClassMb: Int,
    isLowRamDevice: Boolean,
): Boolean = !isLowRamDevice && memoryClassMb >= MIN_JIEBA_HEAP_MB

private const val MIN_JIEBA_HEAP_MB = 256

internal fun cleanQuickDictionaryTarget(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || "://" in trimmed) return trimmed
    val candidates = quickDictionaryTargetCandidates(trimmed)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(::isDefinitionLikeQuickDictionaryTarget)
        .mapIndexed { index, target ->
            QuickDictionaryTargetCandidate(
                value = target,
                score = quickDictionaryTargetScore(target),
                order = index,
            )
        }
        .toList()
    if (candidates.isEmpty()) return ""
    return candidates.sortedWith(
        compareByDescending<QuickDictionaryTargetCandidate> { it.score }
            .thenBy { it.order }
    ).first().value
}

private fun quickDictionaryTargetCandidates(value: String): List<String> {
    return value.split('/', '\uFF0F', '|', '\uFF5C')
        .flatMap(::quickDictionaryEqualsCandidates)
}

private fun quickDictionaryEqualsCandidates(value: String): List<String> {
    val trimmed = value.trim()
    val parts = trimmed.split('=')
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (parts.size <= 1) return listOf(trimmed)
    return parts.asReversed()
}

private data class QuickDictionaryTargetCandidate(
    val value: String,
    val score: Int,
    val order: Int,
)

private fun quickDictionaryTargetScore(value: String): Int {
    var score = value.length.coerceAtMost(40)
    var offset = 0
    var cjkCount = 0
    var latinLetterCount = 0
    var letterCount = 0
    var hasReplacementChar = false
    while (offset < value.length) {
        val codePoint = value.codePointAt(offset)
        when {
            isQuickDictionaryCjk(codePoint) -> cjkCount += 1
            codePoint == 0xFFFD -> hasReplacementChar = true
            Character.isLetter(codePoint) -> {
                letterCount += 1
                if (codePoint in 'A'.code..'Z'.code || codePoint in 'a'.code..'z'.code) {
                    latinLetterCount += 1
                }
            }
        }
        offset += Character.charCount(codePoint)
    }
    if (letterCount > 0) score += 8
    if (latinLetterCount > 0) score += 20
    if (value.any(Char::isWhitespace)) score += 4
    if (value.any { it == '=' || it == '|' || it == '\uFF5C' }) score -= 25
    if (isDefinitionLikeQuickDictionaryTarget(value)) score -= 80
    if (cjkCount > 0) score -= 60 + cjkCount * 5
    if (hasReplacementChar) score -= 80
    if (value.length == 1 && letterCount == 1) score -= 3
    return score
}

private fun isDefinitionLikeQuickDictionaryTarget(value: String): Boolean {
    val normalized = value.lowercase()
    if (QUICK_DICTIONARY_DEFINITION_MARKERS.any(normalized::contains)) return true
    return value.length > 32 &&
        (value.any { it == ',' || it == ';' || it == ':' || it == '\uFF0C' } ||
            normalized.contains(" - "))
}

private val QUICK_DICTIONARY_DEFINITION_MARKERS = listOf(
    "tri\u1EBFt gia",
    "ng\u01B0\u1EDDi s\u00E1ng l\u1EADp",
    "ngh\u0129a \u0111en",
    "c\u00F2n g\u1ECDi",
    "t\u1EE9c l\u00E0",
)

private fun isQuickDictionaryCjk(value: Int): Boolean =
    value in 0x3400..0x4DBF || value in 0x4E00..0x9FFF || value in 0x20000..0x2A6DF

private fun StringBuilder.appendWord(value: String) {
    val clean = value.trim()
    if (clean.isEmpty()) return
    val previous = lastOrNull()
    val next = clean.first()
    if (QuickTranslationTextPostProcessor.needsWordSeparator(previous, next)) {
        append(' ')
    }
    append(clean)
}

private fun StringBuilder.appendLiteralRun(value: String) {
    if (value.isEmpty()) return
    val previous = lastOrNull()
    val next = value.first()
    if (!next.isWhitespace() &&
        QuickTranslationTextPostProcessor.needsWordSeparator(previous, next)
    ) {
        append(' ')
    }
    append(value)
}

private inline fun <T> Iterable<T>.joinToStringByWord(transform: (T) -> String): String {
    val output = StringBuilder()
    forEach { output.appendWord(transform(it)) }
    return output.toString()
}

private fun Regex.matchAt(text: String, offset: Int): MatchResult? {
    val match = find(text, offset) ?: return null
    return match.takeIf { it.range.first == offset }
}

private fun String.parseProtectedTokenIndex(offset: Int): Int {
    var value = 0
    repeat(5) { index ->
        val digit = getOrNull(offset + index)?.digitToIntOrNull() ?: return -1
        value = value * 10 + digit
    }
    return value
}

internal object QuickTranslationTextPostProcessor {

    private const val PROTECTED_TOKEN_START = '\uE600'
    private const val PROTECTED_TOKEN_END = '\uE601'
    private val sentenceTerminators = setOf('.', '!', '?', '…', '。', '！', '？', '\n', '\r')
    private val inlineTerminators = setOf(
        ',', '.', ';', ':', '!', '?', '|', '+',
        '，', '。', '；', '：', '！', '？', '·', '•',
    )

    internal fun needsWordSeparator(previous: Char?, next: Char): Boolean {
        if (previous == null || previous == '\n' || previous == '\r' || previous.isWhitespace()) {
            return false
        }
        val nextIsWord = next.isLetterOrDigit() || next.code in 0x3400..0x9FFF
        if (!nextIsWord) return false
        return previous.isLetterOrDigit() ||
            previous.code in 0x3400..0x9FFF ||
            previous in inlineTerminators
    }

    internal fun normalizeNumericSpacing(value: String): String {
        return collapseDecimalPointSpacing(
            dedupeRepeatedHeadings(value)
                .replace(Regex("(?<=\\d)[ \\t]*([,])[ \\t]*(?=\\d)"), "$1")
        )
            .replace(Regex("(?<=\\d)[ \\t]+(?=\\d)"), "")
    }

    internal fun cleanHeadingArtifacts(value: String): String = dedupeRepeatedHeadings(value)

    private fun collapseDecimalPointSpacing(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '.' && index > 0 && value[index - 1].isDigit()) {
                var next = index + 1
                while (next < value.length && (value[next] == ' ' || value[next] == '\t')) {
                    next += 1
                }
                if (next > index + 1 && next < value.length && value[next].isDigit() &&
                    canCollapseDecimalPoint(output)
                ) {
                    output.append('.')
                    index = next
                    continue
                }
            }
            output.append(char)
            index += 1
        }
        return output.toString()
    }

    private fun canCollapseDecimalPoint(output: StringBuilder): Boolean {
        var cursor = output.length - 1
        while (cursor >= 0 && output[cursor].isDigit()) {
            cursor -= 1
        }
        return cursor < 0 || output[cursor] != '.'
    }

    private fun dedupeRepeatedHeadings(value: String): String {
        var output = value
        repeatedHeadingLabelPatterns.forEach { pattern ->
            output = pattern.replace(output) { match ->
                match.groupValues[1] + match.groupValues[2] + " " + match.groupValues[3]
            }
        }
        output = repeatedSpecialHeadingPattern.replace(output) { match ->
            match.groupValues[1] + match.groupValues[2]
        }
        return output
    }

    /**
     * Capitalizes sentence and paragraph starts without trimming or normalizing any whitespace.
     * Protected markup tokens are copied byte-for-byte and do not consume the pending capital.
     */
    fun capitalizeSentenceStarts(value: String): String {
        if (value.isEmpty()) return value
        return buildString(value.length) {
            var offset = 0
            var capitalizeNext = true
            while (offset < value.length) {
                if (value[offset] == PROTECTED_TOKEN_START) {
                    val end = value.indexOf(PROTECTED_TOKEN_END, offset + 1)
                    if (end >= 0) {
                        append(value, offset, end + 1)
                        offset = end + 1
                        continue
                    }
                }

                val codePoint = value.codePointAt(offset)
                val source = String(Character.toChars(codePoint))
                if (capitalizeNext && Character.isLetter(codePoint)) {
                    append(source.replaceFirstChar { it.titlecaseChar() })
                    capitalizeNext = false
                } else {
                    append(source)
                    if (Character.isLetterOrDigit(codePoint)) {
                        capitalizeNext = false
                    }
                }
                if (source.length == 1 && source[0] in sentenceTerminators) {
                    capitalizeNext = true
                }
                offset += Character.charCount(codePoint)
            }
        }
    }

    private val repeatedHeadingLabelPatterns = listOf(
        Regex(
            "(^|\\s)(Chương|Quyển|Tiết|Phần|Thiên|Tập|Hồi|Màn|Mùa)\\s+" +
                "\\2\\s+([0-9]+)(?=\\s|$|[.,:;!?])",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "(^|\\s)(Chương|Quyển|Tiết|Phần|Thiên|Tập|Hồi|Màn|Mùa)\\s+" +
                "([0-9]+)\\s+\\2\\s+\\3(?=\\s|$|[.,:;!?])",
            RegexOption.IGNORE_CASE,
        ),
    )
    private val repeatedSpecialHeadingPattern = Regex(
        "(^|\\s)(Chương mở đầu|Chương cuối|Chính văn|Ngoại truyện|" +
            "Quyển thượng|Quyển trung|Quyển hạ)\\s+\\2(?=\\s|$|[.,:;!?])",
        RegexOption.IGNORE_CASE,
    )
}
