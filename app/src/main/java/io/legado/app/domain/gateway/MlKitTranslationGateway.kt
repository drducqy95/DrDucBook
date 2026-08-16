package io.legado.app.domain.gateway

data class MlKitLanguageModel(
    val languageTag: String,
    val displayName: String,
    val downloaded: Boolean,
)

class MlKitMissingLanguageModelException(
    val sourceLanguage: String,
    val targetLanguage: String,
    val missingLanguageTags: List<String>,
) : IllegalStateException(
    "Missing ML Kit language packs: ${missingLanguageTags.joinToString(", ")}. " +
        "Open Translation Settings > Google ML Kit language packs, download the missing packs, then retry."
)

class MlKitEmptyTranslationException : IllegalStateException(
    "Google ML Kit returned an empty translation. Retry the chapter or reinstall the language packs."
)

interface MlKitTranslationGateway {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null,
    ): String

    suspend fun getLanguageModels(): List<MlKitLanguageModel>

    suspend fun downloadLanguage(languageTag: String)

    suspend fun deleteLanguage(languageTag: String)
}
