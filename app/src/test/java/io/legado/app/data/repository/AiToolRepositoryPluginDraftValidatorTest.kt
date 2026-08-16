package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolRepositoryPluginDraftValidatorTest {

    @Test
    fun validatorAllowsBenignPublicSourceScript() {
        validateAgentVbookPluginDraftMetadata("https://books.example/source")

        validateAgentVbookPluginDraftFile(
            fileName = "search.js",
            content = """
                async function execute(keyword, page) {
                  const url = "https://books.example/search?q=" + encodeURIComponent(keyword);
                  return JSON.stringify({ success: true, data: [] });
                }
            """.trimIndent(),
        )
    }

    @Test
    fun validatorAllowsVbookJavaBridgeHttpApis() {
        validateAgentVbookPluginDraftFile(
            fileName = "search.js",
            content = """
                function httpGet(url) {
                  if (typeof java !== "undefined" && java.connect) return java.connect(url).get();
                  if (typeof java !== "undefined" && java.source) return java.source.get(url);
                  return java.ajax(url);
                }
            """.trimIndent(),
        )
    }

    @Test
    fun validatorAllowsAnonymousFunctionsAndSynchronousPromiseStyle() {
        validateAgentVbookPluginDraftFile(
            fileName = "search.js",
            content = """
                function execute() {
                  return new Promise(function(resolve, reject) {
                    fetch("https://books.example/search").then(function(response) {
                      resolve(response.text());
                    }).catch(function(error) { reject(error); });
                  });
                }
            """.trimIndent(),
        )
    }

    @Test
    fun validatorRejectsUnsafeFileNamesAndPaths() {
        assertTrue(
            runCatching {
                validateAgentVbookPluginDraftFile("../evil.js", "function execute() {}")
            }.isFailure
        )
        assertTrue(isUnsafeAgentVbookPluginPath("../evil.js"))
        assertTrue(isUnsafeAgentVbookPluginPath("C:\\Users\\user\\secret.txt"))
    }

    @Test
    fun installFilePathValidatorOnlyAllowsLocalZipPaths() {
        assertEquals(
            "/sdcard/Download/plugin.zip",
            validateAgentVbookPluginInstallFilePath("/sdcard/Download/plugin.zip").normalizedPath(),
        )
        assertEquals(
            "/sdcard/Download/PLUGIN.ZIP",
            validateAgentVbookPluginInstallFilePath("/sdcard/Download/PLUGIN.ZIP").normalizedPath(),
        )

        listOf(
            "../plugin.zip",
            "C:\\Users\\user\\plugin.zip",
            "file:///sdcard/Download/plugin.zip",
            "content://downloads/plugin.zip",
            "/sdcard/Download/plugin.apk",
            "/sdcard/Download/plugin.zip\u0000",
        ).forEach { path ->
            assertTrue(
                path,
                runCatching { validateAgentVbookPluginInstallFilePath(path) }.isFailure,
            )
        }
    }

    private fun java.io.File.normalizedPath(): String = path.replace('\\', '/')

    @Test
    fun validatorRejectsForbiddenRhinoJavaAndDynamicCodeApis() {
        listOf(
            "Packages.java.io.File('/sdcard')",
            "java.lang.Runtime.getRuntime().exec('id')",
            "new ProcessBuilder('sh')",
            "System.exit(0)",
            "eval('danger()')",
            "Function('return 1')",
        ).forEach { content ->
            assertTrue(
                content,
                runCatching {
                    validateAgentVbookPluginDraftFile("search.js", content)
                }.isFailure,
            )
        }
    }

    @Test
    fun validatorRejectsPrivateLocalAndNonHttpUrls() {
        listOf(
            "http://localhost:8080",
            "http://127.0.0.1/page",
            "http://10.0.0.1/page",
            "http://192.168.1.10/page",
            "http://[::1]/page",
            "file:///sdcard/secret.txt",
            "content://settings/system",
        ).forEach { url ->
            assertTrue(url, validateAgentVbookPluginDraftUrl(url).isFailure)
            assertTrue(
                url,
                runCatching {
                    validateAgentVbookPluginDraftFile("search.js", """const url = "$url";""")
                }.isFailure,
            )
        }
    }

    @Test
    fun validatorNormalizesPublicPluginUrlsWithoutResolvingDns() {
        val result = validateAgentVbookPluginDraftUrl("https://Books.Example/a?q=1#fragment")

        assertEquals("https://books.example/a?q=1", result.getOrThrow())
    }
}
