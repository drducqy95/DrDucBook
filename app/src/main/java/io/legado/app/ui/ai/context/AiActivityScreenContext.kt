package io.legado.app.ui.ai.context

import android.app.Activity
import io.legado.app.ui.ai.bubble.ChatBubbleCoordinator
import java.net.URI

fun Activity.publishAiScreenContext(
    screen: String,
    attributes: Map<String, String?> = emptyMap(),
    sensitive: Boolean = false,
) {
    AiScreenContextRegistry.register(
        AiScreenContextSnapshot(
            ownerId = AiActivityScreenContext.ownerId(javaClass.name, System.identityHashCode(this)),
            screen = screen,
            attributes = AiActivityScreenContext.compactAttributes(attributes),
            sensitive = sensitive,
        )
    )
    ChatBubbleCoordinator.refresh()
}

fun Activity.clearAiScreenContext() {
    AiScreenContextRegistry.clear(
        AiActivityScreenContext.ownerId(javaClass.name, System.identityHashCode(this))
    )
    ChatBubbleCoordinator.refresh()
}

object AiActivityScreenContext {

    fun ownerId(className: String, identityHashCode: Int): String {
        return "$className@$identityHashCode"
    }

    fun compactAttributes(attributes: Map<String, String?>): Map<String, String> {
        return attributes.mapNotNull { (key, value) ->
            val compactValue = value?.trim()
            if (compactValue.isNullOrEmpty()) {
                null
            } else {
                key to compactValue
            }
        }.toMap()
    }

    fun safeUrlLabel(url: String?): String? {
        val compactUrl = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            val uri = URI(compactUrl)
            val scheme = uri.scheme ?: return compactUrl
            val host = uri.host ?: return compactUrl.substringBefore('?').substringBefore('#')
            buildString {
                append(scheme)
                append("://")
                append(host)
                if (uri.port >= 0) {
                    append(':')
                    append(uri.port)
                }
                append(uri.rawPath?.takeIf { it.isNotBlank() } ?: "/")
            }
        }.getOrElse {
            compactUrl.substringBefore('?').substringBefore('#')
        }
    }
}
