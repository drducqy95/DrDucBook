package io.legado.app.domain.usecase

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.TranslationConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class MlKitResidualRepairInstrumentedTest {

    @Test
    fun chineseToVietnameseOutputIsUsableAfterResidualRepair() = runBlocking {
        val koin = GlobalContext.get()
        val mlKit = koin.get<MlKitTranslationGateway>()
        val downloaded = mlKit.getLanguageModels()
            .filter { it.downloaded }
            .mapTo(hashSetOf()) { it.languageTag }
        assumeTrue("ML Kit zh and vi models must be downloaded", "zh" in downloaded && "vi" in downloaded)

        val source = "\u53f6\u957f\u751f\u6765\u4e86\u3002"
        val raw = mlKit.translate(
            text = source,
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            sourceLanguage = "zh",
        )
        val quickTranslation = koin.get<QuickTranslationGateway>()
        val repaired = repairResidualCjkForVietnamese(
            text = raw,
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { quickTranslation.translate(it) },
            phoneticResidual = { quickTranslation.hanViet(it) },
        )

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("mlKitRawOutput", raw)
                putString("mlKitRepairedOutput", repaired)
            },
        )
        assertTrue("ML Kit returned empty output", raw.isNotBlank())
        assertTrue("Residual repair returned empty output", repaired.isNotBlank())
        assertFalse("Residual CJK remains after repair: $repaired", repaired.hasCjkSourceForTest())
    }
}

private fun String.hasCjkSourceForTest(): Boolean = codePoints().anyMatch { value ->
    value in 0x3040..0x30FF ||
        value in 0x3400..0x4DBF ||
        value in 0x4E00..0x9FFF ||
        value in 0xAC00..0xD7AF ||
        value in 0xF900..0xFAFF ||
        value in 0x20000..0x323AF
}
