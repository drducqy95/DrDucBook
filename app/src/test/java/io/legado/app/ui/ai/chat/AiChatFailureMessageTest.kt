package io.legado.app.ui.ai.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatFailureMessageTest {

    @Test
    fun failureRemainsVisibleAfterPartialAssistantText() {
        assertEquals(
            "Đang tạo plugin…\n\nYêu cầu thất bại: API không tương thích",
            combineAiAssistantFailure(
                text = "Đang tạo plugin…",
                failureText = "Yêu cầu thất bại: API không tương thích",
            ),
        )
    }

    @Test
    fun failureIsTheMessageWhenAssistantDidNotProduceText() {
        assertEquals(
            "Yêu cầu thất bại",
            combineAiAssistantFailure(text = "", failureText = "Yêu cầu thất bại"),
        )
    }
}
