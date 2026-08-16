package io.legado.app.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.DictPair
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Opt-in device benchmark for the complete bundled QT pack. */
@RunWith(AndroidJUnit4::class)
class QuickTranslationPerformanceInstrumentedTest {

    @Test
    fun reportsColdAndWarmChapterLatency() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val repository = GlobalContext.get().get<QuickTranslationGateway>()
        val projectTerms = listOf(
            DictPair("\u53f6\u957f\u751f", "Di\u1ec7p Tr\u01b0\u1eddng Sinh"),
            DictPair("\u8d70\u8fdb", "\u0111i v\u00e0o"),
            DictPair("\u623f\u95f4", "ph\u00f2ng"),
            DictPair("\u5f20\u5143\u6e05", "Tr\u01b0\u01a1ng Nguy\u00ean Thanh"),
            DictPair("\u8bf4\u9053", "n\u00f3i"),
        )
        val source = buildString {
            repeat(220) {
                append("\u53f6\u957f\u751f\u8d70\u8fdb\u623f\u95f4\uff0c\u5bf9\u5f20\u5143\u6e05\u8bf4\u9053\u3002\n")
            }
        }
        val runtime = Runtime.getRuntime()
        val usedBeforeMb = runtime.usedHeapMb()
        val coldStartedAt = SystemClock.elapsedRealtime()
        val coldOutput = repository.translate(source, projectTerms)
        val coldMs = SystemClock.elapsedRealtime() - coldStartedAt
        val usedAfterColdMb = runtime.usedHeapMb()
        val warmTimes = LongArray(3) {
            val startedAt = SystemClock.elapsedRealtime()
            repository.translate(source, projectTerms)
            SystemClock.elapsedRealtime() - startedAt
        }

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putInt("qtSourceChars", source.length)
                putLong("qtColdMs", coldMs)
                putString("qtWarmMs", warmTimes.joinToString(","))
                putInt("qtHeapClassMb", activityManager.memoryClass)
                putBoolean("qtLowRamDevice", activityManager.isLowRamDevice)
                putBoolean(
                    "qtJiebaEnabledByPolicy",
                    shouldEnableJiebaTokenizer(
                        activityManager.memoryClass,
                        activityManager.isLowRamDevice,
                    ),
                )
                putLong("qtUsedHeapBeforeMb", usedBeforeMb)
                putLong("qtUsedHeapAfterColdMb", usedAfterColdMb)
                putString("qtOutputSample", coldOutput.take(200))
            },
        )
        assertTrue(coldOutput.isNotBlank())
        assertTrue(coldOutput.contains("Di\u1ec7p Tr\u01b0\u1eddng Sinh"))
        assertTrue(coldOutput.contains("Tr\u01b0\u01a1ng Nguy\u00ean Thanh"))
    }
}

private fun Runtime.usedHeapMb(): Long = (totalMemory() - freeMemory()) / (1024L * 1024L)
