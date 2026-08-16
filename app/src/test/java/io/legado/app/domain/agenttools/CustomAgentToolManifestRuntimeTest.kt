package io.legado.app.domain.agenttools

import com.google.gson.JsonParser
import io.legado.app.data.agenttools.CustomAgentToolRuntime
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAgentToolManifestRuntimeTest {

    @Test
    fun validDeterministicToolExecutesAndValidatesOutput() {
        val manifest = parseManifest(
            script = """
                function execute(input, context) {
                  return { sum: input.a + input.b };
                }
            """.trimIndent(),
            inputSchema = objectSchema(
                properties = mapOf(
                    "a" to mapOf("type" to "integer", "description" to "First number"),
                    "b" to mapOf("type" to "integer", "description" to "Second number"),
                ),
                required = listOf("a", "b"),
            ),
            outputSchema = objectSchema(
                properties = mapOf(
                    "sum" to mapOf("type" to "integer", "description" to "Sum"),
                ),
                required = listOf("sum"),
            ),
        )

        val result = CustomAgentToolRuntime().execute(manifest, """{"a":2,"b":3}""")

        assertEquals(
            JsonParser.parseString("""{"sum":5}"""),
            JsonParser.parseString(result.outputJson),
        )
    }

    @Test
    fun invalidSchemaAndForbiddenScriptReportFieldAndLine() {
        val result = CustomAgentToolManifestParser.parse(
            manifestJson(
                script = """
                    function execute(input, context) {
                      var value = input.name;
                      java.lang.Runtime.getRuntime().exec("id");
                      return { value: value };
                    }
                """.trimIndent(),
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Name"),
                    ),
                ),
            )
        )

        assertTrue(result.errors.any { it.field == "inputSchema.additionalProperties" })
        assertTrue(result.errors.any { it.field == "script" && it.line == 3 })
    }

    @Test
    fun infiniteLoopIsStoppedByTimeout() {
        val manifest = parseManifest(
            timeoutMs = 50,
            script = """
                function execute(input, context) {
                  while (true) {}
                }
            """.trimIndent(),
        )

        val error = runCatching {
            CustomAgentToolRuntime().execute(manifest, "{}")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("timed out", ignoreCase = true))
    }

    @Test
    fun forbiddenHostApisPathTraversalAndSecretsAreRejected() {
        listOf(
            "Packages.java.io.File('/sdcard')",
            "new ProcessBuilder('sh')",
            "Class.forName('java.lang.Runtime')",
            "System.getenv('HF_TOKEN')",
            "var path = '../secret.txt';",
            "var token = 'sk-testsecretplaceholder0000';",
            "document.cookie",
        ).forEach { script ->
            val result = CustomAgentToolManifestParser.parse(manifestJson(script = script))

            assertTrue(script, result.errors.any { it.field == "script" })
        }
    }

    @Test
    fun customToolCannotPretendToBeVbookOrLegacyApi() {
        listOf(
            """return { source: "vbook://plugin/0123456789abcdef" };""",
            """var link = "legado://import/bookSource?src=https://example.test/source.json";""",
            """var link = "yuedu://booksource/importonline?src=https://example.test/source.json";""",
            """var manifest = "plugin.json";""",
            """var runtime = VbookExecutor;""",
        ).forEach { script ->
            val result = CustomAgentToolManifestParser.parse(
                manifestJson(
                    script = """
                        function execute(input, context) {
                          $script
                          return { ok: true };
                        }
                    """.trimIndent(),
                )
            )

            assertTrue(
                script,
                result.errors.any {
                    it.field == "script" &&
                        it.message.contains("VBook or legacy source API")
                },
            )
        }
    }

    @Test
    fun oversizedOutputIsBlockedBeforeReturningToAgent() {
        val manifest = parseManifest(
            maxOutputChars = 80,
            outputSchema = mapOf("type" to "string", "description" to "Large text"),
            script = """
                function execute(input, context) {
                  return "x".repeat(200);
                }
            """.trimIndent(),
        )

        val error = runCatching {
            CustomAgentToolRuntime().execute(manifest, "{}")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("output exceeds", ignoreCase = true))
    }

    @Test
    fun networkBridgeRequiresCapabilityAndAllowedDomain() {
        val readOnlyManifest = parseManifest(
            script = """
                function execute(input, context) {
                  return context.fetch("https://api.example/data");
                }
            """.trimIndent(),
            outputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any?>()),
        )

        val readOnlyError = runCatching {
            CustomAgentToolRuntime().execute(readOnlyManifest, "{}")
        }.exceptionOrNull()

        assertNotNull(readOnlyError)
        assertTrue(readOnlyError!!.message.orEmpty().contains("NETWORK capability"))

        val networkManifest = parseManifest(
            capabilities = listOf("READ", "NETWORK"),
            allowedDomains = listOf("api.example"),
            script = """
                function execute(input, context) {
                  var response = context.fetch("https://api.example/data", { method: "GET" });
                  return { status: response.status, body: response.body };
                }
            """.trimIndent(),
            outputSchema = objectSchema(
                properties = mapOf(
                    "status" to mapOf("type" to "integer", "description" to "HTTP status"),
                    "body" to mapOf("type" to "string", "description" to "Body"),
                ),
                required = listOf("status", "body"),
            ),
        )
        val runtime = CustomAgentToolRuntime(
            networkBridge = CustomAgentToolNetworkBridge { request ->
                assertEquals("https://api.example/data", request.url)
                CustomAgentToolExecutionResponse(
                    ok = true,
                    status = 200,
                    url = request.url,
                    body = "ok",
                )
            }
        )

        val result = runtime.execute(networkManifest, "{}")

        assertEquals(
            JsonParser.parseString("""{"status":200,"body":"ok"}"""),
            JsonParser.parseString(result.outputJson),
        )
    }

    @Test
    fun networkManifestRejectsLocalTargetsAndSensitiveHeaders() {
        val localResult = CustomAgentToolManifestParser.parse(
            manifestJson(
                capabilities = listOf("READ", "NETWORK"),
                allowedDomains = listOf("127.0.0.1"),
                script = """
                    function execute(input, context) {
                      return context.fetch("http://127.0.0.1:8080/private");
                    }
                """.trimIndent(),
            )
        )
        assertTrue(localResult.errors.any { it.field.startsWith("allowedDomains") })
        assertTrue(localResult.errors.any { it.field == "script" })

        val manifest = parseManifest(
            capabilities = listOf("READ", "NETWORK"),
            allowedDomains = listOf("api.example"),
            script = """
                function execute(input, context) {
                  return context.fetch("https://api.example/data", {
                    headers: { Authorization: "Bearer secret" }
                  });
                }
            """.trimIndent(),
            outputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any?>()),
        )

        val error = runCatching {
            CustomAgentToolRuntime(
                networkBridge = CustomAgentToolNetworkBridge {
                    error("Bridge must not receive sensitive headers")
                }
            ).execute(manifest, "{}")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("sensitive header", ignoreCase = true))
    }

    private fun parseManifest(
        script: String,
        inputSchema: Map<String, Any?> = objectSchema(),
        outputSchema: Map<String, Any?> = objectSchema(),
        capabilities: List<String> = listOf("READ"),
        allowedDomains: List<String> = emptyList(),
        timeoutMs: Long = 1_000,
        maxOutputChars: Int = 2_000,
    ): CustomAgentToolManifest {
        return CustomAgentToolManifestParser.parse(
            manifestJson(
                script = script,
                inputSchema = inputSchema,
                outputSchema = outputSchema,
                capabilities = capabilities,
                allowedDomains = allowedDomains,
                timeoutMs = timeoutMs,
                maxOutputChars = maxOutputChars,
            )
        ).getOrThrow()
    }

    private fun manifestJson(
        script: String,
        inputSchema: Map<String, Any?> = objectSchema(),
        outputSchema: Map<String, Any?> = objectSchema(),
        capabilities: List<String> = listOf("READ"),
        allowedDomains: List<String> = emptyList(),
        timeoutMs: Long = 1_000,
        maxOutputChars: Int = 2_000,
    ): String {
        return GSON.toJson(
            mapOf(
                "schemaVersion" to 1,
                "id" to "custom_sum_tool",
                "name" to "Custom sum tool",
                "description" to "A deterministic custom Agent tool fixture",
                "version" to "1.0.0",
                "inputSchema" to inputSchema,
                "outputSchema" to outputSchema,
                "capabilities" to capabilities,
                "allowedDomains" to allowedDomains,
                "timeoutMs" to timeoutMs,
                "maxOutputChars" to maxOutputChars,
                "script" to script,
            )
        )
    }

    private fun objectSchema(
        properties: Map<String, Any?> = emptyMap(),
        required: List<String> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to required,
        "additionalProperties" to false,
    )
}
