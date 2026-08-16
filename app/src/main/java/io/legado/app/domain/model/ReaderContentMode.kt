package io.legado.app.domain.model

enum class ReaderContentMode {
    RAW,
    TRANSLATION,
    HAN_VIET,
    QUICK_TRANSLATOR,
    GOOGLE,
    ML_KIT,
    AI,
    NMT,
}

data class ReaderTranslationCacheIdentity(
    val provider: String,
    val targetLanguage: String,
)

fun ReaderContentMode.translationCacheIdentity(
    selectedTargetLanguage: String,
): ReaderTranslationCacheIdentity? = when (this) {
    ReaderContentMode.RAW -> null
    ReaderContentMode.TRANSLATION -> null
    ReaderContentMode.HAN_VIET -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_HAN_VIET,
        targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
    )
    ReaderContentMode.QUICK_TRANSLATOR -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
        targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
    )
    ReaderContentMode.GOOGLE -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_GOOGLE,
        targetLanguage = selectedTargetLanguage,
    )
    ReaderContentMode.ML_KIT -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_ML_KIT,
        targetLanguage = selectedTargetLanguage,
    )
    ReaderContentMode.AI -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_APP_AI,
        targetLanguage = selectedTargetLanguage,
    )
    ReaderContentMode.NMT -> ReaderTranslationCacheIdentity(
        provider = TranslationConstants.PROVIDER_NMT,
        targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
    )
}

fun ReaderContentMode.displaysTranslationProvider(
    provider: String,
    selectedTargetLanguage: String,
): Boolean = when (this) {
    ReaderContentMode.TRANSLATION -> TranslationConstants.preferredContentProviders(
        selectedTargetLanguage
    ).any { it.provider == provider }
    else -> translationCacheIdentity(selectedTargetLanguage)?.provider == provider
}

fun ReaderContentMode.supportsQuickDictionaryEditing(): Boolean =
    this == ReaderContentMode.QUICK_TRANSLATOR
