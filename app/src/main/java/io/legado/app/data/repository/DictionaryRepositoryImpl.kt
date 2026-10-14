package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.model.AiTranslationRefinePipeline
import io.legado.app.domain.model.BookDictionary
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.SinoForeignNameDetector
import io.legado.app.domain.model.normalizedForRuntime
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DictionaryRepositoryImpl : DictionaryGateway {

    private companion object {
        const val DICT_FILE_NAME = "translation_dictionary.json"

        private val POLLUTED_WORDS = setOf(
            "的", "了", "着", "在", "被", "把", "将", "和", "或", "个", "几", "从", "到",
            "间", "边", "旁", "前", "后", "上", "下", "里", "外", "中", "内",
            "这", "那", "我", "你", "他", "她", "它", "们", "谁", "甚", "么",
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千", "万",
            "是", "不", "有", "无", "没", "非", "微", "很", "太", "极",
            "说", "道", "看", "见", "听", "闻", "走", "去", "来", "出", "入",
            "第", "章", "节", "回", "卷", "篇"
        )
    }

    private data class CacheEntry(
        val lastModified: Long,
        val dictionary: BookDictionary,
    )

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    private fun getDictFile(book: Book): File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = File(cacheDir, book.getFolderName())
        return File(bookFolder, DICT_FILE_NAME)
    }

    override fun getBookDictionaries(book: Book): BookDictionary {
        val dictFile = getDictFile(book)
        if (!dictFile.exists()) {
            memoryCache.remove(book.bookUrl)
            return BookDictionary(book.bookUrl)
        }
        val fileLastModified = dictFile.lastModified()
        val cached = memoryCache[book.bookUrl]
        if (cached != null && cached.lastModified == fileLastModified) {
            return cached.dictionary
        }
        return try {
            val stored = GSON.fromJson(dictFile.readText(), BookDictionary::class.java)
                ?: BookDictionary(book.bookUrl)
            val rawPairs = runCatching { stored.pairs }
                .getOrNull()
                .orEmpty()
                .map(DictPair::normalizedForRuntime)
                var hasChanges = false
                val sanitizedPairs = rawPairs.mapNotNull { pair ->
                    val orig = pair.original.trim()
                    val trans = pair.translation.trim()
                    if (orig.isBlank() || trans.isBlank()) return@mapNotNull null
                    if (AiTranslationRefinePipeline.hasCjkTextCodePoints(trans) || trans.equals(orig, ignoreCase = true)) {
                        hasChanges = true
                        return@mapNotNull null
                    }
                    if (isPollutedBookDictionaryTerm(orig, trans)) {
                        hasChanges = true
                        return@mapNotNull null
                    }
                    val candidate = SinoForeignNameDetector.classifyName(orig)
                    val cleanTrans = if (candidate != null && candidate.origin != "chinese" && candidate.suggested.isNotBlank() && candidate.confidence >= 0.85f) {
                        val unaccented = SinoForeignNameDetector.stripVietnameseDiacritics(trans.lowercase())
                        if (unaccented.contains("mai kiet") || unaccented.contains("kiet phu") ||
                            unaccented.contains("qiao en") || unaccented.contains("kieu an") ||
                            unaccented.contains("lac khac") || unaccented.contains("la khac") ||
                            candidate.detectionTier in setOf("exact", "canonical_alias", "title_compound")
                        ) {
                            candidate.suggested
                        } else trans
                    } else trans
                    if (cleanTrans != pair.translation) {
                        hasChanges = true
                        pair.copy(translation = cleanTrans)
                    } else {
                        pair
                    }
                }
                if (hasChanges) {
                    val updated = BookDictionary(
                        bookUrl = book.bookUrl,
                        pairs = sanitizedPairs,
                        updatedAt = System.currentTimeMillis(),
                    )
                    runCatching { saveDictionary(book, updated) }
                }
                val result = BookDictionary(
                    bookUrl = book.bookUrl,
                    pairs = sanitizedPairs,
                    updatedAt = stored.updatedAt,
                )
                memoryCache[book.bookUrl] = CacheEntry(fileLastModified, result)
                result
            } catch (e: Exception) {
                BookDictionary(book.bookUrl)
            }
    }

    override fun updateBookDic(book: Book, newPairs: List<DictPair>) {
        val existingDict = getBookDictionaries(book)
        val updatedPairs = existingDict.pairs.toMutableList()

        for (newPair in newPairs.map(DictPair::normalizedForRuntime)) {
            val orig = newPair.original.trim()
            val trans = newPair.translation.trim()
            if (orig.isBlank() || trans.isBlank() || isPollutedBookDictionaryTerm(orig, trans)) {
                continue
            }
            val existingIndex = updatedPairs.indexOfFirst {
                it.original == newPair.original
            }
            if (existingIndex >= 0) {
                updatedPairs[existingIndex] = newPair
            } else {
                updatedPairs.add(newPair)
            }
        }

        val updatedDict = existingDict.copy(
            pairs = updatedPairs,
            updatedAt = System.currentTimeMillis()
        )

        saveDictionary(book, updatedDict)
    }

    override fun replaceBookDictionary(book: Book, pairs: List<DictPair>) {
        val cleanPairs = pairs.map(DictPair::normalizedForRuntime).filterNot {
            isPollutedBookDictionaryTerm(it.original, it.translation)
        }
        saveDictionary(
            book,
            BookDictionary(
                bookUrl = book.bookUrl,
                pairs = cleanPairs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun isPollutedBookDictionaryTerm(original: String, translation: String): Boolean {
        val orig = original.trim()
        val trans = translation.trim().lowercase()
        // Single characters must NEVER enter BookDictionary
        if (orig.codePointCount(0, orig.length) < 2) return true

        // Function words and basic particles
        if (POLLUTED_WORDS.contains(orig)) return true

        // Known corrupted translations of common words
        if (orig == "入门" && trans.contains("cửa vào")) return true
        if (orig == "贡献" && trans.contains("đóng góp")) return true
        if (orig == "随手" && trans.contains("tiện lợi")) return true
        if (orig == "上门" && trans.contains("ghé thăm")) return true
        if (orig == "一眼" && trans.contains("một cái nhìn")) return true
        if (orig == "正常来说" && trans.contains("đang thường")) return true
        if (orig in setOf("第", "章", "间", "洛", "门", "手", "边", "眼", "房", "紧", "闭", "的", "被")) return true
        return false
    }


    private fun saveDictionary(book: Book, dictionary: BookDictionary) {
        val dictFile = getDictFile(book)
        dictFile.parentFile?.mkdirs()
        dictFile.writeText(GSON.toJson(dictionary))
        memoryCache[book.bookUrl] = CacheEntry(dictFile.lastModified(), dictionary)
    }

    override fun clearBookDictionary(book: Book) {
        memoryCache.remove(book.bookUrl)
        val dictFile = getDictFile(book)
        if (dictFile.exists()) {
            dictFile.delete()
        }
    }
}
