package io.legado.app.web

import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.webservice.WebServiceUiTranslationRequest
import io.legado.app.domain.webservice.WebServiceUiTranslationResponse
import io.legado.app.ui.config.translation.TranslationConfig
import org.koin.core.context.GlobalContext

object WebServiceUiTranslationController {
    private const val TAG = "WebServiceUiTrans"

    suspend fun translate(request: WebServiceUiTranslationRequest): WebServiceUiTranslationResponse {
        val language = request.targetLanguage.trim().lowercase().ifBlank { "vi" }
        val values = request.texts.take(100).map { it.take(500) }
        // Static labels are Vietnamese, but dynamic source/book text can be Chinese. Keep the
        // quick translator for Vietnamese so CJK metadata is translated as well; other locales
        // use the selected chapter provider. Normalize zh-CN to the provider's `zh` code.
        val requestedLanguage = when {
            language.startsWith("zh") -> "zh"
            else -> language
        }
        if (language.startsWith("vi")) {
            val translator = GlobalContext.get().get<io.legado.app.domain.usecase.TranslateDynamicUiTextUseCase>()
            return WebServiceUiTranslationResponse(
                language,
                values.mapIndexed { index, text ->
                    translator.execute(
                        scopeKey = "web-ui:${request.scopeKey}:$index",
                        originalText = text,
                        forceRetranslate = request.forceRetranslate,
                    ).getOrElse { text }
                },
            )
        }
        val translator = GlobalContext.get().get<TranslateChapterUseCase>()
        val configuredProvider = TranslationConfig.llmProvider
        val primaryProvider = configuredProvider.takeIf {
            TranslationConstants.supportsTargetLanguage(it, requestedLanguage)
        }
        val providerChain = listOfNotNull(
            primaryProvider,
            TranslationConstants.PROVIDER_GOOGLE,
            TranslationConstants.PROVIDER_ML_KIT.takeIf {
                TranslationConstants.supportsTargetLanguage(it, requestedLanguage)
            },
        ).distinct()

        val translated = values.map { text ->
            if (text.isBlank()) text
            else {
                var translatedText: String? = null
                var lastException: Throwable? = null
                for (provider in providerChain) {
                    val attempt = translator.executeSuggestion(
                        text = text,
                        provider = provider,
                        targetLanguage = requestedLanguage,
                    )
                    if (attempt.isSuccess) {
                        val candidate = attempt.getOrNull()?.trim().orEmpty()
                        if (candidate.isNotBlank() && candidate != text) {
                            translatedText = candidate
                            break
                        }
                    } else {
                        lastException = attempt.exceptionOrNull()
                    }
                }
                translatedText ?: throw (lastException ?: IllegalStateException("TRANSLATION_FAILED_ALL_PROVIDERS"))
            }
        }
        return WebServiceUiTranslationResponse(language, translated)
    }
}
