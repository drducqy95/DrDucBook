package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.ui.ai.bubble.ChatBubbleNormalizedPosition
import io.legado.app.ui.ai.bubble.ChatBubbleOrientation
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefFloat
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefFloat
import splitties.init.appCtx

object AiChatBubbleConfig {

    var enabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiChatBubbleEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.aiChatBubbleEnabled, value)

    fun getPosition(orientation: ChatBubbleOrientation): ChatBubbleNormalizedPosition? {
        val x = appCtx.getPrefFloat(orientation.xKey, UNSET_POSITION)
        val y = appCtx.getPrefFloat(orientation.yKey, UNSET_POSITION)
        if (x == UNSET_POSITION || y == UNSET_POSITION) return null
        return ChatBubbleNormalizedPosition(x = x, y = y)
    }

    fun savePosition(
        orientation: ChatBubbleOrientation,
        position: ChatBubbleNormalizedPosition,
    ) {
        appCtx.putPrefFloat(orientation.xKey, position.x.coerceIn(0f, 1f))
        appCtx.putPrefFloat(orientation.yKey, position.y.coerceIn(0f, 1f))
    }

    private val ChatBubbleOrientation.xKey: String
        get() = when (this) {
            ChatBubbleOrientation.PORTRAIT -> PreferKey.aiChatBubblePortraitX
            ChatBubbleOrientation.LANDSCAPE -> PreferKey.aiChatBubbleLandscapeX
        }

    private val ChatBubbleOrientation.yKey: String
        get() = when (this) {
            ChatBubbleOrientation.PORTRAIT -> PreferKey.aiChatBubblePortraitY
            ChatBubbleOrientation.LANDSCAPE -> PreferKey.aiChatBubbleLandscapeY
        }

    private const val UNSET_POSITION = -1f
}
