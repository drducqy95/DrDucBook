package io.legado.app.data.repository

import android.app.Application
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.utils.putPrefString
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class QuickTranslationRepositoryTest {

    @Before
    fun setUpAppCtx() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.AUTO.value,
        )
    }

    @Test
    fun jiebaIsDisabledWhenHeapCannotAbsorbItsDictionary() {
        assertFalse(shouldEnableJiebaTokenizer(memoryClassMb = 192, isLowRamDevice = false))
        assertFalse(shouldEnableJiebaTokenizer(memoryClassMb = 512, isLowRamDevice = true))
        assertTrue(shouldEnableJiebaTokenizer(memoryClassMb = 256, isLowRamDevice = false))
    }

    @Test
    fun constructorCompilesStaticPatterns() {
        assertNotNull(QuickTranslationRepository())
    }

    @Test
    fun literalVietnameseTextIsNotSplitIntoCharacters() {
        val actual = QuickTranslationRepository().translate(
            text = "h\u1ed9i qu\u1ebf anh",
            projectTerms = emptyList(),
        )

        assertEquals("h\u1ed9i qu\u1ebf anh", actual)
    }

    @Test
    fun literalUrlsNumbersAndLineBreaksRemainIntact() {
        val source = "https://example.com/a1\r\nmodel-X2"

        val actual = QuickTranslationRepository().translate(source, emptyList())

        assertEquals(source, actual)
    }

    @Test
    fun abstractClassifierOrdinalAndPossessiveUseVietnameseWordOrder() {
        val actual = QuickTranslationRepository().translate(
            text = "两条命。我的第二条命。杀死第二条命的方式。",
            projectTerms = emptyList(),
        ).lowercase()

        assertTrue(actual, actual.contains("2 cuộc đời"))
        assertTrue(actual, actual.contains("cuộc đời thứ 2 của tôi"))
        assertTrue(actual, actual.contains("cách giết chết cuộc đời thứ hai"))
        assertFalse(actual, actual.contains("con mệnh"))
        assertFalse(actual, actual.contains("tôi của"))
        assertFalse(actual, actual.contains("của cách thức"))
    }

    @Test
    fun xianGongKaiWuAuthorNoteKeepsNarrativeGrammarReadable() {
        val source = "人都有两条命。一条叫世俗，一条叫理想。" +
            "具体到文艺工作者的身上，第二条命就叫做艺术生命。\n" +
            "我喜欢写书，写书本身就是我的动力之源。" +
            "这些过程，都是在滋养本人的艺术生命。\n" +
            "我的第二条命，能坚持多久？我不太清楚。\n" +
            "杀死第二条命的方式，有太多太多了。"

        val actual = QuickTranslationRepository().translate(source, emptyList()).lowercase()

        listOf(
            "mỗi người đều có hai cuộc đời",
            "một cuộc đời gọi là thế tục",
            "một cuộc đời gọi là lý tưởng",
            "cuộc đời thứ hai được gọi là đời sống nghệ thuật",
            "chính việc viết sách là nguồn động lực của tôi",
            "nuôi dưỡng đời sống nghệ thuật của chính mình",
            "cuộc đời thứ 2 của tôi",
            "cách giết chết cuộc đời thứ hai",
        ).forEach { expected -> assertTrue(actual, actual.contains(expected)) }
        assertFalse(actual, actual.contains("con mệnh"))
        assertFalse(actual, actual.contains("tôi của"))
        assertFalse(actual, actual.contains("của cách thức"))
    }

    @Test
    fun xianGongKaiWuAuthorNoteUsesContextualModernVietnamese() {
        val source = "大家好，我是蛊真人。" +
            "今天给大家带来一本新书《仙工开物》。" +
            "父母未老，无须担心。一人吃饱，全家不饿。" +
            "或许就是我的个人舒适圈，圈地自萌。" +
            "第一次开始考虑榜单，考虑读者活动，不过会虚心学习。" +
            "最大的爱好就是打游戏。本人是有社恐的，在社交平台多做活动。" +
            "出山，是为了再进山逐月。"

        val actual = QuickTranslationRepository().translate(source, emptyList()).lowercase()

        listOf(
            "xin chào mọi người",
            "cổ chân nhân",
            "giới thiệu đến mọi người một cuốn sách mới",
            "tiên công khai vật",
            "cha mẹ chưa già",
            "một người ăn no, cả nhà không đói",
            "vùng an toàn của riêng tôi",
            "tự vui trong thế giới riêng",
            "bảng xếp hạng",
            "hoạt động dành cho độc giả",
            "khiêm tốn học hỏi",
            "sở thích lớn nhất",
            "chơi game",
            "ngại giao tiếp",
            "nền tảng mạng xã hội",
            "đuổi theo ánh trăng",
        ).forEach { expected -> assertTrue(actual, actual.contains(expected)) }
        assertFalse(actual, actual.contains("familymart"))
        assertFalse(actual, actual.contains("ấm cúng khuyên"))
        assertFalse(actual, actual.contains("trục tháng"))
    }

    @Test
    fun mappedTranslationReturnsExactRawRangeForRepeatedProjectTerm() {
        val source = "前文惠桂英来了，惠桂英走了"
        val terms = listOf(DictPair("惠桂英", "hội quế anh"))
        val repository = QuickTranslationRepository()

        val mapped = repository.translateMapped(source, terms)
        val display = mapped.text.lowercase()
        val selected = "hội quế anh"
        val secondDisplayStart = display.indexOf(selected, display.indexOf(selected) + selected.length)
        val selection = mapped.asDisplayText(source).mapSelection(
            displayStart = secondDisplayStart,
            displayEnd = secondDisplayStart + selected.length,
        )

        assertEquals(repository.translate(source, terms), mapped.text)
        assertEquals(source.indexOf("惠桂英", source.indexOf("惠桂英") + 1), selection.sourceStart)
        assertEquals(selection.sourceStart!! + "惠桂英".length, selection.sourceEnd)
        assertEquals(1f, selection.confidence)
    }

    @Test
    fun mappedTranslationTextMatchesStringApiWithProtectedLayout() {
        val source = "<p>惠桂英访问 https://example.com/a1</p>\r\n惠桂英回来了。"
        val terms = listOf(DictPair("惠桂英", "hội quế anh"))
        val repository = QuickTranslationRepository()

        val plain = repository.translate(source, terms)
        val mapped = repository.translateMapped(source, terms)

        assertEquals(plain, mapped.text)
        assertTrue(mapped.segments.isNotEmpty())
        assertTrue(mapped.segments.all { segment ->
            segment.sourceStart in 0..source.length &&
                segment.sourceEnd in segment.sourceStart..source.length &&
                segment.displayStart in 0..mapped.text.length &&
                segment.displayEnd in segment.displayStart..mapped.text.length
        })
    }

    @Test
    fun tokenBoundaryBeatsLongUnrelatedTargetAtEqualCoverage() {
        val actual = QuickTranslationRepository().translate(
            text = "\u5973\u4eba\u5927\u817f\u4e0a",
            projectTerms = emptyList(),
        )

        assertTrue(actual, actual.contains("ph\u1ee5 n\u1eef", ignoreCase = true))
        assertTrue(actual, actual.contains("\u0111\u00f9i", ignoreCase = true))
        assertFalse(actual, actual.contains("Qu\u1ed1c h\u1ed9i", ignoreCase = true))
    }

    @Test
    fun realisticWebNovelParagraphDoesNotMixUnrelatedLegacyTargets() {
        val source = "系着围裙的崔桂英左手端碗，" +
            "右手握勺，边呼喊边敲打着粥缸边缘。\n" +
            "坐在旁边正给水烟袋装烟叶的李维汉" +
            "一脚踢在女人大腚上，没好气地骂道："

        val actual = QuickTranslationRepository().translate(source, emptyList())

        assertFalse(actual, actual.contains("Quốc hội Nhân dân Toàn quốc", ignoreCase = true))
        assertTrue(actual, actual.contains("mông", ignoreCase = true))
        assertTrue(actual, actual.contains("tẩu thuốc", ignoreCase = true))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun laoShiRenChapterOneFixtureUsesReadableQtPhrases() {
        val source = "第1章\n" +
            "“细那康子们，吃饭了，呜嘞呜嘞呜嘞～”\n" +
            "系着围裙的崔桂英左手端碗，右手握勺，边呼喊边敲打着粥缸边缘。\n" +
            "坐在旁边正给水烟袋装烟叶的李维汉一脚踢在女人大腚上，没好气地骂道：\n" +
            "“脑子进水了你，唤猪崽呢？”\n" +
            "半大小子吃垮老子。格格不入。"

        val actual = QuickTranslationRepository().translate(
            text = source,
            projectTerms = listOf(
                DictPair("崔桂英", "Thôi Quế Anh", QuickDictionaryType.NAME),
                DictPair("李维汉", "Lý Duy Hán", QuickDictionaryType.NAME),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("thôi quế anh đeo tạp dề"))
        assertTrue(actual, normalized.contains("tay trái bưng bát"))
        assertTrue(actual, normalized.contains("tay phải cầm muôi"))
        assertTrue(actual, normalized.contains("vừa gọi vừa gõ"))
        assertTrue(actual, normalized.contains("tẩu thuốc lào"))
        assertTrue(actual, normalized.contains("bực bội mắng"))
        assertFalse(normalized.contains("hệ đang"))
        assertFalse(normalized.contains("đoan oản"))
        assertFalse(normalized.contains("chúc hang"))
        assertFalse(normalized.contains("đứa trẻ các"))
        assertFalse(normalized.contains("lão tử, triết gia"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun laoShiRenChapterTwoVillageTermsAreReadable() {
        val source = "“潘子，你去喊郑大筒！”\n" +
            "“好的，爷。”\n" +
            "郑大筒叫郑华民，是思源村的诊所大夫，也就是赤脚医生。\n" +
            "“雷子，你去喊刘瞎子。”\n" +
            "刘瞎子本名叫刘金霞。\n" +
            "崔桂英呵斥道：“细那康子没大没小的，要叫刘奶奶。”\n" +
            "郑大筒知道自己要让位了，走出屋门，恰好看见远处有一辆三轮车被骑着过来，车上坐着一个老太婆。\n" +
            "“好嘞，好嘞，让你受累了，受累了。”"

        val actual = QuickTranslationRepository().translate(
            text = source,
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("trịnh đại đồng"))
        assertTrue(actual, normalized.contains("trịnh hoa dân"))
        assertTrue(actual, normalized.contains("thôn tư nguyên"))
        assertTrue(actual, normalized.contains("bác sĩ phòng khám"))
        assertTrue(actual, normalized.contains("thầy thuốc chân đất"))
        assertTrue(actual, normalized.contains("lưu mù"))
        assertTrue(actual, normalized.contains("lưu kim hà"))
        assertTrue(actual, normalized.contains("xe ba bánh"))
        assertTrue(actual, normalized.contains("bà lão"))
        assertTrue(actual, normalized.contains("bà lưu"))
        assertTrue(actual, normalized.contains("làm phiền"))
        assertFalse(normalized.contains("khang tử"))
        assertFalse(normalized.contains("lưu mù lòa"))
        assertFalse(normalized.contains("1 cái bà lão"))
        assertFalse(normalized.contains("1 cái lão thái bà"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun laoShiRenChapterOneUsesKinshipAddressing() {
        val source = "奶，我也要肉。你们爹妈可一粒米没往奶这里交。" +
            "爷，下午撑船去摘莲蓬呗。" +
            "她男人李维汉也没去，借口出船了，其实人在家，不去的原因是不好意思；" +
            "毕竟已经让潘子雷子领着远子、虎子、石头五个孩子去吃席了，他这个大人再去吃相就难看了。" +
            "五个孩子不仅自己吃，还捎带拿了不少，尤其是那种饭桌上按人头分的硬菜；" +
            "李追远学着哥哥们那样，在身前铺桌子的红塑料纸上撕扯下一块，拿来包吃食。"

        val actual = QuickTranslationRepository().translate(
            text = source,
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("bà ơi, cháu cũng muốn ăn thịt"))
        assertTrue(actual, normalized.contains("cha mẹ các cháu"))
        assertTrue(actual, normalized.contains("một hạt gạo"))
        assertTrue(actual, normalized.contains("ông ơi"))
        assertTrue(actual, normalized.contains("chống thuyền"))
        assertTrue(actual, normalized.contains("lý duy hán"))
        assertTrue(actual, normalized.contains("phan tử"))
        assertTrue(actual, normalized.contains("lôi tử"))
        assertTrue(actual, normalized.contains("viễn tử"))
        assertTrue(actual, normalized.contains("hổ tử"))
        assertTrue(actual, normalized.contains("thạch đầu"))
        assertTrue(actual, normalized.contains("năm đứa trẻ"))
        assertTrue(actual, normalized.contains("ăn cỗ"))
        assertTrue(actual, normalized.contains("giấy nhựa đỏ"))
        assertTrue(actual, normalized.contains("đồ ăn"))
        assertFalse(actual, normalized.contains("sữa"))
        assertFalse(actual, normalized.contains("gia,"))
        assertFalse(actual, normalized.contains("bạn"))
        assertFalse(actual, normalized.contains("tôi cũng phải"))
        assertFalse(actual, normalized.contains("lý theo đuổi xa"))
        assertFalse(actual, normalized.contains("sấm tử"))
        assertFalse(actual, normalized.contains("5 cái đứa trẻ"))
        assertFalse(actual, normalized.contains("ăn tịch"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun quickTranslationPronounProfilesCanBeSelected() {
        val app = RuntimeEnvironment.getApplication()
        val repository = QuickTranslationRepository()

        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.ANCIENT.value,
        )
        val ancient = repository.translate(
            "夫君，你知道我在等你。他也来了，她却不在。",
            emptyList(),
        ).lowercase()
        assertTrue(ancient, ancient.contains("chàng"))
        assertTrue(ancient, ancient.contains("thiếp"))
        assertTrue(ancient, ancient.contains("hắn"))
        assertTrue(ancient, ancient.contains("cô"))
        assertFalse(ancient, ancient.contains("bạn"))

        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.MODERN.value,
        )
        val modern = repository.translate(
            "夫君，你知道我在等你。他也来了，她却不在。",
            emptyList(),
        ).lowercase()
        assertTrue(modern, modern.contains("bạn") || modern.contains("ngươi"))
        assertTrue(modern, modern.contains("tôi") || modern.contains("ta"))
        assertTrue(modern, modern.contains("hắn"))
        assertTrue(modern, modern.contains("cô"))
        assertFalse(modern, modern.contains("anh ấy"))
        assertFalse(modern, modern.contains("cô ấy"))

        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.WESTERN.value,
        )
        val western = repository.translate(
            "公爵阁下，你知道我在等你吗？他去了教堂，她留在庄园。",
            emptyList(),
        ).lowercase()
        assertTrue(western, western.contains("ngài") || western.contains("ngươi"))
        assertTrue(western, western.contains("tôi") || western.contains("ta"))
        assertTrue(western, western.contains("hắn"))
        assertTrue(western, western.contains("cô"))
        assertFalse(western, western.contains("ông ấy"))

        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.OFF.value,
        )
        val off = repository.translate(
            "夫君，你知道我在等你。他也来了。",
            emptyList(),
        ).lowercase()
        assertTrue(off, off.contains("bạn") || off.contains("ngươi"))
        assertTrue(off, off.contains("tôi") || off.contains("ta"))
        assertTrue(off, off.contains("anh ấy") || off.contains("hắn"))
        assertFalse(off, off.contains("chàng"))
    }

    @Test
    fun quickTranslationAutoPronounProfileDetectsAncientAndWesternContext() {
        val repository = QuickTranslationRepository()

        val ancient = repository.translate(
            "夫君，你知道我在等你。",
            emptyList(),
        ).lowercase()
        assertTrue(ancient, ancient.contains("chàng"))
        assertTrue(ancient, ancient.contains("thiếp"))

        val western = repository.translate(
            "公爵阁下，你知道我在等你吗？",
            emptyList(),
        ).lowercase()
        assertTrue(western, western.contains("ngài"))
        assertTrue(western, western.contains("tôi"))
    }

    @Test
    fun quickTranslationModernPronounsRespectRuralRoles() {
        val source = "李追远以前也被父母带去看过单位的文艺汇演，但昨日他受小黄莺表演的冲击不小。\n" +
            "终于，船行到家，李维汉将竹篙一丢，顾不得拴船绳，抱起李追远就跳下了船，只是他已很是疲惫。\n" +
            "郑大筒叫郑华民，是思源村的诊所大夫，因他喜欢拿大针筒故意吓唬孩子。\n" +
            "刘金霞得了白内障，眼睛看不大清楚了。她也就干脆将家里田租给他人种。\n" +
            "留下个李菊香带一个同样刚出生的闺女。她痛苦地侧身倒在地上。\n" +
            "崔桂英诧异道：“你这是要出去？”\n" +
            "孩子他奶，你快来看看孩子。孩子他爷已经去请人了。\n" +
            "潘子：“远子刚掉下去就被他爷拽起来。”\n" +
            "刘金霞：“你照顾伢儿吧，让他再睡一觉，醒了就好了。”\n" +
            "郑大筒说：“可能是其它问题。”\n" +
            "郑大筒点了点头，来时路上潘子对他说了些，此时，他只能嘱咐道：“到了晚上还不醒的话，明早就往镇上送吧。”\n" +
            "这年头，想将日子过得富余些还得靠其它营生，没男人怎么了，干嘛要这样作践自己。\n" +
            "李追远回过头，先看见的是一双红色高跟鞋。黑色的旗袍紧裹着她的身躯。她，很美。"

        val actual = QuickTranslationRepository().translate(
            text = source,
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("hôm qua hắn"))
        assertTrue(actual, normalized.contains("hắn đã rất") || normalized.contains("hắn rất"))
        assertTrue(actual, normalized.contains("hắn thích"))
        assertTrue(actual, normalized.contains("cô"))
        assertTrue(actual, normalized.contains("cô đau đớn") || normalized.contains("cô nghiêng"))
        assertTrue(actual, normalized.contains("ông đây là muốn") || normalized.contains("ông định"))
        assertTrue(actual, normalized.contains("bà nó"))
        assertTrue(actual, normalized.contains("ông nó"))
        assertTrue(actual, normalized.contains("bà chăm sóc") || normalized.contains("bà trông"))
        assertTrue(actual, normalized.contains("để nó ngủ thêm một giấc") || normalized.contains("để cậu ngủ thêm một giấc"))
        assertTrue(actual, normalized.contains("vấn đề khác"))
        assertTrue(actual, normalized.contains("hắn chỉ có thể dặn"))
        assertTrue(actual, normalized.contains("cô rất đẹp"))
        assertFalse(actual, normalized.contains("anh ấy thích cầm ống tiêm"))
        assertFalse(actual, normalized.contains("cô ấy cũng liền"))
        assertFalse(actual, normalized.contains("bà ấy đau đớn"))
        assertFalse(actual, normalized.contains("bà ấy rất đẹp"))
        assertFalse(actual, normalized.contains("cậu chỉ có thể dặn"))
        assertFalse(actual, normalized.contains("ông ấy"))
        assertFalse(actual, normalized.contains("anh ấy"))
        assertFalse(actual, normalized.contains("cô ấy"))
        assertFalse(actual, normalized.contains("cô ta"))
        assertFalse(actual, normalized.contains("bạn đây là muốn"))
        assertFalse(actual, normalized.contains("đứa nhỏ anh ấy sữa"))
        assertFalse(actual, normalized.contains("đứa nhỏ anh ấy gia"))
        assertFalse(actual, normalized.contains("bạn sống thế nào"))
        assertFalse(actual, normalized.contains("có chuyện gì vậy"))
        assertFalse(actual, normalized.contains("bạn đang làm gì"))
        assertFalse(actual, normalized.contains("của nó nó hỏi đề"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun pluralSuffixDoesNotRenderAsVietnameseSuffix() {
        val actual = QuickTranslationRepository().translate(
            text = "孩子们拿着碗。",
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertFalse(actual, normalized.contains("đứa trẻ các"))
        assertFalse(actual, normalized.contains("trẻ em các"))
        assertTrue(
            actual,
            normalized.contains("trẻ con") ||
                normalized.contains("trẻ em") ||
                normalized.contains("bọn nhỏ") ||
                normalized.contains("các đứa trẻ"),
        )
    }

    @Test
    fun definitionLikeCvdictTargetDoesNotLeakIntoDialogue() {
        val actual = QuickTranslationRepository().translate(
            text = "半大小子吃垮老子。",
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertFalse(actual, normalized.contains("triết gia"))
        assertFalse(actual, normalized.contains("người sáng lập đạo giáo"))
        assertTrue(actual, normalized.contains("trai choai"))
        assertTrue(actual, normalized.contains("nhà cha"))
    }

    @Test
    fun fallbackStillProtectsMarkupUrlsAndRemovesCjk() {
        val actual = QuickTranslationRepository().translate(
            text = "<p>龙 https://example.com/a?q=1 {{value}}</p>",
            projectTerms = emptyList(),
        )

        assertTrue(actual, actual.contains("https://example.com/a?q=1"))
        assertTrue(actual, actual.contains("{{value}}"))
        assertTrue(actual, actual.startsWith("<p>"))
        assertTrue(actual, actual.endsWith("</p>"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun bundledGrammarRulesRenderCoreConnectorsInVietnameseOrder() {
        val actual = QuickTranslationRepository().translate(
            text = "因为天黑所以回家。如果下雨就停工。虽然累但是继续。",
            projectTerms = listOf(
                DictPair("天黑", "trời tối"),
                DictPair("回家", "về nhà"),
                DictPair("下雨", "trời mưa"),
                DictPair("停工", "nghỉ làm"),
                DictPair("累", "mệt"),
                DictPair("继续", "tiếp tục"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("vì trời tối nên về nhà"))
        assertTrue(actual, normalized.contains("nếu trời mưa thì nghỉ làm"))
        assertTrue(actual, normalized.contains("tuy mệt nhưng tiếp tục"))
        assertFalse(normalized.contains("bởi vì trời tối cho nên"))
        assertFalse(normalized.contains("nhưng là"))
    }

    @Test
    fun bundledGrammarRulesRenderBaBeiAndComparisons() {
        val actual = QuickTranslationRepository().translate(
            text = "他把门打开。门被他打开。他比我高。",
            projectTerms = listOf(
                DictPair("他", "anh ấy", QuickDictionaryType.PRONOUN),
                DictPair("我", "tôi", QuickDictionaryType.PRONOUN),
                DictPair("门", "cửa"),
                DictPair("打开", "mở"),
                DictPair("高", "cao"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("hắn mở cửa"))
        assertTrue(actual, normalized.contains("cửa bị hắn mở"))
        assertTrue(actual, normalized.contains("hắn cao hơn tôi"))
        assertFalse(normalized.contains("đem cửa mở"))
        assertFalse(normalized.contains("bị hắn mở cửa"))
    }

    @Test
    fun bundledGrammarRulesRenderParticlesAspectAndQuestions() {
        val actual = QuickTranslationRepository().translate(
            text = "慢慢地走。跑得很快。你去吗？他来过。拿着碗。",
            projectTerms = listOf(
                DictPair("慢慢", "chậm rãi"),
                DictPair("走", "đi"),
                DictPair("跑", "chạy"),
                DictPair("很快", "rất nhanh"),
                DictPair("你", "bạn", QuickDictionaryType.PRONOUN),
                DictPair("去", "đi"),
                DictPair("他", "anh ấy", QuickDictionaryType.PRONOUN),
                DictPair("来", "đến"),
                DictPair("拿", "cầm"),
                DictPair("碗", "bát"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("đi chậm rãi"))
        assertTrue(actual, normalized.contains("chạy đến mức rất nhanh"))
        assertTrue(actual, normalized.contains("bạn đi không"))
        assertTrue(actual, normalized.contains("hắn đã từng đến"))
        assertTrue(actual, normalized.contains("cầm bát"))
        assertFalse(normalized.contains("chậm rãi địa"))
        assertFalse(normalized.contains("đắc"))
        assertFalse(normalized.contains("mạ"))
    }

    @Test
    fun structuredGrammarReadsUnitsMoneyDatesTimesAndDecimals() {
        val actual = QuickTranslationRepository().translate(
            text = "三点一四。三点五公里。二十美元。五个人。两个小时。" +
                "2026年7月27日星期一。下午三点二十分十五秒。",
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("3.14"))
        assertTrue(actual, normalized.contains("3.5 km"))
        assertTrue(actual, normalized.contains("20 đô la mỹ"))
        assertTrue(actual, normalized.contains("5 người"))
        assertTrue(actual, normalized.contains("2 giờ"))
        assertTrue(actual, normalized.contains("ngày 27 tháng 7 năm 2026"))
        assertTrue(actual, normalized.contains("thứ hai"))
        assertTrue(actual, normalized.contains("3 giờ 20 phút 15 giây"))
        assertTrue(actual, normalized.contains("chiều"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun qt2025RuntimeAppliesNameAndNumberLuatNhanRules() {
        val actual = QuickTranslationRepository().translate(
            text = "把主意打在了司马俊一的身上。方圆三五里的范围内。百分之三点一四。",
            projectTerms = emptyList(),
        ).lowercase()

        assertTrue(actual, actual.contains("có mưu đồ xấu với tư mã tuấn nhất"))
        assertTrue(actual, actual.contains("trong phạm vi bán kính 3-5 dặm"))
        assertTrue(actual, actual.contains("3.14%"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun qt2025RuntimeCombinesSurnameAndTitleBeforeHanVietFallback() {
        val actual = QuickTranslationRepository().translate(
            text = "司马副掌门走来了",
            projectTerms = emptyList(),
        ).lowercase()

        assertTrue(actual, actual.startsWith("tư mã phó chưởng môn"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun qt2025NumberRuleUsesAndroidSafeChapterContextDetection() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("第{s}章" to "chương {s}"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )

        val match = requireNotNull(
            runtime.matchAt(
                text = "第一千章",
                offset = 0,
                resolveName = { _, _ -> null },
                containsExact = { false },
            )
        )

        assertEquals("chương 1000", match.translation)
    }

    @Test
    fun qt2025NameRuleAtEndUsesLongestKnownNameWithoutConsumingFollowingText() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("很想{n}" to "rất nhớ {n}"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )
        val text = "很想司马俊一走"

        val match = requireNotNull(
            runtime.matchAt(
                text = text,
                offset = 0,
                resolveName = { start, endExclusive ->
                    text.substring(start, endExclusive)
                        .takeIf { it == "司马俊一" }
                        ?.let { "Tư Mã Tuấn Nhất" }
                },
                containsExact = { false },
            )
        )

        assertEquals("rất nhớ Tư Mã Tuấn Nhất", match.translation)
        assertEquals("很想司马俊一".length, match.endExclusive)
    }

    @Test
    fun qt2025NumberRulesTreatDictionaryWhitespaceAsOptional() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("{s}-{s} 年" to "năm {1}-{2}"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )

        val match = requireNotNull(
            runtime.matchAt(
                text = "3-5年",
                offset = 0,
                resolveName = { _, _ -> null },
                containsExact = { false },
            )
        )

        assertEquals("năm 3-5", match.translation)
    }

    @Test
    fun qt2025DateRuleUsesMungForSingleDigitDay() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("{s}年{s}月{s}号" to "ngày {3} tháng {2} năm {1}"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )

        val match = requireNotNull(
            runtime.matchAt(
                text = "2026年7月3号",
                offset = 0,
                resolveName = { _, _ -> null },
                containsExact = { false },
            )
        )

        assertEquals("mùng 3 tháng 7 năm 2026", match.translation)
    }

    @Test
    fun qt2025NormalizesPreposedApproximationBeforeNumberRules() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("{s}(多|余)里" to "hơn {s} dặm"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )

        val match = requireNotNull(
            runtime.matchAt(
                text = "余百里",
                offset = 0,
                resolveName = { _, _ -> null },
                containsExact = { false },
            )
        )

        assertEquals("hơn 100 dặm", match.translation)
        assertEquals("余百里".length, match.endExclusive)
    }

    @Test
    fun qt2025QuantityLiangRuleDoesNotConsumePrefixOfLongerNumber() {
        val runtime = requireNotNull(
            Qt2025Runtime.create(
                rules = listOf("{s}两" to "{s} lượng"),
                surnames = emptyList(),
                suffixes = emptyList(),
            )
        )

        val match = runtime.matchAt(
            text = "一两三",
            offset = 0,
            resolveName = { _, _ -> null },
            containsExact = { false },
        )

        assertEquals(null, match)
    }

    @Test
    fun structuredGrammarReadsAdministrativePlaceHierarchy() {
        val actual = QuickTranslationRepository().translate(
            text = "中国广东省深圳市南山区科技路9号",
            projectTerms = listOf(
                DictPair("中国", "Trung Quốc", QuickDictionaryType.NAME),
                DictPair("广东", "Quảng Đông", QuickDictionaryType.NAME),
                DictPair("深圳", "Thâm Quyến", QuickDictionaryType.NAME),
                DictPair("南山", "Nam Sơn", QuickDictionaryType.NAME),
                DictPair("科技", "Khoa Kỹ", QuickDictionaryType.NAME),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("trung quốc"))
        assertTrue(actual, normalized.contains("tỉnh quảng đông"))
        assertTrue(actual, normalized.contains("thành phố thâm quyến"))
        assertTrue(actual, normalized.contains("quận nam sơn"))
        assertTrue(actual, normalized.contains("đường khoa kỹ"))
        assertTrue(actual, normalized.contains("số 9"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun structuredGrammarReadsChapterVolumeOrderVariantsAndDeduplicatesHeadings() {
        val actual = QuickTranslationRepository().translate(
            text = "第001章 开端\n第一卷\n卷二\n正文卷\n上卷\n番外篇\n第第十回\n第十二节\n" +
                "第1章 第1章\n卷一 卷一\n捞尸人第十章开端\n捞尸人 chương 11 开端",
            projectTerms = listOf(DictPair("开端", "mở đầu")),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("chương 1 mở đầu"))
        assertTrue(actual, normalized.contains("quyển 1"))
        assertTrue(actual, normalized.contains("quyển 2"))
        assertTrue(actual, normalized.contains("chính văn"))
        assertTrue(actual, normalized.contains("quyển thượng"))
        assertTrue(actual, normalized.contains("ngoại truyện"))
        assertTrue(actual, normalized.contains("hồi 10"))
        assertTrue(actual, normalized.contains("tiết 12"))
        assertFalse(normalized.contains("chương 1 chương 1"))
        assertFalse(normalized.contains("quyển 1 quyển 1"))
        assertTrue(actual, normalized.contains("chương 10 mở đầu"))
        assertTrue(actual, normalized.contains("chương 11 mở đầu"))
        assertFalse(actual, actual.codePoints().anyMatch(::isQuickDictionaryCjkForTest))
    }

    @Test
    fun bundledQt2020LookupCreatesTypedTerms() {
        val actual = QuickTranslationRepository().translate(
            text = "01\u6708 01 \u53F7",
            projectTerms = emptyList(),
        )

        assertTrue(actual.isNotBlank())
    }

    @Test
    fun postProcessorCapitalizesSentencesWithoutChangingWhitespace() {
        val source = "  xin chào\tthế giới\n\n  câu mới.   câu sau\nđoạn mới  "

        val actual = QuickTranslationTextPostProcessor.capitalizeSentenceStarts(source)

        assertEquals(
            "  Xin chào\tthế giới\n\n  Câu mới.   Câu sau\nĐoạn mới  ",
            actual,
        )
    }

    @Test
    fun postProcessorDoesNotRewriteProtectedMarkupTokens() {
        val source = "\uE600QT00000\uE601xin chào.\n\uE600QT00001\uE601đoạn mới"

        val actual = QuickTranslationTextPostProcessor.capitalizeSentenceStarts(source)

        assertEquals(
            "\uE600QT00000\uE601Xin chào.\n\uE600QT00001\uE601Đoạn mới",
            actual,
        )
    }

    @Test
    fun wordSeparatorIsInsertedAfterInlineDelimitersAndAdjacentWords() {
        assertTrue(QuickTranslationTextPostProcessor.needsWordSeparator(',', 'T'))
        assertTrue(QuickTranslationTextPostProcessor.needsWordSeparator('|', 'p'))
        assertTrue(QuickTranslationTextPostProcessor.needsWordSeparator('+', 'p'))
        assertTrue(QuickTranslationTextPostProcessor.needsWordSeparator('a', 'b'))
    }

    @Test
    fun wordSeparatorPreservesExistingWhitespaceAndOpeningPunctuation() {
        assertFalse(QuickTranslationTextPostProcessor.needsWordSeparator(' ', 'b'))
        assertFalse(QuickTranslationTextPostProcessor.needsWordSeparator('\n', 'b'))
        assertFalse(QuickTranslationTextPostProcessor.needsWordSeparator('(', 'b'))
        assertFalse(QuickTranslationTextPostProcessor.needsWordSeparator(null, 'b'))
    }

    @Test
    fun numericSpacingCollapsesSplitChineseDigitsAndDecimalPunctuation() {
        assertEquals(
            "343,3 vạn chữ · chương 12",
            QuickTranslationTextPostProcessor.normalizeNumericSpacing(
                "3 4 3, 3 vạn chữ · chương 1 2"
            ),
        )
    }

    @Test
    fun projectTermWinsOverStructuredDateRuleAtSameOffset() {
        val actual = QuickTranslationRepository().translate(
            text = "01\u6708 01 \u53F7",
            projectTerms = listOf(DictPair("01\u6708", "thang tuy chon")),
        )

        assertTrue(actual, actual.startsWith("Thang tuy chon"))
    }

    @Test
    fun projectTermWithDigitsAndHanMatchesExactly() {
        val projectTerms = listOf(DictPair("01\u6708", "thang tuy chon"))
        val actual = QuickTranslationRepository().translate(
            text = "01\u6708",
            projectTerms = projectTerms,
        )

        assertEquals("Thang tuy chon", actual)
    }

    @Test
    fun projectTermIsNotSwallowedByLongerBundledPhrase() {
        val actual = QuickTranslationRepository().translate(
            text = "\u4E2D\u534E\u4EBA\u6C11\u5171\u548C\u56FD",
            projectTerms = listOf(DictPair("\u4E2D\u534E", "custom china")),
        )

        assertTrue(actual, actual.startsWith("Custom china"))
        assertFalse(actual.contains("C\u1ED9ng h\u00F2a Nh\u00E2n d\u00E2n Trung Hoa"))
    }

    @Test
    fun targetChooserSkipsHanAlternativeWhenVietnameseAlternativeExists() {
        assertEquals(
            "my system",
            cleanQuickDictionaryTarget("\u7CFB\u7EDF/my system"),
        )
        val actual = QuickTranslationRepository().translate(
            text = "\u7CFB\u7EDF",
            projectTerms = listOf(DictPair("\u7CFB\u7EDF", "\u7CFB\u7EDF/my system")),
        )

        assertEquals("My system", actual)
    }

    @Test
    fun targetChooserSplitsLegacyPipeAndEqualsAlternatives() {
        assertEquals(
            "t\u00ECnh nh\u00E2n",
            cleanQuickDictionaryTarget("nh\u00E2n=t\u00ECnh nh\u00E2n"),
        )
        assertEquals(
            "\u00FD d\u00E2m",
            cleanQuickDictionaryTarget("d\u00E2m=\u00FD d\u00E2m"),
        )
        assertEquals(
            "h\u00FAt",
            cleanQuickDictionaryTarget("h\u00FAt|h\u1EA5p|h\u00EDt"),
        )
        assertEquals(
            "kh\u00F4ng ngh\u0129 th\u00EAm",
            cleanQuickDictionaryTarget(
                "kh\u00F4ng ngh\u0129 th\u00EAm|kh\u00F4ng ngh\u0129 n\u1EEFa"
            ),
        )
        assertEquals(
            "d\u1EA5u b\u1EB1ng =",
            cleanQuickDictionaryTarget("d\u1EA5u b\u1EB1ng ="),
        )
    }

    @Test
    fun attributiveDeChoosesModifierLocationAndPossessionOrder() {
        val actual = QuickTranslationRepository().translate(
            text = "年轻的女调查员。嘴角的笑容。张元清的房间。",
            projectTerms = listOf(
                DictPair("年轻", "trẻ"),
                DictPair("女调查员", "nữ điều tra viên"),
                DictPair("嘴角", "khóe miệng"),
                DictPair("笑容", "nụ cười"),
                DictPair("张元清", "Trương Nguyên Thanh"),
                DictPair("房间", "phòng"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("nữ điều tra viên trẻ"))
        assertTrue(actual, normalized.contains("nụ cười ở khóe miệng"))
        assertTrue(actual, normalized.contains("phòng của trương nguyên thanh"))
        assertFalse(normalized.contains("nữ điều tra viên của trẻ"))
        assertFalse(normalized.contains("nụ cười của khóe miệng"))
    }

    @Test
    fun leadingHeadPhraseCapturesNounPhraseAndReordersClothingModifier() {
        val actual = QuickTranslationRepository().translate(
            text = "为首的黑衣调查员走来。",
            projectTerms = listOf(
                DictPair("黑衣", "trang phục màu đen"),
                DictPair("调查员", "điều tra viên"),
                DictPair("走来", "đi tới"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("điều tra viên áo đen dẫn đầu"))
        assertTrue(normalized.contains("đi tới"))
        assertFalse(normalized.contains("trang phục màu đen điều tra viên"))
        assertFalse(normalized.contains("cầm đầu"))
        assertFalse(normalized.contains("quý ông"))
    }

    @Test
    fun projectLuatNhanSupportsPosConstrainedSlots() {
        val actual = QuickTranslationRepository().translate(
            text = "漆黑的长刀。",
            projectTerms = listOf(
                DictPair(
                    "{0:adj}的{1:noun}",
                    "{1} {0}",
                    QuickDictionaryType.LUAT_NHAN,
                ),
                DictPair("漆黑", "đen kịt"),
                DictPair("长刀", "trường đao"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(normalized.contains("trường đao đen kịt"))
        assertFalse(normalized.contains("của"))
    }

    @Test
    fun indexedLeadingSlotTemplateStillMatchesPersonTitle() {
        val actual = QuickTranslationRepository().translate(
            text = "\u53f6\u957f\u751f\u5148\u751f\u3002",
            projectTerms = listOf(
                DictPair(
                    "{0:person}\u5148\u751f",
                    "ti\u00ean sinh {0}",
                    QuickDictionaryType.LUAT_NHAN,
                ),
                DictPair(
                    "\u53f6\u957f\u751f",
                    "Di\u1ec7p Tr\u01b0\u1eddng Sinh",
                    QuickDictionaryType.NAME,
                ),
            ),
        )

        assertEquals("Ti\u00ean sinh Di\u1ec7p Tr\u01b0\u1eddng Sinh.", actual)
    }

    @Test
    fun ahoCorasickTokenPlanReordersPersonModifierWithoutSpecificPhrasePatch() {
        val actual = QuickTranslationRepository().translate(
            text = "黑衣调查员走来。",
            projectTerms = listOf(
                DictPair("黑衣", "trang phục màu đen"),
                DictPair("调查员", "điều tra viên"),
                DictPair("走来", "đi tới"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("điều tra viên áo đen"))
        assertTrue(actual, normalized.contains("đi tới"))
        assertFalse(normalized.contains("trang phục màu đen điều tra viên"))
    }

    @Test
    fun dynamicGrammarSlotCapturesAhoSegmentedPersonPhrase() {
        val actual = QuickTranslationRepository().translate(
            text = "黑衣调查员对张元清说。",
            projectTerms = listOf(
                DictPair(
                    "{0:person}对{1:person}说",
                    "{0} nói với {1}",
                    QuickDictionaryType.LUAT_NHAN,
                ),
                DictPair("黑衣", "trang phục màu đen"),
                DictPair("调查员", "điều tra viên"),
                DictPair("张元清", "Trương Nguyên Thanh"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("điều tra viên áo đen nói với trương nguyên thanh"))
        assertFalse(normalized.contains("trang phục màu đen điều tra viên"))
        assertFalse(normalized.contains("đối trương nguyên thanh nói"))
    }

    @Test
    fun ahoGrammarSearchExploresShorterModifierBeforeLongProjectPersonTerm() {
        val actual = QuickTranslationRepository().translate(
            text = "\u9ED1\u8863\u8C03\u67E5\u5458\u5BF9\u5F20\u5143\u6E05\u8BF4\u3002",
            projectTerms = listOf(
                DictPair(
                    "{0:person}\u5BF9{1:person}\u8BF4",
                    "{0} n\u00F3i v\u1EDBi {1}",
                    QuickDictionaryType.LUAT_NHAN,
                ),
                DictPair(
                    "\u9ED1\u8863\u8C03\u67E5\u5458",
                    "h\u1EAFc y \u0111i\u1EC1u tra vi\u00EAn",
                ),
                DictPair("\u9ED1\u8863", "trang ph\u1EE5c m\u00E0u \u0111en"),
                DictPair("\u8C03\u67E5\u5458", "\u0111i\u1EC1u tra vi\u00EAn"),
                DictPair("\u5F20\u5143\u6E05", "Tr\u01B0\u01A1ng Nguy\u00EAn Thanh"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(
            actual,
            normalized.contains(
                "\u0111i\u1EC1u tra vi\u00EAn \u00E1o \u0111en n\u00F3i v\u1EDBi " +
                    "tr\u01B0\u01A1ng nguy\u00EAn thanh"
            ),
        )
        assertFalse(normalized.contains("h\u1EAFc y \u0111i\u1EC1u tra vi\u00EAn n\u00F3i"))
    }

    @Test
    fun leadingHeadPhraseSupportsBarePrefixBeforeNounPhrase() {
        val actual = QuickTranslationRepository().translate(
            text = "\u4E3A\u9996\u8C03\u67E5\u5458\u8D70\u6765\u3002",
            projectTerms = listOf(
                DictPair("\u8C03\u67E5\u5458", "\u0111i\u1EC1u tra vi\u00EAn"),
                DictPair("\u8D70\u6765", "\u0111i t\u1EDBi"),
            ),
        )
        val normalized = actual.lowercase()

        assertTrue(actual, normalized.contains("\u0111i\u1EC1u tra vi\u00EAn d\u1EABn \u0111\u1EA7u"))
        assertTrue(actual, normalized.contains("\u0111i t\u1EDBi"))
        assertFalse(normalized.contains("c\u1EA7m \u0111\u1EA7u \u0111i\u1EC1u tra vi\u00EAn"))
    }

    @Test
    fun projectAhoTrieHandlesLargeCaseInsensitiveTermSet() {
        val projectTerms = (0 until 1_200).map { index ->
            DictPair("\u8BCD\u6761$index", "term $index")
        } + DictPair("ABC\u7CFB\u7EDF", "custom engine")

        val actual = QuickTranslationRepository().translate(
            text = "abc\u7CFB\u7EDF",
            projectTerms = projectTerms,
        )

        assertEquals("Custom engine", actual)
    }

    @Test
    fun translationRestoresLargeProtectedTokenWorkload() {
        val repository = QuickTranslationRepository()
        repository.warmUp()
        val source = (0 until 400).joinToString(separator = "") { index ->
            "\u7CFB\u7EDF https://example.com/$index?q=$index {{value_$index}}\n"
        }

        val actual = repository.translate(
            text = source,
            projectTerms = listOf(DictPair("\u7CFB\u7EDF", "engine")),
            customPhonetics = emptyList(),
        )

        assertFalse(actual.contains("\uE600QT"))
        assertTrue(actual.startsWith("Engine https://example.com/0?q=0 {{value_0}}"))
        assertTrue(actual.contains("Engine https://example.com/399?q=399 {{value_399}}"))
    }

    @Test
    fun directionalComplementsClassifiersLocationsAndSpeechStayReadable() {
        val actual = QuickTranslationRepository().translate(
            text = buildString {
                append("\u5979\u8FC7\u6765\u4E86\uFF0C\u8D8A\u6765\u8D8A\u8FD1\uFF0C")
                append("\u4ECE\u753B\u4E2D\u8D70\u51FA\uFF0C\u53C8\u8D70\u5411\u753B\u91CC\u3002")
                append("\u5979\u4F38\u51FA\u4E86\u624B\uFF0C\u4E24\u53EA\u624B\u6293\u4F4F\u4E86\u4ED6\u7684\u80A9\u8180\u3002\n")
                append("\u4ED6\u7741\u5F00\u773C\uFF0C\u7AD9\u8D77\u8EAB\uFF0C")
                append("\u4ECE\u5C4B\u91CC\u8D70\u51FA\u6765\uFF0C\u8F7B\u624B\u8F7B\u811A\u5730\u8D70\u5230\u8239\u8FB9\u3002\n")
                append("\u6C34\u9762\u4E0B\u6709\u4E00\u4E2A\u4EBA\uFF0C\u5934\u4E0D\u65F6\u6D6E\u51FA\u6C34\u9762\uFF0C")
                append("\u9010\u6E10\u9760\u8FD1\uFF0C\u6700\u540E\u505C\u4E0B\u4E86\u3002\n")
                append("\u90D1\u5927\u7B52\u6536\u62FE\u597D\u4E1C\u897F\uFF0C\u70B9\u70B9\u5934\uFF0C")
                append("\u6CA1\u597D\u6C14\u5730\u8BF4\u9053\uFF1A\u201C\u6CA1\u5565\u4E8B\u513F\u3002\u201D")
            },
            projectTerms = listOf(
                DictPair("\u90D1\u5927\u7B52", "Tr\u1ECBnh \u0110\u1EA1i \u0110\u1ED3ng"),
                DictPair("\u80A9\u8180", "vai"),
                DictPair("\u5C4B\u91CC", "trong nh\u00E0"),
                DictPair("\u8D70\u5230", "\u0111i \u0111\u1EBFn"),
                DictPair("\u6293\u4F4F", "n\u1EAFm l\u1EA5y"),
                DictPair("\u4E0D\u65F6", "th\u1EC9nh tho\u1EA3ng"),
            ),
        )
        val normalized = actual.lowercase()

        listOf(
            "c\u00F4 \u0111\u00E3 \u0111\u1EBFn",
            "ng\u00E0y c\u00E0ng g\u1EA7n",
            "b\u01B0\u1EDBc ra kh\u1ECFi b\u1EE9c tranh",
            "\u0111i v\u00E0o trong tranh",
            "c\u00F4 \u0111\u01B0a tay ra",
            "hai b\u00E0n tay",
            "h\u1EAFn m\u1EDF m\u1EAFt",
            "\u0111\u1EE9ng d\u1EADy",
            "r\u00F3n r\u00E9n",
            "b\u00EAn m\u1EA1n thuy\u1EC1n",
            "d\u01B0\u1EDBi m\u1EB7t n\u01B0\u1EDBc",
            "n\u1ED5i l\u00EAn m\u1EB7t n\u01B0\u1EDBc",
            "d\u1EA7n \u0111\u1EBFn g\u1EA7n",
            "cu\u1ED1i c\u00F9ng d\u1EEBng l\u1EA1i",
            "thu d\u1ECDn \u0111\u1ED3 \u0111\u1EA1c xong",
            "g\u1EADt \u0111\u1EA7u",
            "b\u1EF1c b\u1ED9i n\u00F3i",
            "kh\u00F4ng sao",
        ).forEach { expected ->
            assertTrue("Missing '$expected' in: $actual", normalized.contains(expected))
        }
        listOf(
            "\u0111\u00E3 t\u1EEBng \u0111\u1EBFn",
            "du\u1ED7i v\u1EDBi",
            "2 con tay",
            "0 con",
            "4 tu\u1EA7n",
            "\u0111\u00F4ng v\u00E0 t\u00E2y",
            "tr\u1EE3n m\u1EDF",
        ).forEach { rejected ->
            assertFalse("Unexpected '$rejected' in: $actual", normalized.contains(rejected))
        }
    }

    @Test
    fun productiveAspectDegreeAndSequenceTemplatesPreserveVietnameseOrder() {
        val actual = QuickTranslationRepository().translate(
            text = "\u9053\u8DEF\u8D8A\u6765\u8D8A\u7A84\u3002\u4ED6\u9010\u6E10\u9760\u8FD1\uFF0C" +
                "\u4E0D\u518D\u540E\u9000\u3002\u521A\u8FDB\u95E8\u5C31\u5750\u4E0B\u3002",
            projectTerms = listOf(
                DictPair("\u9053\u8DEF", "con \u0111\u01B0\u1EDDng"),
                DictPair("\u7A84", "h\u1EB9p"),
                DictPair("\u9760\u8FD1", "\u0111\u1EBFn g\u1EA7n"),
                DictPair("\u540E\u9000", "l\u00F9i l\u1EA1i"),
                DictPair("\u8FDB\u95E8", "v\u00E0o c\u1EEDa"),
                DictPair("\u5750\u4E0B", "ng\u1ED3i xu\u1ED1ng"),
            ),
        ).lowercase()

        assertTrue(actual, actual.contains("con \u0111\u01B0\u1EDDng ng\u00E0y c\u00E0ng h\u1EB9p"))
        assertTrue(actual, actual.contains("h\u1EAFn d\u1EA7n \u0111\u1EBFn g\u1EA7n"))
        assertTrue(actual, actual.contains("kh\u00F4ng c\u00F2n l\u00F9i l\u1EA1i"))
        assertTrue(actual, actual.contains("v\u1EEBa v\u00E0o c\u1EEDa \u0111\u00E3 ng\u1ED3i xu\u1ED1ng"))
    }

    @Test
    fun chapterNarrativeCorrectionsSurviveWiderGrammarMatches() {
        val actual = QuickTranslationRepository().translate(
            text = buildString {
                append("\u5979\u50CF\u662F\u753B\u91CC\u7684\u4EBA\u3002")
                append("\u6B64\u523B\uFF0C\u4ED6\u5FD8\u8BB0\u4E86\u81EA\u5DF1\u7684\u5904\u5883\uFF0C")
                append("\u5FFD\u7565\u4E86\u65E0\u6CD5\u547C\u5438\u7684\u6050\u614C\u548C\u53E3\u9F3B\u91CC\u4E0D\u65AD\u545B\u8FDB\u7684\u6C34\u3002\n")
                append("\u5979\u626D\u7740\u8170\u5531\u7740\u6B4C\uFF0C\u767D\u51C0\u5F97\u5982\u540C\u4E00\u4E2A\u74F7\u5A03\u3002")
                append("\u6C1B\u56F4\u611F\u88AB\u626D\u66F2\uFF0C\u50CF\u662F\u4E00\u4E2A\u6253\u4E86\u9EBB\u9189\u9000\u53BB\u6548\u679C\u7684\u4EBA\u3002\n")
                append("\u4EE5\u524D\u5728\u5B66\u6821\u91CC\u73A9\u8FC7\u7684\u62D4\u6CB3\uFF0C\u4E0D\u8FC7\u8FD9\u6B21\u4ED6\u662F\u7EF3\u5B50\u3002")
                append("\u8EAB\u4E0A\u80CC\u7740\u7AF9\u7BD3\uFF0C\u6B7B\u6C89\u6B7B\u6C89\u3002\n")
                append("\u4ED6\u6CA1\u518D\u6389\u94FE\u5B50\uFF0C\u4F7F\u51FA\u5403\u5976\u7684\u52B2\u6491\u7BD9\u3002")
                append("\u4ED6\u60CA\u6050\u5730\u6307\u5411\u524D\u65B9\uFF0C\u4E00\u56E2\u9ED1\u8272\u5934\u53D1\u8DDF\u7740\u5411\u8FD9\u91CC\u8FC7\u6765\u3002\n")
                append("\u201C\u6709\u6C14\u513F\u4E0D\uFF01\u201D\u201C\u7ED9\u5C0F\u8FDC\u4FAF\u62CD\u62CD\u80CC\u3002\u201D")
                append("\u4F46\u4ED6\u4F9D\u65E7\u6CA1\u6709\u9192\u3002")
            },
            projectTerms = emptyList(),
        )
        val normalized = actual.lowercase()

        listOf(
            "nh\u01B0 ng\u01B0\u1EDDi trong tranh",
            "qu\u00EAn m\u1EA5t t\u00ECnh c\u1EA3nh c\u1EE7a m\u00ECnh",
            "n\u1ED7i ho\u1EA3ng s\u1EE3 v\u00EC kh\u00F4ng th\u1EC3 th\u1EDF",
            "n\u01B0\u1EDBc li\u00EAn t\u1EE5c s\u1EB7c v\u00E0o mi\u1EC7ng m\u0169i",
            "v\u1EEBa l\u1EAFc eo v\u1EEBa h\u00E1t",
            "tr\u1EAFng tr\u1EBBo nh\u01B0 m\u1ED9t b\u00FAp b\u00EA s\u1EE9",
            "b\u1EA7u kh\u00F4ng kh\u00ED",
            "ng\u01B0\u1EDDi v\u1EEBa h\u1EBFt thu\u1ED1c m\u00EA",
            "tr\u00F2 k\u00E9o co t\u1EEBng ch\u01A1i \u1EDF tr\u01B0\u1EDDng tr\u01B0\u1EDBc \u0111\u00E2y",
            "h\u1EAFn l\u1EA1i l\u00E0 s\u1EE3i d\u00E2y",
            "\u0111eo g\u00F9i tre tr\u00EAn l\u01B0ng",
            "n\u1EB7ng tr\u0129u",
            "kh\u00F4ng c\u00F2n l\u00E0m h\u1ECFng vi\u1EC7c",
            "d\u00F9ng h\u1EBFt s\u1EE9c b\u00ECnh sinh",
            "ho\u1EA3ng s\u1EE3 ch\u1EC9 v\u1EC1 ph\u00EDa tr\u01B0\u1EDBc",
            "m\u1EDB t\u00F3c \u0111en",
            "\u0111u\u1ED5i theo v\u1EC1 ph\u00EDa n\u00E0y",
            "c\u00F2n th\u1EDF kh\u00F4ng",
            "v\u1ED7 l\u01B0ng",
            "v\u1EABn ch\u01B0a t\u1EC9nh",
        ).forEach { expected ->
            assertTrue("Missing '$expected' in: $actual", normalized.contains(expected))
        }
        listOf(
            "h\u1ECDa ng\u01B0\u1EDDi \u1EDF trong",
            "tr\u1EE5c d\u1EA7n d\u1EA7n",
            "c\u00F3 kh\u00ED m\u00E0 kh\u00F4ng",
            "v\u1ED7 v\u1ED7 \u0111\u1ECDc",
            "nh\u01B0 tr\u01B0\u1EDBc",
        ).forEach { rejected ->
            assertFalse("Unexpected '$rejected' in: $actual", normalized.contains(rejected))
        }
    }

    @Test
    fun reviewedNameRepairsGenericProjectPhraseButTypedProjectNameStillWins() {
        val repository = QuickTranslationRepository()
        val genericProjectOutput = repository.translate(
            text = "李维汉就是抓着这竹篓向上发力。",
            projectTerms = listOf(
                DictPair("李维汉", "Lý Huy Hán", QuickDictionaryType.VIETPHRASE),
            ),
        )
        val typedProjectOutput = repository.translate(
            text = "李维汉朝那边看去。",
            projectTerms = listOf(
                DictPair("李维汉", "Lý Vị Hán", QuickDictionaryType.NAME),
            ),
        )

        assertTrue(genericProjectOutput, genericProjectOutput.contains("Lý Duy Hán"))
        assertFalse(genericProjectOutput, genericProjectOutput.contains("Lý Huy Hán"))
        assertTrue(typedProjectOutput, typedProjectOutput.contains("Lý Vị Hán"))
    }

    @Test
    fun projectNameAwareNarrativeTemplatesPreserveNamesAndVietnameseOrder() {
        val actual = QuickTranslationRepository().translate(
            text = buildString {
                append("再搭配小黄莺的仪态动作。")
                append("他受小黄莺表演的冲击不比哥哥弟弟们小。")
                append("在那简陋棚子下的小黄莺。")
                append("他抓住了李追远的两侧肩膀。")
                append("李追远的眼里。")
                append("雷子抱着李维汉的腰向后发力。")
                append("李维汉来不及起身就对潘子吼了一声。")
            },
            projectTerms = listOf(
                DictPair("小黄莺", "Tiểu Hoàng Oanh"),
                DictPair("李追远", "Lý Truy Viễn", QuickDictionaryType.NAME),
                DictPair("李维汉", "Lý Huy Hán"),
                DictPair("雷子", "Lôi Tử"),
                DictPair("潘子", "Phan Tử"),
            ),
        ).lowercase()

        listOf(
            "kết hợp với dáng điệu của tiểu hoàng oanh",
            "hắn cũng bị ấn tượng mạnh bởi màn biểu diễn của tiểu hoàng oanh",
            "tiểu hoàng oanh dưới căn lều đơn sơ ấy",
            "túm lấy hai bên vai của lý truy viễn",
            "trong mắt lý truy viễn",
            "ôm eo lý duy hán, dồn sức kéo về sau",
            "lý duy hán chưa kịp đứng dậy đã hét với phan tử",
        ).forEach { expected ->
            assertTrue("Missing '$expected' in: $actual", actual.contains(expected))
        }
        assertFalse(actual, actual.contains("lý huy hán"))
    }
}

private fun isQuickDictionaryCjkForTest(codePoint: Int): Boolean =
    codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0x20000..0x2A6DF
