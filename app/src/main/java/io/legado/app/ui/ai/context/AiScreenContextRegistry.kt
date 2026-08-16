package io.legado.app.ui.ai.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiScreenContextSnapshot(
    val ownerId: String,
    val screen: String,
    val attributes: Map<String, String> = emptyMap(),
    val sensitive: Boolean = false,
)

object AiScreenContextRegistry {

    private val contexts = linkedMapOf<String, AiScreenContextSnapshot>()
    private val _current = MutableStateFlow<AiScreenContextSnapshot?>(null)
    val current: StateFlow<AiScreenContextSnapshot?> = _current.asStateFlow()

    @Synchronized
    fun register(snapshot: AiScreenContextSnapshot) {
        val sanitized = snapshot.sanitizedForAgent()
        contexts[snapshot.ownerId] = sanitized
        _current.value = sanitized
    }

    @Synchronized
    fun clear(ownerId: String) {
        contexts.remove(ownerId)
        _current.value = contexts.values.lastOrNull()
    }

    @Synchronized
    fun clearAll() {
        contexts.clear()
        _current.value = null
    }

    private fun AiScreenContextSnapshot.sanitizedForAgent(): AiScreenContextSnapshot {
        if (sensitive) {
            return copy(attributes = emptyMap())
        }
        val sanitizedAttributes = attributes.mapValues { (key, value) ->
            if (key.isSensitiveContextKey()) REDACTED_VALUE else value
        }
        return copy(attributes = sanitizedAttributes)
    }

    private fun String.isSensitiveContextKey(): Boolean {
        val normalized = filter(Char::isLetterOrDigit).lowercase()
        return sensitiveAttributeKeyParts.any(normalized::contains)
    }

    private const val REDACTED_VALUE = "[redacted]"

    private val sensitiveAttributeKeyParts = listOf(
        "apikey",
        "authorization",
        "cookie",
        "password",
        "secret",
        "token",
    )
}
