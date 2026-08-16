package io.legado.app.data.repository

import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CancellationException

class QuickDictionaryPackStoreTest {

    private lateinit var root: File
    private lateinit var store: QuickDictionaryPackStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("quick-dictionary-pack-test").toFile()
        store = QuickDictionaryPackStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importBuildsDeduplicatedMmapIndexAndMatchesLongestTerms() {
        val input = File(root, "VietPhrase.txt").apply {
            writeText(
                """
                天=thiên
                天道=thiên đạo
                天道=bản trùng phải bị bỏ
                dòng không hợp lệ
                """.trimIndent()
            )
        }
        val progress = mutableListOf<Int>()

        val result = store.importPack(
            sourceFile = input,
            displayName = "VietPhrase",
            type = QuickDictionaryType.VIETPHRASE,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            onProgress = { progress += it.processedLines },
        )
        val pack = requireNotNull(result.pack)

        assertEquals(2, pack.entryCount)
        assertEquals(1, result.rejectedLines)
        assertEquals(1, result.duplicateLines)
        assertTrue(progress.isNotEmpty())
        assertTrue(File(root, "${pack.id}.qtdict").isFile)
        assertEquals(
            listOf("天=thiên", "天道=thiên đạo"),
            File(root, "${pack.id}.source.txt").readLines(),
        )

        val matches = store.matchEntries(
            context = "天道 rồi đến 天",
            projectKey = "",
            activeUniverseKey = "",
        )
        assertTrue(matches.any { it.raw == "天道" && it.target == "thiên đạo" })
        assertTrue(matches.any { it.raw == "天" && it.target == "thiên" })
        assertFalse(matches.any { it.target.contains("bản trùng") })
    }

    @Test
    fun matchEntriesNormalizesLegacyIndexedAlternativeTargets() {
        val raw = "\u5598\u606F"
        val legacyTarget = "h\u00FAt|h\u1EA5p|h\u00EDt"
        val placeholder = "x".repeat(legacyTarget.toByteArray(Charsets.UTF_8).size)
        val input = File(root, "legacy-pack.txt").apply {
            writeText("$raw=$placeholder", Charsets.UTF_8)
        }
        val pack = requireNotNull(
            store.importPack(
                sourceFile = input,
                displayName = "Legacy",
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
                onProgress = {},
            ).pack
        )
        overwriteOnlyTargetBytes(File(root, "${pack.id}.qtdict"), legacyTarget)
        store = QuickDictionaryPackStore(root)

        val match = store.matchEntries(raw, "", "").single()

        assertEquals("h\u00FAt", match.target)
    }

    @Test
    fun projectPackOnlyAppliesToItsProject() {
        val input = File(root, "Names.txt").apply {
            writeText("叶长生=Diệp Trường Sinh")
        }
        store.importPack(
            sourceFile = input,
            displayName = "Tên dự án",
            type = QuickDictionaryType.NAME,
            scope = QuickDictionaryScope.PROJECT,
            scopeKey = "book-a",
            onProgress = {},
        )

        assertTrue(
            store.matchEntries("叶长生", "book-b", "").isEmpty()
        )
        assertEquals(
            "Diệp Trường Sinh",
            store.matchEntries("叶长生", "book-a", "").single().target,
        )
    }

    @Test
    fun deleteRemovesMetadataSourceAndIndex() {
        val input = File(root, "Pronouns.txt").apply {
            writeText("本座=bản tọa")
        }
        val pack = store.importPack(
            sourceFile = input,
            displayName = "Đại từ",
            type = QuickDictionaryType.PRONOUN,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            onProgress = {},
        ).pack!!

        store.deletePack(pack.id)

        assertTrue(store.packs.value.isEmpty())
        assertFalse(File(root, "${pack.id}.qtdict").exists())
        assertFalse(File(root, "${pack.id}.source.txt").exists())
        assertFalse(File(root, "${pack.id}.json").exists())
    }

    @Test(timeout = 120_000)
    fun fullQt2020VietPhraseBuildsAsBoundedMmapPack() {
        val source = listOf(
            File("src/debug/assets/offline/qt2020/VietPhrase.txt"),
            File("app/src/debug/assets/offline/qt2020/VietPhrase.txt"),
        ).firstOrNull(File::isFile)
        requireNotNull(source) { "QT2020 VietPhrase debug source is missing" }

        val result = store.importPack(
            sourceFile = source,
            displayName = "QT2020 VietPhrase",
            type = QuickDictionaryType.VIETPHRASE,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            onProgress = {},
        )
        val pack = requireNotNull(result.pack)

        assertTrue(pack.entryCount > 700_000)
        assertTrue(pack.indexBytes in 30_000_000L..100_000_000L)
        assertTrue(
            store.matchEntries("01月 01 号", "", "")
                .any { it.raw == "01月 01 号" && it.target.isNotBlank() }
        )
    }

    @Test
    fun importSkipsExistingAndInFileDuplicates() {
        val input = File(root, "duplicate-import.txt").apply {
            writeText(
                """
                existing=old
                fresh=new
                fresh=must not overwrite
                """.trimIndent()
            )
        }
        val progress = mutableListOf<io.legado.app.domain.model.QuickDictionaryImportProgress>()

        val result = store.importPack(
            sourceFile = input,
            displayName = "Only new entries",
            type = QuickDictionaryType.VIETPHRASE,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            isExistingEntry = { _, raw -> raw.equals("existing", ignoreCase = true) },
            onProgress = progress::add,
        )

        assertEquals(1, result.importedEntries)
        assertEquals(2, result.duplicateLines)
        assertEquals(0, result.rejectedLines)
        assertEquals(input.length(), progress.last().processedBytes)
        assertEquals(input.length(), progress.last().totalBytes)
        assertEquals(
            listOf("fresh=new"),
            File(root, "${requireNotNull(result.pack).id}.source.txt").readLines(),
        )
    }

    @Test
    fun duplicateOnlyImportDoesNotCreateEmptyPack() {
        val input = File(root, "duplicates-only.txt").apply {
            writeText("existing=must not overwrite")
        }

        val result = store.importPack(
            sourceFile = input,
            displayName = "Duplicates",
            type = QuickDictionaryType.NAME,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            isExistingEntry = { _, _ -> true },
            onProgress = {},
        )

        assertEquals(0, result.importedEntries)
        assertEquals(1, result.duplicateLines)
        assertTrue(result.pack == null)
        assertTrue(store.packs.value.isEmpty())
    }

    @Test
    fun malformedEncodingRollsBackAllStagingFiles() {
        val input = File(root, "broken-utf8.txt").apply {
            writeBytes(
                byteArrayOf(0xC3.toByte(), 0x28.toByte(), '='.code.toByte(), 'x'.code.toByte())
            )
        }

        val failure = runCatching {
            store.importPack(
                sourceFile = input,
                displayName = "Broken",
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
                onProgress = {},
            )
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(store.packs.value.isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("pack_") })
    }

    @Test
    fun cancellationDuringIndexingRollsBackAllStagingFiles() {
        val input = File(root, "cancel.txt").apply {
            bufferedWriter().use { output ->
                repeat(6_000) { index -> output.appendLine("term$index=value$index") }
            }
        }

        val failure = runCatching {
            store.importPack(
                sourceFile = input,
                displayName = "Cancel",
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
                onProgress = { progress ->
                    if (progress.phase == io.legado.app.domain.model.QuickDictionaryImportPhase.INDEXING &&
                        progress.processedLines >= 5_000
                    ) {
                        throw CancellationException("test cancellation")
                    }
                },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(store.packs.value.isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("pack_") })
    }

    @Test
    fun startupRemovesPackFilesWithoutCompleteAtomicMetadataSet() {
        val orphanId = "pack_${"0".repeat(32)}"
        File(root, "$orphanId.qtdict").writeText("partial")
        File(root, "$orphanId.source.txt").writeText("partial")

        store = QuickDictionaryPackStore(root)

        assertFalse(File(root, "$orphanId.qtdict").exists())
        assertFalse(File(root, "$orphanId.source.txt").exists())
        assertTrue(store.packs.value.isEmpty())
    }

    @Test(timeout = 900_000)
    fun fiveMillionLineImportAndWarmLookupPerformanceGate() {
        assumeTrue(System.getenv("QUICK_DICTIONARY_5M_TEST") == "1")
        val input = File(root, "five-million.txt")
        input.bufferedWriter(Charsets.UTF_8, 256 * 1024).use { output ->
            repeat(5_000_000) { index ->
                output.append("term")
                    .append(index.toString().padStart(7, '0'))
                    .append("=value")
                    .append(index.toString())
                    .append('\n')
            }
        }
        forceGc()
        val heapBefore = usedHeapBytes()

        val startedAt = System.nanoTime()
        val result = store.importPack(
            sourceFile = input,
            displayName = "5M performance gate",
            type = QuickDictionaryType.VIETPHRASE,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
            onProgress = {},
        )
        val importMillis = (System.nanoTime() - startedAt) / 1_000_000
        forceGc()
        val retainedHeap = (usedHeapBytes() - heapBefore).coerceAtLeast(0)

        assertEquals(5_000_000, result.importedEntries)
        assertTrue("Import took ${importMillis}ms", importMillis < 12 * 60 * 1_000)
        assertTrue(
            "Retained Java heap grew by $retainedHeap bytes",
            retainedHeap < 160L * 1024 * 1024,
        )
        val lookupStartedAt = System.nanoTime()
        repeat(100) {
            val matches = store.matchEntries(
                "term0000000 term2499999 term4999999",
                "",
                "",
            )
            assertEquals(3, matches.size)
        }
        val lookupMillis = (System.nanoTime() - lookupStartedAt) / 1_000_000
        println(
            "QUICK_DICTIONARY_5M_METRICS " +
                "importMs=$importMillis retainedHeapBytes=$retainedHeap lookup100Ms=$lookupMillis " +
                "indexBytes=${requireNotNull(result.pack).indexBytes}"
        )
        assertTrue("100 warm lookups took ${lookupMillis}ms", lookupMillis < 5_000)
    }

    private fun forceGc() {
        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun overwriteOnlyTargetBytes(indexFile: File, target: String) {
        val targetBytes = target.toByteArray(Charsets.UTF_8)
        RandomAccessFile(indexFile, "rw").use { input ->
            input.seek(BUCKET_COUNT_OFFSET.toLong())
            val bucketCount = input.readIntLeForTest()
            input.seek(HEADER_SIZE.toLong())
            var entryOffset = 0
            repeat(bucketCount) {
                val pointer = input.readIntLeForTest()
                if (pointer != 0) entryOffset = pointer
            }
            require(entryOffset > 0)
            input.seek((entryOffset + SOURCE_LENGTH_OFFSET).toLong())
            val sourceLength = input.readUnsignedShortLeForTest()
            input.skipBytes(RESERVED_BYTES)
            val currentTargetLength = input.readIntLeForTest()
            require(currentTargetLength == targetBytes.size)
            input.seek((entryOffset + ENTRY_HEADER_SIZE + sourceLength * Char.SIZE_BYTES).toLong())
            input.write(targetBytes)
        }
    }

    private fun RandomAccessFile.readIntLeForTest(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun RandomAccessFile.readUnsignedShortLeForTest(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private companion object {
        const val BUCKET_COUNT_OFFSET = 12
        const val HEADER_SIZE = 32
        const val SOURCE_LENGTH_OFFSET = 4
        const val RESERVED_BYTES = 2
        const val ENTRY_HEADER_SIZE = 12
    }
}
