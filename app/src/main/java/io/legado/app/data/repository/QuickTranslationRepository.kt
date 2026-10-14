package io.legado.app.data.repository

import android.app.ActivityManager
import io.legado.app.constant.AppLog
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
import io.legado.app.domain.model.toQuickPhoneticPair
import io.legado.app.domain.model.toQuickTranslationPair
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.getPrefString
import com.huaban.analysis.jieba.JiebaSegmenter
import splitties.init.appCtx
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
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
class QuickTranslationRepository(
    private val externalPackStore: QuickDictionaryPackStore? = null,
) : QuickTranslationGateway {

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
    private val emptyProjectTrie = FastTermMapLookup(emptyList())
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
        (cachedPack.get()?.version ?: detectedPackVersion) +
            "|external:${externalPackStore?.versionToken() ?: "none"}"

    override fun warmUp() {
        pack()
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
    ): String = translate(text, projectTerms, customPhonetics, pronounMode, null)

    private val HAN_MIDDLE_DOT_MOJIBAKE_REGEX = Regex("""(?<=[\p{IsHan}])[?\uFFFD](?=[\p{IsHan}])""")

    internal fun normalizeRawChineseSource(text: String): String {
        if (text.isEmpty()) return text
        var result = if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text else Normalizer.normalize(text, Normalizer.Form.NFC)
        if ('?' in result || '\uFFFD' in result) {
            result = result.replace(HAN_MIDDLE_DOT_MOJIBAKE_REGEX, "·")
        }
        return result
    }

    override fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
        beamWidth: Int?,
    ): String {
        if (text.isEmpty()) return text
        val safeText = normalizeRawChineseSource(text)
        if (safeText.codePoints().noneMatch(::isCjk)) return safeText
        val effectiveBeamWidth = beamWidth ?: TranslationConfig.quickTranslationBeamWidth
        val tStart = System.currentTimeMillis()
        val externalEntries = externalPackStore?.matchEntries(
            context = safeText,
            projectKey = "",
            activeUniverseKey = "",
        ).orEmpty()
        val safeProjectTerms = if (projectTerms.isEmpty() && externalEntries.isEmpty()) {
            emptyList()
        } else {
            (projectTerms + externalEntries.mapNotNull { it.toQuickTranslationPair() })
                .map(DictPair::normalizedForRuntime)
        }
        val safeCustomPhonetics = if (customPhonetics.isEmpty() && externalEntries.isEmpty()) {
            emptyList()
        } else {
            (customPhonetics + externalEntries.mapNotNull { it.toQuickPhoneticPair() })
                .map(DictPair::normalizedForRuntime)
        }
        val tPrep = System.currentTimeMillis()
        val pack = pack()
        val tPack = System.currentTimeMillis()
        val projectRuntime = projectRuntimeFor(safeProjectTerms, pack)
        val projectTrie = projectRuntime.trie
        val tTrie = System.currentTimeMillis()
        val customPhoneticMap = if (safeCustomPhonetics.isEmpty()) {
            emptyMap()
        } else {
            safeCustomPhonetics.asSequence()
                .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
                .filter { it.original.codePointCount(0, it.original.length) == 1 }
                .distinctBy { it.original }
                .associate { it.original to it.translation.trim() }
        }
        val protected = protectMarkup(safeText)
        val translatedTokenCache = HashMap<String, String>()
        val tSetup = System.currentTimeMillis()
        val lexicalTimer = LexicalTimer()
        var totalLexicalTime = 0L
        var totalPostTime = 0L
        val translated = protected.layout.tokens.joinToString(separator = "") { token ->
            when (token) {
                is TranslationTextToken.TextToken -> {
                    if (token.raw.codePoints().noneMatch(::isCjk)) {
                        token.raw
                    } else {
                        translatedTokenCache.getOrPut(token.raw) {
                            val t0 = System.currentTimeMillis()
                            val lexical = translateProtected(
                                text = token.raw,
                                projectRuntime = projectRuntime,
                                pack = pack,
                                customPhonetics = customPhoneticMap,
                                timer = lexicalTimer,
                                beamWidth = effectiveBeamWidth,
                            )
                            val t1 = System.currentTimeMillis()
                            totalLexicalTime += (t1 - t0)
                            val tokenPost = postProcessToken(
                                sourceText = token.raw,
                                text = lexical,
                                pronounMode = pronounMode,
                            )
                            val t2 = System.currentTimeMillis()
                            totalPostTime += (t2 - t1)
                            tokenPost
                        }
                    }
                }

                is TranslationTextToken.ProtectedToken -> protected.tokenFor(token)
                else -> token.raw
            }
        }
        val tTokensDone = System.currentTimeMillis()
        val fullPost = postProcessFullText(
            text = translated,
            pack = pack,
            customPhonetics = customPhoneticMap,
        )
        val tFullPost = System.currentTimeMillis()
        val result = protected.restore(
            QuickTranslationTextPostProcessor.cleanHeadingArtifacts(
                QuickTranslationTextPostProcessor.capitalizeSentenceStarts(fullPost)
            )
        )
        val tPost = System.currentTimeMillis()
        val total = tPost - tStart
        if (total > 500) {
            AppLog.put("QT-PERF translate() prep=${tPrep-tStart}ms pack=${tPack-tPrep}ms trie=${tTrie-tPack}ms setup=${tSetup-tTrie}ms lexical=${totalLexicalTime}ms (base=${lexicalTimer.baseMatch}ms proj=${lexicalTimer.projMatch}ms plan=${lexicalTimer.plan}ms [struct=${lexicalTimer.structured}ms qt2025=${lexicalTimer.qt2025}ms gram=${lexicalTimer.grammar}ms tmpl=${lexicalTimer.template}ms]) tokenPost=${totalPostTime}ms fullPost=${tFullPost-tTokensDone}ms finalize=${tPost-tFullPost}ms TOTAL=${total}ms len=${text.length} tokens=${protected.layout.tokens.size}")
        }
        return result
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
    ): MappedTranslation = translateMapped(text, projectTerms, customPhonetics, pronounMode, null)

    override fun translateMapped(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
        beamWidth: Int?,
    ): MappedTranslation {
        if (text.isEmpty()) return MappedTranslation(text, emptyList(), MAPPED_ENGINE)
        val safeText = normalizeRawChineseSource(text)
        if (safeText.codePoints().noneMatch(::isCjk)) {
            return MappedTranslation(
                text = safeText,
                segments = listOf(
                    DisplaySourceSegment(
                        sourceStart = 0,
                        sourceEnd = safeText.length,
                        displayStart = 0,
                        displayEnd = safeText.length,
                        confidence = 1f,
                        exactCharacterMapping = true,
                    )
                ),
                engine = MAPPED_ENGINE,
            )
        }
        val effectiveBeamWidth = beamWidth ?: TranslationConfig.quickTranslationBeamWidth
        val externalEntries = externalPackStore?.matchEntries(
            context = safeText,
            projectKey = "",
            activeUniverseKey = "",
        ).orEmpty()
        val safeProjectTerms = (
            projectTerms + externalEntries.mapNotNull { it.toQuickTranslationPair() }
            ).map(DictPair::normalizedForRuntime)
        val safeCustomPhonetics = (
            customPhonetics + externalEntries.mapNotNull { it.toQuickPhoneticPair() }
            ).map(DictPair::normalizedForRuntime)
        val pack = pack()
        val projectRuntime = projectRuntimeFor(safeProjectTerms, pack)
        val customPhoneticMap = safeCustomPhonetics.asSequence()
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .filter { it.original.codePointCount(0, it.original.length) == 1 }
            .distinctBy { it.original }
            .associate { it.original to it.translation.trim() }
        val layout = TranslationTextTokenizer.tokenize(safeText)
        val output = StringBuilder(safeText.length * 2)
        val segments = mutableListOf<DisplaySourceSegment>()
        val capitalizer = StatefulSentenceCapitalizer()
        val translatedTokenCache = HashMap<String, LocalMappedText>()
        var sourceOffset = 0
        layout.tokens.forEach { token ->
            val displayOffset = output.length
            when (token) {
                is TranslationTextToken.TextToken -> {
                    val mapped = translatedTokenCache.getOrPut(token.raw) {
                        translateTextTokenMapped(
                            text = token.raw,
                            projectRuntime = projectRuntime,
                            pack = pack,
                            customPhonetics = customPhoneticMap,
                            pronounMode = pronounMode,
                            beamWidth = effectiveBeamWidth,
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

    override fun findMatchingTerms(
        text: String,
        projectTerms: List<DictPair>,
        types: Set<QuickDictionaryType>?,
        minLength: Int,
    ): List<DictPair> {
        if (text.isBlank() || text.codePoints().noneMatch(::isCjk)) return emptyList()
        val pack = pack()
        val safeProjectTerms = projectTerms.map(DictPair::normalizedForRuntime)
        val projectRuntime = projectRuntimeFor(safeProjectTerms, pack)

        val projectMatches = projectRuntime.trie.allMatchesByStart(text)
        val baseMatches = pack.baseTrie.allMatchesByStart(text)

        val matchedTerms = LinkedHashMap<String, DictPair>()

        // 1. Project / user-defined matches: always accept length >= 2
        for (list in projectMatches) {
            if (list.isNullOrEmpty()) continue
            for (match in list) {
                val term = match.term
                val raw = term.source.trim()
                val target = term.target.trim()
                if (raw.length >= 2 && target.isNotBlank()) {
                    matchedTerms[raw] = DictPair(raw, target)
                }
            }
        }

        // 2. Base pack matches: NAME >= 2 chars, TERM >= 2 chars, VIETPHRASE >= 3 chars
        for (list in baseMatches) {
            if (list.isNullOrEmpty()) continue
            for (match in list) {
                val term = match.term
                val raw = term.source.trim()
                val target = term.target.trim()
                if (target.isBlank()) continue

                val isAccepted = when (term.type) {
                    QuickDictionaryType.NAME -> raw.length >= minLength
                    QuickDictionaryType.TERM -> raw.length >= minLength
                    QuickDictionaryType.VIETPHRASE -> raw.length >= minLength
                    else -> raw.length >= minLength
                }
                if (isAccepted && (types == null || term.type in types)) {
                    if (!matchedTerms.containsKey(raw)) {
                        matchedTerms[raw] = DictPair(raw, target)
                    }
                }
            }
        }

        return matchedTerms.values.toList()
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

    private class LexicalTimer {
        var baseMatch: Long = 0
        var projMatch: Long = 0
        var plan: Long = 0
        var structured: Long = 0
        var qt2025: Long = 0
        var grammar: Long = 0
        var template: Long = 0
    }

    private fun translateProtected(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        timer: LexicalTimer? = null,
        beamWidth: Int = 1,
    ): String {
        val t0 = System.currentTimeMillis()
        val projectMatches = projectRuntime.trie.allMatchesByStart(text)
        val t1 = System.currentTimeMillis()
        val baseMatches = pack.baseTrie.allMatchesByStart(text)
        val t2 = System.currentTimeMillis()
        timer?.let {
            it.projMatch += (t1 - t0)
            it.baseMatch += (t2 - t1)
        }
        val matches = RuntimeMatchIndex(
            text = text,
            projectMatches = projectMatches,
            baseMatches = baseMatches,
            jiebaTokens = emptyList(),
        )
        val output = StringBuilder(text.length * 2)
        var offset = 0
        while (offset < text.length) {
            val candidate = fmmCandidateAt(text, offset, projectRuntime, pack, customPhonetics, matches, timer)
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
                output.appendLiteralRun(candidate.translation)
            } else if (candidate.translation != QUICK_DICTIONARY_IGNORE_TARGET) {
                output.appendWord(candidate.translation)
            }
            offset = candidate.endExclusive
        }
        return output.toString()
    }

    private fun translateTextTokenMapped(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode? = null,
        beamWidth: Int = 1,
    ): LocalMappedText {
        return translateProtectedMapped(
            text = text,
            projectRuntime = projectRuntime,
            pack = pack,
            customPhonetics = customPhonetics,
            beamWidth = beamWidth,
        )
    }


    private fun translateProtectedMapped(
        text: String,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        beamWidth: Int = 1,
    ): LocalMappedText {
        val matches = RuntimeMatchIndex(
            text = text,
            projectMatches = projectRuntime.trie.allMatchesByStart(text),
            baseMatches = pack.baseTrie.allMatchesByStart(text),
            jiebaTokens = emptyList(),
        )
        val output = LocalMappedTextBuilder(text)
        var offset = 0
        while (offset < text.length) {
            val candidate = fmmCandidateAt(text, offset, projectRuntime, pack, customPhonetics, matches)
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
            if (candidate.translation != QUICK_DICTIONARY_IGNORE_TARGET) {
                output.append(
                    sourceStart = offset,
                    sourceEnd = candidate.endExclusive,
                    value = candidate.translation,
                    literal = candidate.kind == CandidateKind.LITERAL,
                )
            }
            offset = candidate.endExclusive
        }
        return output.build()
    }

    private fun postProcessToken(
        sourceText: String,
        text: String,
        pronounMode: QuickTranslationPronounMode? = null,
    ): String = text

    private fun postProcessFullText(
        text: String,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
    ): String = QuickTranslationTextPostProcessor.normalizeNumericSpacing(
        QuickTranslationGrammarPolish.polish(
            replaceRemainingCjkWithPhonetics(
                text = applyPostRules(text, pack.postRules),
                customPhonetics = customPhonetics,
                bundledPhonetics = pack.phonetics,
            )
        )
    )

    private fun postProcessTranslatedText(
        sourceText: String,
        text: String,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode? = null,
    ): String {
        val tokenPass = postProcessToken(sourceText, text, pronounMode)
        return postProcessFullText(tokenPass, pack, customPhonetics)
    }

    private fun remapProcessedSegments(
        sourceText: String,
        lexical: LocalMappedText,
        processed: String,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        pronounMode: QuickTranslationPronounMode? = null,
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

    private fun fmmCandidateAt(
        text: String,
        offset: Int,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        matches: RuntimeMatchIndex,
        timer: LexicalTimer? = null,
    ): TranslationCandidate? {
        val candidates = translationCandidatesAt(
            text = text,
            offset = offset,
            projectRuntime = projectRuntime,
            pack = pack,
            customPhonetics = customPhonetics,
            matches = matches,
            timer = timer,
        )
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull(
            compareBy<TranslationCandidate> { it.endExclusive - offset }
                .thenBy { it.priority }
                .thenBy { it.score }
        )
    }

    private fun translationCandidatesAt(
        text: String,
        offset: Int,
        projectRuntime: ProjectTrieCache,
        pack: QuickPack,
        customPhonetics: Map<String, String>,
        matches: RuntimeMatchIndex,
        timer: LexicalTimer? = null,
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
        val tS0 = System.currentTimeMillis()
        val structuredMatch = if (!insideProjectTerm &&
            mayStartStructured(text, offset) &&
            matches.projectTermsAt(offset).isEmpty()
        ) {
            bestStructuredMatch(text, offset)
        } else {
            null
        }
        timer?.let { it.structured += (System.currentTimeMillis() - tS0) }
        if (!insideProjectTerm && matches.projectTermsAt(offset).isEmpty()) {
            val tQ0 = System.currentTimeMillis()
            pack.qt2025Runtime?.matchAt(
                text = text,
                offset = offset,
                resolveName = matches::qt2025NameTarget,
                hasNameAtOffset = matches.hasQt2025Name(offset),
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
            timer?.let { it.qt2025 += (System.currentTimeMillis() - tQ0) }
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
            val tG0 = System.currentTimeMillis()
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
                val coveredByProjectTerm = matches.projectTermsAt(offset).any { projectTerm ->
                    projectTerm.endExclusive >= grammar.endExclusive
                }
                val isPersonExemption = grammar.isPerson &&
                    matches.projectTermsAt(offset).none { it.term.type == QuickDictionaryType.NAME }
                if ((!coveredByProjectTerm || isPersonExemption) && (!coveredByBaseLexicalTerm ||
                    grammar.priority >= TRUSTED_GRAMMAR_PRIORITY &&
                    !matches.hasCorrectionOverride(offset, grammar.endExclusive))
                ) {
                    candidates += TranslationCandidate(
                        endExclusive = grammar.endExclusive,
                        translation = grammar.translation,
                        priority = grammar.priority,
                        score = grammar.scoreFrom(offset, GRAMMAR_SCORE_BONUS),
                    )
                }
            }
            timer?.let { it.grammar += (System.currentTimeMillis() - tG0) }
        }
        val tT0 = System.currentTimeMillis()
        val firstChar = text.getOrNull(offset)
        val isPunctuation = firstChar != null && firstChar in PUNCTUATION_OR_BREAK_CHARS
        val termsAtOffset = matches.termsAt(offset)
        val hasLeadingSlotTemplates = !insideProjectTerm && !isPunctuation && termsAtOffset.isNotEmpty() &&
            (projectRuntime.leadingSlotTemplateIndex.isNotEmpty() || pack.leadingSlotTemplateIndex.isNotEmpty())
        val indexedProject = if (isPunctuation) emptyList() else projectRuntime.indexedTemplatesAt(text, offset)
        val indexedPack = if (isPunctuation) emptyList() else pack.indexedTemplatesAt(text, offset)
        val leadingTemplates = if (hasLeadingSlotTemplates) {
            leadingSlotTemplatesAt(
                text = text,
                offset = offset,
                matches = matches,
                projectIndex = projectRuntime.leadingSlotTemplateIndex,
                packIndex = pack.leadingSlotTemplateIndex,
            )
        } else {
            emptyList()
        }
        if (indexedProject.isNotEmpty() || indexedPack.isNotEmpty() || leadingTemplates.isNotEmpty()) {
            bestTemplateMatch(
                text = text,
                offset = offset,
                indexedTemplates = if (indexedProject.isEmpty()) indexedPack else indexedProject + indexedPack,
                leadingSlotTemplates = leadingTemplates,
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
    }
    timer?.let { it.template += (System.currentTimeMillis() - tT0) }
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
        if (match.term.target.isEmpty()) return 0L
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

    private val CORE_QT_FALLBACKS = mapOf(
        "的" to "của",
        "了" to "rồi",
        "着" to "đang",
        "得" to "được",
    )

    private fun fallbackCandidate(
        text: String,
        offset: Int,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): TranslationCandidate {
        if (text.startsWith("……", offset)) {
            return TranslationCandidate(
                endExclusive = offset + 2,
                translation = "...",
                priority = 0,
                score = FALLBACK_SCORE,
                kind = CandidateKind.LITERAL,
            )
        }
        if (text.startsWith("——", offset)) {
            return TranslationCandidate(
                endExclusive = offset + 2,
                translation = "-",
                priority = 0,
                score = FALLBACK_SCORE,
                kind = CandidateKind.LITERAL,
            )
        }
        val codePoint = text.codePointAt(offset)
        val source = String(Character.toChars(codePoint))
        val punctuation = when (source) {
            "。" -> "."
            "，", "、" -> ","
            "！" -> "!"
            "？" -> "?"
            "：" -> ":"
            "；" -> ";"
            "“", "”" -> "\""
            "‘", "’" -> "'"
            "（" -> "("
            "）" -> ")"
            "【" -> "["
            "】" -> "]"
            "《", "》" -> "\""
            "…" -> "..."
            "—" -> "-"
            else -> null
        }
        if (punctuation != null) {
            return TranslationCandidate(
                endExclusive = offset + Character.charCount(codePoint),
                translation = punctuation,
                priority = 0,
                score = FALLBACK_SCORE,
                kind = CandidateKind.LITERAL,
            )
        }
        val reading = customPhonetics[source] ?: CORE_QT_FALLBACKS[source] ?: bundledPhonetics[source]
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
        isAdverbSource(match.term.source) -> TermPos.ADVERB
        isNounSource(match.term.source) -> TermPos.NOUN
        isDescriptiveModifier(match) -> TermPos.ADJECTIVE
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
    private fun hasAnyCjk(text: String): Boolean {
        for (i in text.indices) {
            val code = text[i].code
            if (code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF) return true
        }
        return false
    }

    private fun replaceRemainingCjkWithPhonetics(
        text: String,
        customPhonetics: Map<String, String>,
        bundledPhonetics: Map<String, String>,
    ): String {
        if (!hasAnyCjk(text)) return text
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
        if (indexedTemplates.isEmpty() && leadingSlotTemplates.isEmpty()) return null
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
        if (projectIndex.isEmpty() && packIndex.isEmpty()) return emptyList()
        if (text.getOrNull(offset) in PUNCTUATION_OR_BREAK_CHARS) return emptyList()
        val terms = matches.termsAt(offset)
        if (terms.isEmpty()) return emptyList()
        val termEnds = ArrayList<Int>(minOf(terms.size, MAX_SLOT_CANDIDATES) + 1)
        for (i in 0 until minOf(terms.size, MAX_SLOT_CANDIDATES)) {
            val end = terms[i].endExclusive
            if (!termEnds.contains(end)) termEnds.add(end)
        }
        val grammarPhraseEnd = grammarPhraseCandidateAt(
            text = text,
            start = offset,
            acceptedPos = emptySet(),
            matches = matches,
        )?.endExclusive
        if (grammarPhraseEnd != null && !termEnds.contains(grammarPhraseEnd)) {
            termEnds.add(grammarPhraseEnd)
        }

        val candidates = ArrayList<SourceTemplate>()
        fun addMatching(index: LeadingSlotTemplateIndex) {
            candidates.addAll(index.unindexed)
            for (end in termEnds) {
                val ch = text.getOrNull(end) ?: continue
                val templates = index.byNextLiteralChar[ch]
                if (templates != null) {
                    for (template in templates) {
                        val literal = template.nextLiteralAfterFirstSlot
                        if (literal == null || text.startsWith(literal, end)) {
                            candidates.add(template)
                        }
                    }
                }
                val lower = ch.lowercaseChar()
                if (lower != ch) {
                    val lowerTemplates = index.byNextLiteralChar[lower]
                    if (lowerTemplates != null) {
                        for (template in lowerTemplates) {
                            val literal = template.nextLiteralAfterFirstSlot
                            if (literal == null || text.startsWith(literal, end)) {
                                candidates.add(template)
                            }
                        }
                    }
                }
            }
        }
        addMatching(projectIndex)
        addMatching(packIndex)
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
            val result = LinkedHashMap<RuntimeMatchIndex.TermSlotDedupKey, TermMatch>()
            fun add(match: TermMatch) {
                if (match.term.target.isBlank() ||
                    match.term.source.any { it in PUNCTUATION_OR_BREAK_CHARS || it.isWhitespace() } ||
                    !part.accepts(posOf(match, matches))
                ) return
                val key = RuntimeMatchIndex.TermSlotDedupKey(normalize(match.term.source), match.endExclusive)
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
        val firstChar = text.getOrNull(offset) ?: return null
        var best: StructuredMatch? = null
        fun consider(candidate: StructuredMatch?) {
            if (candidate == null) return
            val cur = best
            if (cur == null || candidate.endExclusive > cur.endExclusive ||
                (candidate.endExclusive == cur.endExclusive && candidate.priority > cur.priority)
            ) {
                best = candidate
            }
        }
        if (firstChar == '第') {
            consider(matchChapterHeading(text, offset))
            consider(matchOrdinalNoun(text, offset))
            consider(matchOrdinalPlace(text, offset))
        } else if (firstChar in CHAPTER_HEADING_NON_DI_FIRST_CHARS) {
            consider(matchChapterHeading(text, offset))
        }

        if (firstChar in WEEKDAY_FIRST_CHARS) {
            consider(matchWeekdayFullDate(text, offset))
            consider(matchWeekday(text, offset))
        }

        if (firstChar in TIME_PREFIX_FIRST_CHARS) {
            consider(matchChineseTime(text, offset))
        }

        if (firstChar in NUMBER_FIRST_CHARS) {
            consider(matchFullDate(text, offset))
            consider(matchYearMonth(text, offset))
            consider(matchMonthDay(text, offset))
            consider(matchDigitalTime(text, offset))
            consider(matchChineseTime(text, offset))
            consider(matchPercent(text, offset))
            consider(matchNumberUnit(text, offset))
            consider(matchDecimalNumber(text, offset))
        }
        return best
    }

    private fun bestGrammarMatch(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        var best: StructuredMatch? = null
        fun consider(candidate: StructuredMatch?) {
            if (candidate == null) return
            val cur = best
            if (cur == null || candidate.priority > cur.priority ||
                (candidate.priority == cur.priority && candidate.endExclusive > cur.endExclusive)
            ) {
                best = candidate
            }
        }
        val firstChar = text.getOrNull(offset) ?: return null
        if (firstChar in PUNCTUATION_OR_BREAK_CHARS) return null
        if (matches.termsAt(offset).isNotEmpty()) {
            consider(matchPlaceHierarchy(text, offset, matches))
            consider(matchDynamicAttributiveDe(text, offset, matches))
            consider(matchPluralSuffix(text, offset, matches))
            consider(matchModifierHeadPhrase(offset, matches))
            consider(matchPossessiveOrdinalNoun(text, offset, matches))
        }
        if (firstChar == '为') {
            consider(matchLeadingHeadPhrase(text, offset, matches))
        }
        if (firstChar == '将' || firstChar == '將') {
            consider(matchDisposalParticle(text, offset, matches))
        }
        if (firstChar in INTERROGATIVE_FIRST_CHARS) {
            consider(matchInterrogativePattern(text, offset, matches))
        }
        return best
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
            val nextChar = text.getOrNull(name.endExclusive) ?: continue
            val suffixes = PLACE_HIERARCHY_SUFFIXES_BY_FIRST_CHAR[nextChar] ?: continue
            for ((suffix, label) in suffixes) {
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

    private fun matchDisposalParticle(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        val existingTerms = matches.termsAt(offset)
        if (existingTerms.any { it.term.source.length > 1 }) {
            return null
        }
        val nextOffset = offset + 1
        if (nextOffset >= text.length) return null
        val nextChar = text[nextOffset]
        if (nextChar in DISPOSAL_EXCLUDED_NEXT_CHARS) return null
        val nextTerms = matches.termsAt(nextOffset)
        val hasObject = nextTerms.any { termMatch ->
            val pos = posOf(termMatch, matches)
            pos in NOUN_COMPATIBLE_POS ||
                pos == TermPos.PRONOUN ||
                termMatch.term.type == QuickDictionaryType.PRONOUN ||
                termMatch.term.source in DISPOSAL_OBJECT_PRONOUNS ||
                isNounSource(termMatch.term.source)
        } || nextChar in DISPOSAL_OBJECT_FIRST_CHARS
        if (hasObject) {
            return StructuredMatch(
                endExclusive = nextOffset,
                translation = "đem",
                priority = TRUSTED_GRAMMAR_PRIORITY + 5,
            )
        }
        return null
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
        val nextDe = text.indexOf(ATTRIBUTIVE_DE_LITERAL, offset + 1)
        if (nextDe == -1 || nextDe > offset + 16) return null
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
        val nextMen = text.indexOf(PLURAL_SUFFIX, offset + 1)
        if (nextMen == -1 || nextMen > offset + 16) return null
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
        val terms = matches.termsAt(offset)
        if (terms.none { it.term.target.isNotBlank() && (renderPersonModifier(it) != null || posOf(it, matches) == TermPos.ADJECTIVE) }) {
            return null
        }
        var best: StructuredMatch? = null
        terms.asSequence()
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
                        val isPerson = renderPersonModifier(modifier.first) != null || posOf(head, matches) == TermPos.PERSON
                        val candidate = StructuredMatch(
                            endExclusive = head.endExclusive,
                            translation = rendered,
                            priority = 118,
                            isPerson = isPerson,
                        )
                        val current = best
                        if (current == null || candidate.endExclusive > current.endExclusive) {
                            best = candidate
                        }
                    }
            }
        return best
    }

    private fun matchInterrogativePattern(
        text: String,
        offset: Int,
        matches: RuntimeMatchIndex,
    ): StructuredMatch? {
        for ((pattern, length, prefix) in INTERROGATIVE_PATTERNS) {
            if (!text.startsWith(pattern, offset)) continue
            val verbStart = offset + length
            val phrase = grammarPhraseCandidateAt(
                text = text,
                start = verbStart,
                acceptedPos = emptySet(),
                matches = matches,
            )
            val verb = phrase ?: matches.termsAt(verbStart)
                .firstOrNull { it.term.target.isNotBlank() }
                ?: continue
            return StructuredMatch(
                endExclusive = verb.endExclusive,
                translation = "$prefix ${verb.term.target} hay không",
                priority = TRUSTED_GRAMMAR_PRIORITY + 30,
            )
        }
        return null
    }

    private fun renderAttributiveDe(
        left: TermMatch,
        right: TermMatch,
        leftTarget: String,
        rightTarget: String,
        matches: RuntimeMatchIndex? = null,
    ): String? {
        if (isVerbOrAction(right, rightTarget, matches)) {
            if (posOf(left, matches) in setOf(TermPos.ADVERB, TermPos.ADJECTIVE) ||
                isDescriptiveModifier(left) ||
                isActionAttributiveModifier(left, leftTarget, matches)
            ) {
                return listOf(leftTarget, rightTarget).joinToStringByWord { it }
            }
        }
        if (isPersonHead(right) && isActionAttributiveModifier(left, leftTarget, matches)) {
            return listOf(rightTarget, leftTarget).joinToStringByWord { it }
        }
        if (isPossessiveOwner(left)) {
            if (posOf(right, matches) in NOUN_COMPATIBLE_POS || isNounSource(right.term.source)) {
                return listOf(rightTarget, "của", leftTarget).joinToStringByWord { it }
            }
            return null
        }
        if (posOf(right, matches) in ATTRIBUTIVE_ACTION_HEAD_POS &&
            isActionAttributiveModifier(left, leftTarget, matches)
        ) {
            return listOf(rightTarget, leftTarget).joinToStringByWord { it }
        }
        val lowerRight = rightTarget.lowercase()
        if (ABSTRACT_CONCEPT_TARGETS.any { lowerRight.contains(it) } &&
            (posOf(left, matches) == TermPos.ADJECTIVE ||
                isDescriptiveModifier(left) ||
                isActionAttributiveModifier(left, leftTarget, matches))
        ) {
            return listOf(rightTarget, leftTarget).joinToStringByWord { it }
        }
        if (posOf(right, matches) in NOUN_COMPATIBLE_POS || isNounSource(right.term.source)) {
            if (posOf(left, matches) in setOf(TermPos.VERB, TermPos.ADVERB, TermPos.ADJECTIVE) ||
                isDescriptiveModifier(left) ||
                isActionAttributiveModifier(left, leftTarget, matches)
            ) {
                return listOf(rightTarget, leftTarget).joinToStringByWord { it }
            }
        }
        return when (posOf(left, matches)) {
            TermPos.LOCATION -> listOf(rightTarget, "ở", leftTarget).joinToStringByWord { it }
            TermPos.ADJECTIVE -> listOf(rightTarget, leftTarget).joinToStringByWord { it }
            TermPos.PERSON, TermPos.NAME -> if (posOf(right, matches) in NOUN_COMPATIBLE_POS || isNounSource(right.term.source)) {
                listOf(rightTarget, "của", leftTarget).joinToStringByWord { it }
            } else null
            else -> when {
                isAgeModifier(left) ->
                    listOf(rightTarget, "lúc", leftTarget).joinToStringByWord { it }
                isLocationModifier(left) ->
                    listOf(rightTarget, "ở", leftTarget).joinToStringByWord { it }
                isDescriptiveModifier(left) ->
                    listOf(rightTarget, leftTarget).joinToStringByWord { it }
                else -> null
            }
        }
    }

    private fun isVerbOrAction(
        match: TermMatch,
        target: String,
        matches: RuntimeMatchIndex?,
    ): Boolean {
        val pos = posOf(match, matches)
        if (pos == TermPos.VERB) return true
        val source = match.term.source
        if (VERB_SOURCE_SUFFIXES.any(source::endsWith)) return true
        val lowerTarget = target.lowercase()
        return VERB_TARGET_PREFIXES.any(lowerTarget::startsWith)
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
        if (match.term.type == QuickDictionaryType.NAME || isPersonHead(match)) return true
        val target = match.term.target
        if (target.any(Char::isUpperCase) && isLikelyPersonName(target, source)) return true
        val normalized = target.lowercase()
        if (POSSESSIVE_OWNER_TARGETS.any { normalized == it || normalized.contains(it) }) return true
        if (isDescriptiveModifier(match) || isLocationModifier(match)) return false
        val pos = match.term.runtimePos
        if (pos == TermPos.ADJECTIVE || pos == TermPos.VERB || pos == TermPos.LOCATION) return false
        if (source.length >= 2 && NON_PERSON_NAME_SUFFIXES.any(source::endsWith)) return false
        return false
    }

    private fun isLikelyPersonName(target: String, source: String): Boolean {
        if (source.any { it in NON_PERSON_SOURCE_CHARS }) return false
        if (NON_PERSON_NAME_SUFFIXES.any(source::endsWith)) return false
        val words = target.trim().split(Regex("\\s+"))
        if (words.size !in 2..4) return false
        if (!words.all { it.isNotEmpty() && it.first().isUpperCase() }) return false
        val lower = target.lowercase()
        if (NON_PERSON_TARGET_WORDS.any { lower.contains(it) }) return false
        return true
    }

    private fun isAgeModifier(match: TermMatch): Boolean {
        val source = match.term.source
        if (source.length >= 2 && source.endsWith("岁")) {
            val numPart = source.dropLast(1)
            if (numPart.all { it.isDigit() || it in '０'..'９' || it in CHINESE_DIGITS || it in CHINESE_MULTIPLIERS || it == '十' || it == '百' }) {
                return true
            }
        }
        val target = match.term.target.trim().lowercase()
        return target.endsWith("tuổi") && target.dropLast(4).trim().all { it.isDigit() || it.isWhitespace() }
    }

    private fun isLocationModifier(match: TermMatch): Boolean {
        val source = match.term.source
        return LOCATION_MODIFIER_SUFFIXES.any(source::endsWith)
    }

    private fun isDescriptiveModifier(match: TermMatch): Boolean {
        if (match.term.type == QuickDictionaryType.NAME) return false
        val target = match.term.target
        val source = match.term.source
        if (target.any(Char::isUpperCase) && isLikelyPersonName(target, source)) return false
        if (isPersonHead(match)) return false
        if (isDescriptiveSource(source)) return true
        val lowerTarget = target.lowercase()
        return DESCRIPTIVE_MODIFIER_TARGET_PREFIXES.any(lowerTarget::startsWith)
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
        val nextDe = text.indexOf(ATTRIBUTIVE_DE_LITERAL, offset + 1)
        if (nextDe == -1 || nextDe > offset + 16) return null
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
            val periodRaw = match.groupValues[1]
            val period = TIME_PREFIX_TRANSLATIONS[periodRaw].orEmpty()
            val unit = if (period.isNotBlank() || match.value.endsWith("时") || match.value.endsWith("時") || match.value.contains("整") || match.value.contains("左右")) {
                "giờ"
            } else {
                "điểm"
            }
            return StructuredMatch(
                match.range.last + 1,
                listOf("$hour $unit", period).filter(String::isNotBlank).joinToString(" "),
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
        val trimmed = pattern.trim()
        for (c in CHINESE_PUNCTUATION_CHARS) {
            if (trimmed.contains(c)) return c.toString()
        }
        val duplicateMatch = DUPLICATE_SPACE_PATTERN.matchEntire(trimmed)
        if (duplicateMatch != null) {
            return duplicateMatch.groupValues[1].removePrefix("\\b").removeSuffix("\\b").trim()
        }
        val candidate = trimmed.removePrefix("\\b").removeSuffix("\\b").trim()
        if (candidate.length >= 3 && !candidate.any { it in POST_RULE_META_CHARS } && '\\' !in candidate) {
            return candidate
        }
        if (trimmed.contains("thế giới", ignoreCase = true)) {
            return "thế giới"
        }
        if (trimmed.contains("của", ignoreCase = true) && !trimmed.contains("|của", ignoreCase = true) && !trimmed.contains("của|", ignoreCase = true)) {
            return "của"
        }
        for (kw in KNOWN_TRIGGER_WORDS) {
            if (trimmed.contains(kw, ignoreCase = true)) {
                return kw
            }
        }
        if (trimmed.contains("?")) return "?"
        if (trimmed.contains("!")) return "!"
        if (trimmed.startsWith(",(?=")) return ","
        return null
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
        val currentToken = externalPackStore?.versionToken() ?: "none"
        cachedPack.get()?.let { if (it.versionToken == currentToken) return it }
        return synchronized(packLoadLock) {
            val cached = cachedPack.get()
            if (cached != null && cached.versionToken == currentToken) {
                cached
            } else {
                loadPack(currentToken).also(cachedPack::set)
            }
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
                .filter { pair ->
                    val orig = pair.original.trim()
                    if (orig in PROTECTED_CLEAN_TERMS) {
                        pair.type == QuickDictionaryType.NAME && orig.length >= 2
                    } else if (orig.length == 1 && pair.type == QuickDictionaryType.VIETPHRASE) {
                        false
                    } else {
                        true
                    }
                }
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
                trie = FastTermMapLookup(terms),
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

    private fun loadCatalogPack(): CatalogPack = CatalogPack(emptyMap())

    private fun loadPack(currentToken: String = ""): QuickPack {
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
        val templates = mutableListOf<SourceTemplate>()
        val postRules = emptyList<PostRule>()
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
        val qt2025Lookup = MappedTermLookup.openOrNull(QT2025_TERM_INDEX_ASSET)
        val compatibilityLookup = qt2025Lookup
            ?: MappedTermLookup.openOrNull(QT2020_TERM_INDEX_ASSET)
            ?: FastTermMapLookup(emptyList())
        val limitedTemplates = templates.take(MAX_TEMPLATE_RULES)
        val templatesByFirstChar = limitedTemplates.indexByFirstLiteralChar()
        val leadingSlotTemplateIndex = limitedTemplates.filter {
            (it.parts.firstOrNull() as? TemplatePart.Literal)?.value.isNullOrEmpty()
        }.indexLeadingSlotTemplates()
        return QuickPack(
            version = if (qt2025Lookup != null) QT2025_PACK_VERSION else QT2020_PACK_VERSION,
            baseTrie = compatibilityLookup,
            phonetics = phonetics,
            templates = limitedTemplates,
            templatesByFirstChar = templatesByFirstChar,
            leadingSlotTemplateIndex = leadingSlotTemplateIndex,
            postRules = postRules,
            qt2025Runtime = qt2025Runtime,
            versionToken = currentToken,
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
        val versionToken: String = "",
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
        val trie: TermLookup,
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
    ) {
        fun isEmpty(): Boolean = byNextLiteralChar.isEmpty() && unindexed.isEmpty()
        fun isNotEmpty(): Boolean = !isEmpty()
    }
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
        val isPerson: Boolean = false,
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
        val nextLiteralAfterFirstSlot: String? = run {
            val firstSlot = parts.indexOfFirst { it is TemplatePart.Slot }
            if (firstSlot < 0) null
            else {
                for (i in (firstSlot + 1) until parts.size) {
                    val p = parts[i]
                    if (p is TemplatePart.Slot) break
                    if (p is TemplatePart.Literal && p.value.isNotEmpty()) {
                        return@run p.value
                    }
                }
                null
            }
        },
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

        fun hasQt2025Name(start: Int): Boolean =
            termsAt(start).any { match ->
                match.term.target.isNotBlank() &&
                    match.term.type in QT2025_NAME_SLOT_TYPES
            }

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

        internal data class TermSlotDedupKey(
            val source: String,
            val endExclusive: Int,
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

    /**
     * Fast map-based term lookup implementing the original Quick Translator algorithm:
     * O(N) hash table build without Trie or Aho-Corasick graph construction.
     * Greedy Longest Prefix Match from maxSourceChars down to 1.
     */
    private class FastTermMapLookup(terms: List<Term>) : TermLookup {
        private val termsBySource: HashMap<String, Term> = HashMap(terms.size)
        private val maxSourceChars: Int
        private val lengthsMaskByFirstChar = LongArray(65536)
        private val fallbackMasks = HashMap<Int, Long>()

        init {
            var maxLen = 0
            terms.forEach { term ->
                val key = normalize(term.source)
                if (key.isEmpty()) return@forEach
                val existing = termsBySource[key]
                if (existing == null || isBetterTerm(term, existing)) {
                    termsBySource[key] = term
                }
                val len = term.source.length
                if (len > maxLen) {
                    maxLen = len
                }
                val firstCode = key[0].code
                val shift = len.coerceAtMost(62)
                if (firstCode < 65536) {
                    lengthsMaskByFirstChar[firstCode] = lengthsMaskByFirstChar[firstCode] or (1L shl shift)
                } else {
                    fallbackMasks[firstCode] = (fallbackMasks[firstCode] ?: 0L) or (1L shl shift)
                }
            }
            maxSourceChars = maxLen.coerceAtMost(32)
        }

        private fun getMaskFor(firstCode: Int): Long {
            return if (firstCode < 65536) lengthsMaskByFirstChar[firstCode] else (fallbackMasks[firstCode] ?: 0L)
        }

        override fun longestAt(text: String, offset: Int): TermMatch? {
            val available = (text.length - offset).coerceAtMost(maxSourceChars)
            if (available <= 0) return null
            val firstCode = text[offset].code
            val mask = getMaskFor(firstCode)
            if (mask == 0L) return null
            var currentMask = mask and if (available >= 62) -1L else ((1L shl (available + 1)) - 1L)
            while (currentMask > 1L) {
                val len = 63 - java.lang.Long.numberOfLeadingZeros(currentMask)
                if (len <= 0) break
                currentMask = currentMask and (1L shl len).inv()
                val key = normalize(text.substring(offset, offset + len))
                val term = termsBySource[key]
                if (term != null) {
                    return TermMatch(term, offset, offset + len)
                }
            }
            return null
        }

        override fun allAt(text: String, offset: Int): List<TermMatch> {
            val available = (text.length - offset).coerceAtMost(maxSourceChars)
            if (available <= 0) return emptyList()
            val firstCode = text[offset].code
            val mask = getMaskFor(firstCode)
            if (mask == 0L) return emptyList()
            var matches: MutableList<TermMatch>? = null
            var currentMask = mask and if (available >= 62) -1L else ((1L shl (available + 1)) - 1L)
            while (currentMask > 1L) {
                val len = 63 - java.lang.Long.numberOfLeadingZeros(currentMask)
                if (len <= 0) break
                currentMask = currentMask and (1L shl len).inv()
                val key = normalize(text.substring(offset, offset + len))
                val term = termsBySource[key]
                if (term != null) {
                    if (matches == null) matches = ArrayList(4)
                    matches.add(TermMatch(term, offset, offset + len))
                }
            }
            return matches ?: emptyList()
        }

        override fun allMatchesByStart(text: String): Array<MutableList<TermMatch>?> {
            val textLen = text.length
            val matches = arrayOfNulls<MutableList<TermMatch>>(textLen)
            for (offset in 0 until textLen) {
                val available = (textLen - offset).coerceAtMost(maxSourceChars)
                if (available <= 0) continue
                val firstCode = text[offset].code
                val mask = getMaskFor(firstCode)
                if (mask == 0L) continue
                var list: MutableList<TermMatch>? = null
                var currentMask = mask and if (available >= 62) -1L else ((1L shl (available + 1)) - 1L)
                while (currentMask > 1L) {
                    val len = 63 - java.lang.Long.numberOfLeadingZeros(currentMask)
                    if (len <= 0) break
                    currentMask = currentMask and (1L shl len).inv()
                    val key = normalize(text.substring(offset, offset + len))
                    val term = termsBySource[key]
                    if (term != null) {
                        if (list == null) list = ArrayList(4)
                        list.add(TermMatch(term, offset, offset + len))
                    }
                }
                if (list != null) {
                    matches[offset] = list
                }
            }
            return matches
        }

        override fun containsExact(text: String): Boolean {
            return termsBySource.containsKey(normalize(text))
        }

        private fun isBetterTerm(candidate: Term, current: Term): Boolean {
            return when {
                candidate.target.isEmpty() != current.target.isEmpty() -> candidate.target.isEmpty()
                candidate.projectOwned != current.projectOwned -> candidate.projectOwned
                candidate.sourceQuality != current.sourceQuality ->
                    termSourceQualityRank(candidate.sourceQuality) > termSourceQualityRank(current.sourceQuality)
                candidate.type != current.type -> termTypeRank(candidate.type) > termTypeRank(current.type)
                quickDictionaryTargetScore(candidate.target) != quickDictionaryTargetScore(current.target) ->
                    quickDictionaryTargetScore(candidate.target) > quickDictionaryTargetScore(current.target)
                else -> candidate.sourcePriority < current.sourcePriority
            }
        }
    }

    private class MappedTermLookup private constructor(
        private val buffer: ByteBuffer,
        private val bucketCount: Int,
        private val maxSourceChars: Int,
        private val firstSourceChars: BooleanArray,
        private val lengthsMaskByFirstChar: LongArray,
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
            val firstCode = text[offset].lowercaseChar().code
            if (!firstSourceChars[firstCode]) return emptyList()
            val lengthMask = lengthsMaskByFirstChar[firstCode]
            if (lengthMask == 0L) return emptyList()
            val hashes = checkNotNull(hashScratch.get())
            var hash = FNV_OFFSET_BASIS
            for (length in 1..available) {
                hash = (hash xor text[offset + length - 1].lowercaseChar().code) * FNV_PRIME
                hashes[length - 1] = hash
            }
            var matches: MutableList<TermMatch>? = null
            for (length in available downTo 1) {
                if (length < 63 && (lengthMask and (1L shl length)) == 0L) continue
                val entryOffset = findEntry(text, offset, length, hashes[length - 1])
                if (entryOffset >= 0) {
                    if (matches == null) matches = mutableListOf()
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
            return matches ?: emptyList()
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
            (buffer.duplicate().position(targetOffset) as ByteBuffer).get(bytes, 0, targetLength)
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
            private const val TARGET_CACHE_SIZE = 16_384
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
                    val charIndex = scanSourceCharIndex(mapped)
                    MappedTermLookup(
                        buffer = mapped,
                        bucketCount = mapped.getInt(BUCKET_COUNT_OFFSET),
                        maxSourceChars = mapped.getInt(MAX_SOURCE_CHARS_OFFSET),
                        firstSourceChars = charIndex.firstSourceChars,
                        lengthsMaskByFirstChar = charIndex.lengthsMask,
                    )
                }.getOrNull()
            }

            fun openFileOrNull(file: java.io.File): MappedTermLookup? {
                if (!file.isFile || file.length() == 0L) return null
                return runCatching {
                    val mapped = FileInputStream(file).channel.use { channel ->
                        channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            0L,
                            file.length(),
                        )
                    }.order(ByteOrder.LITTLE_ENDIAN)
                    validate(mapped)
                    val charIndex = scanSourceCharIndex(mapped)
                    MappedTermLookup(
                        buffer = mapped,
                        bucketCount = mapped.getInt(BUCKET_COUNT_OFFSET),
                        maxSourceChars = mapped.getInt(MAX_SOURCE_CHARS_OFFSET),
                        firstSourceChars = charIndex.firstSourceChars,
                        lengthsMaskByFirstChar = charIndex.lengthsMask,
                    )
                }.getOrNull()
            }

            private class SourceCharIndex(
                val firstSourceChars: BooleanArray,
                val lengthsMask: LongArray,
            )

            private fun scanSourceCharIndex(buffer: ByteBuffer): SourceCharIndex {
                val chars = BooleanArray(Char.MAX_VALUE.code + 1)
                val lengthsMask = LongArray(Char.MAX_VALUE.code + 1)
                val bucketCount = buffer.getInt(BUCKET_COUNT_OFFSET)
                repeat(bucketCount) { bucket ->
                    val entryOffset = buffer.getInt(HEADER_SIZE + bucket * Int.SIZE_BYTES)
                    if (entryOffset != 0) {
                        val sourceLength = buffer.getShort(entryOffset + Int.SIZE_BYTES).toInt() and 0xffff
                        if (sourceLength > 0) {
                            val sourceOffset = entryOffset + ENTRY_HEADER_SIZE
                            val charCode = buffer.getChar(sourceOffset).lowercaseChar().code
                            chars[charCode] = true
                            if (sourceLength < 63) {
                                lengthsMask[charCode] = lengthsMask[charCode] or (1L shl sourceLength)
                            } else {
                                lengthsMask[charCode] = lengthsMask[charCode] or (1L shl 62)
                            }
                        }
                    }
                }
                return SourceCharIndex(chars, lengthsMask)
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
        private const val PACK_VERSION = "qt-clean-4.2.27-v11"
        private const val QT2025_PACK_VERSION = "qt2025-fmm-classic-v1"
        private const val QT2020_PACK_VERSION = "qt2020-2025.09.01+qt-clean-4.2.27-v11"
        private val PROTECTED_CLEAN_TERMS = setOf(
            "第", "章", "节", "回", "卷", "篇", "零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "的", "了", "着", "在", "被", "把", "将", "和", "或", "个", "几", "从", "到",
            "间", "边", "旁", "前", "后", "上", "下", "里", "外", "中", "内",
            "这", "那", "我", "你", "他", "她", "它", "们", "谁", "甚", "么",
            "是", "不", "有", "无", "没", "非", "微", "很", "太", "极",
            "说", "道", "看", "见", "听", "闻", "走", "去", "来", "出", "入",
            "床", "门", "窗"
        )
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
        private const val PROJECT_TEMPLATE_PRIORITY = 900
        private const val MAX_TEMPLATE_RULES = 4_000
        private const val MAX_PROJECT_TEMPLATE_RULES = 512
        private const val MAX_POST_RULES = 1_000
        private const val MAX_SLOT_CANDIDATES = 8
        private const val PROJECT_TRIE_CACHE_SIZE = 8
        private const val POST_RULE_META_CHARS = "[]()|+*?{}^$."
        private val CHINESE_PUNCTUATION_CHARS = charArrayOf('。', '，', '、', '：', '；', '！', '？')
        private val PUNCTUATION_OR_BREAK_CHARS = setOf(
            '。', '，', '、', '：', '；', '！', '？', '\n', '\r',
            '.', ',', '!', '?', ';', ':', '“', '”', '‘', '’', '"', '\'', '(', ')', '（', '）',
            '《', '》', '〈', '〉', '【', '】', '—', '…',
        )
        private val DUPLICATE_SPACE_PATTERN = Regex("""^\\b([^\\]+?)\\s\+\1\\b$""")
        private val KNOWN_TRIGGER_WORDS = listOf(
            "thụ mệt mỏi", "không lớn", "Bạn sống", "Có chuyện", "bạn đang", "Phật Đà",
            "giờ trái phải", "tương lai", "Viễn Tử", "Hổ Tử", "Thạch Đầu", "năm",
            "anh ấy", "cô ấy", "chúng tôi", "có thể", "dần dần", "không ngừng",
            "Đại Thiền", "Thiếu Lâm", "Bạch Mã", "Lan Nhược", "Huyền Thiên", "Kim Các", "Bảo Tích", "Trấn Hải",
            "trong tự", "tự trong", "trong", "các", "những", "được", "Chương", "Quyển", "Tiết", "tại", "ở"
        )
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
        private val ATTRIBUTIVE_ACTION_HEAD_POS = setOf(TermPos.PERSON, TermPos.NAME, TermPos.NOUN)
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
            "过", "了", "曾", "曾经", "被", "把",
        )
        private val INTERROGATIVE_PATTERNS = listOf(
            Triple("有没有", 3, "có"),
            Triple("会不会", 3, "có"),
            Triple("是不是", 3, "có phải là"),
            Triple("能不能", 3, "có thể"),
            Triple("可不可以", 4, "có thể"),
        )
        private val INTERROGATIVE_FIRST_CHARS = setOf('有', '会', '是', '能', '可')
        private val DISPOSAL_EXCLUDED_NEXT_CHARS = setOf(
            '军', '領', '领', '官', '士', '帥', '帅', '相', '星', '才', '門', '门',
            '要', '來', '来', '近', '至', '必', '后', '後'
        )
        private val DISPOSAL_OBJECT_FIRST_CHARS = setOf(
            '自', '他', '她', '你', '我', '它', '其', '这', '這', '那', '此',
            '一', '两', '兩', '三', '四', '五', '六', '七', '八', '九', '十',
            '全', '整', '所', '诸', '諸', '众', '眾', '各', '本'
        )
        private val DISPOSAL_OBJECT_PRONOUNS = setOf(
            "自身", "自己", "我", "我们", "你", "你们", "他", "他们", "她", "她们",
            "它", "它们", "其", "这", "这个", "这些", "那", "那个", "那些", "此"
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
        private val PLACE_HIERARCHY_SUFFIXES_BY_FIRST_CHAR: Map<Char, List<Pair<String, String>>> =
            PLACE_HIERARCHY_SUFFIX_LABELS.groupBy { it.first[0] }
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
            "大家", "众人", "人们", "对方", "别人", "旁人",
            "弟子", "门徒", "徒弟", "师傅", "师父", "长老", "宗主", "门人",
            "修士", "道友", "兄台", "阁下", "前辈", "后辈", "晚辈",
        )
        private val POSSESSIVE_OWNER_TARGETS = listOf(
            "tôi", "ta", "mình", "ngươi", "anh", "cô", "hắn", "nàng",
            "ông", "bà", "người", "điều tra viên", "công tử", "tiểu thư",
            "đệ tử", "sư phụ", "trưởng lão", "môn nhân",
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
            "更多", "更少", "很大", "微小", "极多", "极少",
            "一模一样", "唯我独尊", "不可思议", "肉身成圣", "生不如死", "生生", "生生的",
            "庞大庄严", "庄严", "未知", "神秘", "具体", "无力", "单纯", "完整", "基本",
            "实打实", "珍稀", "稀有", "珍贵", "纯净", "雄厚", "狂暴", "浩瀚", "宏大",
            "相同", "不同", "奇特", "微弱", "强烈", "绝对", "相对", "无穷", "无限",
            "对面", "旁边", "附近", "周围", "上方", "下方", "前边", "后边", "左边", "右边", "外边", "里边",
        )
        private val DESCRIPTIVE_MODIFIER_CHARS = setOf(
            '大', '小', '高', '低', '长', '短', '黑', '白', '红', '青',
            '蓝', '绿', '紫', '黄', '灰', '瘦', '胖', '细', '粗', '新',
            '旧', '冷', '热', '美', '丑', '浓', '淡', '深', '浅', '柔',
            '硬', '精', '灵', '多', '少', '窄', '宽', '慢', '快',
            '样', '同', '异', '殊', '常', '特', '奇', '妙', '绝', '纯',
            '杂', '真', '假', '虚', '实', '强', '弱', '烈', '暴', '狂',
            '静', '平', '稳', '乱', '危', '险', '安', '全', '整', '完',
            '缺', '准', '确', '明', '暗', '清', '浊', '显', '隐', '微',
            '圣', '神', '秘', '凡', '尊', '极', '严', '庞', '重', '轻',
            '急', '缓', '软', '空', '无', '未', '知', '基', '本', '单',
            '巨', '广', '宏',
        )
        private val DESCRIPTIVE_MODIFIER_TARGET_PREFIXES = listOf(
            "m\u1EC7t", "nhanh", "r\u1EA5t", "cao",
            "trẻ", "già", "đậm", "nồng", "đẹp", "tinh", "gầy", "thon",
            "mảnh", "cao", "nhỏ", "dài", "đen", "trắng", "đỏ", "xanh",
            "tím", "vàng", "xám", "lớn", "kỳ", "đặc", "bình", "lạnh",
            "ấm", "dịu", "sáng", "trong", "sâu", "linh",
            "hẹp", "rộng", "chậm", "nhiều", "ít", "tối thiểu", "tối đa", "đầy", "vô số",
            "đối diện", "bên cạnh", "gần đây", "xung quanh", "phía trên", "phía dưới", "phía trước", "phía sau", "bên trái", "bên phải", "bên ngoài", "bên trong",
            "giống", "như", "khác", "thần bí", "huyền bí", "bất lực", "đơn thuần", "đơn giản",
            "quý hiếm", "hiếm", "to lớn", "uy nghiêm", "thánh", "chưa biết", "cụ thể", "hoàn chỉnh",
            "toàn bộ", "mạnh", "yếu", "sạch", "thuần khiết", "hùng hậu", "tự do", "vô thượng",
            "tuyệt đối", "tương tự", "thực sự", "đặc biệt", "nguy hiểm", "chân chính", "thực thụ",
            "không thể tưởng tượng", "duy ngã độc tôn", "nhục thân thành thánh", "sống sờ sờ",
            "hoàn chỉnh", "tinh hoa", "cần thiết", "dự trữ", "đo lường", "khai mở",
        )
        private val ABSTRACT_CONCEPT_TARGETS = listOf(
            "cảnh giới", "ý cảnh", "thần lực", "khí thế", "bối cảnh", "pháp bào",
            "quy luật", "tham số", "nội dung", "sự tình", "biến cố", "thuộc tính",
            "cơ thể", "bản chất", "thể hiện", "võ học", "công pháp", "pháp môn",
            "thức mở đầu", "tướng", "ấn", "chi bảo", "lực lượng", "sức mạnh",
            "âm thanh", "tiếng", "ký ức", "linh dược", "nghề nghiệp", "chức nghiệp",
            "huyệt khiếu", "phương pháp", "tinh hoa", "dự trữ", "biểu hiện", "bảo vật",
        )
        private val NON_PERSON_SOURCE_CHARS = setOf(
            '经', '典', '法', '印', '拳', '寺', '界', '域', '门', '派', '宗', '神', '仙',
            '圣', '道', '体', '术', '功', '境', '式', '相', '国', '城', '阵', '殿', '阁', '峰', '谷'
        )
        private val NON_PERSON_NAME_SUFFIXES = listOf(
            "经", "典", "法", "印", "拳", "寺", "界", "域", "门", "派", "宗", "神", "仙",
            "圣", "道", "体", "术", "功", "境", "式", "相", "国", "城", "阵", "殿", "阁", "峰", "谷"
        )
        private val NON_PERSON_TARGET_WORDS = listOf(
            "kinh", "điển", "pháp", "ấn", "quyền", "tự", "giới", "vực", "thần", "tiên",
            "thánh", "đạo", "thể", "thuật", "công", "cảnh", "tướng", "chức nghiệp",
            "nghề nghiệp", "quốc", "thành trì", "thành thánh", "trận", "điện", "các", "phong", "cốc",
            "bảo", "bí bản", "quy luật", "tham số", "bối cảnh"
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
            "懂得", "明白", "理解", "解释",
        )
        private val VERB_TARGET_PREFIXES = listOf(
            "\u0111eo", "b\u01B0ng", "c\u1EA7m", "g\u00F5", "g\u1ECDi", "nh\u1ED3i",
            "m\u1EAFng", "ng\u1ED3i", "s\u00FAt", "\u0111\u00E1", "m\u1EDF",
            "ch\u1EA1y", "v\u1EC1", "ngh\u1EC9", "ti\u1EBFp t\u1EE5c",
            "nói", "hỏi", "đáp", "nhìn", "nghĩ", "cảm thấy", "cho rằng",
            "phát hiện", "nghe", "đi", "đến", "vào", "rời", "lấy", "đặt",
            "đánh", "giết", "tấn công", "bảo vệ", "cười", "khóc", "ngồi",
            "đứng", "nằm", "hiểu", "giải thích", "mang", "đem",
        )
        private val NOUN_SOURCE_SUFFIXES = listOf(
            "刀", "剑", "枪", "弓", "箭", "书", "信", "纸", "笔", "灯", "门",
            "窗", "桌", "椅", "床", "车", "船", "药", "丹", "符", "阵", "法",
            "术", "功", "拳", "掌", "指", "腿", "脚", "手", "眼", "脸", "头",
            "心", "魂", "力", "气", "血", "骨", "肉", "房间", "笑容", "声音",
            "气息", "目光", "身体", "衣服", "长刀",
        )
        private val CATALOG_ORDER = listOf(
            QuickDictionaryType.VIETPHRASE,
            QuickDictionaryType.NAME,
            QuickDictionaryType.PHONETIC,
            QuickDictionaryType.PRONOUN,
            QuickDictionaryType.LUAT_NHAN,
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
        private val NUMBER_FIRST_CHARS: Set<Char> = setOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '０', '１', '２', '３', '４', '５', '６', '７', '８', '９',
            '零', '〇', '一', '壹', '二', '贰', '貳', '两', '兩', '三', '叁', '參',
            '四', '肆', '五', '伍', '六', '陆', '陸', '七', '柒', '八', '捌',
            '九', '玖', '十', '拾', '百', '佰', '千', '仟', '万', '萬', '亿', '億', '兆',
            '点', '點', '.', '．',
        )
        private val TIME_PREFIX_FIRST_CHARS: Set<Char> = setOf(
            '早', '上', '中', '下', '傍', '晚', '深', '凌', '半',
        )
        private val WEEKDAY_FIRST_CHARS: Set<Char> = setOf('星', '周', '週', '礼', '禮')
        private val CHAPTER_HEADING_NON_DI_FIRST_CHARS: Set<Char> = buildSet {
            CHAPTER_LABELS.keys.forEach { if (it.isNotEmpty()) add(it[0]) }
            SPECIAL_CHAPTER_HEADINGS.keys.forEach { if (it.isNotEmpty()) add(it[0]) }
        }
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
    val trimmed = value.substringBefore('\t').substringBefore("//").trim()
    if (trimmed.isEmpty() || "://" in trimmed) return trimmed
    val candidates = quickDictionaryTargetCandidates(trimmed)
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (candidates.isEmpty()) return ""
    return candidates.firstOrNull { target ->
        !isDefinitionLikeQuickDictionaryTarget(target) &&
            !target.codePoints().anyMatch(::isQuickDictionaryCjk)
    } ?: candidates.firstOrNull {
        !isDefinitionLikeQuickDictionaryTarget(it)
    } ?: candidates.first()
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

internal object QuickTranslationGrammarPolish {
    private val CUA_EXCESSIVE_REGEX = Regex(
        """(?iu)(?<![\p{L}\p{N}])của\s+(muốn|say rượu|phát hành|sống sót|bất hạnh|bất công|ngột ngạt|đáng ngờ|sụp đổ|phí sức|nổi bật|táo bạo|yêu thích|hi vọng|kỳ vọng)(?![\p{L}\p{N}])"""
    )
    private val INVERTED_CONVERT_PATTERNS = listOf(
        Regex("""(?iu)(?<![\p{L}\p{N}])ngồi dậy của phí sức(?![\p{L}\p{N}])""") to "phí sức ngồi dậy",
        Regex("""(?iu)(?<![\p{L}\p{N}])đã cừu nhân của say rượu(?![\p{L}\p{N}])""") to "cừu nhân đã say rượu",
        Regex("""(?iu)(?<![\p{L}\p{N}])vì ngươi mở(?![\p{L}\p{N}])""") to "mở ra cho ngươi",
        Regex("""(?iu)(?<![\p{L}\p{N}])vì bạn mở(?![\p{L}\p{N}])""") to "mở ra cho ngươi",
        Regex("""(?iu)(?<![\p{L}\p{N}])vận chuyển ống giảm thanh giới(?![\p{L}\p{N}])""") to "gắn ống giảm thanh",
        Regex("""(?iu)(?<![\p{L}\p{N}])vận chuyển cách âm dụng cụ(?![\p{L}\p{N}])""") to "gắn thiết bị giảm thanh",
        Regex("""(?iu)(?<![\p{L}\p{N}])thương cầm bảo vệ\s*ngực\s*an(?![\p{L}\p{N}])""") to "ngực bảo an cầm súng",
        Regex("""(?iu)(?<![\p{L}\p{N}])thương cầm bảo vệ an(?![\p{L}\p{N}])""") to "bảo an cầm súng",
        Regex("""(?iu)(?<![\p{L}\p{N}])bị thương đánh(?![\p{L}\p{N}])""") to "bị trúng đạn",
        Regex("""(?iu)(?<![\p{L}\p{N}])chiếc thương(?![\p{L}\p{N}])""") to "khẩu súng",
        Regex("""(?iu)(?<![\p{L}\p{N}])đó kinh nghiệm của bất hạnh(?![\p{L}\p{N}])""") to "kinh nghiệm bất hạnh đó",
        Regex("""(?iu)(?<![\p{L}\p{N}])tuổi của bây giờ(?![\p{L}\p{N}])""") to "tuổi bây giờ",
        Regex("""(?iu)(?<![\p{L}\p{N}])bắt đầu của sống về đêm(?![\p{L}\p{N}])""") to "bắt đầu cuộc sống về đêm",
        Regex("""(?iu)(?<![\p{L}\p{N}])người trung niên của men say(?![\p{L}\p{N}])""") to "người trung niên say rượu",
        Regex("""(?iu)(?<![\p{L}\p{N}])bảo đảm an ninh công cộng(?![\p{L}\p{N}])""") to "nhân viên bảo an",
        Regex("""(?iu)(?<![\p{L}\p{N}])thân phận của (?:ngươi|bạn) (liệp sát giả|kẻ săn đuổi)(?![\p{L}\p{N}])""") to "thân phận $1 của ngươi",
        Regex("""(?iu)(?<![\p{L}\p{N}])thân phận (?:ngươi|bạn) (liệp sát giả|kẻ săn đuổi)(?![\p{L}\p{N}])""") to "thân phận $1 của ngươi",
        Regex("""(?iu)(?<![\p{L}\p{N}])(đao|trường đao|thương|súng|đoản đao|vũ khí|vật|đồ vật) của trong tay(?![\p{L}\p{N}])""") to "$1 trong tay",
        Regex("""(?iu)(?<![\p{L}\p{N}])của trong tay(?![\p{L}\p{N}])""") to "trong tay",
        Regex("""(?iu)(?<![\p{L}\p{N}])trong mắt của(?![\p{L}\p{N}])""") to "trong mắt",
        Regex("""(?iu)(?<![\p{L}\p{N}])(cấp bậc|thời gian|Ouka|Hank|Mia|thủ đô|tình huống|trạng thái) của bây giờ(?![\p{L}\p{N}])""") to "$1 hiện tại",
        Regex("""(?iu)(?<![\p{L}\p{N}])của bây giờ(?![\p{L}\p{N}])""") to "hiện tại",
        Regex("""(?iu)(?<![\p{L}\p{N}])niệm của chỉ(?![\p{L}\p{N}])""") to "kỷ vật duy nhất",
        Regex("""(?iu)(?<![\p{L}\p{N}])dũng tâm của dám(?![\p{L}\p{N}])""") to "trái tim dũng cảm",
        Regex("""(?iu)(?<![\p{L}\p{N}])đã đồ đạc của tới tay(?![\p{L}\p{N}])""") to "đồ vật đã tới tay",
        Regex("""(?iu)(?<![\p{L}\p{N}])thứ (?:1|nhất) (?:giờ giữa|thì gian)(?![\p{L}\p{N}])""") to "ngay lập tức",
        Regex("""(?iu)(?<![\p{L}\p{N}])này 1 giờ(?![\p{L}\p{N}])""") to "điểm này",
        Regex("""(?iu)(?<![\p{L}\p{N}])Rót:(?![\p{L}\p{N}])""") to "Ghi chú:",
        Regex("""(?iu)(?<![\p{L}\p{N}])Chỉ ra:(?![\p{L}\p{N}])""") to "Nhắc nhở:",
        Regex("""(?iu)(?<![\p{L}\p{N}])pháp lực giá trị(?![\p{L}\p{N}])""") to "điểm pháp lực",
        Regex("""(?iu)(?<![\p{L}\p{N}])ở ở(?![\p{L}\p{N}])""") to "ở",
        Regex("""(?iu)(?<![\p{L}\p{N}])nguy nguy hiểm(?![\p{L}\p{N}])""") to "nguy hiểm",
        Regex("""(?iu)(?<![\p{L}\p{N}])lý do mà N P làm như vậy(?![\p{L}\p{N}])""") to "sở dĩ làm như vậy",
        Regex("""(?iu)(?<![\p{L}\p{N}])nhỏ bé như hạt bụi(?![\p{L}\p{N}])""") to "hơi lớn",
    )

    fun polish(text: String): String {
        if (text.isBlank()) return text
        var output = text
        for ((pattern, replacement) in INVERTED_CONVERT_PATTERNS) {
            output = pattern.replace(output, replacement)
        }
        output = CUA_EXCESSIVE_REGEX.replace(output) { match ->
            match.groupValues[1]
        }
        return output
    }
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

    private val ROGUE_BOOLEAN_REGEX = Regex("(?<=\\s|^)(?:true|false)(?=\\s|$|[.,!?;:\"'”’])", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex(" {2,}")
    private val COMMA_DIGIT_REGEX = Regex("(?<=\\d)[ \\t]*([,])[ \\t]*(?=\\d)")
    private val SPACE_DIGIT_REGEX = Regex("(?<=\\d)[ \\t]+(?=\\d)")
    private val HEADING_KEYWORDS = arrayOf(
        "Chương", "chương", "Quyển", "quyển", "Tiết", "tiết", "Phần", "phần",
        "Thiên", "thiên", "Tập", "tập", "Hồi", "hồi", "Màn", "màn", "Mùa", "mùa",
        "Chính văn", "chính văn", "Ngoại truyện", "ngoại truyện"
    )

    internal fun cleanRogueBooleanLiterals(text: String): String {
        if (text.isEmpty() || (!text.contains("true", ignoreCase = true) && !text.contains("false", ignoreCase = true))) {
            return text
        }
        return text.replace(ROGUE_BOOLEAN_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    internal fun normalizeNumericSpacing(value: String): String {
        return cleanRogueBooleanLiterals(
            collapseDecimalPointSpacing(
                dedupeRepeatedHeadings(value)
                    .replace(COMMA_DIGIT_REGEX, "$1")
            )
                .replace(SPACE_DIGIT_REGEX, "")
        )
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
        var hasHeadingKeyword = false
        for (kw in HEADING_KEYWORDS) {
            if (value.contains(kw)) {
                hasHeadingKeyword = true
                break
            }
        }
        if (!hasHeadingKeyword) return value

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
