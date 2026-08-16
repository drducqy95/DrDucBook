package io.legado.app.ui.ai.bubble

import io.legado.app.ui.main.MainRouteAiChat
import io.legado.app.ui.main.MainRouteHome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBubbleCoordinatorTest {

    @Test
    fun `chat route excludes floating bubble`() {
        assertTrue(isChatBubbleExcludedScreen(MainRouteAiChat::class.simpleName))
    }

    @Test
    fun `other routes keep floating bubble available`() {
        assertFalse(isChatBubbleExcludedScreen(MainRouteHome::class.simpleName))
        assertFalse(isChatBubbleExcludedScreen(null))
    }
}
