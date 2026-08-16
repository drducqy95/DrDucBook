package io.legado.app.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.agenttools.CustomAgentToolRuntime
import io.legado.app.data.dao.AiCustomToolDao
import io.legado.app.data.entities.AiCustomTool
import io.legado.app.data.entities.AiCustomToolVersion
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.domain.agenttools.CustomAgentToolDraft
import io.legado.app.domain.agenttools.CustomAgentToolExecutionRequest
import io.legado.app.domain.agenttools.CustomAgentToolExecutionResponse
import io.legado.app.domain.agenttools.CustomAgentToolFixtureResult
import io.legado.app.domain.agenttools.CustomAgentToolLifecycleState
import io.legado.app.domain.agenttools.CustomAgentToolManifestParser
import io.legado.app.domain.agenttools.CustomAgentToolNetworkBridge
import io.legado.app.domain.agenttools.CustomAgentToolSnapshot
import io.legado.app.domain.agenttools.CustomAgentToolTestStatus
import io.legado.app.domain.agenttools.CustomAgentToolValidationStatus
import io.legado.app.domain.agenttools.CustomAgentToolVersionSnapshot
import io.legado.app.domain.agenttools.toToolDefinition
import io.legado.app.domain.gateway.CustomAgentToolGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class CustomAgentToolRepository(
    private val dao: AiCustomToolDao,
    private val runtime: CustomAgentToolRuntime = CustomAgentToolRuntime(
        networkBridge = OkHttpCustomAgentToolNetworkBridge,
        resolver = { host -> InetAddress.getAllByName(host) },
    ),
) : CustomAgentToolGateway {

    override fun observeTools(): Flow<List<CustomAgentToolSnapshot>> = combine(
        dao.observeTools(),
        dao.observeVersions(),
    ) { tools, versions ->
        val byTool = versions.groupBy(AiCustomToolVersion::toolId)
        tools.map { it.toSnapshot(byTool[it.id].orEmpty()) }
    }

    override fun registeredToolDefinitions(): List<AiToolDefinition> = runBlocking(Dispatchers.IO) {
        loadSnapshots()
            .mapNotNull { snapshot -> snapshot.activeManifestDefinition() }
            .sortedBy(AiToolDefinition::name)
    }

    override fun availableToolDefinitions(): List<AiToolDefinition> = runBlocking(Dispatchers.IO) {
        loadSnapshots()
            .filter(CustomAgentToolSnapshot::enabled)
            .mapNotNull { snapshot -> snapshot.activeManifestDefinition() }
            .sortedBy(AiToolDefinition::name)
    }

    override suspend fun createDraft(draft: CustomAgentToolDraft): CustomAgentToolSnapshot =
        withContext(Dispatchers.IO) {
            val metadata = DraftMetadata.from(draft.manifestJson)
            require(metadata.toolName !in BUILT_IN_TOOL_NAMES) {
                "Custom tool id conflicts with a built-in tool"
            }
            val validation = CustomAgentToolManifestParser.parse(
                rawJson = draft.manifestJson,
                reservedToolIds = BUILT_IN_TOOL_NAMES,
            )
            val existing = dao.getToolByName(metadata.toolName)
            val previousVersionTime = existing
                ?.let { dao.getVersions(it.id).maxOfOrNull(AiCustomToolVersion::createdAt) }
                ?: Long.MIN_VALUE
            val now = maxOf(System.currentTimeMillis(), previousVersionTime + 1L)
            val toolId = existing?.id ?: "custom_tool_${UUID.randomUUID().compact()}"
            val versionId = "custom_tool_version_${UUID.randomUUID().compact()}"
            val normalizedFixture = draft.fixtureArgumentsJson.ifBlank { "{}" }
            val validationStatus = if (validation.valid) {
                CustomAgentToolValidationStatus.VALID
            } else {
                CustomAgentToolValidationStatus.INVALID
            }
            val tool = existing?.copy(
                name = metadata.name,
                description = metadata.description,
                updatedAt = now,
            ) ?: AiCustomTool(
                id = toolId,
                toolName = metadata.toolName,
                name = metadata.name,
                description = metadata.description,
                enabled = false,
                activeVersionId = null,
                createdAt = now,
                updatedAt = now,
            )
            val version = AiCustomToolVersion(
                id = versionId,
                toolId = toolId,
                toolName = metadata.toolName,
                version = metadata.version,
                name = metadata.name,
                description = metadata.description,
                manifestJson = draft.manifestJson,
                checksum = validation.manifest?.checksum ?: draft.manifestJson.sha256Label(),
                capabilitiesCsv = validation.manifest?.capabilities.orEmpty().toCsv(),
                allowedDomainsJson = GSON.toJson(validation.manifest?.allowedDomains.orEmpty()),
                lifecycleState = CustomAgentToolLifecycleState.DRAFT.name,
                validationStatus = validationStatus,
                validationMessage = validation.errors.joinToString("\n") { error ->
                    error.line?.let { "${error.field}:$it: ${error.message}" }
                        ?: "${error.field}: ${error.message}"
                },
                testStatus = CustomAgentToolTestStatus.NOT_RUN,
                testMessage = "",
                testOutputJson = null,
                fixtureArgumentsJson = normalizedFixture,
                createdAt = now,
                validatedAt = if (validation.valid) now else null,
                approvedAt = null,
                testedAt = null,
            )
            dao.saveDraft(tool, version)
            requireNotNull(loadSnapshot(toolId))
        }

    override suspend fun validateLatestDraft(toolId: String): CustomAgentToolSnapshot = withContext(Dispatchers.IO) {
        val tool = requireNotNull(dao.getTool(toolId)) { "Custom tool not found" }
        val latest = requireNotNull(dao.getVersions(tool.id).maxByOrNull(AiCustomToolVersion::createdAt)) {
            "Custom tool has no draft"
        }
        val validation = CustomAgentToolManifestParser.parse(
            rawJson = latest.manifestJson,
            reservedToolIds = BUILT_IN_TOOL_NAMES,
        )
        val now = System.currentTimeMillis()
        val status = if (validation.valid) {
            CustomAgentToolValidationStatus.VALID
        } else {
            CustomAgentToolValidationStatus.INVALID
        }
        val lifecycle = if (validation.valid) {
            CustomAgentToolLifecycleState.VALIDATED
        } else {
            CustomAgentToolLifecycleState.DRAFT
        }
        check(
            dao.updateValidation(
                versionId = latest.id,
                lifecycleState = lifecycle.name,
                validationStatus = status,
                validationMessage = validation.errors.joinToString("\n") {
                    it.line?.let { line -> "${it.field}:$line: ${it.message}" }
                        ?: "${it.field}: ${it.message}"
                },
                validatedAt = now,
            ) == 1
        ) { "Custom tool validation state was not updated" }
        requireNotNull(loadSnapshot(tool.id))
    }

    override suspend fun runFixture(toolId: String): CustomAgentToolFixtureResult = withContext(Dispatchers.IO) {
        val tool = requireNotNull(dao.getTool(toolId)) { "Custom tool not found" }
        val latest = requireNotNull(dao.getVersions(tool.id).maxByOrNull(AiCustomToolVersion::createdAt)) {
            "Custom tool has no version"
        }
        val manifest = CustomAgentToolManifestParser.parse(
            rawJson = latest.manifestJson,
            reservedToolIds = BUILT_IN_TOOL_NAMES,
        ).getOrThrow()
        val result = runCatching {
            runtime.execute(manifest, latest.fixtureArgumentsJson)
        }
        val now = System.currentTimeMillis()
        val fixture = result.fold(
            onSuccess = { output ->
                CustomAgentToolFixtureResult(
                    versionId = latest.id,
                    status = CustomAgentToolTestStatus.PASS,
                    message = "Fixture passed in ${output.durationMs} ms",
                    outputJson = output.outputJson,
                    durationMs = output.durationMs,
                )
            },
            onFailure = { error ->
                CustomAgentToolFixtureResult(
                    versionId = latest.id,
                    status = CustomAgentToolTestStatus.FAIL,
                    message = error.message.orEmpty(),
                    outputJson = null,
                    durationMs = 0L,
                )
            },
        )
        check(
            dao.updateTestResult(
                versionId = latest.id,
                testStatus = fixture.status,
                testMessage = fixture.message.take(MAX_TEST_MESSAGE_CHARS),
                testOutputJson = fixture.outputJson?.take(MAX_TEST_OUTPUT_CHARS),
                testedAt = now,
            ) == 1
        ) { "Custom tool fixture state was not updated" }
        fixture
    }

    override suspend fun approveLatestVersion(toolId: String): CustomAgentToolSnapshot = withContext(Dispatchers.IO) {
        val tool = requireNotNull(dao.getTool(toolId)) { "Custom tool not found" }
        val latest = requireNotNull(dao.getVersions(tool.id).maxByOrNull(AiCustomToolVersion::createdAt)) {
            "Custom tool has no version"
        }
        val manifest = CustomAgentToolManifestParser.parse(
            rawJson = latest.manifestJson,
            reservedToolIds = BUILT_IN_TOOL_NAMES,
        ).getOrThrow()
        require(latest.lifecycleState == CustomAgentToolLifecycleState.VALIDATED.name) {
            "Custom tool version must be validated before approval"
        }
        require(latest.validationStatus == CustomAgentToolValidationStatus.VALID) {
            latest.validationMessage.ifBlank { "Custom tool version is not validated" }
        }
        require(latest.testStatus == CustomAgentToolTestStatus.PASS) {
            latest.testMessage.ifBlank { "Custom tool fixture must pass before approval" }
        }
        val now = System.currentTimeMillis()
        val approvedLifecycle = if (tool.enabled) {
            CustomAgentToolLifecycleState.ENABLED
        } else {
            CustomAgentToolLifecycleState.APPROVED
        }
        check(
            dao.updateLifecycle(
                versionId = latest.id,
                lifecycleState = approvedLifecycle.name,
                approvedAt = now,
            ) == 1
        ) { "Custom tool version was not approved" }
        check(
            dao.updateActiveVersion(
                toolId = tool.id,
                versionId = latest.id,
                name = manifest.name,
                description = manifest.description,
                updatedAt = now,
            ) == 1
        ) { "Custom tool active version was not updated" }
        requireNotNull(loadSnapshot(tool.id))
    }

    override suspend fun setEnabled(
        toolId: String,
        enabled: Boolean,
    ): CustomAgentToolSnapshot = withContext(Dispatchers.IO) {
        val tool = requireNotNull(dao.getTool(toolId)) { "Custom tool not found" }
        val active = requireNotNull(tool.activeVersionId?.let { dao.getVersion(it) }) {
            "Custom tool has no approved active version"
        }
        val manifest = CustomAgentToolManifestParser.parse(
            rawJson = active.manifestJson,
            reservedToolIds = BUILT_IN_TOOL_NAMES,
        ).getOrThrow()
        require(active.validationStatus == CustomAgentToolValidationStatus.VALID) {
            active.validationMessage.ifBlank { "Custom tool active version is invalid" }
        }
        require(active.toVersionSnapshot().approved) {
            "Custom tool active version must be approved before enabling"
        }
        val now = System.currentTimeMillis()
        val lifecycle = if (enabled) {
            CustomAgentToolLifecycleState.ENABLED
        } else {
            CustomAgentToolLifecycleState.DISABLED
        }
        check(dao.updateEnabled(tool.id, enabled, now) == 1) {
            "Custom tool enabled state was not updated"
        }
        check(dao.updateLifecycle(active.id, lifecycle.name, active.approvedAt) == 1) {
            "Custom tool lifecycle state was not updated"
        }
        manifest.toToolDefinition()
        requireNotNull(loadSnapshot(tool.id))
    }

    override suspend fun rollback(toolId: String): CustomAgentToolSnapshot = withContext(Dispatchers.IO) {
        val tool = requireNotNull(dao.getTool(toolId)) { "Custom tool not found" }
        val ordered = dao.getVersions(tool.id).sortedByDescending(AiCustomToolVersion::createdAt)
        val activeIndex = ordered.indexOfFirst { it.id == tool.activeVersionId }
        require(activeIndex >= 0) { "Custom tool has no active version" }
        val target = ordered.drop(activeIndex + 1).firstOrNull { version ->
            val snapshot = version.toVersionSnapshot()
            snapshot.valid && snapshot.approved
        } ?: error("Custom tool has no older approved version")
        val now = System.currentTimeMillis()
        check(
            dao.updateActiveVersion(
                toolId = tool.id,
                versionId = target.id,
                name = target.name,
                description = target.description,
                updatedAt = now,
            ) == 1
        ) { "Custom tool rollback did not update active version" }
        if (tool.enabled) {
            dao.updateLifecycle(target.id, CustomAgentToolLifecycleState.ENABLED.name, target.approvedAt)
        }
        requireNotNull(loadSnapshot(tool.id))
    }

    override suspend fun delete(toolId: String): Unit = withContext(Dispatchers.IO) {
        check(dao.deleteTool(toolId) == 1) { "Custom tool was not deleted" }
    }

    override suspend fun execute(call: AiToolCall): AiToolResult? = withContext(Dispatchers.IO) {
        val tool = dao.getToolByName(call.name) ?: return@withContext null
        if (!tool.enabled) {
            return@withContext AiToolResult(
                callId = call.id,
                name = call.name,
                content = GSON.toJson(mapOf("error" to "Custom tool is disabled", "tool" to call.name)),
            )
        }
        val active = tool.activeVersionId?.let { dao.getVersion(it) }
            ?: return@withContext AiToolResult(
                callId = call.id,
                name = call.name,
                content = GSON.toJson(mapOf("error" to "Custom tool has no active version", "tool" to call.name)),
            )
        val manifest = CustomAgentToolManifestParser.parse(
            rawJson = active.manifestJson,
            reservedToolIds = BUILT_IN_TOOL_NAMES,
        ).getOrThrow()
        val result = runCatching { runtime.execute(manifest, call.arguments) }
            .getOrElse { error ->
                return@withContext AiToolResult(
                    callId = call.id,
                    name = call.name,
                    content = GSON.toJson(
                        mapOf(
                            "error" to "Custom tool execution failed",
                            "tool" to call.name,
                            "message" to error.message.orEmpty(),
                        )
                    ),
                )
            }
        AiToolResult(callId = call.id, name = call.name, content = result.outputJson)
    }

    private suspend fun loadSnapshots(): List<CustomAgentToolSnapshot> {
        val versionsByTool = dao.observeVersionsSnapshot().groupBy(AiCustomToolVersion::toolId)
        return dao.getTools().map { tool -> tool.toSnapshot(versionsByTool[tool.id].orEmpty()) }
    }

    private suspend fun loadSnapshot(toolId: String): CustomAgentToolSnapshot? {
        val tool = dao.getTool(toolId) ?: return null
        return tool.toSnapshot(dao.getVersions(tool.id))
    }

    private fun CustomAgentToolSnapshot.activeManifestDefinition(): AiToolDefinition? {
        val active = activeVersion?.takeIf { it.valid && it.approved } ?: return null
        return runCatching {
            CustomAgentToolManifestParser.parse(
                rawJson = active.manifestJson,
                reservedToolIds = BUILT_IN_TOOL_NAMES,
            ).getOrThrow().toToolDefinition()
        }.getOrNull()
    }

    private fun AiCustomTool.toSnapshot(versions: List<AiCustomToolVersion>): CustomAgentToolSnapshot {
        val versionSnapshots = versions.map { version -> version.toVersionSnapshot() }
        return CustomAgentToolSnapshot(
            id = id,
            toolName = toolName,
            name = name,
            description = description,
            enabled = enabled,
            activeVersionId = activeVersionId,
            versions = versionSnapshots,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun AiCustomToolVersion.toVersionSnapshot(): CustomAgentToolVersionSnapshot {
        return CustomAgentToolVersionSnapshot(
            id = id,
            toolId = toolId,
            toolName = toolName,
            version = version,
            name = name,
            description = description,
            manifestJson = manifestJson,
            checksum = checksum,
            capabilities = capabilitiesCsv.toCapabilities(),
            allowedDomains = allowedDomainsJson.toStringList(),
            lifecycleState = runCatching {
                CustomAgentToolLifecycleState.valueOf(lifecycleState)
            }.getOrDefault(CustomAgentToolLifecycleState.DRAFT),
            validationStatus = validationStatus,
            validationMessage = validationMessage,
            testStatus = testStatus,
            testMessage = testMessage,
            testOutputJson = testOutputJson,
            fixtureArgumentsJson = fixtureArgumentsJson,
            createdAt = createdAt,
            validatedAt = validatedAt,
            approvedAt = approvedAt,
            testedAt = testedAt,
        )
    }

    private fun String.toCapabilities(): Set<AgentToolCapability> {
        if (isBlank()) return emptySet()
        return split(',')
            .mapNotNull { name -> runCatching { AgentToolCapability.valueOf(name) }.getOrNull() }
            .toSet()
    }

    private fun Set<AgentToolCapability>.toCsv(): String =
        sortedBy(AgentToolCapability::name).joinToString(",") { it.name }

    private fun String.toStringList(): List<String> =
        runCatching { GSON.fromJson(this, Array<String>::class.java).toList() }
            .getOrDefault(emptyList())

    private fun UUID.compact(): String = toString().replace("-", "")

    private data class DraftMetadata(
        val toolName: String,
        val name: String,
        val description: String,
        val version: String,
    ) {
        companion object {
            fun from(manifestJson: String): DraftMetadata {
                val root = JsonParser.parseString(manifestJson).asJsonObject
                val toolName = root.string("id").trim()
                require(CUSTOM_TOOL_ID_PATTERN.matches(toolName)) {
                    "Custom tool id must start with custom_ and use lowercase letters, numbers or '_'"
                }
                val version = root.string("version").trim()
                require(SEMVER_PATTERN.matches(version)) {
                    "Custom tool version must use semantic versioning"
                }
                return DraftMetadata(
                    toolName = toolName,
                    name = root.string("name").trim(),
                    description = root.string("description").trim(),
                    version = version,
                )
            }

            private fun JsonObject.string(name: String): String {
                val value = get(name)
                require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    "$name is required"
                }
                return value.asString
            }
        }
    }

    private object OkHttpCustomAgentToolNetworkBridge : CustomAgentToolNetworkBridge {
        override fun fetch(request: CustomAgentToolExecutionRequest): CustomAgentToolExecutionResponse {
            val client = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val requestBuilder = Request.Builder()
                .url(request.url)
                .header("User-Agent", TOOL_USER_AGENT)
            request.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            when (request.method) {
                "HEAD" -> requestBuilder.head()
                "POST" -> requestBuilder.post((request.body ?: "").toRequestBody())
                else -> requestBuilder.get()
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body
                val bodyText = body.source().let { source ->
                    val buffer = Buffer()
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer, minOf(8_192L, MAX_NETWORK_BYTES + 1 - total))
                        if (read == -1L) break
                        total += read
                        if (total > MAX_NETWORK_BYTES) {
                            throw IllegalStateException("Network response exceeds ${MAX_NETWORK_BYTES / 1024 / 1024} MiB")
                        }
                    }
                    buffer.readUtf8()
                }
                return CustomAgentToolExecutionResponse(
                    ok = response.isSuccessful,
                    status = response.code,
                    url = response.request.url.toString(),
                    headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() },
                    body = bodyText,
                )
            }
        }
    }

    private companion object {
        private val BUILT_IN_TOOL_NAMES = AiToolRepository.toolDefinitions.mapTo(mutableSetOf()) { it.name }
        private val CUSTOM_TOOL_ID_PATTERN = Regex("""custom_[a-z0-9_]{3,56}""")
        private val SEMVER_PATTERN = Regex(
            """(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)""" +
                """(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""" +
                """(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"""
        )
        private const val MAX_TEST_MESSAGE_CHARS = 2_000
        private const val MAX_TEST_OUTPUT_CHARS = 4_000
        private const val MAX_NETWORK_BYTES = 5L * 1024L * 1024L
        private const val TOOL_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 DrDucBook-AgentTool/1.0"
    }
}

private suspend fun AiCustomToolDao.observeVersionsSnapshot(): List<AiCustomToolVersion> {
    return getTools().flatMap { getVersions(it.id) }
}

private fun String.sha256Label(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return "sha256:" + digest.joinToString("") { "%02x".format(it) }
}
