package io.legado.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HachimiOnnxTranslatorInstrumentedTest {

    @Test
    fun bundledNmtGraphsRunOnDeviceAndReportProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translator = HachimiOnnxTranslator(context)
        var completed = 0
        var total = 0
        try {
            val result = translator.translate(
                text = "你好。",
                policy = HachimiDecodePolicy(
                    maxNewTokens = 32,
                    maxSourceTokens = 48,
                    noRepeatNgramSize = 2,
                ),
                onProgress = { done, all, _ ->
                    completed = done
                    total = all
                },
            )

            assertTrue("NMT returned an empty translation", result.text.isNotBlank())
            assertTrue("NMT produced no tokens", result.generatedTokens > 0)
            assertEquals(total, completed)
            assertTrue(total > 0)
        } finally {
            translator.close()
        }
    }
}
