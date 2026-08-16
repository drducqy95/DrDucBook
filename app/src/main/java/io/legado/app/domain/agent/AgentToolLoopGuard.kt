package io.legado.app.domain.agent

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.AiToolCall

class AgentToolLoopGuard(
    private val detectionCount: Int = DEFAULT_DETECTION_COUNT,
) {

    private val counts = mutableMapOf<String, Int>()

    fun recordAndIsLoop(call: AiToolCall): Boolean {
        val key = call.loopKey()
        val count = (counts[key] ?: 0) + 1
        counts[key] = count
        return count >= detectionCount
    }

    private fun AiToolCall.loopKey(): String {
        return "$name|${arguments.canonicalJsonOrTrimmed()}|${metadata.orEmpty().trim()}"
    }

    companion object {
        const val DEFAULT_DETECTION_COUNT = 3
    }
}

private fun String.canonicalJsonOrTrimmed(): String {
    val value = trim()
    if (value.isEmpty()) return "{}"
    return runCatching {
        JsonParser.parseString(value).canonicalJson()
    }.getOrElse {
        value
    }
}

private fun JsonElement.canonicalJson(): String {
    return when {
        isJsonObject -> asJsonObject.canonicalJson()
        isJsonArray -> asJsonArray.canonicalJson()
        isJsonNull -> "null"
        else -> toString()
    }
}

private fun JsonObject.canonicalJson(): String {
    return entrySet()
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${key.escapeJsonKey()}\":${value.canonicalJson()}"
        }
}

private fun JsonArray.canonicalJson(): String {
    return joinToString(prefix = "[", postfix = "]") { it.canonicalJson() }
}

private fun String.escapeJsonKey(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}
