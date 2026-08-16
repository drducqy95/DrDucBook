package io.legado.app.data.agenttools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.domain.agenttools.CustomAgentToolExecutionException
import io.legado.app.domain.agenttools.CustomAgentToolExecutionRequest
import io.legado.app.domain.agenttools.CustomAgentToolExecutionResponse
import io.legado.app.domain.agenttools.CustomAgentToolExecutionResult
import io.legado.app.domain.agenttools.CustomAgentToolJsonSchema
import io.legado.app.domain.agenttools.CustomAgentToolManifest
import io.legado.app.domain.agenttools.CustomAgentToolManifestParser
import io.legado.app.domain.agenttools.CustomAgentToolNetworkBridge
import io.legado.app.help.vbook.SafeContext
import io.legado.app.help.vbook.SafeContextFactory
import io.legado.app.utils.GSON
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.net.InetAddress
import java.util.Locale

class CustomAgentToolRuntime(
    private val networkBridge: CustomAgentToolNetworkBridge = DisabledNetworkBridge,
    private val resolver: (String) -> Array<InetAddress> = { emptyArray() },
) {

    fun execute(
        manifest: CustomAgentToolManifest,
        argumentsJson: String,
    ): CustomAgentToolExecutionResult {
        val startedAt = System.currentTimeMillis()
        val arguments = parseArguments(argumentsJson)
        CustomAgentToolJsonSchema.validateValue(arguments, manifest.inputSchema, "input")
            .takeIf(List<*>::isNotEmpty)
            ?.let { errors ->
                throw CustomAgentToolExecutionException(
                    "Custom tool input failed schema validation: ${errors.joinToString { it.message }}",
                )
            }

        val factory = SafeContextFactory()
        val cx = factory.enterContext() as SafeContext
        try {
            cx.startTime = System.currentTimeMillis()
            cx.timeoutMs = manifest.timeoutMs
            cx.isCancelled = false
            cx.optimizationLevel = -1

            val scope: Scriptable = cx.initStandardObjects()
            installBlockedGlobals(scope)
            installNativeFunctions(scope, manifest)
            ScriptableObject.putProperty(scope, "_agentInputJson", arguments.toString())

            cx.evaluateString(scope, BOOTSTRAP, "custom_agent_tool_bootstrap.js", 1, null)
            cx.evaluateString(scope, manifest.script, "${manifest.id}.js", 1, null)
            val execute = scope.get("execute", scope)
            if (execute !is Function) {
                throw CustomAgentToolExecutionException("Custom tool script must define execute(input, context)")
            }
            val output = cx.evaluateString(scope, EXECUTE_WRAPPER, "custom_agent_tool_execute.js", 1, null)
                ?.let(Context::toString)
                .orEmpty()
            if (output.isBlank() || output == "undefined") {
                throw CustomAgentToolExecutionException("Custom tool returned a value that cannot be serialized to JSON")
            }
            if (output.length > manifest.maxOutputChars) {
                throw CustomAgentToolExecutionException(
                    "Custom tool output exceeds ${manifest.maxOutputChars} characters",
                )
            }
            val outputJson = parseOutput(output)
            CustomAgentToolJsonSchema.validateValue(outputJson, manifest.outputSchema, "output")
                .takeIf(List<*>::isNotEmpty)
                ?.let { errors ->
                    throw CustomAgentToolExecutionException(
                        "Custom tool output failed schema validation: ${errors.joinToString { it.message }}",
                    )
                }
            return CustomAgentToolExecutionResult(
                toolId = manifest.id,
                outputJson = outputJson.toString(),
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (error: VirtualMachineError) {
            throw error
        } catch (error: ThreadDeath) {
            throw error
        } catch (error: CustomAgentToolExecutionException) {
            throw error
        } catch (error: Throwable) {
            throw CustomAgentToolExecutionException(
                "Custom tool execution blocked or failed: ${error.message.orEmpty()}",
                error,
            )
        } finally {
            Context.exit()
        }
    }

    private fun parseArguments(argumentsJson: String) =
        runCatching { JsonParser.parseString(argumentsJson) }
            .getOrElse { error ->
                throw CustomAgentToolExecutionException("Custom tool arguments must be valid JSON", error)
            }

    private fun parseOutput(outputJson: String) =
        runCatching { JsonParser.parseString(outputJson) }
            .getOrElse { error ->
                throw CustomAgentToolExecutionException("Custom tool output must be valid JSON", error)
            }

    private fun installBlockedGlobals(scope: Scriptable) {
        BLOCKED_GLOBALS.forEach { name ->
            defineNative(scope, name) {
                throw SecurityException("$name is not available in custom Agent tools")
            }
        }
    }

    private fun installNativeFunctions(
        scope: Scriptable,
        manifest: CustomAgentToolManifest,
    ) {
        defineNative(scope, "_agentLog") {
            // Logs are intentionally dropped in v1 to avoid storing sensitive input/output.
            ""
        }
        defineNative(scope, "_agentFetch") { args ->
            val url = Context.toString(args.getOrNull(0)).trim()
            val optionsJson = Context.toString(args.getOrNull(1)).takeIf { it.isNotBlank() }
                ?: "{}"
            executeFetch(manifest, url, optionsJson).toJsonObject().toString()
        }
    }

    private fun executeFetch(
        manifest: CustomAgentToolManifest,
        rawUrl: String,
        optionsJson: String,
    ): CustomAgentToolExecutionResponse {
        if (AgentToolCapability.NETWORK !in manifest.capabilities) {
            throw SecurityException("Tool does not declare NETWORK capability")
        }
        val normalizedUrl = CustomAgentToolManifestParser.validateNetworkTarget(
            rawUrl = rawUrl,
            allowedDomains = manifest.allowedDomains,
            resolver = resolver,
        )
        val options = runCatching { JsonParser.parseString(optionsJson).asJsonObject }
            .getOrElse { JsonObject() }
        val method = options.string("method", "GET")
            .uppercase(Locale.ROOT)
            .takeIf(ALLOWED_HTTP_METHODS::contains)
            ?: throw SecurityException("Unsupported HTTP method")
        val timeoutMs = options.long("timeoutMs", manifest.timeoutMs)
            .coerceIn(1_000L, manifest.timeoutMs.coerceAtMost(MAX_NETWORK_TIMEOUT_MS))
        val headers = options.get("headers")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            ?.associate { it.key to it.value.asString }
            .orEmpty()
        val blockedHeader = headers.keys.firstOrNull { it.lowercase(Locale.ROOT) in BLOCKED_HEADERS }
        if (blockedHeader != null) {
            throw SecurityException("Custom tools cannot set sensitive header: $blockedHeader")
        }
        val body = options.get("body")
            ?.takeUnless { it.isJsonNull }
            ?.let { if (it.isJsonPrimitive) it.asString else it.toString() }
        val response = networkBridge.fetch(
            CustomAgentToolExecutionRequest(
                url = normalizedUrl,
                method = method,
                headers = headers,
                body = body,
                timeoutMs = timeoutMs,
            )
        )
        if (response.body.length > MAX_NETWORK_BODY_CHARS) {
            throw CustomAgentToolExecutionException("Network response exceeds ${MAX_NETWORK_BODY_CHARS} characters")
        }
        return response.copy(url = response.url.ifBlank { normalizedUrl })
    }

    private fun CustomAgentToolExecutionResponse.toJsonObject(): JsonObject {
        return JsonObject().apply {
            addProperty("ok", ok)
            addProperty("status", status)
            addProperty("url", url)
            add("headers", GSON.toJsonTree(headers))
            addProperty("body", body)
        }
    }

    private fun JsonObject.string(name: String, defaultValue: String): String {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: defaultValue
    }

    private fun JsonObject.long(name: String, defaultValue: Long): Long {
        return get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            ?: defaultValue
    }

    private fun defineNative(
        scope: Scriptable,
        name: String,
        body: (Array<out Any?>) -> Any?,
    ) {
        val function = object : BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable,
                args: Array<out Any?>,
            ): Any? = body(args)

            override fun getFunctionName(): String = name
        }
        ScriptableObject.defineProperty(
            scope,
            name,
            function,
            ScriptableObject.DONTENUM or ScriptableObject.READONLY or ScriptableObject.PERMANENT,
        )
    }

    private object DisabledNetworkBridge : CustomAgentToolNetworkBridge {
        override fun fetch(request: CustomAgentToolExecutionRequest): CustomAgentToolExecutionResponse {
            throw SecurityException("Network bridge is not configured")
        }
    }

    private companion object {
        private const val MAX_NETWORK_TIMEOUT_MS = 20_000L
        private const val MAX_NETWORK_BODY_CHARS = 5 * 1024 * 1024
        private val ALLOWED_HTTP_METHODS = setOf("GET", "HEAD", "POST")
        private val BLOCKED_HEADERS = setOf("authorization", "cookie", "set-cookie", "proxy-authorization")
        private val BLOCKED_GLOBALS = listOf(
            "eval",
            "Function",
            "load",
            "require",
            "Packages",
            "java",
            "javax",
            "android",
            "kotlin",
            "XMLHttpRequest",
            "fetch",
            "localStorage",
            "sessionStorage",
            "indexedDB",
        )
        private val BOOTSTRAP = """
            var console = Object.freeze({
              log: function(message) { _agentLog(String(message)); },
              warn: function(message) { _agentLog(String(message)); },
              error: function(message) { _agentLog(String(message)); }
            });
            var __agentInput = Object.freeze(JSON.parse(String(_agentInputJson)));
            var __agentContext = Object.freeze({
              fetch: function(url, options) {
                var raw = _agentFetch(String(url), JSON.stringify(options || {}));
                return JSON.parse(String(raw));
              },
              log: function(message) { _agentLog(String(message)); }
            });
        """.trimIndent()
        private val EXECUTE_WRAPPER = """
            (function() {
              var result = execute(__agentInput, __agentContext);
              if (typeof result === "undefined") return "null";
              return JSON.stringify(result);
            })()
        """.trimIndent()
    }
}
