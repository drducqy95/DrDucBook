package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitGrammarPostProcessorTest {

    @Test
    fun healsAloneAdverbialCalque() {
        val input = "Những người mạnh mẽ đó thậm chí có thể đánh chìm một hòn đảo hoặc tàn sát một đất nước một mình."
        val output = MlKitGrammarPostProcessor.process(input)
        assertTrue(
            "Expected 'một mình' to be moved before the actions: $output",
            output.contains("một mình đánh chìm một hòn đảo hoặc tàn sát một đất nước")
        )
    }

    @Test
    fun healsAdjectiveInfinitiveCalques() {
        val input = "Nó rất đơn giản để làm."
        val output = MlKitGrammarPostProcessor.process(input)
        assertEquals("Nó rất dễ thực hiện.", output)
    }

    @Test
    fun healsCopulaAndSymptomCalques() {
        val input1 = "Điều này là rất quan trọng, có nhiều tính năng."
        val output1 = MlKitGrammarPostProcessor.process(input1)
        assertTrue("Expected 'Điều này rất quan trọng': $output1", output1.contains("Điều này rất quan trọng"))

        val input2 = "Chỉ khi Tô Hiểu có một số đau đầu."
        val output2 = MlKitGrammarPostProcessor.process(input2)
        assertTrue("Expected 'hơi đau đầu': $output2", output2.contains("hơi đau đầu"))
    }

    @Test
    fun healsPassiveUsageCalques() {
        val input = "Khác tạm thời không thể được sử dụng."
        val output = MlKitGrammarPostProcessor.process(input)
        assertEquals("các mục khác tạm thời chưa thể sử dụng.", output)
    }

    @Test
    fun healsMidSentenceCapitalization() {
        val input1 = "Tô Hiểu Cố gắng kích hoạt thương hiệu."
        val output1 = MlKitGrammarPostProcessor.process(input1)
        assertEquals("Tô Hiểu cố gắng kích hoạt thương hiệu.", output1)

        val input2 = "Tô Hiểu Bây giờ ngoại trừ mục đầu tiên."
        val output2 = MlKitGrammarPostProcessor.process(input2)
        assertEquals("Tô Hiểu bây giờ ngoại trừ mục đầu tiên.", output2)
    }

    @Test
    fun healsPronounDriftsInThirdPersonNarrative() {
        val input1 = "Vài dòng chữ xuất hiện trước mặt bạn."
        val output1 = MlKitGrammarPostProcessor.process(input1, pronounMode = QuickTranslationPronounMode.AUTO)
        assertTrue("Expected 'trước mắt hắn': $output1", output1.contains("trước mắt hắn"))

        val input2 = "Bạn có thể đoán được kết quả."
        val output2 = MlKitGrammarPostProcessor.process(input2)
        assertTrue("Expected 'có thể đoán được': $output2", output2.contains("có thể đoán được"))
    }

    @Test
    fun healsExpandedMidSentenceCapitalization() {
        val input = "Hắn Ngồi xổm trên mặt đất, Tô Hiểu Mặc áo khoác."
        val output = MlKitGrammarPostProcessor.process(input)
        assertEquals("Hắn ngồi xổm trên mặt đất, Tô Hiểu mặc áo khoác.", output)
    }

    @Test
    fun healsDuplicateVerbsAndLetCalque() {
        val input1 = "Tô Hiểu___ Ngồi xổm Ngồi trên mái nhà, mặc màu đen, mặc một chiếc mũ."
        val output1 = MlKitGrammarPostProcessor.process(input1)
        assertEquals("Tô Hiểu ngồi xổm trên mái nhà, mặc đồ đen, đội một chiếc mũ.", output1)

        val input2 = "Điều đó không thể hãy để hắn dao động."
        val output2 = MlKitGrammarPostProcessor.process(input2)
        assertEquals("Điều đó không thể làm hắn dao động.", output2)
    }

    @Test
    fun healsNarrativeShouldDieCalque() {
        val input = "Nhìn thấy vết thương trí mạng, và bạn nên chết."
        val output = MlKitGrammarPostProcessor.process(input)
        assertTrue("Expected 'vốn dĩ hẳn là đã chết': $output", output.contains("vốn dĩ hẳn là đã chết"))
    }

    @Test
    fun healsSpacesAroundBrackets() {
        val input = "【 Trạng thái 】: Bình thường."
        val output = MlKitGrammarPostProcessor.process(input)
        assertEquals("【Trạng thái】: Bình thường.", output)
    }

    @Test
    fun healsExpandedSubjectsMidSentenceCapitalization() {
        val input = "Liệp sát giả Không nói gì, Khế ước giả Chưa xuất hiện, Hai người Cùng tiến vào."
        val output = MlKitGrammarPostProcessor.process(input)
        assertEquals("Liệp sát giả không nói gì, Khế ước giả chưa xuất hiện, Hai người cùng tiến vào.", output)
    }

    @Test
    fun healsNovelSyntaxAndDeduplication() {
        val input1 = "Tô Hiểu thấy một bộ phận bảo vệ mặc đồng phục mặc đồng phục."
        val output1 = MlKitGrammarPostProcessor.process(input1)
        assertEquals("Tô Hiểu thấy một nhân viên bảo vệ mặc đồng phục.", output1)

        val input2 = "Tô Hiểu của squat, cảm thấy đầu của cái đầu."
        val output2 = MlKitGrammarPostProcessor.process(input2)
        assertEquals("Tô Hiểu đang ngồi xổm, cảm thấy đầu óc choáng váng.", output2)

        val input3 = "Yu Guang quét, không có máu miễn phí."
        val output3 = MlKitGrammarPostProcessor.process(input3)
        assertEquals("khóe mắt quét qua, không có vết máu chảy ra.", output3)
    }

    @Test
    fun protectsDialogueQuotesAndNormalizesBrackets() {
        val dialogueInput = "Hắn nhìn đối phương: \", Bạn có thể đi cùng tôi không?\""
        val dialogueOutput = MlKitGrammarPostProcessor.process(dialogueInput)
        assertTrue("Leading comma in quote should be cleaned: $dialogueOutput", dialogueOutput.contains("\"Bạn có thể đi cùng tôi không?\""))

        val bracketInput = "[Săn giết giả, bạn không chết, là / không sẽ tham gia. \""
        val bracketOutput = MlKitGrammarPostProcessor.process(bracketInput, sourceCjk = "【猎杀者，你并没有死亡】")
        assertTrue("Bracket should be normalized: $bracketOutput", bracketOutput.startsWith("【") && bracketOutput.endsWith("】"))
        assertTrue("Second person pronoun in system prompt should be preserved: $bracketOutput", bracketOutput.contains("bạn không chết"))
    }
}
