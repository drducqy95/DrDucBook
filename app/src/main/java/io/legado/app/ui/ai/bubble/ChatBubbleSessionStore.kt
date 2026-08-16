package io.legado.app.ui.ai.bubble

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatBubbleAgentStatus {
    IDLE,
    RUNNING,
    WAITING_APPROVAL,
    ERROR,
}

@Stable
data class ChatBubbleSessionSnapshot(
    val conversationId: String? = null,
    val status: ChatBubbleAgentStatus = ChatBubbleAgentStatus.IDLE,
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
    val chatReady: Boolean = false,
    val messages: ImmutableList<ChatBubbleMessagePreview> = persistentListOf(),
)

@Stable
data class ChatBubbleMessagePreview(
    val role: String,
    val content: String,
)

object ChatBubbleSessionStore {
    private val _state = MutableStateFlow(ChatBubbleSessionSnapshot())
    val state: StateFlow<ChatBubbleSessionSnapshot> = _state.asStateFlow()
    private val _cancelRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelRequests = _cancelRequests.asSharedFlow()
    private val _sendRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val sendRequests = _sendRequests.asSharedFlow()
    private var controllerOwnerId: String? = null

    @Synchronized
    fun markRunning(conversationId: String?) {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            status = ChatBubbleAgentStatus.RUNNING,
            errorMessage = null,
        )
    }

    @Synchronized
    fun markWaitingApproval(conversationId: String?) {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            status = ChatBubbleAgentStatus.WAITING_APPROVAL,
            unreadCount = (_state.value.unreadCount + 1).coerceAtMost(MAX_BADGE_COUNT),
            errorMessage = null,
        )
    }

    @Synchronized
    fun markFinished(conversationId: String?) {
        val current = _state.value
        if (current.status == ChatBubbleAgentStatus.ERROR) return
        _state.value = current.copy(
            conversationId = conversationId ?: current.conversationId,
            status = ChatBubbleAgentStatus.IDLE,
            unreadCount = if (current.status == ChatBubbleAgentStatus.RUNNING) {
                (current.unreadCount + 1).coerceAtMost(MAX_BADGE_COUNT)
            } else current.unreadCount,
        )
    }

    @Synchronized
    fun markError(conversationId: String?, message: String) {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            status = ChatBubbleAgentStatus.ERROR,
            unreadCount = (_state.value.unreadCount + 1).coerceAtMost(MAX_BADGE_COUNT),
            errorMessage = message.take(MAX_ERROR_CHARS),
        )
    }

    @Synchronized
    fun markSeen() {
        _state.value = _state.value.copy(unreadCount = 0)
    }

    fun requestCancel() {
        _cancelRequests.tryEmit(Unit)
    }

    fun requestSend(content: String): Boolean {
        val message = content.trim()
        return message.isNotEmpty() && _state.value.chatReady && _sendRequests.tryEmit(message)
    }

    @Synchronized
    fun registerController(ownerId: String) {
        controllerOwnerId = ownerId
        _state.value = _state.value.copy(chatReady = true)
    }

    @Synchronized
    fun unregisterController(ownerId: String) {
        if (controllerOwnerId != ownerId) return
        controllerOwnerId = null
        _state.value = _state.value.copy(chatReady = false)
    }

    @Synchronized
    fun updateTranscript(
        conversationId: String?,
        messages: List<ChatBubbleMessagePreview>,
    ) {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            messages = messages.takeLast(MAX_MESSAGE_PREVIEWS).toImmutableList(),
        )
    }

    @Synchronized
    internal fun reset() {
        controllerOwnerId = null
        _state.value = ChatBubbleSessionSnapshot()
    }

    private const val MAX_BADGE_COUNT = 99
    private const val MAX_ERROR_CHARS = 500
    private const val MAX_MESSAGE_PREVIEWS = 6
}
