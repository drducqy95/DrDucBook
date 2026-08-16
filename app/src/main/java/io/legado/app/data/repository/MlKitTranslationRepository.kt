package io.legado.app.data.repository

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.legado.app.domain.gateway.MlKitLanguageModel
import io.legado.app.domain.gateway.MlKitEmptyTranslationException
import io.legado.app.domain.gateway.MlKitMissingLanguageModelException
import io.legado.app.domain.gateway.MlKitTranslationGateway
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MlKitTranslationRepository : MlKitTranslationGateway {

    private val modelManager = RemoteModelManager.getInstance()
    private val translatorCache = ConcurrentHashMap<String, Translator>()
    private val downloadedModelsMutex = Mutex()
    @Volatile
    private var downloadedModelsCache: Set<String>? = null

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?,
    ): String {
        if (text.isBlank()) return text
        val source = resolveSourceLanguage(text, sourceLanguage)
            ?: error("ML Kit không thể xác định ngôn ngữ nguồn")
        val target = normalizeLanguage(targetLanguage)
            ?: error("ML Kit does not support target language: $targetLanguage")
        if (source == target) return text
        var downloadedModels = downloadedLanguageTags()
        var missingModels = missingMlKitTranslationModels(source, target, downloadedModels)
        if (missingModels.isNotEmpty()) {
            downloadedModels = downloadedLanguageTags(forceRefresh = true)
            missingModels = missingMlKitTranslationModels(source, target, downloadedModels)
        }
        if (missingModels.isNotEmpty()) {
            throw MlKitMissingLanguageModelException(
                sourceLanguage = source,
                targetLanguage = target,
                missingLanguageTags = missingModels,
            )
        }

        val translator = translatorCache.getOrPut("$source->$target") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }
        return translator.translate(text).await().takeIf(String::isNotBlank)
            ?: throw MlKitEmptyTranslationException()
    }

    override suspend fun getLanguageModels(): List<MlKitLanguageModel> {
        val downloaded = downloadedLanguageTags()
        val vietnamese = Locale.forLanguageTag("vi")
        return TranslateLanguage.getAllLanguages()
            .distinct()
            .map { language ->
                MlKitLanguageModel(
                    languageTag = language,
                    displayName = Locale.forLanguageTag(language)
                        .getDisplayName(vietnamese)
                        .replaceFirstChar { it.titlecase(vietnamese) },
                    downloaded = language in downloaded,
                )
            }
            .sortedWith(compareBy<MlKitLanguageModel> { !it.downloaded }.thenBy { it.displayName })
    }

    override suspend fun downloadLanguage(languageTag: String) {
        val language = normalizeLanguage(languageTag)
            ?: error("ML Kit does not support language: $languageTag")
        modelManager.download(
            TranslateRemoteModel.Builder(language).build(),
            DownloadConditions.Builder().build(),
        ).await()
        downloadedModelsMutex.withLock {
            downloadedModelsCache = (downloadedModelsCache.orEmpty() + language).toSet()
        }
    }

    override suspend fun deleteLanguage(languageTag: String) {
        val language = normalizeLanguage(languageTag)
            ?: error("ML Kit does not support language: $languageTag")
        modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(language).build()).await()
        downloadedModelsMutex.withLock {
            downloadedModelsCache = downloadedModelsCache.orEmpty() - language
        }
    }

    private suspend fun identifyLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        return try {
            identifier.identifyLanguage(text.take(2_000)).await()
        } finally {
            identifier.close()
        }.takeUnless { it == "und" }.orEmpty()
    }

    private suspend fun resolveSourceLanguage(text: String, sourceLanguage: String?): String? {
        sourceLanguage?.takeIf { it.isNotBlank() }?.let { explicit ->
            return normalizeLanguage(explicit)
        }
        return inferMlKitSourceLanguage(text)
            ?: normalizeLanguage(identifyLanguage(text))
    }

    private fun normalizeLanguage(languageTag: String): String? =
        normalizeMlKitLanguageTag(languageTag)

    private suspend fun downloadedLanguageTags(forceRefresh: Boolean = false): Set<String> {
        if (!forceRefresh) downloadedModelsCache?.let { return it }
        return downloadedModelsMutex.withLock {
            if (!forceRefresh) downloadedModelsCache?.let { return@withLock it }
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
                .mapNotNullTo(hashSetOf()) { normalizeMlKitLanguageTag(it.language) }
                .also { downloadedModelsCache = it }
        }
    }
}

internal fun normalizeMlKitLanguageTag(languageTag: String): String? {
    val candidate = languageTag.trim().replace('_', '-')
    if (candidate.isEmpty()) return null
    TranslateLanguage.fromLanguageTag(candidate)?.let { return it }
    val normalized = candidate.lowercase(Locale.ROOT)
    val primary = normalized.substringBefore('-')
    val mapped = when (primary) {
        "zh", "cmn", "yue" -> "zh"
        "iw" -> "he"
        "in" -> "id"
        else -> primary
    }
    return TranslateLanguage.fromLanguageTag(mapped)
}

internal fun inferMlKitSourceLanguage(text: String): String? {
    var hanCount = 0
    var kanaCount = 0
    var hangulCount = 0
    var letterCount = 0
    var offset = 0
    val sample = text.take(4_000)
    while (offset < sample.length) {
        val codePoint = sample.codePointAt(offset)
        if (Character.isLetter(codePoint)) letterCount += 1
        if (isMlKitHanCodePoint(codePoint)) hanCount += 1
        if (isMlKitKanaCodePoint(codePoint)) kanaCount += 1
        if (isMlKitHangulCodePoint(codePoint)) hangulCount += 1
        offset += Character.charCount(codePoint)
    }
    val letters = letterCount.coerceAtLeast(1)
    return when {
        kanaCount > 0 && (kanaCount + hanCount) * 2 >= letters -> "ja"
        hangulCount > 0 && hangulCount * 2 >= letters -> "ko"
        hanCount > 0 && hanCount * 3 >= letters -> "zh"
        else -> null
    }
}

private fun isMlKitHanCodePoint(value: Int): Boolean =
    value in 0x3400..0x4DBF ||
        value in 0x4E00..0x9FFF ||
        value in 0xF900..0xFAFF ||
        value in 0x20000..0x2A6DF ||
        value in 0x2A700..0x2B73F ||
        value in 0x2B740..0x2B81F ||
        value in 0x2B820..0x2CEAF ||
        value in 0x2CEB0..0x2EE5F ||
        value in 0x2EBF0..0x2EE5F ||
        value in 0x2F800..0x2FA1F ||
        value in 0x30000..0x3134F ||
        value in 0x31350..0x323AF

private fun isMlKitKanaCodePoint(value: Int): Boolean =
    value in 0x3040..0x30FF ||
        value in 0x31F0..0x31FF ||
        value in 0xFF66..0xFF9F ||
        value in 0x1AFF0..0x1AFFF ||
        value in 0x1B000..0x1B16F

private fun isMlKitHangulCodePoint(value: Int): Boolean =
    value in 0x1100..0x11FF ||
        value in 0x3130..0x318F ||
        value in 0xA960..0xA97F ||
        value in 0xAC00..0xD7AF ||
        value in 0xD7B0..0xD7FF

internal fun missingMlKitTranslationModels(
    sourceLanguage: String,
    targetLanguage: String,
    downloadedLanguageTags: Set<String>,
): List<String> {
    return listOf(sourceLanguage, targetLanguage)
        .distinct()
        .filterNot(downloadedLanguageTags::contains)
}

private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
    addOnSuccessListener(continuation::resume)
    addOnFailureListener(continuation::resumeWithException)
}
