package io.legado.app.domain.model

object MlKitDictionaryEnforcer {

    fun enforce(
        sourceCjk: String,
        translatedVi: String,
        dictionaries: List<DictPair>,
        canonicalMemory: List<CanonicalTranslationMemory> = emptyList(),
        hanVietResolver: (String) -> String = { "" },
    ): String {
        if (sourceCjk.isBlank() || translatedVi.isBlank()) return translatedVi
        var result = translatedVi

        // 1. Process canonical memory entities first (aliases -> canonical target)
        if (canonicalMemory.isNotEmpty()) {
            for (mem in canonicalMemory) {
                val raw = mem.raw.trim()
                val target = mem.target.trim()
                if (raw.isBlank() || target.isBlank()) continue
                if (sourceCjk.contains(raw)) {
                    for (alias in mem.aliases.sortedByDescending { it.length }) {
                        val trimmedAlias = alias.trim()
                        if (trimmedAlias.isNotBlank() && !trimmedAlias.equals(target, ignoreCase = true)) {
                            result = replaceWordBoundary(
                                text = result,
                                find = trimmedAlias,
                                replace = target,
                                protectedPhrases = listOf(target),
                            )
                        }
                    }
                }
            }
        }

        // 2. Process dictionaries sorted by longest CJK original first (Greedy Longest Match)
        val sortedDicts = dictionaries
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .sortedByDescending { it.original.length }

        val allTargets = (sortedDicts.map { it.translation.trim() } + canonicalMemory.map { it.target.trim() })
            .filter { it.isNotBlank() }
            .distinct()

        for (pair in sortedDicts) {
            val raw = pair.original.trim()
            val target = pair.translation.trim()
            if (raw.isBlank() || target.isBlank()) continue

            if (sourceCjk.contains(raw)) {
                // If residual CJK remains in translated text, replace directly
                if (result.contains(raw)) {
                    result = result.replace(raw, target)
                }

                // Check Han-Viet variant
                val hanViet = hanVietResolver(raw).trim()
                if (hanViet.isNotBlank() && !hanViet.equals(target, ignoreCase = true)) {
                    result = replaceWordBoundary(
                        text = result,
                        find = hanViet,
                        replace = target,
                        protectedPhrases = allTargets,
                    )
                }

                // Check Pinyin variants (e.g., "Su Xiao" for 苏晓)
                val pinyin = resolveSimplePinyin(raw)
                if (pinyin.isNotBlank() && !pinyin.equals(target, ignoreCase = true) && result.contains(pinyin, ignoreCase = true)) {
                    result = replaceWordBoundary(
                        text = result,
                        find = pinyin,
                        replace = target,
                        protectedPhrases = allTargets,
                    )
                }
            }
        }

        // 3. Heal English-pivot NMT mistranslations via Pivot Semantic Disambiguator
        result = enforcePivotDisambiguation(
            sourceCjk = sourceCjk,
            translatedVi = result,
            dictionaries = sortedDicts,
            canonicalMemory = canonicalMemory,
        )

        return result
    }

    data class MaskResult(
        val maskedText: String,
        val replacements: List<Pair<String, String>>, // placeholder to target translation
    )

    val BUILTIN_NOVEL_TERMS = listOf(
        "轮回乐园" to "Luân Hồi Lạc Viên",
        "乐园" to "Lạc Viên",
        "猎杀者" to "Liệp sát giả",
        "契约者" to "Khế ước giả",
        "衍生位面" to "vị diện phái sinh",
        "衍生世界" to "thế giới phái sinh",
        "位面" to "vị diện",
        "世界之源" to "Nguồn Thế Giới",
        "刺杀哥亚王国国王" to "Ám sát Quốc vương Goa",
        "哥亚王国" to "Vương quốc Goa",
        "储物空间" to "không gian trữ vật",
        "储物戒" to "nhẫn trữ vật",
        "储物袋" to "túi trữ vật",
        "未解锁" to "chưa mở khóa",
        "已解锁" to "đã mở khóa",
        "阶位" to "giai vị",
        "烙印" to "lạc ấn",
        "苏晓" to "Tô Hiểu",
        "海贼王" to "One Piece",
        "宇智波斑" to "Uchiha Madara",
        "宇智波佐助" to "Uchiha Sasuke",
        "宇智波鼬" to "Uchiha Itachi",
        "宇智波带土" to "Uchiha Obito",
        "宇智波" to "Uchiha",
        "漩涡鸣人" to "Uzumaki Naruto",
        "木叶" to "Konoha",
        "写轮眼" to "Sharingan",
        "白眼" to "Byakugan",
        "轮回眼" to "Rinnegan",
        "恶魔果实" to "Trái Ác Quỷ",
        "霸气" to "Bá Khí",
        "幻影旅团" to "Lữ đoàn Phantom",
        "库洛洛" to "Chrollo",
        "死神" to "Bleach",
        "黑崎一护" to "Kurosaki Ichigo",
    )

    fun maskEntities(
        sourceText: String,
        dictionaries: List<DictPair>,
        canonicalMemory: List<CanonicalTranslationMemory> = emptyList(),
    ): MaskResult {
        if (sourceText.isBlank()) return MaskResult(sourceText, emptyList())
        val terms = mutableListOf<Pair<String, String>>()
        // 1. Canonical Memory (Story Memory - highest priority)
        for (mem in canonicalMemory) {
            val raw = mem.raw.trim()
            val target = mem.target.trim()
            if (raw.length >= 2 && target.isNotBlank()) {
                terms.add(raw to target)
            }
        }
        // 2. Custom & Project dictionaries
        for (pair in dictionaries) {
            val raw = pair.original.trim()
            val target = pair.translation.trim()
            if (raw.length >= 2 && target.isNotBlank()) {
                terms.add(raw to target)
            }
        }
        // 3. Builtin Novel Terms (fallback defaults for novel terms not covered above)
        for ((raw, target) in BUILTIN_NOVEL_TERMS) {
            if (raw.length >= 2 && target.isNotBlank()) {
                terms.add(raw to target)
            }
        }

        val sortedTerms = terms.distinctBy { it.first }.sortedByDescending { it.first.length }
        if (sortedTerms.isEmpty()) return MaskResult(sourceText, emptyList())

        var text = sourceText
        val replacements = mutableListOf<Pair<String, String>>()
        var id = 0
        for ((raw, target) in sortedTerms) {
            if (text.contains(raw)) {
                val placeholder = "__ENT_${id}__"
                replacements.add(placeholder to target)
                text = text.replace(raw, placeholder)
                id++
            }
        }
        return MaskResult(text, replacements)
    }

    fun unmaskEntities(
        translatedText: String,
        replacements: List<Pair<String, String>>,
    ): String {
        if (translatedText.isBlank() || replacements.isEmpty()) return translatedText
        val repMap = replacements.mapIndexed { idx, pair -> idx to pair.second }.toMap()

        // 1. Match full placeholders with or without surrounding underscores:
        // __ENT_0__, __ent_0__, __ ENT _ 0 __, ent_0, ENT_0, ent0, ENT0, ent_2chưa
        val tokenRegex = Regex(
            """_*__\s*(?:ENT|ent)\s*_?\s*(\d+)\s*__*_*|(?<![\p{L}\p{N}])(?:ENT|ent)\s*[_]?\s*(\d+)(?![\d])_*""",
            RegexOption.IGNORE_CASE
        )

        val unmasked = tokenRegex.replace(translatedText) { matchResult ->
            val idStr = matchResult.groupValues[1].ifBlank { matchResult.groupValues[2] }
            val id = idStr.toIntOrNull()
            if (id != null && repMap.containsKey(id)) {
                val target = repMap[id].orEmpty()
                val matchEnd = matchResult.range.last + 1
                if (matchEnd < translatedText.length && translatedText[matchEnd].isLetter()) {
                    "$target "
                } else {
                    target
                }
            } else {
                matchResult.value
            }
        }

        // 2. Clean any residual orphan placeholder tokens that couldn't be mapped
        return unmasked.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:ENT|ent)_\d+(?![\p{L}\p{N}])"""), "")
    }

    private fun resolveSimplePinyin(cjk: String): String {
        return when (cjk) {
            "苏晓" -> "Su Xiao"
            "轮回乐园" -> "Round Back to the Park"
            "猎杀者" -> "Hunter"
            "契约者" -> "Contractor"
            "海贼王" -> "One Piece"
            "火影忍者" -> "Naruto"
            "死神" -> "Bleach"
            "哥亚王国" -> "Kingdom of the Koa King"
            "宇智波斑" -> "Uchiha Madara"
            "宇智波" -> "Uchiha"
            "储物空间" -> "Storage Space"
            "烙印" -> "Imprint"
            else -> ""
        }
    }

    data class PivotPolysemyPattern(
        val triggerCjk: String,
        val pivotPattern: Regex,
        val canonicalTarget: String,
    )

    val PIVOT_POLYSEMY_PATTERNS = listOf(
        PivotPolysemyPattern("小腿", Regex("""(?iu)(?<![\p{L}\p{N}])(?:con bê|chân nhỏ)(?![\p{L}\p{N}])"""), "bắp chân"),
        PivotPolysemyPattern("骨折", Regex("""(?iu)(?<![\p{L}\p{N}])bị hỏng(?![\p{L}\p{N}])"""), "bị gãy"),
        PivotPolysemyPattern("击穿", Regex("""(?iu)(?<![\p{L}\p{N}])(?:bị hỏng|bị phá hủy)(?![\p{L}\p{N}])"""), "bị bắn thủng"),
        PivotPolysemyPattern("麻", Regex("""(?iu)(?<![\p{L}\p{N}])(?:là một cây gai dầu|cây gai dầu|gai dầu)(?![\p{L}\p{N}])"""), "tê rần"),
        PivotPolysemyPattern("落地", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Căn hộ|căn hộ)(?![\p{L}\p{N}])"""), "tiếp đất"),
        PivotPolysemyPattern("消音器", Regex("""(?iu)(?:tải khẩu súng lục của sự im lặng|khẩu súng lục của sự im lặng|súng lục im lặng|bộ phận giảm thanh|bộ giảm thanh|sự im lặng)"""), "ống giảm thanh"),
        PivotPolysemyPattern("皱眉", Regex("""(?iu)(?:nếp nhăn màu nâu|nếp nhăn)"""), "nhíu mày"),
        PivotPolysemyPattern("眉头", Regex("""(?iu)(?:nếp nhăn màu nâu|chân mày màu nâu|lông mày màu nâu|nếp nhăn)(?![\p{L}\p{N}])"""), "chân mày nhíu lại"),
        PivotPolysemyPattern("皱起", Regex("""(?iu)(?:nếp nhăn màu nâu|nếp nhăn)"""), "nhíu mày"),
        PivotPolysemyPattern("血迹", Regex("""(?iu)(?:không có máu miễn phí|máu miễn phí)"""), "vết máu"),
        PivotPolysemyPattern("余光", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Yu\s*Guang|Dư quang)(?:\s*quét)?(?![\p{L}\p{N}])"""), "khóe mắt quét qua"),
        PivotPolysemyPattern("衍生位面", Regex("""(?iu)['"]?(?:mặt phẳng có nguồn gốc|vị diện có nguồn gốc|mặt phẳng phái sinh)['"]?"""), "vị diện phái sinh"),
        PivotPolysemyPattern("衍生世界", Regex("""(?iu)['"]?(?:mặt phẳng có nguồn gốc|thế giới dẫn xuất)['"]?"""), "thế giới phái sinh"),
        PivotPolysemyPattern("位面", Regex("""(?iu)(?<![\p{L}\p{N}])(?:mặt phẳng|mặt phẳng không gian)(?![\p{L}\p{N}])"""), "vị diện"),
        PivotPolysemyPattern("世界之源", Regex("""(?iu)['"]?Nguồn['"]?\s*thế giới['"]?"""), "Nguồn Thế Giới"),
        PivotPolysemyPattern("穿梭", Regex("""(?iu)(?<![\p{L}\p{N}])đưa đón(?![\p{L}\p{N}])"""), "du hành"),
        PivotPolysemyPattern("军官", Regex("""(?iu)(?<![\p{L}\p{N}])nhân viên quân sự(?![\p{L}\p{N}])"""), "sĩ quan"),
        PivotPolysemyPattern("袖口", Regex("""(?iu)(?<![\p{L}\p{N}])còng(?![\p{L}\p{N}])"""), "cổ tay áo"),
        PivotPolysemyPattern("保安", Regex("""(?iu)(?:bảo đảm an ninh công cộng|bộ phận bảo vệ)"""), "nhân viên bảo vệ"),
        PivotPolysemyPattern("喷涌", Regex("""(?iu)(?<![\p{L}\p{N}])Máu bị xịt(?![\p{L}\p{N}])"""), "Máu tươi phun trào"),
        PivotPolysemyPattern("喷溅", Regex("""(?iu)(?<![\p{L}\p{N}])Máu bị xịt(?![\p{L}\p{N}])"""), "Máu tươi phun trào"),
        PivotPolysemyPattern("手背", Regex("""(?iu)(?<![\p{L}\p{N}])và tay(?![\p{L}\p{N}])"""), "và mu bàn tay"),
        PivotPolysemyPattern("仰倒", Regex("""(?iu)(?<![\p{L}\p{N}])(?:cúi đầu xuống đất|té ngửa)(?![\p{L}\p{N}])"""), "ngã ngửa xuống đất"),
        PivotPolysemyPattern("窝囊", Regex("""(?iu)(?<![\p{L}\p{N}])Đây là một điều(?![\p{L}\p{N}])"""), "Đây là một điều uất ức"),
        PivotPolysemyPattern("蹲姿", Regex("""(?iu)(?<![\p{L}\p{N}])Tô Hiểu của squat bị đảo ngược(?![\p{L}\p{N}])"""), "Tô Hiểu ngã gục xuống đất"),
        PivotPolysemyPattern("发昏", Regex("""(?iu)(?<![\p{L}\p{N}])đầu của cái đầu(?![\p{L}\p{N}])"""), "đầu óc choáng váng"),
        PivotPolysemyPattern("抽动", Regex("""(?iu)(?<![\p{L}\p{N}])(?:là tinh chỉnh|tinh chỉnh)(?![\p{L}\p{N}])"""), "giật giật"),
        PivotPolysemyPattern("黑洞洞", Regex("""(?iu)(?:hang động màu đen|hang đen)"""), "đen ngòm"),
        PivotPolysemyPattern("枪口", Regex("""(?iu)(?:các khẩu súng|khẩu súng)"""), "họng súng"),
        PivotPolysemyPattern("剧痛", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Nguy cơ|nguy cơ)(?![\p{L}\p{N}])"""), "Cơn đau dữ dội"),
        PivotPolysemyPattern("费力", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Đầy đủ|đầy đủ)(?![\p{L}\p{N}])"""), "Cố hết sức"),
        PivotPolysemyPattern("皮肉翻卷", Regex("""(?iu)(?:đang đỏ bừng|đỏ bừng)"""), "da thịt lật ra"),
        PivotPolysemyPattern("探入", Regex("""(?iu)(?<![\p{L}\p{N}])(?:khám phá nó|khám phá)(?![\p{L}\p{N}])"""), "thò vào"),
        PivotPolysemyPattern("表现", Regex("""(?iu)(?<![\p{L}\p{N}])hiệu suất(?![\p{L}\p{N}])"""), "biểu hiện"),
        PivotPolysemyPattern("露出笑容", Regex("""(?iu)(?:nụ cười được tiết lộ|tiết lộ nụ cười)"""), "nở nụ cười"),
        PivotPolysemyPattern("神经性毒素", Regex("""(?iu)chất dẫn truyền dây thần kinh"""), "độc tố thần kinh"),
        PivotPolysemyPattern("淬", Regex("""(?iu)được làm nguội bằng"""), "được tẩm"),
        PivotPolysemyPattern("无所不能", Regex("""(?iu)(?:Không sao\?|Toàn diện\?)"""), "Toàn năng?"),
        PivotPolysemyPattern("复生", Regex("""(?iu)(?:hòa giải\?|hòa giải)"""), "hồi sinh người đã khuất?"),
        PivotPolysemyPattern("鉴于", Regex("""(?iu)(?:Đưa ra danh tính|đưa ra danh tính)"""), "Dựa vào thân phận"),
        PivotPolysemyPattern("乐园", Regex("""(?iu)['"]?(?:Nhạc Viên|nhạc viên|Le Park|thiên đường)['"]?"""), "Lạc Viên"),
        PivotPolysemyPattern("轮回乐园", Regex("""(?iu)['"]?(?:Luân Hồi Nhạc Viên|quay trở lại công viên|công viên tái sinh|thiên đường trận đấu|thiên đường quay trở lại|thiên đường tròn|Round Back to the Park)['"]?"""), "Luân Hồi Lạc Viên"),
        PivotPolysemyPattern("海贼王", Regex("""(?iu)ở một trong những người"""), "One Piece"),
        PivotPolysemyPattern("法术伤害", Regex("""(?iu)chấn thương chính tả"""), "sát thương phép"),
        PivotPolysemyPattern("防御力", Regex("""(?iu)(?<![\p{L}\p{N}])quốc phòng(?![\p{L}\p{N}])"""), "phòng ngự"),
        PivotPolysemyPattern("生命值", Regex("""(?iu)(?<![\p{L}\p{N}])y tế(?![\p{L}\p{N}])"""), "sinh mệnh"),
        PivotPolysemyPattern("杀戮天赋", Regex("""(?iu)giết chết tài năng"""), "thiên phú giết chóc"),
        PivotPolysemyPattern("杀死目标", Regex("""(?iu)giết bàn thắng"""), "tiêu diệt mục tiêu"),
        PivotPolysemyPattern("苏晓", Regex("""(?iu)\bSu\\s*Xiao(?:dai)?\b"""), "Tô Hiểu"),
        PivotPolysemyPattern("储物空间", Regex("""(?iu)(?:không gian tiết kiệm|tiết kiệm không gian|không gian lưu trữ)"""), "không gian trữ vật"),
        PivotPolysemyPattern("储物戒", Regex("""(?iu)nhẫn tiết kiệm"""), "nhẫn trữ vật"),
        PivotPolysemyPattern("储物袋", Regex("""(?iu)túi tiết kiệm"""), "túi trữ vật"),
        PivotPolysemyPattern("烙印", Regex("""(?iu)(?:thương hiệu|nhãn hiệu)"""), "lạc ấn"),
        PivotPolysemyPattern("阶位", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Lớp|lớp học|lớp)(?![\p{L}\p{N}])"""), "giai vị"),
        PivotPolysemyPattern("未解锁", Regex("""(?iu)được mở khóa"""), "chưa mở khóa"),
        PivotPolysemyPattern("哥亚王国", Regex("""(?iu)(?:Kingdom of the Koa King|Kingdom of the Goa King|Kingdom of Goa|vương quốc Koa)"""), "Vương quốc Goa"),
        PivotPolysemyPattern("刺杀哥亚王国国王", Regex("""(?iu)Assassination 'Kingdom of the Koa King"""), "Ám sát Quốc vương Goa"),
        PivotPolysemyPattern("暗杀", Regex("""(?iu)(?<![\p{L}\p{N}])Assassination(?![\p{L}\p{N}])"""), "Ám sát"),
        PivotPolysemyPattern("刺杀", Regex("""(?iu)(?<![\p{L}\p{N}])Assassination(?![\p{L}\p{N}])"""), "Ám sát"),
        PivotPolysemyPattern("难度", Regex("""(?iu)khó khăn của thế giới"""), "độ khó của thế giới"),
        PivotPolysemyPattern("难度", Regex("""(?iu)đoán khó khăn"""), "đoán độ khó"),
        PivotPolysemyPattern("宇智波斑", Regex("""(?iu)(?:Yisi Bo ban|Yisi Bouvelle|Vũ Trí Ba Ban)"""), "Uchiha Madara"),
        PivotPolysemyPattern("宇智波", Regex("""(?iu)(?:Yisi Bo|Vũ Trí Ba)"""), "Uchiha"),
        PivotPolysemyPattern("被动技能", Regex("""(?iu)kỹ năng thụ động"""), "kỹ năng bị động"),
        PivotPolysemyPattern("契约者", Regex("""(?iu)nhà thầu"""), "Khế ước giả"),
        PivotPolysemyPattern("猎杀者", Regex("""(?iu)(?<![\p{L}\p{N}])(?:Săn giết giả|săn giết giả|người đi săn|thợ săn|kẻ săn mồi)(?![\p{L}\p{N}])"""), "Liệp sát giả"),
        PivotPolysemyPattern("后坐力", Regex("""(?iu)lực ngồi phía sau"""), "lực giật lùi"),
        PivotPolysemyPattern("弹夹", Regex("""(?iu)(?<![\p{L}\p{N}])tạp chí(?![\p{L}\p{N}])"""), "băng đạn"),
        PivotPolysemyPattern("弹匣", Regex("""(?iu)(?<![\p{L}\p{N}])tạp chí(?![\p{L}\p{N}])"""), "băng đạn"),
        PivotPolysemyPattern("车水马龙", Regex("""(?iu)(?:dòng sông|xe nước mã rồng)"""), "xe cộ như nước"),
        PivotPolysemyPattern("满身酒气", Regex("""(?iu)(?:với một chiếc xe say xỉn|say rượu|chiếc xe say xỉn)"""), "nồng nặc mùi rượu"),
        PivotPolysemyPattern("发飘", Regex("""(?iu)(?:tự hào|nổi bồng bềnh)"""), "lảo đảo"),
        PivotPolysemyPattern("出鞘", Regex("""(?iu)dao dài từ bao kiếm"""), "rút đao khỏi vỏ"),
        PivotPolysemyPattern("一跃而下", Regex("""(?iu)Nó sẽ nằm dưới đỉnh cao sáu mét"""), "Từ nóc nhà cao sáu mét nhảy vọt xuống"),
        PivotPolysemyPattern("退后", Regex("""(?iu)(?<![\p{L}\p{N}])assed(?![\p{L}\p{N}])"""), "đã lùi lại"),
        PivotPolysemyPattern("汗毛直立", Regex("""(?iu)(?:mái tóc lạnh thẳng|tóc lạnh thẳng|lông tơ dựng đứng)"""), "lông tơ dựng đứng"),
        PivotPolysemyPattern("快步前冲", Regex("""(?iu)Đi về phía trước về phía trước"""), "Sải bước lao về phía trước"),
        PivotPolysemyPattern("有些诡异", Regex("""(?iu)có phần hơi"""), "có phần kỳ dị"),
    )

    fun enforcePivotDisambiguation(
        sourceCjk: String,
        translatedVi: String,
        dictionaries: List<DictPair>,
        canonicalMemory: List<CanonicalTranslationMemory> = emptyList(),
    ): String {
        if (sourceCjk.isBlank() || translatedVi.isBlank()) return translatedVi
        var result = translatedVi

        val dynamicTargetMap = LinkedHashMap<String, String>()
        // 1. Novel builtin terms take standard base precedence
        for ((raw, target) in BUILTIN_NOVEL_TERMS) {
            if (raw.isNotBlank() && target.isNotBlank()) {
                dynamicTargetMap[raw] = target
            }
        }
        // 2. Story memory can override builtin terms
        for (mem in canonicalMemory) {
            val raw = mem.raw.trim()
            val target = mem.target.trim()
            if (raw.isNotBlank() && target.isNotBlank()) {
                dynamicTargetMap[raw] = target
            }
        }
        // 3. Fallback dictionaries fill in remaining terms
        for (dict in dictionaries) {
            val raw = dict.original.trim()
            val target = dict.translation.trim()
            if (raw.isNotBlank() && target.isNotBlank() && !dynamicTargetMap.containsKey(raw)) {
                dynamicTargetMap[raw] = target
            }
        }

        for (pattern in PIVOT_POLYSEMY_PATTERNS) {
            if (sourceCjk.contains(pattern.triggerCjk) && pattern.pivotPattern.containsMatchIn(result)) {
                val preferredTarget = dynamicTargetMap[pattern.triggerCjk]
                    ?.takeIf { it.isNotBlank() }
                    ?: pattern.canonicalTarget

                result = pattern.pivotPattern.replace(result) { matchResult ->
                    val matchedText = matchResult.value
                    preserveCase(matchedText, preferredTarget)
                }
            }
        }

        return result
    }

    private fun preserveCase(sample: String, target: String): String {
        if (sample.isBlank() || target.isBlank()) return target
        // If target starts with uppercase letter, it is a Title/Proper Noun (e.g. "Luân Hồi Lạc Viên", "Tô Hiểu")
        // Always preserve its capitalized proper noun form.
        if (target.first().isUpperCase()) {
            return target
        }
        // If target is a lowercase common word, capitalize only if the matched sample was capitalized (e.g. "Căn hộ" -> "Tiếp đất")
        val firstLetter = sample.firstOrNull { it.isLetter() } ?: return target
        return if (firstLetter.isUpperCase()) {
            target.replaceFirstChar { it.uppercase() }
        } else {
            target
        }
    }

    private fun replaceWordBoundary(
        text: String,
        find: String,
        replace: String,
        protectedPhrases: List<String> = emptyList(),
    ): String {
        if (find.isBlank() || !text.contains(find, ignoreCase = true)) return text

        val placeholders = mutableListOf<String>()
        var maskedText = text
        val activeProtected = protectedPhrases.filter { it.isNotBlank() && it.contains(find, ignoreCase = true) }

        for (phrase in activeProtected) {
            val placeholder = "\u0000PROT_${placeholders.size}\u0000"
            placeholders.add(phrase)
            maskedText = maskedText.replace(phrase, placeholder)
        }

        val escaped = Regex.escape(find)
        val regex = Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val replaced = regex.replace(maskedText) { matchResult ->
            val matched = matchResult.value
            when {
                matched.all { it.isUpperCase() || !it.isLetter() } -> replace.uppercase()
                matched.first().isUpperCase() -> replace.replaceFirstChar { it.uppercase() }
                else -> replace
            }
        }

        var unmasked = replaced
        placeholders.forEachIndexed { index, phrase ->
            unmasked = unmasked.replace("\u0000PROT_$index\u0000", phrase)
        }
        return unmasked
    }
}
