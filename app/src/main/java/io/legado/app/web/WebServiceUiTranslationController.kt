package io.legado.app.web

import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.webservice.WebServiceUiTranslationRequest
import io.legado.app.domain.webservice.WebServiceUiTranslationResponse
import io.legado.app.ui.config.translation.TranslationConfig
import org.koin.core.context.GlobalContext

object WebServiceUiTranslationController {
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
        val provider = configuredProvider.takeIf {
            TranslationConstants.supportsTargetLanguage(it, requestedLanguage)
        } ?: TranslationConstants.PROVIDER_GOOGLE
        val translated = values.map { text ->
            if (text.isBlank()) text else translator.executeSuggestion(
                text = text,
                provider = provider,
                targetLanguage = requestedLanguage,
            ).getOrElse { text }
        }
        return WebServiceUiTranslationResponse(language, translated)
    }
}
