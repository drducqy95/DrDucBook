package io.legado.app.ui.ai.bubble

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBubbleSessionStoreTest {

    @After
    fun tearDown() {
        ChatBubbleSessionStore.reset()
    }

    @Test
    fun sessionStatusSurvivesHostReattachmentAndTracksUnreadWork() {
        ChatBubbleSessionStore.markRunning("chat-1")
        assertEquals(ChatBubbleAgentStatus.RUNNING, ChatBubbleSessionStore.state.value.status)

        ChatBubbleSessionStore.markWaitingApproval("chat-1")
        assertEquals(ChatBubbleAgentStatus.WAITING_APPROVAL, ChatBubbleSessionStore.state.value.status)
        assertEquals(1, ChatBubbleSessionStore.state.value.unreadCount)

        ChatBubbleSessionStore.markSeen()
        assertEquals(0, ChatBubbleSessionStore.state.value.unreadCount)
        assertEquals("chat-1", ChatBubbleSessionStore.state.value.conversationId)
    }

    @Test
    fun errorIsNotClearedByFollowingIdleSignal() {
        ChatBubbleSessionStore.markRunning("chat-2")
        ChatBubbleSessionStore.markError("chat-2", "provider failed")
        ChatBubbleSessionStore.markFinished("chat-2")

        assertEquals(ChatBubbleAgentStatus.ERROR, ChatBubbleSessionStore.state.value.status)
        assertEquals("provider failed", ChatBubbleSessionStore.state.value.errorMessage)
    }

    @Test
    fun controllerOwnerPublishesTranscriptAndCannotBeDetachedByAnotherOwner() {
        ChatBubbleSessionStore.registerController("owner-1")
        ChatBubbleSessionStore.updateTranscript(
            conversationId = "chat-3",
            messages = listOf(ChatBubbleMessagePreview("user", "hello")),
        )

        ChatBubbleSessionStore.unregisterController("owner-2")
        assertEquals(true, ChatBubbleSessionStore.state.value.chatReady)
        assertEquals("hello", ChatBubbleSessionStore.state.value.messages.single().content)

        ChatBubbleSessionStore.unregisterController("owner-1")
        assertEquals(false, ChatBubbleSessionStore.state.value.chatReady)
    }
}
