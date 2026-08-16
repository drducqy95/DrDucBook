package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.MlKitLanguageModel
import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.model.BrowserPageTextNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranslateBrowserPageUseCaseTest {

    @Test
    fun translatesEligibleNodesAndSkipsUrls() = runBlocking {
        val useCase = TranslateBrowserPageUseCase(FakeMlKitGateway())

        val result = useCase.execute(
            listOf(
                BrowserPageTextNode("1", "第一章", "a"),
                BrowserPageTextNode("2", "https://example.com", "b"),
                BrowserPageTextNode("3", "Hello world", "c"),
            )
        )

        assertEquals(2, result.translations.size)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun rejectsOutputThatKeepsMostCjkText() {
        val useCase = TranslateBrowserPageUseCase(
            FakeMlKitGateway { text -> text }
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                useCase.execute(listOf(BrowserPageTextNode("1", "第一章内容", "a")))
            }
        }
    }

    private class FakeMlKitGateway(
        private val translateBlock: (String) -> String = { "Nội dung đã dịch" },
    ) : MlKitTranslationGateway {
        override suspend fun translate(
            text: String,
            targetLanguage: String,
            sourceLanguage: String?,
        ): String = translateBlock(text)

        override suspend fun getLanguageModels(): List<MlKitLanguageModel> = emptyList()

        override suspend fun downloadLanguage(languageTag: String) = Unit

        override suspend fun deleteLanguage(languageTag: String) = Unit
    }
}
