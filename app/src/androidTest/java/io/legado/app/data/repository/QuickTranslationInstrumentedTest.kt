package io.legado.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickDictionaryType
import splitties.init.appCtx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class QuickTranslationInstrumentedTest {

    private val cjk = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]")

    @Test
    fun phoneticFallbackCoversSimplifiedCharactersAndDictionaryTargets() {
        val repository = QuickTranslationRepository()
        val source = "脉 超能"
        val translated = repository.translate(
            text = source,
            projectTerms = listOf(DictPair("超能", "异术")),
            customPhonetics = emptyList(),
        )

        assertFalse("QT left CJK in output: $translated", cjk.containsMatchIn(translated))
        assertTrue("Missing fallback reading in: $translated", translated.contains("mạch", ignoreCase = true))
        assertTrue("Missing reading from dictionary target in: $translated", translated.contains("dị", ignoreCase = true))
        assertTrue("Missing reading from dictionary target in: $translated", translated.contains("thuật", ignoreCase = true))
    }

    @Test
    fun bundledDictionaryCatalogsAreBrowsableByEveryQtLane() {
        val repository = QuickTranslationRepository()
        val catalogs = repository.getBuiltInCatalogs()

        assertEquals(
            setOf(
                QuickDictionaryType.NAME,
                QuickDictionaryType.VIETPHRASE,
                QuickDictionaryType.PHONETIC,
                QuickDictionaryType.PRONOUN,
                QuickDictionaryType.LUAT_NHAN,
            ),
            catalogs.map { it.type }.toSet(),
        )
        assertTrue(catalogs.all { it.entryCount > 0 })
        catalogs.forEach { catalog ->
            val sample = repository.searchBuiltInEntries(
                type = catalog.type,
                limit = 3,
                catalogId = catalog.id,
            )
            assertTrue("Catalog ${catalog.name} is not browsable", sample.isNotEmpty())
            assertTrue(sample.all { it.type == catalog.type })
            assertTrue(sample.all { it.catalogId == catalog.id })
            assertTrue(
                "Catalog ${catalog.name} does not expose a reading",
                sample.all { it.hanViet.isNotBlank() },
            )
        }
    }

    @Test
    fun vietPhraseCatalogProvidesHanVietForTheReportedEntry() {
        val repository = QuickTranslationRepository()
        val raw = "\u51ED\u7A7A\u51FA\u73B0"
        val entry = repository.searchBuiltInEntries(
            type = QuickDictionaryType.VIETPHRASE,
            query = raw,
            limit = 10,
        ).first { it.raw == raw }

        assertTrue("Missing Han-Viet reading for $raw", entry.hanViet.isNotBlank())
        assertFalse("Han-Viet reading still contains CJK: ${entry.hanViet}", cjk.containsMatchIn(entry.hanViet))
    }

    @Test
    fun debugApkLoadsFullQt2020MappedIndexAndPreservesParagraphLayout() {
        val repository = QuickTranslationRepository()

        assertTrue(repository.packVersion.startsWith("qt2020-2025.09.01"))
        assertEquals(
            "  Ending song\n\nCung Cự Giải  ",
            repository.translate(
                text = "  片尾曲\n\n巨蟹宫  ",
                projectTerms = emptyList(),
                customPhonetics = emptyList(),
            ),
        )
    }

    @Test
    fun translationPreservesMarkupUrlPlaceholdersAndUnicodeLayoutExactly() {
        val repository = QuickTranslationRepository()
        val source =
            "  \u7CFB\u7EDF<em>{{name}}</em><em>\u7CFB\u7EDF</em>\r\n\t" +
                "\u7B2C\u4E8C\u6BB5 https://example.com/x?q=1 \u2029${'$'}{value} %1${'$'}s"

        assertEquals(
            "  Engine<em>{{name}}</em><em>engine</em>\r\n\t" +
                "Second block https://example.com/x?q=1 \u2029${'$'}{value} %1${'$'}s",
            repository.translate(
                text = source,
                projectTerms = listOf(
                    DictPair("\u7CFB\u7EDF", "engine"),
                    DictPair("\u7B2C\u4E8C\u6BB5", "second block"),
                ),
                customPhonetics = emptyList(),
            ),
        )
    }

    @Test
    fun qt2020EditableSourceCatalogsAreStreamedWithoutLoadingTheWholeDictionary() {
        val repository = QuickTranslationRepository()
        val catalogs = repository.getBuiltInCatalogs()

        assertEquals(
            mapOf(
                "qt2020:names2" to 1_768,
                "qt2020:names" to 929,
                "qt2020:vietphrase2" to 4_748,
                "qt2020:vietphrase" to 728_698,
                "qt2020:pronouns" to 12_063,
            ),
            catalogs
                .filter { it.id.startsWith("qt2020:") }
                .associate { it.id to it.entryCount },
        )
        val raw = "\u7247\u5C3E\u66F2"
        val result = repository.searchBuiltInEntries(
            type = QuickDictionaryType.VIETPHRASE,
            query = raw,
            limit = 10,
            catalogId = "qt2020:vietphrase2",
        )
        assertTrue(result.any { it.raw == raw && it.target.isNotBlank() })
    }

    @Test
    fun editableOverrideTakesPriorityOverQt2020MappedEntry() {
        val repository = QuickTranslationRepository()

        assertEquals(
            "Khúc kết do người dùng chốt",
            repository.translate(
                text = "片尾曲",
                projectTerms = listOf(
                    DictPair("片尾曲", "Khúc kết do người dùng chốt")
                ),
                customPhonetics = emptyList(),
            ),
        )
    }

    @Test
    fun debugApkContainsOnlyQt2020RuntimeData() {
        val expected = setOf(
            "ChinesePhienAmWords.txt",
            "LuatNhan.txt",
            "Names.txt",
            "Names2.txt",
            "NOTICE.txt",
            "Pronouns.txt",
            "qt2020-index-manifest.json",
            "qt2020-source-manifest.json",
            "qt2020-terms.qtdict",
            "VietPhrase.txt",
            "VietPhrase2.txt",
        )

        val bundled = appCtx.assets.list("offline/qt2020").orEmpty().toSet()

        assertEquals(expected, bundled)
        appCtx.assets.openFd("offline/qt2020/qt2020-terms.qtdict").use { descriptor ->
            assertTrue(descriptor.length > 30_000_000L)
        }
    }

    @Test
    fun qt2020RuntimeAssetsMatchRecordedSourceAndIndexHashes() {
        val expectedHashes = mapOf(
            "Names2.txt" to "add1c5ed4a04ad22aab025790436cc10d3953be3a48e44b709fca88c2f53e7d5",
            "Names.txt" to "3de8d1a76849b3533a976076cbe47036d294b5a2807a58c5ec389a71424d8258",
            "VietPhrase2.txt" to "5bb1505c43a671fbffc291dcdea0e72dd52fc6c6b4b8bed7a6762c6ca65400e3",
            "VietPhrase.txt" to "071ab090ca7be127c8c3a78dba22489e3b7ba8d602e959f8d34648468763bf84",
            "Pronouns.txt" to "830635cdc2af2e6e377d6aadb9d108a40b3c7c3bb2d7b58df6222f328b71ed55",
            "ChinesePhienAmWords.txt" to "dfca89326ff0e425445c861bdf95b8527edbfdeda50e8553f2e03382e8ab448b",
            "LuatNhan.txt" to "a547fb210ec9eab7f31a8e564744a241ac198aad9a0458fc5e1b44229938ed9e",
            "qt2020-terms.qtdict" to "8d0ddc59a832d3dcfacb98aeba3c91a0c6fabf8514d9115b6146eaf7ab8d86f1",
        )

        expectedHashes.forEach { (fileName, expectedHash) ->
            assertEquals(
                "Unexpected QT2020 asset content: $fileName",
                expectedHash,
                sha256Asset("offline/qt2020/$fileName"),
            )
        }
    }

    private fun sha256Asset(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appCtx.assets.open(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
