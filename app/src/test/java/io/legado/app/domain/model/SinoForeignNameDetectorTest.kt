package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinoForeignNameDetectorTest {

    @Test
    fun detectsExactFantasyNames() {
        val deneir = SinoForeignNameDetector.classifyName("迪奈尔")
        assertNotNull(deneir)
        assertEquals("western", deneir?.origin)
        assertEquals("Deneir", deneir?.suggested)

        val mystra = SinoForeignNameDetector.classifyName("密斯特拉")
        assertNotNull(mystra)
        assertEquals("western", mystra?.origin)
        assertEquals("Mystra", mystra?.suggested)

        val moen = SinoForeignNameDetector.classifyName("蒙恩")
        assertNotNull(moen)
        assertEquals("western", moen?.origin)
        assertEquals("Moen", moen?.suggested)

        val palsha = SinoForeignNameDetector.classifyName("帕尔夏")
        assertNotNull(palsha)
        assertEquals("western", palsha?.origin)
        assertEquals("Palsha", palsha?.suggested)
    }

    @Test
    fun skipsStandardChineseNames() {
        val zhang = SinoForeignNameDetector.classifyName("张无忌")
        assertEquals("chinese", zhang?.origin)

        val li = SinoForeignNameDetector.classifyName("李逍遥")
        assertEquals("chinese", li?.origin)

        val wang = SinoForeignNameDetector.classifyName("王小明")
        assertEquals("chinese", wang?.origin)
    }

    @Test
    fun detectsJapaneseKanjiNames() {
        val sato = SinoForeignNameDetector.classifyName("佐藤太郎", SinoForeignNameDetector.GenreOriginBias.EASTERN_JP)
        assertNotNull(sato)
        assertEquals("japanese", sato?.origin)
        assertEquals("Satō Tarō", sato?.suggested)

        val kirigaya = SinoForeignNameDetector.classifyName("桐谷和人", SinoForeignNameDetector.GenreOriginBias.EASTERN_JP)
        assertNotNull(kirigaya)
        assertEquals("japanese", kirigaya?.origin)
        assertEquals("Kirigaya Kazuto", kirigaya?.suggested)
    }

    @Test
    fun detectsKoreanHanjaNames() {
        val sung = SinoForeignNameDetector.classifyName("成真宇", SinoForeignNameDetector.GenreOriginBias.EASTERN_KR)
        assertNotNull(sung)
        assertEquals("korean", sung?.origin)
        assertEquals("Sung Jin-Woo", sung?.suggested)

        val choi = SinoForeignNameDetector.classifyName("崔钟仁", SinoForeignNameDetector.GenreOriginBias.EASTERN_KR)
        assertNotNull(choi)
        assertEquals("korean", choi?.origin)
        assertEquals("Choi Jong-In", choi?.suggested)
    }

    @Test
    fun detectsUnicodeRanges() {
        val latin = SinoForeignNameDetector.classifyName("Arthur")
        assertEquals("western", latin?.origin)
        assertEquals("Arthur", latin?.suggested)

        val kana = SinoForeignNameDetector.classifyName("桐ヶ谷")
        assertEquals("japanese", kana?.origin)

        val hangul = SinoForeignNameDetector.classifyName("성진우")
        assertEquals("korean", hangul?.origin)
    }

    @Test
    fun infersGenreBiasCorrectly() {
        assertEquals(
            SinoForeignNameDetector.GenreOriginBias.WESTERN,
            SinoForeignNameDetector.inferGenreBias("<vai_tro>Dịch giả kỳ huyễn phương Tây</vai_tro>"),
        )
        assertEquals(
            SinoForeignNameDetector.GenreOriginBias.EASTERN_JP,
            SinoForeignNameDetector.inferGenreBias("Light Novel / Anime Nhật Bản"),
        )
        assertEquals(
            SinoForeignNameDetector.GenreOriginBias.EASTERN_KR,
            SinoForeignNameDetector.inferGenreBias("Webnovel Hàn Quốc / Manhwa Hunter"),
        )
        assertEquals(
            SinoForeignNameDetector.GenreOriginBias.CROSSOVER,
            SinoForeignNameDetector.inferGenreBias("Xuyên không / Đồng nhân / Đa thế giới"),
        )
        assertEquals(
            SinoForeignNameDetector.GenreOriginBias.NEUTRAL,
            SinoForeignNameDetector.inferGenreBias("Cổ đại Đông phương / Tiên hiệp"),
        )
    }

    @Test
    fun filtersOutChineseNamesInDetectForeignNames() {
        val names = listOf("张无忌", "蒙恩", "佐藤太郎", "成真宇", "李逍遥")
        val candidates = SinoForeignNameDetector.detectForeignNames(
            names,
            SinoForeignNameDetector.GenreOriginBias.CROSSOVER,
        )

        val origins = candidates.map { it.origin }
        assertTrue(origins.contains("western"))
        assertTrue(origins.contains("japanese"))
        assertTrue(origins.contains("korean"))
        assertTrue(!origins.contains("chinese"))
    }

    @Test
    fun assembleLatinDoesNotProduceGarbageMixedOutput() {
        val hamilton = SinoForeignNameDetector.classifyName("汉密尔顿", SinoForeignNameDetector.GenreOriginBias.WESTERN)
        assertNotNull(hamilton)
        assertEquals("western", hamilton?.origin)
        assertEquals("Hamilton", hamilton?.suggested)

        // For non-exact phonetic names, ensure no CJK chars leak into suggested
        val customPhonetic = SinoForeignNameDetector.classifyName("奥古斯托", SinoForeignNameDetector.GenreOriginBias.WESTERN)
        assertNotNull(customPhonetic)
        assertEquals("western", customPhonetic?.origin)
        customPhonetic!!.suggested.forEach { ch ->
            assertTrue("Suggested should not contain CJK: $ch", ch.code < 0x3000)
        }
    }

    @Test
    fun detectsCompoundForeignTerms() {
        val terms = listOf("德拉瑞昂王国语", "伊萨克城堡", "张三帝国")
        val candidates = SinoForeignNameDetector.detectCompoundForeignTerms(
            terms,
            SinoForeignNameDetector.GenreOriginBias.WESTERN,
        )
        assertTrue(candidates.any { it.raw == "德拉瑞昂王国语" && it.origin == "western" })
        assertTrue(candidates.any { it.raw == "德拉瑞昂" && it.origin == "western" })
        assertTrue(candidates.any { it.raw == "伊萨克城堡" && it.origin == "western" })
        assertTrue(candidates.none { it.raw == "张三帝国" })
    }

    @Test
    fun canonicalAliasResolvesConsistentNames() {
        val leo = SinoForeignNameDetector.classifyName("里奥", SinoForeignNameDetector.GenreOriginBias.WESTERN)
        assertNotNull(leo)
        assertEquals("western", leo?.origin)
        assertEquals("Lyon", leo?.suggested)

        val lyon = SinoForeignNameDetector.classifyName("里昂", SinoForeignNameDetector.GenreOriginBias.WESTERN)
        assertNotNull(lyon)
        assertEquals("western", lyon?.origin)
        assertEquals("Lyon", lyon?.suggested)
    }

    @Test
    fun exactMatchesCoverNovelCharacters() {
        assertEquals("Jon", SinoForeignNameDetector.classifyName("乔恩")?.suggested)
        assertEquals("Locke", SinoForeignNameDetector.classifyName("洛克")?.suggested)
        assertEquals("Medvedev", SinoForeignNameDetector.classifyName("梅杰夫")?.suggested)
        assertEquals("Angel", SinoForeignNameDetector.classifyName("安格尔")?.suggested)
        assertEquals("Gwen", SinoForeignNameDetector.classifyName("格温")?.suggested)
        assertEquals("Peter", SinoForeignNameDetector.classifyName("彼得")?.suggested)
        assertEquals("Scott", SinoForeignNameDetector.classifyName("斯科特")?.suggested)
    }

    @Test
    fun findNamesInTextDiscoversKnownEntities() {
        val text = "里昂笑着对安格尔说，乔恩先生在楼上。"
        val names = SinoForeignNameDetector.findNamesInText(text)
        assertTrue(names.contains("里昂"))
        assertTrue(names.contains("安格尔"))
        assertTrue(names.contains("乔恩"))
    }

    @Test
    fun getAliasesReturnsAllVariants() {
        val aliases = SinoForeignNameDetector.getAliases("里昂")
        assertTrue(aliases.contains("里昂"))
        assertTrue(aliases.contains("里奥"))
        assertTrue(aliases.contains("Lyon"))

        val latinAliases = SinoForeignNameDetector.getAliases("Lyon")
        assertTrue(latinAliases.contains("Lyon"))
        assertTrue(latinAliases.contains("里昂"))
        assertTrue(latinAliases.contains("里奥"))
    }

    @Test
    fun compoundSeparatedNamesHandleMiddleDotAndMojibake() {
        assertEquals("Noah", SinoForeignNameDetector.classifyName("诺亚")?.suggested)

        val withMiddleDot = SinoForeignNameDetector.classifyName("诺亚·帕特")
        assertNotNull(withMiddleDot)
        assertEquals("Noah Pat", withMiddleDot?.suggested)
        assertEquals("western", withMiddleDot?.origin)
        assertEquals("compound_separated", withMiddleDot?.detectionTier)

        val withMojibakeQuestionMark = SinoForeignNameDetector.classifyName("诺亚?帕特")
        assertNotNull(withMojibakeQuestionMark)
        assertEquals("Noah Pat", withMojibakeQuestionMark?.suggested)
        assertEquals("western", withMojibakeQuestionMark?.origin)
        assertEquals("compound_separated", withMojibakeQuestionMark?.detectionTier)
    }

    @Test
    fun decomposesWesternTitleSuffixesAndPrefixes() {
        val medvedevMaster = SinoForeignNameDetector.classifyName("梅杰夫大师")
        assertNotNull(medvedevMaster)
        assertEquals("đại sư Medvedev", medvedevMaster?.suggested)
        assertEquals("western", medvedevMaster?.origin)
        assertEquals("title_compound", medvedevMaster?.detectionTier)

        val merlinMarquis = SinoForeignNameDetector.classifyName("梅林侯爵")
        assertNotNull(merlinMarquis)
        assertEquals("hầu tước Merlin", merlinMarquis?.suggested)
        assertEquals("title_compound", merlinMarquis?.detectionTier)

        val eatonCount = SinoForeignNameDetector.classifyName("伊顿伯爵")
        assertNotNull(eatonCount)
        assertEquals("bá tước Eaton", eatonCount?.suggested)
        assertEquals("title_compound", eatonCount?.detectionTier)

        val patViscount = SinoForeignNameDetector.classifyName("帕特子爵")
        assertNotNull(patViscount)
        assertEquals("tử tước Pat", patViscount?.suggested)
        assertEquals("title_compound", patViscount?.detectionTier)

        val oldJon = SinoForeignNameDetector.classifyName("老乔恩")
        assertNotNull(oldJon)
        assertEquals("Lão Jon", oldJon?.suggested)
        assertEquals("title_compound", oldJon?.detectionTier)

        val littleAngel = SinoForeignNameDetector.classifyName("小安格尔")
        assertNotNull(littleAngel)
        assertEquals("Tiểu Angel", littleAngel?.suggested)
        assertEquals("title_compound", littleAngel?.detectionTier)
    }

    @Test
    fun stripVietnameseDiacriticsNormalizesAccents() {
        assertEquals("mai kiet phu", SinoForeignNameDetector.stripVietnameseDiacritics("Mai Kiệt Phu".lowercase()))
        assertEquals("dai su medvedev", SinoForeignNameDetector.stripVietnameseDiacritics("đại sư Medvedev".lowercase()))
        assertEquals("kieu an", SinoForeignNameDetector.stripVietnameseDiacritics("Kiều Ân".lowercase()))
        assertEquals("lac khac", SinoForeignNameDetector.stripVietnameseDiacritics("Lạc Khắc".lowercase()))
    }

    @Test
    fun findNamesInTextDetectsCompoundTitles() {
        val text = "你不是一直想听梅杰夫大师的独奏音乐会吗？"
        val detected = SinoForeignNameDetector.findNamesInText(text)
        assertTrue(detected.contains("梅杰夫"))
        assertTrue(detected.contains("梅杰夫大师"))
    }
}
