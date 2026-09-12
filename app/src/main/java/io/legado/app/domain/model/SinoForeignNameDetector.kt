package io.legado.app.domain.model

import androidx.annotation.Keep

/**
 * Deterministic multi-origin detector for foreign names transliterated into Chinese
 * or written in Kanji/Hanja.
 *
 * Covers:
 * 1. Western names: Sino-phonetic character analysis + Latin syllable assembly + exact fantasy dict
 * 2. Japanese names: Unicode Kana detection + 50+ Kanji surnames + common given-name Kanji
 * 3. Korean names: Unicode Hangul detection + 44 Hanja surnames + common given-name Hanja
 * 4. Chinese names: 100+ common Chinese surnames for filtering out false positives
 *
 * Runs 100% on-device without AI tokens.
 */
object SinoForeignNameDetector {

    // === Unicode Range Constants ===
    private val LATIN_RANGE = 0x0041..0x024F
    private val KANA_RANGE = 0x3040..0x30FF
    private val HANGUL_RANGE = 0xAC00..0xD7AF

    // === Genre Bias Enum ===
    enum class GenreOriginBias {
        NEUTRAL,
        EASTERN_JP,
        EASTERN_KR,
        WESTERN,
        CROSSOVER,
    }

    @Keep
    data class NameCandidate(
        val raw: String,
        val origin: String,           // "western" | "japanese" | "korean" | "chinese"
        val suggested: String,        // Best-effort Latin / Romaji / Romanization
        val confidence: Float,        // 0.0 - 1.0
        val detectionTier: String,    // "unicode" | "exact" | "jp_surname" | "kr_surname" | "phonetic"
    )

    // === 1. Exact Matches for common fantasy / historical / popular names ===
    private val EXACT_MATCHES = mapOf(
        // D&D / Forgotten Realms
        "迪奈尔" to NameCandidate("迪奈尔", "western", "Deneir", 1.0f, "exact"),
        "密斯特拉" to NameCandidate("密斯特拉", "western", "Mystra", 1.0f, "exact"),
        "伊尔明斯特" to NameCandidate("伊尔明斯特", "western", "Elminster", 1.0f, "exact"),
        "崔斯特" to NameCandidate("崔斯特", "western", "Drizzt", 1.0f, "exact"),
        "雷斯林" to NameCandidate("雷斯林", "western", "Raistlin", 1.0f, "exact"),
        "莫邓肯" to NameCandidate("莫邓肯", "western", "Mordenkainen", 1.0f, "exact"),
        // Warcraft / Blizzard
        "阿尔萨斯" to NameCandidate("阿尔萨斯", "western", "Arthas", 1.0f, "exact"),
        "伊利丹" to NameCandidate("伊利丹", "western", "Illidan", 1.0f, "exact"),
        "希尔瓦娜斯" to NameCandidate("希尔瓦娜斯", "western", "Sylvanas", 1.0f, "exact"),
        "吉安娜" to NameCandidate("吉安娜", "western", "Jaina", 1.0f, "exact"),
        "萨尔" to NameCandidate("萨尔", "western", "Thrall", 1.0f, "exact"),
        "麦迪文" to NameCandidate("麦迪文", "western", "Medivh", 1.0f, "exact"),
        "卡德加" to NameCandidate("卡德加", "western", "Khadgar", 1.0f, "exact"),
        // Common Western names frequently transliterated in Chinese novels
        "汉密尔顿" to NameCandidate("汉密尔顿", "western", "Hamilton", 1.0f, "exact"),
        "拿破仑" to NameCandidate("拿破仑", "western", "Napoleon", 1.0f, "exact"),
        "亚历山大" to NameCandidate("亚历山大", "western", "Alexander", 1.0f, "exact"),
        "伊丽莎白" to NameCandidate("伊丽莎白", "western", "Elizabeth", 1.0f, "exact"),
        "维多利亚" to NameCandidate("维多利亚", "western", "Victoria", 1.0f, "exact"),
        "凯瑟琳" to NameCandidate("凯瑟琳", "western", "Catherine", 1.0f, "exact"),
        "理查德" to NameCandidate("理查德", "western", "Richard", 1.0f, "exact"),
        "威廉" to NameCandidate("威廉", "western", "William", 1.0f, "exact"),
        "罗伯特" to NameCandidate("罗伯特", "western", "Robert", 1.0f, "exact"),
        "爱德华" to NameCandidate("爱德华", "western", "Edward", 1.0f, "exact"),
        "查尔斯" to NameCandidate("查尔斯", "western", "Charles", 1.0f, "exact"),
        "约翰" to NameCandidate("约翰", "western", "John", 1.0f, "exact"),
        "亚瑟" to NameCandidate("亚瑟", "western", "Arthur", 1.0f, "exact"),
        "弗雷德" to NameCandidate("弗雷德", "western", "Fred", 1.0f, "exact"),
        "艾琳" to NameCandidate("艾琳", "western", "Eileen", 1.0f, "exact"),
        "海伦" to NameCandidate("海伦", "western", "Helen", 1.0f, "exact"),
        "玛格丽特" to NameCandidate("玛格丽特", "western", "Margaret", 1.0f, "exact"),
        "奥斯卡" to NameCandidate("奥斯卡", "western", "Oscar", 1.0f, "exact"),
        "赫尔曼" to NameCandidate("赫尔曼", "western", "Herman", 1.0f, "exact"),
        // Novel-specific & Common Fantasy/Western names
        "格鲁" to NameCandidate("格鲁", "western", "Grohl", 0.95f, "exact"),
        "蒙恩" to NameCandidate("蒙恩", "western", "Moen", 0.95f, "exact"),
        "帕尔夏" to NameCandidate("帕尔夏", "western", "Palsha", 0.95f, "exact"),
        "乔恩" to NameCandidate("乔恩", "western", "Jon", 1.0f, "exact"),
        "洛克" to NameCandidate("洛克", "western", "Locke", 1.0f, "exact"),
        "里昂" to NameCandidate("里昂", "western", "Lyon", 1.0f, "exact"),
        "里奥" to NameCandidate("里奥", "western", "Lyon", 1.0f, "exact"),
        "梅杰夫" to NameCandidate("梅杰夫", "western", "Medvedev", 1.0f, "exact"),
        "梅林" to NameCandidate("梅林", "western", "Merlin", 1.0f, "exact"),
        "伊顿" to NameCandidate("伊顿", "western", "Eaton", 1.0f, "exact"),
        "奥莉" to NameCandidate("奥莉", "western", "Olly", 0.95f, "exact"),
        "迪姆" to NameCandidate("迪姆", "western", "Dim", 0.95f, "exact"),
        "帕特" to NameCandidate("帕特", "western", "Pat", 0.95f, "exact"),
        "玛娜" to NameCandidate("玛娜", "western", "Mana", 0.95f, "exact"),
        "安格尔" to NameCandidate("安格尔", "western", "Angel", 1.0f, "exact"),
        "格温" to NameCandidate("格温", "western", "Gwen", 1.0f, "exact"),
        "彼得" to NameCandidate("彼得", "western", "Peter", 1.0f, "exact"),
        "斯科特" to NameCandidate("斯科特", "western", "Scott", 1.0f, "exact"),
        "诺亚" to NameCandidate("诺亚", "western", "Noah", 1.0f, "exact"),
    )

    // === 1B. Canonical alias mappings to unify variant transliterations ===
    val CANONICAL_ALIAS_MAP = mapOf(
        "里奥" to "Lyon",
        "里昂" to "Lyon",
        "洛克" to "Locke",
        "罗克" to "Locke",
        "亚瑟" to "Arthur",
        "阿瑟" to "Arthur",
        "艾伦" to "Alan",
        "阿伦" to "Alan",
        "克利斯" to "Chris",
        "克里斯" to "Chris",
        "玛丽" to "Mary",
        "玛莉" to "Mary",
        "乔恩" to "Jon",
        "梅杰夫" to "Medvedev",
        "梅林" to "Merlin",
        "伊顿" to "Eaton",
        "奥莉" to "Olly",
        "迪姆" to "Dim",
        "帕特" to "Pat",
        "格温" to "Gwen",
        "安格尔" to "Angel",
        "诺亚" to "Noah",
    )

    // === 2. Sino-phonetic transliteration chars for Western names ===
    private val SINO_PHONETIC = (
        "迪奈尔斯克特德亚利娅丝瑞拉玛莉安娜伊芙琳贝蒂" +
        "鲁塞菲普恩奇希兰伦邦蒙朗杰姆考曼森逊顿堡夫" +
        "瓦罗伯威廉托查亨乔治华维达格索泰坦莫茨帕尼加" +
        "卡萨巴布吉哈里雷肖凯洛佐丹" +
        // Expanded: common transliteration chars missing from original set
        "密汉阿比皮纳科库提波约诺基法弗尤勒奥彼米丽" +
        "柯温赫列沃西蒂苏多歌腓穆戈肯登史司理兹鲍" +
        "什图厄努古非拿翰艾爱海麦瑟弥梅"
    ).toSet()

    // === 3. Common Chinese surnames for filtering out Chinese names ===
    private val CHINESE_SURNAMES = (
        "李王张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗" +
        "梁宋郑谢韩唐冯于董萧程曹袁邓许傅沈曾彭吕" +
        "苏卢蒋蔡贾丁魏薛叶阎余潘杜戴夏钟汪田任姜" +
        "范方石姚谭廖邹熊金陆郝孔白崔康毛邱秦江史" +
        "顾侯邵孟龙万段漕钱汤尹黎易常武乔贺赖龚文"
    ).toSet()

    // === 4. Japanese Kanji Surnames -> Romaji ===
    private val JP_KANJI_SURNAMES = mapOf(
        "佐藤" to "Satō", "鈴木" to "Suzuki", "高橋" to "Takahashi",
        "田中" to "Tanaka", "伊藤" to "Itō", "渡辺" to "Watanabe",
        "山本" to "Yamamoto", "中村" to "Nakamura", "小林" to "Kobayashi",
        "加藤" to "Katō", "吉田" to "Yoshida", "山田" to "Yamada",
        "佐々木" to "Sasaki", "山口" to "Yamaguchi", "松本" to "Matsumoto",
        "井上" to "Inoue", "木村" to "Kimura", "林" to "Hayashi",
        "清水" to "Shimizu", "山崎" to "Yamazaki", "森" to "Mori",
        "池田" to "Ikeda", "橋本" to "Hashimoto", "阿部" to "Abe",
        "石川" to "Ishikawa", "山下" to "Yamashita", "前田" to "Maeda",
        "岡田" to "Okada", "藤田" to "Fujita", "後藤" to "Gotō",
        "近藤" to "Kondō", "村上" to "Murakami", "遠藤" to "Endō",
        "青木" to "Aoki", "坂本" to "Sakamoto", "福田" to "Fukuda",
        "太田" to "Ōta", "西村" to "Nishimura", "藤井" to "Fujii",
        "岡本" to "Okamoto", "藤原" to "Fujiwara", "三浦" to "Miura",
        "中野" to "Nakano", "原田" to "Harada",
        // Popular anime / light novel surnames
        "桐谷" to "Kirigaya", "結城" to "Yūki", "司波" to "Shiba",
        "上条" to "Kamijō", "御坂" to "Misaka", "竈門" to "Kamado",
        "煉獄" to "Rengoku", "我妻" to "Agatsuma", "五条" to "Gojō",
        "虎杖" to "Itadori", "伏黒" to "Fushiguro", "釘崎" to "Kugisaki",
    )

    // Common JP Given-Name Kanji -> Romaji
    private val JP_GIVEN_NAME_KANJI = mapOf(
        "太郎" to "Tarō", "一郎" to "Ichirō", "二郎" to "Jirō",
        "三郎" to "Saburō", "和人" to "Kazuto", "明日奈" to "Asuna",
        "炭治郎" to "Tanjirō", "禰豆子" to "Nezuko", "善逸" to "Zen'itsu",
        "杏寿郎" to "Kyōjurō", "達也" to "Tatsuya", "深雪" to "Miyuki",
        "当麻" to "Tōma", "美琴" to "Mikoto", "悟" to "Satoru",
        "翔太" to "Shōta", "悠太" to "Yūta", "健太" to "Kenta",
        "大輔" to "Daisuke", "拓也" to "Takuya", "雄介" to "Yūsuke",
        "直樹" to "Naoki", "和也" to "Kazuya", "裕太" to "Yūta",
        "愛" to "Ai", "美咲" to "Misaki", "陽菜" to "Hina",
        "結衣" to "Yui", "葵" to "Aoi", "花" to "Hana",
        "桜" to "Sakura", "遥" to "Haruka", "凛" to "Rin",
    )

    // === 5. Korean Hanja Surnames -> Revised Romanization ===
    private val KR_HANJA_SURNAMES = mapOf(
        "金" to "Kim", "李" to "Lee", "朴" to "Park", "崔" to "Choi",
        "郑" to "Jung", "姜" to "Kang", "赵" to "Cho", "尹" to "Yoon",
        "张" to "Jang", "林" to "Lim", "韩" to "Han", "吴" to "Oh",
        "徐" to "Seo", "申" to "Shin", "权" to "Kwon", "黄" to "Hwang",
        "安" to "Ahn", "宋" to "Song", "柳" to "Ryu", "全" to "Jeon",
        "洪" to "Hong", "高" to "Ko", "文" to "Moon", "杨" to "Yang",
        "孙" to "Son", "裴" to "Bae", "白" to "Baek", "许" to "Heo",
        "刘" to "Yoo", "南" to "Nam", "沈" to "Shim", "卢" to "Noh",
        "河" to "Ha", "郭" to "Kwak", "成" to "Sung", "车" to "Cha",
        "朱" to "Joo", "禹" to "Woo", "具" to "Koo", "闵" to "Min",
        "陈" to "Jin", "池" to "Ji", "严" to "Eom",
    )

    // Common KR Given-Name Hanja -> Romanization
    private val KR_GIVEN_NAME_HANJA = mapOf(
        "真宇" to "Jin-Woo", "俊赫" to "Jun-Hyuk", "民赫" to "Min-Hyuk",
        "成勋" to "Sung-Hoon", "志勋" to "Ji-Hoon", "泰亨" to "Tae-Hyung",
        "正国" to "Jung-Kook", "智旻" to "Ji-Min", "南俊" to "Nam-Joon",
        "硕珍" to "Seok-Jin", "号锡" to "Ho-Seok", "玧其" to "Yoon-Gi",
        "秀智" to "Su-Ji", "智恩" to "Ji-Eun", "允儿" to "Yoon-A",
        "秀贤" to "Soo-Hyun", "铉辰" to "Hyun-Jin", "彰彬" to "Chang-Bin",
        "知韩" to "Ji-Han", "在旭" to "Jae-Wook", "钟仁" to "Jong-In",
        "永勋" to "Yong-Hoon", "达也" to "Tatsuya",
    )

    // === 6. Syllable map for Western name Latin reconstruction ===
    private val SYLLABLE_MAP = mapOf(
        '迪' to listOf("de", "di"), '奈' to listOf("ne", "nai"),
        '尔' to listOf("r", "l", "er"), '斯' to listOf("s", "se"),
        '克' to listOf("k", "ck"), '特' to listOf("t", "te"),
        '德' to listOf("d", "de"), '亚' to listOf("a", "ya"),
        '利' to listOf("li", "ri"), '娅' to listOf("ya", "ia"),
        '瑞' to listOf("ri", "ray"), '娜' to listOf("na"),
        '罗' to listOf("ro", "lo"), '伯' to listOf("ber", "b"),
        '威' to listOf("wi", "vi"), '托' to listOf("to"),
        '查' to listOf("cha"), '亨' to listOf("hen"),
        '乔' to listOf("jo", "geo"), '华' to listOf("wa", "hua"),
        '维' to listOf("vi", "ve"), '达' to listOf("da"),
        '格' to listOf("g", "greg"), '索' to listOf("so"),
        '泰' to listOf("ty", "tai"), '莫' to listOf("mo"),
        '鲁' to listOf("lu", "ru"), '塞' to listOf("se"),
        '菲' to listOf("fi", "phi"), '恩' to listOf("en", "n"),
        '蒙' to listOf("mon", "men"), '朗' to listOf("lan", "lon"),
        '杰' to listOf("je", "ja"), '姆' to listOf("m"),
        '曼' to listOf("man"), '森' to listOf("son", "sen"),
        '顿' to listOf("ton"), '帕' to listOf("pa"),
        '拉' to listOf("la", "ra"), '玛' to listOf("ma"),
        '安' to listOf("an"), '伊' to listOf("i", "e"),
        '卡' to listOf("ca", "ka"), '萨' to listOf("sa"),
        '巴' to listOf("ba"), '布' to listOf("bu", "b"),
        '吉' to listOf("gi", "ji"), '哈' to listOf("ha"),
        '雷' to listOf("ray", "lei"), '凯' to listOf("kai", "ca"),
        '洛' to listOf("lo"), '佐' to listOf("zo"),
        '夏' to listOf("sha", "xia"), '尼' to listOf("ni"),
        '加' to listOf("ga", "ja"), '丝' to listOf("s", "se"),
        '芙' to listOf("fu"), '琳' to listOf("lin"),
        '贝' to listOf("bei", "be"), '莉' to listOf("li"),
        '兰' to listOf("lan"), '伦' to listOf("len", "ron"),
        '瓦' to listOf("wa", "va"), '廉' to listOf("liam"),
        '坦' to listOf("tan"), '丹' to listOf("dan"),
        // Expanded syllables for Western phonetic transliterations
        '汉' to listOf("ham", "han"), '密' to listOf("mil", "mi"),
        '阿' to listOf("a", "al"), '比' to listOf("bi"),
        '皮' to listOf("pi"), '纳' to listOf("na"),
        '科' to listOf("co", "ko"), '库' to listOf("ku", "cu"),
        '提' to listOf("ti"), '波' to listOf("po", "bo"),
        '约' to listOf("yo", "jo"), '诺' to listOf("no"),
        '基' to listOf("ki", "gi"), '法' to listOf("fa"),
        '弗' to listOf("f", "fu"), '尤' to listOf("yu", "u"),
        '勒' to listOf("le"), '奥' to listOf("o", "au"),
        '彼' to listOf("pe", "pi"), '米' to listOf("mi"),
        '丽' to listOf("li"), '柯' to listOf("co", "ko"),
        '温' to listOf("wen", "win"), '赫' to listOf("he", "her"),
        '列' to listOf("le", "lie"), '沃' to listOf("wo", "vo"),
        '西' to listOf("si", "se"), '苏' to listOf("su"),
        '多' to listOf("do"), '歌' to listOf("go", "ge"),
        '腓' to listOf("phi", "fi"), '穆' to listOf("mu"),
        '戈' to listOf("go"), '肯' to listOf("ken"),
        '登' to listOf("den"), '史' to listOf("s", "shi"),
        '司' to listOf("s", "si"), '理' to listOf("ri", "li"),
        '兹' to listOf("z", "tz"), '鲍' to listOf("bau", "bow"),
        '什' to listOf("sh"), '图' to listOf("tu"),
        '厄' to listOf("e"), '努' to listOf("nu"),
        '古' to listOf("gu"), '非' to listOf("fe", "fi"),
        '拿' to listOf("na"), '翰' to listOf("hn", "han"),
        '艾' to listOf("ai", "al"), '爱' to listOf("ed", "ai"),
        '海' to listOf("hai", "he"), '麦' to listOf("mc", "mai"),
        '梅' to listOf("me", "mer", "may"),
        '瑟' to listOf("ther", "se"), '弥' to listOf("mi"),
        '普' to listOf("p", "pu"), '希' to listOf("hi", "si"),
        '考' to listOf("col", "co"), '夫' to listOf("ff", "f"),
        '治' to listOf("ge"), '茨' to listOf("ts", "z"),
        '邦' to listOf("bon", "bang"), '里' to listOf("li", "ri"),
        '肖' to listOf("shaw", "xiao"), '逊' to listOf("son", "xun"),
    )

    // Western title / honorific suffix mapping (longer matches first)
    val WESTERN_TITLE_SUFFIX_MAP = listOf(
        "大主教" to "đại giám mục",
        "主教" to "giám mục",
        "大公" to "đại công",
        "公爵" to "công tước",
        "侯爵" to "hầu tước",
        "伯爵" to "bá tước",
        "子爵" to "tử tước",
        "男爵" to "nam tước",
        "大师" to "đại sư",
        "导师" to "đạo sư",
        "法师" to "pháp sư",
        "巫师" to "phù thủy",
        "神父" to "cha",
        "修士" to "tu sĩ",
        "骑士" to "hiệp sĩ",
        "团长" to "đoàn trưởng",
        "会长" to "hội trưởng",
        "教授" to "giáo sư",
        "阁下" to "các hạ",
        "殿下" to "điện hạ",
        "陛下" to "bệ hạ",
        "夫人" to "phu nhân",
        "小姐" to "tiểu thư",
        "先生" to "tiên sinh",
    )

    // Western honorific prefix mapping
    val WESTERN_TITLE_PREFIX_MAP = listOf(
        "圣" to "Thánh",
        "老" to "Lão",
        "小" to "Tiểu",
        "大" to "Đại",
    )

    fun stripVietnameseDiacritics(text: String): String {
        if (text.isBlank()) return ""
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("""\p{M}"""), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    // Suffixes commonly attached to foreign names in Chinese novels
    private val COMPOUND_SUFFIXES = listOf(
        "王国语", "帝国语", "联邦语", "大陆语",
        "王国", "帝国", "共和国", "公国", "联邦", "领地",
        "城堡", "要塞", "大教堂", "教堂", "神殿",
        "城市", "城", "小镇", "镇", "村",
        "语", "文", "族",
        "群岛", "半岛", "大陆", "山脉", "森林", "平原", "荒原", "海湾", "海峡",
        "教廷", "教会", "骑士团", "军团", "协会", "学派",
        // Western title suffixes
        "大主教", "主教", "大公", "公爵", "侯爵", "伯爵", "子爵", "男爵",
        "大师", "导师", "法师", "巫师", "神父", "修士", "骑士",
        "团长", "会长", "教授", "阁下", "殿下", "陛下", "夫人", "小姐", "先生",
    )

    /**
     * Infers genre bias from configuredPrompt string.
     */
    fun inferGenreBias(configuredPrompt: String): GenreOriginBias {
        val lower = configuredPrompt.lowercase()
        return when {
            lower.contains("light novel") || lower.contains("anime") ||
                lower.contains("nhật bản") || lower.contains("romaji") -> GenreOriginBias.EASTERN_JP
            lower.contains("webnovel hàn") || lower.contains("manhwa") ||
                lower.contains("hàn quốc") || lower.contains("hunter") -> GenreOriginBias.EASTERN_KR
            lower.contains("kỳ huyễn phương tây") || lower.contains("western") ||
                lower.contains("trung cổ") || lower.contains("d&d") -> GenreOriginBias.WESTERN
            lower.contains("xuyên không") || lower.contains("đồng nhân") ||
                lower.contains("đa thế giới") || lower.contains("crossover") -> GenreOriginBias.CROSSOVER
            else -> GenreOriginBias.NEUTRAL
        }
    }

    /**
     * Classifies a list of candidate entity names and returns only non-Chinese ones.
     */
    fun detectForeignNames(
        entityNames: List<String>,
        genreBias: GenreOriginBias = GenreOriginBias.NEUTRAL,
    ): List<NameCandidate> {
        return entityNames
            .mapNotNull { name -> classifyName(name.trim(), genreBias) }
            .filter { it.origin != "chinese" }
            .distinctBy { it.raw }
    }

    /**
     * Detects compound terms where a foreign name prefix is attached to a Chinese suffix
     * such as kingdom, language, empire, city, etc.
     * E.g. "德拉瑞昂王国语" -> detects prefix "德拉瑞昂" as Western name, and yields
     * candidates for both the prefix and the compound term.
     */
    fun detectCompoundForeignTerms(
        terms: List<String>,
        genreBias: GenreOriginBias = GenreOriginBias.NEUTRAL,
    ): List<NameCandidate> {
        return terms.flatMap { term ->
            val trimmed = term.trim()
            val matchedSuffix = COMPOUND_SUFFIXES.firstOrNull { suffix ->
                trimmed.endsWith(suffix) && trimmed.length > suffix.length + 1
            }
            if (matchedSuffix != null) {
                val prefix = trimmed.removeSuffix(matchedSuffix)
                val prefixCandidate = classifyName(prefix, genreBias)
                if (prefixCandidate != null && prefixCandidate.origin != "chinese") {
                    listOf(
                        prefixCandidate,
                        prefixCandidate.copy(raw = trimmed),
                    )
                } else emptyList()
            } else emptyList()
        }.distinctBy { it.raw }
    }

    /**
     * Finds known foreign/canonical character names occurring in the given text.
     */
    fun findNamesInText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val results = mutableListOf<String>()
        val baseNames = (EXACT_MATCHES.keys + CANONICAL_ALIAS_MAP.keys).distinct()
        baseNames.forEach { base ->
            if (text.contains(base)) {
                results.add(base)
                WESTERN_TITLE_SUFFIX_MAP.forEach { (suffix, _) ->
                    val compound = base + suffix
                    if (text.contains(compound)) results.add(compound)
                }
                WESTERN_TITLE_PREFIX_MAP.forEach { (prefix, _) ->
                    val compound = prefix + base
                    if (text.contains(compound)) results.add(compound)
                }
            }
        }
        return results.distinct()
    }

    /**
     * Returns all known raw aliases for a character name based on CANONICAL_ALIAS_MAP and EXACT_MATCHES.
     */
    fun getAliases(name: String): List<String> {
        val canonical = CANONICAL_ALIAS_MAP[name]
            ?: CANONICAL_ALIAS_MAP.values.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: EXACT_MATCHES[name]?.suggested
            ?: EXACT_MATCHES.values.firstOrNull { it.suggested.equals(name, ignoreCase = true) }?.suggested

        if (canonical != null) {
            val aliasesFromCanonical = CANONICAL_ALIAS_MAP.filter { it.value.equals(canonical, ignoreCase = true) }.map { it.key }
            val aliasesFromExact = EXACT_MATCHES.filter { it.value.suggested.equals(canonical, ignoreCase = true) }.map { it.key }
            return (listOf(name, canonical) + aliasesFromCanonical + aliasesFromExact).distinct()
        }
        return listOf(name)
    }

    /**
     * Classifies a single name into Western, Japanese, Korean, or Chinese origin.
     */
    fun classifyName(name: String, bias: GenreOriginBias = GenreOriginBias.NEUTRAL): NameCandidate? {
        if (name.isBlank()) return null

        // 0. Check canonical alias map
        CANONICAL_ALIAS_MAP[name]?.let { canonical ->
            return NameCandidate(name, "western", canonical, 0.95f, "canonical_alias")
        }

        // 1. Check exact dictionary
        EXACT_MATCHES[name]?.let { return it }

        // 1B. Check multi-part compound names separated by middle dot or mojibake separator
        val separatorChars = charArrayOf('·', '・', '?', '•', ' ')
        if (name.any { it in separatorChars }) {
            val parts = name.split(Regex("""[·・?•\s]+""")).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val classifiedParts = parts.map { part -> classifyName(part, bias) }
                if (classifiedParts.all { it != null && it.origin != "chinese" && it.suggested.isNotBlank() }) {
                    val combinedSuggested = classifiedParts.joinToString(" ") { it!!.suggested }
                    val combinedOrigin = classifiedParts.first()!!.origin
                    val minConfidence = classifiedParts.map { it!!.confidence }.minOrNull() ?: 0.9f
                    return NameCandidate(
                        raw = name,
                        origin = combinedOrigin,
                        suggested = combinedSuggested,
                        confidence = minConfidence,
                        detectionTier = "compound_separated",
                    )
                }
            }
        }

        // 1C. Check Western title/honorific suffix (e.g. "梅杰夫大师" -> "đại sư Medvedev", "梅林侯爵" -> "hầu tước Merlin")
        val matchedTitleSuffix = WESTERN_TITLE_SUFFIX_MAP.firstOrNull { (suffix, _) ->
            name.endsWith(suffix) && name.length > suffix.length + 1
        }
        if (matchedTitleSuffix != null) {
            val (suffix, titleVi) = matchedTitleSuffix
            val baseName = name.removeSuffix(suffix)
            val baseCandidate = classifyName(baseName, bias)
            if (baseCandidate != null && baseCandidate.origin != "chinese" && baseCandidate.suggested.isNotBlank()) {
                val combinedSuggested = if (titleVi.isNotBlank()) "$titleVi ${baseCandidate.suggested}".trim() else baseCandidate.suggested
                return NameCandidate(
                    raw = name,
                    origin = baseCandidate.origin,
                    suggested = combinedSuggested,
                    confidence = baseCandidate.confidence,
                    detectionTier = "title_compound",
                )
            }
        }

        // 1D. Check Western honorific prefix (e.g. "老乔恩" -> "Lão Jon", "小安格尔" -> "Tiểu Angel")
        val matchedTitlePrefix = WESTERN_TITLE_PREFIX_MAP.firstOrNull { (prefix, _) ->
            name.startsWith(prefix) && name.length > prefix.length + 1
        }
        if (matchedTitlePrefix != null) {
            val (prefix, prefixVi) = matchedTitlePrefix
            val baseName = name.removePrefix(prefix)
            val baseCandidate = classifyName(baseName, bias)
            if (baseCandidate != null && baseCandidate.origin != "chinese" && baseCandidate.suggested.isNotBlank()) {
                val combinedSuggested = if (prefixVi.isNotBlank()) "$prefixVi ${baseCandidate.suggested}".trim() else baseCandidate.suggested
                return NameCandidate(
                    raw = name,
                    origin = baseCandidate.origin,
                    suggested = combinedSuggested,
                    confidence = baseCandidate.confidence,
                    detectionTier = "title_compound",
                )
            }
        }

        val codePoints = name.codePoints().toArray()

        // 2. Unicode Tier
        if (codePoints.any { it in LATIN_RANGE }) {
            return NameCandidate(name, "western", name, 1.0f, "unicode")
        }
        if (codePoints.any { it in KANA_RANGE }) {
            return NameCandidate(name, "japanese", name, 1.0f, "unicode")
        }
        if (codePoints.any { it in HANGUL_RANGE }) {
            return NameCandidate(name, "korean", name, 1.0f, "unicode")
        }

        // 3. All CJK characters
        val chars = name.toList()

        // 3A. Japanese surname check with genre priority
        if (bias == GenreOriginBias.EASTERN_JP || bias == GenreOriginBias.CROSSOVER) {
            matchJapaneseName(name)?.let { return it }
        }

        // 3B. Korean surname check with genre priority
        if (bias == GenreOriginBias.EASTERN_KR || bias == GenreOriginBias.CROSSOVER) {
            matchKoreanHanjaName(name)?.let { return it }
        }

        // 3C. Chinese surname check (for 2-3 character names)
        val firstChar = chars[0]
        if (firstChar in CHINESE_SURNAMES && chars.size in 2..3) {
            // If in KR genre, Korean name could share Hanja surname (e.g. 金真宇 -> Kim Jin-Woo)
            if (bias == GenreOriginBias.EASTERN_KR) {
                matchKoreanHanjaName(name)?.let { return it }
            }
            return NameCandidate(name, "chinese", "", 0.9f, "cn_surname")
        }

        // 3D. Fallback JP/KR match even without explicit bias if no Chinese surname match
        matchJapaneseName(name)?.let { return it }
        matchKoreanHanjaName(name)?.let { return it }

        // 3E. Western phonetic transliteration check
        val phoneticCount = chars.count { it in SINO_PHONETIC }
        val phoneticRatio = phoneticCount.toFloat() / chars.size
        val threshold = when (bias) {
            GenreOriginBias.WESTERN, GenreOriginBias.CROSSOVER -> 0.45f
            else -> 0.55f
        }

        if (phoneticRatio > threshold) {
            val latin = assembleLatin(chars)
            return NameCandidate(name, "western", latin, 0.7f + phoneticRatio * 0.25f, "phonetic")
        }

        return NameCandidate(name, "chinese", "", 0.6f, "default")
    }

    private fun matchJapaneseName(name: String): NameCandidate? {
        for (len in 3 downTo 1) {
            if (name.length < len) continue
            val prefix = name.substring(0, len)
            val surnameRomaji = JP_KANJI_SURNAMES[prefix] ?: continue

            val given = name.substring(len)
            if (given.isEmpty()) {
                return NameCandidate(name, "japanese", surnameRomaji, 0.85f, "jp_surname")
            }
            val givenRomaji = JP_GIVEN_NAME_KANJI[given]
            val fullRomaji = if (givenRomaji != null) {
                "$surnameRomaji $givenRomaji"
            } else {
                surnameRomaji
            }
            return NameCandidate(name, "japanese", fullRomaji, 0.9f, "jp_surname")
        }
        return null
    }

    private fun matchKoreanHanjaName(name: String): NameCandidate? {
        if (name.length < 2) return null

        val surnameChar = name.substring(0, 1)
        val surnameRoman = KR_HANJA_SURNAMES[surnameChar] ?: return null

        val given = name.substring(1)
        val givenRoman = KR_GIVEN_NAME_HANJA[given]
        val fullRoman = if (givenRoman != null) {
            "$surnameRoman $givenRoman"
        } else {
            surnameRoman
        }
        return NameCandidate(name, "korean", fullRoman, 0.85f, "kr_surname")
    }

    private fun assembleLatin(chars: List<Char>): String {
        val parts = chars.mapNotNull { ch ->
            SYLLABLE_MAP[ch]?.firstOrNull()
        }
        val assembled = parts.joinToString("")
        return if (assembled.length >= 2) {
            assembled.replaceFirstChar { it.uppercaseChar() }
        } else {
            ""
        }
    }
}
