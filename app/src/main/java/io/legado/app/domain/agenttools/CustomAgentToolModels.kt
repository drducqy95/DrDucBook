package io.legado.app.domain.agenttools

import com.google.gson.JsonObject
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.domain.model.AiToolDefinition

enum class CustomAgentToolLifecycleState {
    DRAFT,
    VALIDATED,
    APPROVED,
    ENABLED,
    DISABLED,
    REVOKED,
    QUARANTINED,
}

object CustomAgentToolValidationStatus {
    const val UNVALIDATED = "UNVALIDATED"
    const val VALID = "VALID"
    const val INVALID = "INVALID"
}

object CustomAgentToolTestStatus {
    const val NOT_RUN = "NOT_RUN"
    const val PASS = "PASS"
    const val FAIL = "FAIL"
}

data class CustomAgentToolManifest(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject,
    val capabilities: Set<AgentToolCapability>,
    val allowedDomains: Set<String>,
    val timeoutMs: Long,
    val maxOutputChars: Int,
    val author: String?,
    val checksum: String,
    val script: String,
)

data class CustomAgentToolValidationError(
    val field: String,
    val message: String,
    val line: Int? = null,
)

data class CustomAgentToolValidationResult(
    val manifest: CustomAgentToolManifest?,
    val errors: List<CustomAgentToolValidationError>,
) {
    val valid: Boolean
        get() = errors.isEmpty() && manifest != null

    fun getOrThrow(): CustomAgentToolManifest {
        if (valid) return manifest!!
        throw CustomAgentToolValidationException(errors)
    }
}

class CustomAgentToolValidationException(
    val errors: List<CustomAgentToolValidationError>,
) : IllegalArgumentException(
    errors.joinToString("\n") { error ->
        buildString {
            append(error.field)
            error.line?.let { append(":").append(it) }
            append(": ")
            append(error.message)
        }
    }
)

data class CustomAgentToolExecutionRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long,
)

data class CustomAgentToolExecutionResponse(
    val ok: Boolean,
    val status: Int,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
)

data class CustomAgentToolExecutionResult(
    val toolId: String,
    val outputJson: String,
    val durationMs: Long,
)

fun interface CustomAgentToolNetworkBridge {
    fun fetch(request: CustomAgentToolExecutionRequest): CustomAgentToolExecutionResponse
}

class CustomAgentToolExecutionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

data class CustomAgentToolDraft(
    val manifestJson: String,
    val fixtureArgumentsJson: String = "{}",
)

data class CustomAgentToolVersionSnapshot(
    val id: String,
    val toolId: String,
    val toolName: String,
    val version: String,
    val name: String,
    val description: String,
    val manifestJson: String,
    val checksum: String,
    val capabilities: Set<AgentToolCapability>,
    val allowedDomains: List<String>,
    val lifecycleState: CustomAgentToolLifecycleState,
    val validationStatus: String,
    val validationMessage: String,
    val testStatus: String,
    val testMessage: String,
    val testOutputJson: String?,
    val fixtureArgumentsJson: String,
    val createdAt: Long,
    val validatedAt: Long?,
    val approvedAt: Long?,
    val testedAt: Long?,
) {
    val valid: Boolean
        get() = validationStatus == CustomAgentToolValidationStatus.VALID

    val approved: Boolean
        get() = lifecycleState == CustomAgentToolLifecycleState.APPROVED ||
            lifecycleState == CustomAgentToolLifecycleState.ENABLED ||
            lifecycleState == CustomAgentToolLifecycleState.DISABLED
}

data class CustomAgentToolSnapshot(
    val id: String,
    val toolName: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val activeVersionId: String?,
    val versions: List<CustomAgentToolVersionSnapshot>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val activeVersion: CustomAgentToolVersionSnapshot?
        get() = versions.firstOrNull { it.id == activeVersionId }

    val latestVersion: CustomAgentToolVersionSnapshot?
        get() = versions.maxByOrNull { it.createdAt }
}

data class CustomAgentToolFixtureResult(
    val versionId: String,
    val status: String,
    val message: String,
    val outputJson: String?,
    val durationMs: Long,
)

fun CustomAgentToolManifest.toToolDefinition(): AiToolDefinition =
    AiToolDefinition(
        name = id,
        description = description,
        inputSchema = inputSchema.toMap(),
    )

private fun JsonObject.toMap(): Map<String, Any?> =
    entrySet().associate { (key, value) ->
        key to when {
            value.isJsonNull -> null
            value.isJsonObject -> value.asJsonObject.toMap()
            value.isJsonArray -> value.asJsonArray.map { item ->
                when {
                    item.isJsonNull -> null
                    item.isJsonObject -> item.asJsonObject.toMap()
                    item.isJsonArray -> item.asJsonArray.map { it.toString() }
                    item.isJsonPrimitive -> item.asJsonPrimitive.toAny()
                    else -> item.toString()
                }
            }
            value.isJsonPrimitive -> value.asJsonPrimitive.toAny()
            else -> value.toString()
        }
    }

private fun com.google.gson.JsonPrimitive.toAny(): Any? =
    when {
        isBoolean -> asBoolean
        isNumber -> asNumber
        isString -> asString
        else -> null
    }
