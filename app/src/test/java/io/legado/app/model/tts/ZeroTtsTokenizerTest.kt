package io.legado.app.model.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ZeroTtsTokenizerTest {

    @Test
    fun bpeTokenizerEncodesExpectedTokens() {
        val tempDir = Files.createTempDirectory("zerotts-tok-test").toFile()
        try {
            val jsonFile = File(tempDir, "tokenizer.json")
            val jsonString = """
                {
                  "model": {
                    "vocab": {
                      "<pad>": 0,
                      "<bos>": 1,
                      "<eot>": 2,
                      "<soa>": 3,
                      "<slot>": 4,
                      "<eoa>": 5,
                      "<en>": 6,
                      "<vi>": 7,
                      "<unk>": 8,
                      "X": 9,
                      "i": 10,
                      "n": 11,
                      " ": 12,
                      "c": 13,
                      "h": 14,
                      "à": 15,
                      "o": 16,
                      "ch": 17,
                      "ào": 18,
                      "chào": 19,
                      "Xin": 20
                    },
                    "merges": [
                      "c h",
                      "à o",
                      "ch ào",
                      "X i",
                      "Xi n"
                    ]
                  }
                }
            """.trimIndent()
            jsonFile.writeText(jsonString)

            val tokenizer = ZeroTtsBpeTokenizer.fromFile(jsonFile)
            val tokens = tokenizer.encode("Xin chào")

            assertEquals(5, tokens.size)
            assertEquals(1L, tokens[0]) // <bos>
            assertEquals(20L, tokens[1]) // Xin
            assertEquals(12L, tokens[2]) // space
            assertEquals(19L, tokens[3]) // chào
            assertEquals(2L, tokens[4]) // <eot>
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
