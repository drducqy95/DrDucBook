package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitDictionaryEnforcerTest {

    @Test
    fun masksAndUnmasksEntitiesCorrectly() {
        val raw = "苏晓进入了轮回乐园，遇到了猎杀者。"
        val dicts = listOf(
            DictPair("苏晓", "Tô Hiểu"),
            DictPair("轮回乐园", "Luân Hồi Lạc Viên"),
            DictPair("猎杀者", "Thợ Săn"),
        )
        val maskResult = MlKitDictionaryEnforcer.maskEntities(raw, dicts)
        assertTrue(maskResult.maskedText.contains("__ENT_"))
        assertEquals(3, maskResult.replacements.size)

        // Simulate ML Kit translating around placeholders
        val mockMlKitOutput = maskResult.maskedText.replace("进入了", "đã tiến vào ").replace("，遇到了", ", gặp được ")
        val unmasked = MlKitDictionaryEnforcer.unmaskEntities(mockMlKitOutput, maskResult.replacements)

        assertTrue(unmasked.contains("Tô Hiểu"))
        assertTrue(unmasked.contains("Luân Hồi Lạc Viên"))
        assertTrue(unmasked.contains("Thợ Săn"))
    }

    @Test
    fun healsPivotMistranslationsInNovelContext() {
        val sourceCjk = "苏晓获得了轮回乐园的法术伤害和防御力加成，杀戮天赋被激活，杀死目标。"
        val machineTranslated = "Su Xiao đã nhận được buff quay trở lại công viên với chấn thương chính tả và quốc phòng, giết chết tài năng được kích hoạt, giết bàn thắng."
        val dicts = listOf(
            DictPair("苏晓", "Tô Hiểu"),
        )

        val healed = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = machineTranslated,
            dictionaries = dicts,
            hanVietResolver = { "" },
        )

        assertTrue("Should fix Su Xiao to Tô Hiểu: $healed", healed.contains("Tô Hiểu"))
        assertTrue("Should fix park mistranslation to Luân Hồi Lạc Viên: $healed", healed.contains("Luân Hồi Lạc Viên"))
        assertTrue("Should fix chấn thương chính tả to sát thương phép: $healed", healed.contains("sát thương phép"))
        assertTrue("Should fix quốc phòng to phòng ngự: $healed", healed.contains("phòng ngự"))
        assertTrue("Should fix giết chết tài năng to thiên phú giết chóc: $healed", healed.contains("thiên phú giết chóc"))
        assertTrue("Should fix giết bàn thắng to tiêu diệt mục tiêu: $healed", healed.contains("tiêu diệt mục tiêu"))

        val sourceCjk2 = "刺杀哥亚王国国王，激活烙印，开启储物空间，提升阶位，未解锁。"
        val machineTranslated2 = "Assassination 'Kingdom of the Koa King, kích hoạt thương hiệu, mở ra không gian tiết kiệm, nâng cao Lớp, được mở khóa."
        val healed2 = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk2,
            translatedVi = machineTranslated2,
            dictionaries = emptyList(),
            hanVietResolver = { "" },
        )
        assertTrue("Should fix Assassination Kingdom of Koa: $healed2", healed2.contains("Ám sát Quốc vương Goa") || healed2.contains("Vương quốc Goa"))
        assertTrue("Should fix thương hiệu to lạc ấn: $healed2", healed2.contains("lạc ấn"))
        assertTrue("Should fix không gian tiết kiệm to không gian trữ vật: $healed2", healed2.contains("không gian trữ vật"))
        assertTrue("Should fix Lớp to giai vị: $healed2", healed2.contains("giai vị", ignoreCase = true))
        assertTrue("Should fix được mở khóa to chưa mở khóa: $healed2", healed2.contains("chưa mở khóa"))
    }

    @Test
    fun enforcesCanonicalMemoryWithAliases() {
        val sourceCjk = "宇智波佐助看着漩涡鸣人"
        val translated = "Sasuke nhìn Naruto"
        val memory = listOf(
            CanonicalTranslationMemory(
                identity = "sasuke",
                raw = "宇智波佐助",
                target = "Uchiha Sasuke",
                aliases = listOf("Sasuke"),
            ),
            CanonicalTranslationMemory(
                identity = "naruto",
                raw = "漩涡鸣人",
                target = "Uzumaki Naruto",
                aliases = listOf("Naruto"),
            ),
        )

        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translated,
            dictionaries = emptyList(),
            canonicalMemory = memory,
        )

        assertEquals("Uchiha Sasuke nhìn Uzumaki Naruto", result)
    }

    @Test
    fun unmaskAbsorbsSurroundingUnderscores() {
        val input = "___ENT_0___ nhìn xung quanh, __ent_1_____ bước tới."
        val replacements = listOf(
            "__ENT_0__" to "Tô Hiểu",
            "__ENT_1__" to "Luân Hồi Lạc Viên",
        )
        val unmasked = MlKitDictionaryEnforcer.unmaskEntities(input, replacements)
        assertEquals("Tô Hiểu nhìn xung quanh, Luân Hồi Lạc Viên bước tới.", unmasked)
    }

    @Test
    fun enforcesDynamicDictionariesWithoutHardcoding() {
        val sourceCjk = "小腿一麻，没有血迹流出。落地，装了消音器的手枪，余光扫过。"
        val machineTranslated = "con bê một phen tê dại, không có vết máu chảy ra. Căn hộ, súng lục lắp sự im lặng, Yu Guang quét qua."
        val dynamicDicts = listOf(
            DictPair("小腿", "bắp chân"),
            DictPair("消音器", "ống giảm thanh"),
            DictPair("落地", "tiếp đất"),
            DictPair("余光", "khóe mắt"),
        )
        // 1. Test entity masking with dynamic terms
        val maskResult = MlKitDictionaryEnforcer.maskEntities(sourceCjk, dynamicDicts)
        assertTrue(maskResult.replacements.any { it.second == "bắp chân" })
        assertTrue(maskResult.replacements.any { it.second == "ống giảm thanh" })
        assertTrue(maskResult.replacements.any { it.second == "tiếp đất" })
        assertTrue(maskResult.replacements.any { it.second == "khóe mắt" })

        // 2. Test residual CJK direct replacement via enforce
        val rawResidual = "Sau khi 小腿 không đau, hắn mang 消音器 chuẩn bị 落地, trong 余光 nhìn thấy đối phương."
        val healed = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = rawResidual,
            dictionaries = dynamicDicts,
            hanVietResolver = { "" },
        )
        assertTrue("Expected 'bắp chân': $healed", healed.contains("bắp chân"))
        assertTrue("Expected 'ống giảm thanh': $healed", healed.contains("ống giảm thanh"))
        assertTrue("Expected 'tiếp đất': $healed", healed.contains("tiếp đất"))
        assertTrue("Expected 'khóe mắt': $healed", healed.contains("khóe mắt"))

        // 3. Test machine translated pivot polysemy resolution via enforce
        val healedMachine = MlKitDictionaryEnforcer.enforce(
            sourceCjk = "小腿一麻，没有血迹流出。落地，装了消音器的手枪，余光扫过。某位军官，袖口染血，衍生世界。",
            translatedVi = "con bê là một cây gai dầu, không có máu miễn phí. Căn hộ, súng lục của sự im lặng, Yu Guang quét qua. Một nhân viên quân sự, còng dính máu, mặt phẳng có nguồn gốc.",
            dictionaries = dynamicDicts,
            hanVietResolver = { "" },
        )
        assertTrue("Expected 'bắp chân' instead of 'con bê': $healedMachine", healedMachine.contains("bắp chân"))
        assertTrue("Expected 'tê rần' instead of 'cây gai dầu': $healedMachine", healedMachine.contains("tê rần"))
        assertTrue("Expected 'vết máu' instead of 'máu miễn phí': $healedMachine", healedMachine.contains("vết máu"))
        assertTrue("Expected 'Tiếp đất' instead of 'Căn hộ': $healedMachine", healedMachine.contains("Tiếp đất") || healedMachine.contains("tiếp đất"))
        assertTrue("Expected 'ống giảm thanh' instead of 'sự im lặng': $healedMachine", healedMachine.contains("ống giảm thanh"))
        assertTrue("Expected 'khóe mắt' instead of 'Yu Guang': $healedMachine", healedMachine.contains("khóe mắt", ignoreCase = true))
        assertTrue("Expected 'sĩ quan' instead of 'nhân viên quân sự': $healedMachine", healedMachine.contains("sĩ quan"))
        assertTrue("Expected 'cổ tay áo' instead of 'còng': $healedMachine", healedMachine.contains("cổ tay áo"))
        assertTrue("Expected 'thế giới phái sinh' instead of 'mặt phẳng có nguồn gốc': $healedMachine", healedMachine.contains("thế giới phái sinh"))

        // 4. Test newly added novel terms: 衍生位面, 乐园, 猎杀者, 击穿, 眉头
        val novelCjk = "穿梭在‘衍生位面’中，轮回乐园，猎杀者，小腿被击穿，眉头皱起，无所不能？能复生故去的人？鉴于你猎杀者的身份。"
        val novelRaw = "Đưa đón trong một 'mặt phẳng có nguồn gốc', Luân Hồi Nhạc Viên, Săn giết giả, bắp chân bị hỏng, nếp nhăn màu nâu, Không sao? Có thể hòa giải? Đưa ra danh tính của hắn Săn giết giả."
        val healedNovel = MlKitDictionaryEnforcer.enforce(
            sourceCjk = novelCjk,
            translatedVi = novelRaw,
            dictionaries = listOf(
                DictPair("乐园", "Nhạc Viên"), // Generic dictionary should be overridden by builtin novel terms
                DictPair("猎杀者", "săn giết giả"),
            ),
            hanVietResolver = { "" },
        )
        assertTrue("Expected 'vị diện phái sinh': $healedNovel", healedNovel.contains("vị diện phái sinh"))
        assertTrue("Expected 'Luân Hồi Lạc Viên' instead of 'Nhạc Viên': $healedNovel", healedNovel.contains("Luân Hồi Lạc Viên"))
        assertTrue("Expected 'Liệp sát giả' instead of 'Săn giết giả': $healedNovel", healedNovel.contains("Liệp sát giả"))
        assertTrue("Expected 'bị bắn thủng': $healedNovel", healedNovel.contains("bị bắn thủng"))
        assertTrue("Expected 'chân mày nhíu lại' or 'nhíu mày': $healedNovel", healedNovel.contains("nhíu mày") || healedNovel.contains("chân mày"))
        assertTrue("Expected 'Toàn năng?': $healedNovel", healedNovel.contains("Toàn năng?"))
        assertTrue("Expected 'hồi sinh người đã khuất?': $healedNovel", healedNovel.contains("hồi sinh người đã khuất?"))
        assertTrue("Expected 'Dựa vào thân phận': $healedNovel", healedNovel.contains("Dựa vào thân phận"))
    }
}
