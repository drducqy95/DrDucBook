package io.legado.app.domain.agenttools

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.domain.agent.AgentToolCapability
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

object CustomAgentToolManifestParser {

    fun parse(
        rawJson: String,
        reservedToolIds: Set<String> = emptySet(),
    ): CustomAgentToolValidationResult {
        val errors = mutableListOf<CustomAgentToolValidationError>()
        if (rawJson.isBlank()) {
            return CustomAgentToolValidationResult(
                manifest = null,
                errors = listOf(error("manifest", "Manifest JSON is required")),
            )
        }
        if (rawJson.length > MAX_MANIFEST_CHARS) {
            errors += error("manifest", "Manifest exceeds $MAX_MANIFEST_CHARS characters")
        }
        val root = runCatching { JsonParser.parseString(rawJson) }
            .getOrElse { parseError ->
                return CustomAgentToolValidationResult(
                    manifest = null,
                    errors = listOf(error("manifest", "Invalid JSON: ${parseError.message.orEmpty()}")),
                )
            }
        if (!root.isJsonObject) {
            return CustomAgentToolValidationResult(
                manifest = null,
                errors = listOf(error("manifest", "Manifest root must be a JSON object")),
            )
        }
        val obj = root.asJsonObject
        val unknownFields = obj.keySet() - TOP_LEVEL_FIELDS
        if (unknownFields.isNotEmpty()) {
            errors += error("manifest", "Unknown fields: ${unknownFields.sorted().joinToString()}")
        }

        val schemaVersion = obj.intField("schemaVersion", errors)
        if (schemaVersion != null && schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            errors += error("schemaVersion", "Only schemaVersion $SUPPORTED_SCHEMA_VERSION is supported")
        }

        val id = obj.stringField("id", errors)?.trim().orEmpty()
        if (id.isNotBlank()) {
            if (!TOOL_ID_PATTERN.matches(id)) {
                errors += error("id", "Tool id must start with custom_ and use 3-64 lowercase letters, numbers or '_'")
            }
            if (id in reservedToolIds) {
                errors += error("id", "Tool id conflicts with a built-in tool")
            }
        }

        val name = obj.stringField("name", errors)?.trim().orEmpty()
        if (name.isNotBlank() && name.length > MAX_NAME_CHARS) {
            errors += error("name", "Name exceeds $MAX_NAME_CHARS characters")
        }

        val description = obj.stringField("description", errors)?.trim().orEmpty()
        if (description.isNotBlank() && description.length > MAX_DESCRIPTION_CHARS) {
            errors += error("description", "Description exceeds $MAX_DESCRIPTION_CHARS characters")
        }

        val version = obj.stringField("version", errors)?.trim().orEmpty()
        if (version.isNotBlank() && !SEMVER_PATTERN.matches(version)) {
            errors += error("version", "Version must use semantic versioning, for example 1.0.0")
        }

        val inputSchema = obj.objectField("inputSchema", errors)
        if (inputSchema != null) {
            errors += CustomAgentToolJsonSchema.validateSchema(
                schema = inputSchema,
                field = "inputSchema",
                requireRootObject = true,
                requireClosedRoot = true,
            )
        }

        val outputSchema = obj.objectField("outputSchema", errors)
        if (outputSchema != null) {
            errors += CustomAgentToolJsonSchema.validateSchema(
                schema = outputSchema,
                field = "outputSchema",
                requireRootObject = false,
                requireClosedRoot = false,
            )
        }

        val capabilities = parseCapabilities(obj.arrayField("capabilities", errors), errors)
        if (capabilities.isNotEmpty()) {
            val unsupported = capabilities - SUPPORTED_CAPABILITIES
            if (unsupported.isNotEmpty()) {
                errors += error(
                    "capabilities",
                    "Unsupported custom tool capabilities: ${unsupported.joinToString { it.name }}",
                )
            }
        }

        val allowedDomains = parseAllowedDomains(obj.arrayField("allowedDomains", errors), errors)
        if (AgentToolCapability.NETWORK in capabilities && allowedDomains.isEmpty()) {
            errors += error("allowedDomains", "NETWORK tools must declare at least one allowed domain")
        }
        if (AgentToolCapability.NETWORK !in capabilities && allowedDomains.isNotEmpty()) {
            errors += error("allowedDomains", "allowedDomains requires the NETWORK capability")
        }

        val timeoutMs = obj.longField("timeoutMs", errors)
        if (timeoutMs != null && timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            errors += error("timeoutMs", "timeoutMs must be between $MIN_TIMEOUT_MS and $MAX_TIMEOUT_MS")
        }

        val maxOutputChars = obj.intField("maxOutputChars", errors)
        if (maxOutputChars != null && maxOutputChars !in MIN_OUTPUT_CHARS..MAX_OUTPUT_CHARS) {
            errors += error(
                "maxOutputChars",
                "maxOutputChars must be between $MIN_OUTPUT_CHARS and $MAX_OUTPUT_CHARS",
            )
        }

        val author = obj.stringField("author", errors, required = false)?.trim()?.takeIf(String::isNotBlank)
        val script = obj.stringField("script", errors).orEmpty()
        errors += validateScript(script, rawJson)

        val scriptChecksum = script.sha256Hex()
        val checksum = obj.stringField("checksum", errors, required = false)?.trim().orEmpty()
        val normalizedChecksum = checksum.removePrefix("sha256:").lowercase(Locale.ROOT)
        if (checksum.isNotBlank() && normalizedChecksum != scriptChecksum) {
            errors += error("checksum", "Checksum does not match script")
        }

        val manifest = if (errors.isEmpty()) {
            CustomAgentToolManifest(
                schemaVersion = schemaVersion ?: SUPPORTED_SCHEMA_VERSION,
                id = id,
                name = name,
                description = description,
                version = version,
                inputSchema = inputSchema!!,
                outputSchema = outputSchema!!,
                capabilities = capabilities + AgentToolCapability.READ,
                allowedDomains = allowedDomains,
                timeoutMs = timeoutMs ?: DEFAULT_TIMEOUT_MS,
                maxOutputChars = maxOutputChars ?: DEFAULT_OUTPUT_CHARS,
                author = author,
                checksum = "sha256:$scriptChecksum",
                script = script,
            )
        } else {
            null
        }
        return CustomAgentToolValidationResult(manifest = manifest, errors = errors)
    }

    fun validateScript(script: String, rawManifestJson: String = script): List<CustomAgentToolValidationError> {
        val errors = mutableListOf<CustomAgentToolValidationError>()
        if (script.isBlank()) {
            errors += error("script", "Script is required")
            return errors
        }
        if (script.length > MAX_SCRIPT_CHARS) {
            errors += error("script", "Script exceeds $MAX_SCRIPT_CHARS characters")
        }
        if ('\u0000' in script) {
            errors += error("script", "Script contains invalid null bytes", script.lineOf(script.indexOf('\u0000')))
        }
        PATH_TRAVERSAL_PATTERN.find(script)?.let { match ->
            errors += error("script", "Script contains unsafe path traversal", script.lineOf(match.range.first))
        }
        WINDOWS_ABSOLUTE_PATH_PATTERN.find(script)?.let { match ->
            errors += error("script", "Script contains unsafe absolute path", script.lineOf(match.range.first))
        }
        SECRET_PATTERN.find(script)?.let { match ->
            errors += error("script", "Script appears to contain a secret", script.lineOf(match.range.first))
        }
        FORBIDDEN_SCRIPT_PATTERNS.firstNotNullOfOrNull { (pattern, label) ->
            pattern.find(script)?.let { match -> match to label }
        }?.let { (match, label) ->
            errors += error("script", "Script uses a forbidden API ($label)", script.lineOf(match.range.first))
        }
        LEGACY_IMPERSONATION_PATTERNS.firstNotNullOfOrNull { (pattern, label) ->
            pattern.find(script)?.let { match -> match to label }
        }?.let { (match, label) ->
            errors += error(
                "script",
                "Script attempts to impersonate a VBook or legacy source API ($label)",
                script.lineOf(match.range.first),
            )
        }
        LARGE_ALLOCATION_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(script)
        }?.let { match ->
            errors += error("script", "Script contains an unsafe large allocation", script.lineOf(match.range.first))
        }
        URL_PATTERN.findAll(script).forEach { match ->
            validatePublicUrlLiteral(match.value).exceptionOrNull()?.let { urlError ->
                errors += error("script", urlError.message.orEmpty(), script.lineOf(match.range.first))
            }
        }
        return errors.map { manifestLineError ->
            if (rawManifestJson === script || manifestLineError.line == null) {
                manifestLineError
            } else {
                manifestLineError
            }
        }
    }

    fun validateNetworkTarget(
        rawUrl: String,
        allowedDomains: Set<String>,
        resolver: (String) -> Array<InetAddress> = { emptyArray() },
    ): String {
        val normalized = validatePublicUrlLiteral(rawUrl).getOrThrow()
        val host = URI(normalized).host.lowercase(Locale.ROOT)
        require(allowedDomains.any { host.matchesAllowedDomain(it) }) {
            "URL host is not in the tool allow-list"
        }
        val resolved = runCatching { resolver(host) }.getOrDefault(emptyArray())
        require(resolved.none(InetAddress::isBlockedCustomToolAddress)) {
            "URL host resolved to a local or private address"
        }
        return normalized
    }

    private fun parseCapabilities(
        raw: JsonArray?,
        errors: MutableList<CustomAgentToolValidationError>,
    ): Set<AgentToolCapability> {
        if (raw == null) return emptySet()
        val values = linkedSetOf<AgentToolCapability>()
        raw.forEachIndexed { index, item ->
            val capability = item.jsonStringOrNull()?.trim().orEmpty()
            if (capability.isBlank()) {
                errors += error("capabilities[$index]", "Capability must be a string")
                return@forEachIndexed
            }
            val normalized = capability.uppercase(Locale.ROOT)
                .removePrefix("AGENTTOOLCAPABILITY.")
                .replace('-', '_')
                .replace('.', '_')
            val parsed = runCatching { AgentToolCapability.valueOf(normalized) }.getOrNull()
            if (parsed == null) {
                errors += error("capabilities[$index]", "Unknown capability: $capability")
            } else {
                values += parsed
            }
        }
        return values
    }

    private fun parseAllowedDomains(
        raw: JsonArray?,
        errors: MutableList<CustomAgentToolValidationError>,
    ): Set<String> {
        if (raw == null) return emptySet()
        if (raw.size() > MAX_ALLOWED_DOMAINS) {
            errors += error("allowedDomains", "At most $MAX_ALLOWED_DOMAINS domains are allowed")
        }
        val domains = linkedSetOf<String>()
        raw.forEachIndexed { index, item ->
            val domain = item.jsonStringOrNull()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (domain.isBlank()) {
                errors += error("allowedDomains[$index]", "Domain must be a string")
                return@forEachIndexed
            }
            if (!isValidAllowedDomain(domain)) {
                errors += error("allowedDomains[$index]", "Invalid or unsafe domain: $domain")
                return@forEachIndexed
            }
            domains += domain
        }
        return domains
    }

    private fun validatePublicUrlLiteral(rawUrl: String): Result<String> = runCatching {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Custom tool URLs must use http or https"
        }
        require(uri.rawUserInfo.isNullOrBlank()) {
            "Custom tool URLs with embedded credentials are not allowed"
        }
        val host = uri.host?.trim()?.trim('[', ']')?.lowercase(Locale.ROOT)
        require(!host.isNullOrBlank()) {
            "Custom tool URL host is required"
        }
        require(!host.isBlockedCustomToolHost()) {
            "Custom tool cannot target local host names"
        }
        host.toInetAddressIfLiteral()?.let { address ->
            require(!address.isBlockedCustomToolAddress()) {
                "Custom tool cannot target local or private network addresses"
            }
        }
        URI(
            scheme,
            null,
            host,
            uri.port,
            uri.path?.takeIf(String::isNotBlank),
            uri.query,
            null,
        ).normalize().toASCIIString()
    }

    private fun JsonObject.stringField(
        name: String,
        errors: MutableList<CustomAgentToolValidationError>,
        required: Boolean = true,
    ): String? {
        val value = get(name)
        if (value == null || value.isJsonNull) {
            if (required) errors += error(name, "$name is required")
            return null
        }
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            errors += error(name, "$name must be a string")
            return null
        }
        val text = value.asString
        if (required && text.isBlank()) {
            errors += error(name, "$name is required")
        }
        return text
    }

    private fun JsonObject.intField(
        name: String,
        errors: MutableList<CustomAgentToolValidationError>,
        required: Boolean = true,
    ): Int? {
        val value = get(name)
        if (value == null || value.isJsonNull) {
            if (required) errors += error(name, "$name is required")
            return null
        }
        val primitive = value.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        if (primitive == null || !primitive.isNumber) {
            errors += error(name, "$name must be a number")
            return null
        }
        return runCatching { primitive.asInt }.getOrElse {
            errors += error(name, "$name must be an integer")
            null
        }
    }

    private fun JsonObject.longField(
        name: String,
        errors: MutableList<CustomAgentToolValidationError>,
        required: Boolean = true,
    ): Long? {
        val value = get(name)
        if (value == null || value.isJsonNull) {
            if (required) errors += error(name, "$name is required")
            return null
        }
        val primitive = value.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        if (primitive == null || !primitive.isNumber) {
            errors += error(name, "$name must be a number")
            return null
        }
        return runCatching { primitive.asLong }.getOrElse {
            errors += error(name, "$name must be an integer")
            null
        }
    }

    private fun JsonObject.arrayField(
        name: String,
        errors: MutableList<CustomAgentToolValidationError>,
        required: Boolean = true,
    ): JsonArray? {
        val value = get(name)
        if (value == null || value.isJsonNull) {
            if (required) errors += error(name, "$name is required")
            return null
        }
        if (!value.isJsonArray) {
            errors += error(name, "$name must be an array")
            return null
        }
        return value.asJsonArray
    }

    private fun JsonObject.objectField(
        name: String,
        errors: MutableList<CustomAgentToolValidationError>,
        required: Boolean = true,
    ): JsonObject? {
        val value = get(name)
        if (value == null || value.isJsonNull) {
            if (required) errors += error(name, "$name is required")
            return null
        }
        if (!value.isJsonObject) {
            errors += error(name, "$name must be an object")
            return null
        }
        return value.asJsonObject
    }

    private fun JsonElement.jsonStringOrNull(): String? {
        return takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.takeIf(JsonPrimitive::isString)
            ?.asString
    }

    private fun error(field: String, message: String, line: Int? = null) =
        CustomAgentToolValidationError(field = field, message = message, line = line)

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.lineOf(index: Int): Int {
        if (index < 0) return 1
        return substring(0, index.coerceAtMost(length)).count { it == '\n' } + 1
    }

    private fun String.matchesAllowedDomain(pattern: String): Boolean {
        return if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*.")
            this == suffix || endsWith(".$suffix")
        } else {
            this == pattern
        }
    }

    private fun isValidAllowedDomain(domain: String): Boolean {
        val candidate = domain.removePrefix("*.")
        if (candidate.isBlank() || candidate.length > 253) return false
        if (candidate.isBlockedCustomToolHost()) return false
        if (candidate.toInetAddressIfLiteral()?.isBlockedCustomToolAddress() == true) return false
        return ALLOWED_DOMAIN_PATTERN.matches(candidate)
    }

    private val TOP_LEVEL_FIELDS = setOf(
        "schemaVersion",
        "id",
        "name",
        "description",
        "version",
        "inputSchema",
        "outputSchema",
        "capabilities",
        "allowedDomains",
        "timeoutMs",
        "maxOutputChars",
        "author",
        "checksum",
        "script",
    )
    private const val SUPPORTED_SCHEMA_VERSION = 1
    private const val MAX_MANIFEST_CHARS = 250_000
    private const val MAX_SCRIPT_CHARS = 120_000
    private const val MAX_NAME_CHARS = 120
    private const val MAX_DESCRIPTION_CHARS = 1_000
    private const val DEFAULT_TIMEOUT_MS = 5_000L
    private const val MIN_TIMEOUT_MS = 50L
    private const val MAX_TIMEOUT_MS = 20_000L
    private const val DEFAULT_OUTPUT_CHARS = 20_000
    private const val MIN_OUTPUT_CHARS = 2
    private const val MAX_OUTPUT_CHARS = 200_000
    private const val MAX_ALLOWED_DOMAINS = 32
    private val TOOL_ID_PATTERN = Regex("""custom_[a-z0-9_]{3,56}""")
    private val SEMVER_PATTERN = Regex(
        """(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)""" +
            """(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""" +
            """(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"""
    )
    private val SUPPORTED_CAPABILITIES = setOf(
        AgentToolCapability.READ,
        AgentToolCapability.NETWORK,
    )
    private val ALLOWED_DOMAIN_PATTERN =
        Regex("""(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}""")
    private val URL_PATTERN =
        Regex("""(?i)\b(?:https?|file|content|android\.resource|jar|ftp)://[^\s"'`<>\])}]+""")
    private val PATH_TRAVERSAL_PATTERN =
        Regex("""(?:^|[^A-Za-z0-9_])\.\.(?:[\\/]|$)""")
    private val WINDOWS_ABSOLUTE_PATH_PATTERN =
        Regex("""(?i)\b[A-Z]:[\\/][^\s"'`<>]+""")
    private val SECRET_PATTERN = Regex(
        """(?i)\b(?:sk-[a-z0-9_-]{16,}|ghp_[a-z0-9]{16,}|AIza[a-z0-9_-]{20,}|hf_[a-z0-9]{20,}|bearer\s+[a-z0-9._~+/-]{20,})"""
    )
    private val FORBIDDEN_SCRIPT_PATTERNS = listOf(
        Regex("""(?i)\bPackages(?:\.|\b)""") to "Rhino Packages bridge",
        Regex("""(?i)\bimport(?:Class|Package)\s*\(""") to "Rhino Java import bridge",
        Regex("""(?i)\b(?:java|javax|android|kotlin)\.""") to "platform package access",
        Regex("""(?i)\borg\.mozilla\.""") to "Rhino internals",
        Regex("""(?i)\bProcessBuilder\b""") to "process execution",
        Regex("""(?i)\bRuntime\s*\.\s*getRuntime\s*\(""") to "runtime execution",
        Regex("""(?i)\bSystem\s*\.\s*(?:exit|getenv|setProperty|getProperty)\s*\(""") to "system access",
        Regex("""(?i)\bClass\s*\.\s*forName\s*\(""") to "reflection",
        Regex("""(?i)\b(?:eval|Function|load|require|XMLHttpRequest)\s*\(""") to "dynamic host access",
        Regex("""(?i)\b(?:document\s*\.\s*cookie|CookieStore|SharedPreferences|localStorage|sessionStorage|indexedDB)\b""") to "secret or browser storage access",
        Regex("""(?i)\b(?:readFile|writeFile|deleteFile|mkdirs?|renameTo)\s*\(""") to "direct file access",
        Regex("""(?i)(?:constructor\s*\.\s*constructor|__proto__)""") to "prototype escape",
    )
    private val LEGACY_IMPERSONATION_PATTERNS = listOf(
        Regex("""(?i)\bvbook://""") to "VBook source URI",
        Regex("""(?i)\b(?:legado|yuedu)://""") to "legacy import deep link",
        Regex("""(?i)\bplugin\.json\b""") to "VBook plugin manifest",
        Regex("""(?i)\bvbook_plugins?\b""") to "VBook plugin storage",
        Regex("""\b(?:VbookExecutor|VbookScriptExecutor|VbookPlugin(?:Adapter|Importer|Inspector))\b""") to
            "VBook runtime class",
    )
    private val LARGE_ALLOCATION_PATTERNS = listOf(
        Regex("""(?i)\bnew\s+Array\s*\(\s*\d{7,}"""),
        Regex("""(?i)\bArray\s*\(\s*\d{7,}"""),
        Regex("""(?i)\.repeat\s*\(\s*\d{7,}"""),
    )
}

object CustomAgentToolJsonSchema {

    fun validateSchema(
        schema: JsonObject,
        field: String,
        requireRootObject: Boolean,
        requireClosedRoot: Boolean,
    ): List<CustomAgentToolValidationError> {
        return validateSchemaNode(
            schema = schema,
            field = field,
            depth = 0,
            requireRootObject = requireRootObject,
            requireClosedRoot = requireClosedRoot,
        )
    }

    fun validateValue(
        value: JsonElement,
        schema: JsonObject,
        field: String,
    ): List<CustomAgentToolValidationError> {
        return validateValueNode(value = value, schema = schema, field = field, depth = 0)
    }

    private fun validateSchemaNode(
        schema: JsonObject,
        field: String,
        depth: Int,
        requireRootObject: Boolean,
        requireClosedRoot: Boolean,
    ): List<CustomAgentToolValidationError> {
        val errors = mutableListOf<CustomAgentToolValidationError>()
        if (depth > MAX_SCHEMA_DEPTH) {
            errors += error(field, "Schema exceeds max depth $MAX_SCHEMA_DEPTH")
            return errors
        }
        val type = schema.get("type")?.jsonSchemaStringOrNull()
        if (type !in SUPPORTED_TYPES) {
            errors += error(field, "Schema type must be one of ${SUPPORTED_TYPES.sorted().joinToString()}")
            return errors
        }
        if (depth == 0 && requireRootObject && type != "object") {
            errors += error(field, "Root schema must be an object")
        }
        schema.get("enum")?.let { rawEnum ->
            if (!rawEnum.isJsonArray || rawEnum.asJsonArray.size() > MAX_ENUM_VALUES) {
                errors += error("$field.enum", "enum must be an array with at most $MAX_ENUM_VALUES values")
            }
        }
        when (type) {
            "object" -> {
                val properties = schema.get("properties")
                if (properties != null && !properties.isJsonObject) {
                    errors += error("$field.properties", "properties must be an object")
                }
                val props = properties?.takeIf(JsonElement::isJsonObject)?.asJsonObject
                if (props != null) {
                    if (props.size() > MAX_PROPERTIES) {
                        errors += error("$field.properties", "Schema object has too many properties")
                    }
                    props.entrySet().forEach { (name, child) ->
                        if (!PROPERTY_PATTERN.matches(name)) {
                            errors += error("$field.properties.$name", "Property name is not supported")
                        }
                        if (!child.isJsonObject) {
                            errors += error("$field.properties.$name", "Property schema must be an object")
                        } else {
                            errors += validateSchemaNode(
                                schema = child.asJsonObject,
                                field = "$field.properties.$name",
                                depth = depth + 1,
                                requireRootObject = false,
                                requireClosedRoot = false,
                            )
                        }
                    }
                }
                val required = schema.get("required")
                if (required != null) {
                    if (!required.isJsonArray) {
                        errors += error("$field.required", "required must be an array")
                    } else {
                        val requiredNames = required.asJsonArray.mapIndexedNotNull { index, item ->
                            val name = item.jsonSchemaStringOrNull()
                            if (name == null) {
                                errors += error("$field.required[$index]", "required entry must be a string")
                            }
                            name
                        }
                        val missing = requiredNames.filter { props?.has(it) != true }
                        if (missing.isNotEmpty()) {
                            errors += error("$field.required", "required references unknown properties: ${missing.joinToString()}")
                        }
                        if (requiredNames.size != requiredNames.distinct().size) {
                            errors += error("$field.required", "required entries must be unique")
                        }
                    }
                }
                val additionalProperties = schema.get("additionalProperties")
                if (depth == 0 && requireClosedRoot && additionalProperties?.asBooleanOrNull() != false) {
                    errors += error("$field.additionalProperties", "Root object must set additionalProperties=false")
                }
            }
            "array" -> {
                val items = schema.get("items")
                if (items == null || !items.isJsonObject) {
                    errors += error("$field.items", "Array schema must define object items")
                } else {
                    errors += validateSchemaNode(
                        schema = items.asJsonObject,
                        field = "$field.items",
                        depth = depth + 1,
                        requireRootObject = false,
                        requireClosedRoot = false,
                    )
                }
            }
        }
        return errors
    }

    private fun validateValueNode(
        value: JsonElement,
        schema: JsonObject,
        field: String,
        depth: Int,
    ): List<CustomAgentToolValidationError> {
        val errors = mutableListOf<CustomAgentToolValidationError>()
        if (depth > MAX_SCHEMA_DEPTH) {
            errors += error(field, "Value exceeds max schema depth")
            return errors
        }
        val type = schema.get("type")?.jsonSchemaStringOrNull().orEmpty()
        if (!value.matchesType(type)) {
            errors += error(field, "Expected $type")
            return errors
        }
        schema.get("enum")?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.let { enum ->
            if (enum.none { it == value }) {
                errors += error(field, "Value is not in enum")
            }
        }
        when (type) {
            "object" -> {
                val obj = value.asJsonObject
                val props = schema.get("properties")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: JsonObject()
                val required = schema.get("required")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
                    ?.mapNotNull { it.jsonSchemaStringOrNull() }
                    .orEmpty()
                required.filterNot(obj::has).forEach { name ->
                    errors += error("$field.$name", "Required property is missing")
                }
                if (schema.get("additionalProperties")?.asBooleanOrNull() == false) {
                    val unknown = obj.keySet() - props.keySet()
                    if (unknown.isNotEmpty()) {
                        errors += error(field, "Unknown properties: ${unknown.sorted().joinToString()}")
                    }
                }
                props.entrySet().forEach { (name, child) ->
                    if (obj.has(name) && child.isJsonObject) {
                        errors += validateValueNode(
                            value = obj.get(name),
                            schema = child.asJsonObject,
                            field = "$field.$name",
                            depth = depth + 1,
                        )
                    }
                }
            }
            "array" -> {
                val itemSchema = schema.get("items")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: return errors
                value.asJsonArray.forEachIndexed { index, item ->
                    errors += validateValueNode(
                        value = item,
                        schema = itemSchema,
                        field = "$field[$index]",
                        depth = depth + 1,
                    )
                }
            }
        }
        return errors
    }

    private fun JsonElement.matchesType(type: String): Boolean {
        if (isJsonNull) return false
        return when (type) {
            "object" -> isJsonObject
            "array" -> isJsonArray
            "string" -> isJsonPrimitive && asJsonPrimitive.isString
            "boolean" -> isJsonPrimitive && asJsonPrimitive.isBoolean
            "number" -> isJsonPrimitive && asJsonPrimitive.isNumber
            "integer" -> isJsonPrimitive && asJsonPrimitive.isNumber && runCatching {
                asBigDecimal.stripTrailingZeros().scale() <= 0
            }.getOrDefault(false)
            else -> false
        }
    }

    private fun JsonElement.jsonSchemaStringOrNull(): String? {
        return takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.takeIf(JsonPrimitive::isString)
            ?.asString
    }

    private fun JsonElement.asBooleanOrNull(): Boolean? {
        return takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.takeIf(JsonPrimitive::isBoolean)
            ?.asBoolean
    }

    private fun error(field: String, message: String) =
        CustomAgentToolValidationError(field = field, message = message)

    private val SUPPORTED_TYPES = setOf("object", "array", "string", "number", "integer", "boolean")
    private val PROPERTY_PATTERN = Regex("""[A-Za-z_][A-Za-z0-9_-]{0,63}""")
    private const val MAX_PROPERTIES = 64
    private const val MAX_SCHEMA_DEPTH = 6
    private const val MAX_ENUM_VALUES = 64
}

private fun String.isBlockedCustomToolHost(): Boolean {
    return this == "localhost" ||
        endsWith(".localhost") ||
        endsWith(".local")
}

private fun String.toInetAddressIfLiteral(): InetAddress? {
    val candidate = trim().trim('[', ']')
    if (!IPV4_LITERAL_PATTERN.matches(candidate) && ':' !in candidate) return null
    return runCatching { InetAddress.getByName(candidate) }.getOrNull()
}

private fun InetAddress.isBlockedCustomToolAddress(): Boolean {
    if (
        isAnyLocalAddress ||
        isLoopbackAddress ||
        isLinkLocalAddress ||
        isSiteLocalAddress ||
        isMulticastAddress
    ) {
        return true
    }
    val bytes = address.map { it.toInt() and 0xff }
    return when (bytes.size) {
        4 -> {
            val first = bytes[0]
            val second = bytes[1]
            first == 0 ||
                first == 10 ||
                first == 127 ||
                (first == 100 && second in 64..127) ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 198 && second in 18..19)
        }
        16 -> (bytes[0] and 0xfe) == 0xfc
        else -> true
    }
}

private val IPV4_LITERAL_PATTERN = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
