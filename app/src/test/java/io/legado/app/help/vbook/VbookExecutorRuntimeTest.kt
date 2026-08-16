package io.legado.app.help.vbook

import android.app.Application
import com.script.rhino.RhinoScriptEngine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookExecutorRuntimeTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.injectAsAppCtx()
        RhinoScriptEngine.hashCode()
    }

    @Test
    fun `fetch exposes the cookie actually sent to the plugin`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .removeHeader("CookieJar")
                    .header("Cookie", "accessToken=abc123; session=active")
                    .build()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("account".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val result = execute(
            pluginId = "runtime-cookie",
            client = client,
            script = """
                function execute() {
                    var response = fetch("https://reader.example.test/account");
                    return Response.success({
                        cookie: response.request.headers.cookie || "",
                        userAgent: response.request.headers["user-agent"] || ""
                    });
                }
            """.trimIndent(),
        )

        val data = result.getJSONObject("data")
        assertEquals("accessToken=abc123; session=active", data.getString("cookie"))
        assertEquals(io.legado.app.help.config.AppConfig.userAgent, data.getString("userAgent"))
    }

    @Test
    fun `legacy java http bridge stays inside the vbook fetch sandbox`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("legacy-body".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val result = execute(
            pluginId = "runtime-legacy-java-http",
            client = client,
            script = """
                function execute() {
                    return Response.success({
                        connect: java.connect("https://reader.example.test/connect").get(),
                        source: java.source.get("https://reader.example.test/source"),
                        ajax: java.ajax("https://reader.example.test/ajax"),
                        encoded: java.encodeURI("a b")
                    });
                }
            """.trimIndent(),
        ).getJSONObject("data")

        assertEquals("legacy-body", result.getString("connect"))
        assertEquals("legacy-body", result.getString("source"))
        assertEquals("legacy-body", result.getString("ajax"))
        assertEquals("a%20b", result.getString("encoded"))
    }

    @Test
    fun `synchronous promise chains unwrap to normal vbook output`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("promise-body".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val result = execute(
            pluginId = "runtime-sync-promise",
            client = client,
            script = """
                function execute() {
                    var first = fetch("https://reader.example.test/one").then(function(response) {
                        return response.text();
                    });
                    return Promise.all([first, Promise.resolve("ready")]).then(function(values) {
                        return Response.success({ body: values[0], state: values[1] });
                    });
                }
            """.trimIndent(),
        ).getJSONObject("data")

        assertEquals("promise-body", result.getString("body"))
        assertEquals("ready", result.getString("state"))
    }

    @Test
    fun `browser compatibility object exposes vbook methods and json urls`() {
        val result = execute(
            pluginId = "runtime-browser",
            client = OkHttpClient(),
            script = """
                function execute() {
                    var isolated = Engine.newBrowser();
                    isolated.block([".*api/books.*"]);
                    return Response.success({
                        globalBrowser: typeof browser !== "undefined",
                        hasWaitUrl: typeof isolated.waitUrl === "function",
                        urls: JSON.parse(isolated.urls())
                    });
                }
            """.trimIndent(),
        ).getJSONObject("data")

        assertTrue(result.getBoolean("globalBrowser"))
        assertTrue(result.getBoolean("hasWaitUrl"))
        assertEquals(0, result.getJSONArray("urls").length())
    }

    @Test
    fun `mtc receives public fallback when browser token is unavailable`() {
        val result = execute(
            pluginId = "3bb96398c47c55b3b682e892",
            client = OkHttpClient(),
            pluginName = "MTC",
            pluginSource = "https://metruyencv.com",
            script = """
                function execute() {
                    return Response.success(localStorage.getItem("authorization"));
                }
            """.trimIndent(),
        )

        assertEquals("Bearer public", result.getString("data"))
    }

    @Test
    fun `mtc browser access token replaces cached authorization`() {
        val pluginId = "3bb96398c47c55b3b682e893"
        createPlugin(pluginId, "MTC", "https://metruyencv.com", "")
        assertTrue(
            VbookMtcSessionCompat.syncBrowserCookie(
                context,
                "vbook://plugin/$pluginId",
                "session=active; accessToken=new-token",
            )
        )
        val result = execute(
            pluginId = pluginId,
            client = OkHttpClient(),
            pluginName = "MTC",
            pluginSource = "https://metruyencv.com",
            script = """
                function execute() {
                    return Response.success(localStorage.getItem("authorization"));
                }
            """.trimIndent(),
        )

        assertEquals("Bearer new-token", result.getString("data"))
    }

    @Test
    fun `mtc browser local storage token replaces public fallback`() {
        val pluginId = "3bb96398c47c55b3b682e894"
        createPlugin(pluginId, "MTC", "https://metruyencv.com", "")
        assertTrue(
            VbookMtcSessionCompat.syncBrowserSession(
                context = context,
                sourceUrl = "vbook://plugin/$pluginId",
                cookie = null,
                localStorageJson = "{\"accessToken\":\"storage-token\"}",
            )
        )
        val result = execute(
            pluginId = pluginId,
            client = OkHttpClient(),
            pluginName = "MTC",
            pluginSource = "https://metruyencv.com",
            script = """
                function execute() {
                    return Response.success(localStorage.getItem("authorization"));
                }
            """.trimIndent(),
        )

        assertEquals("Bearer storage-token", result.getString("data"))
    }

    private fun execute(
        pluginId: String,
        client: OkHttpClient,
        script: String,
        pluginName: String = "Runtime",
        pluginSource: String = "https://reader.example.test",
    ): JSONObject {
        createPlugin(pluginId, pluginName, pluginSource, script)

        val raw = VbookExecutor(context, pluginId, client).executeScript(
            scriptName = "home.js",
            functionName = "execute",
            args = emptyArray(),
            configUrl = "",
            configValues = emptyMap(),
        )
        return JSONObject(raw)
    }

    private fun createPlugin(
        pluginId: String,
        pluginName: String,
        pluginSource: String,
        script: String,
    ) {
        val plugin = File(context.filesDir, "vbook_plugins/$pluginId")
        File(plugin, "src").mkdirs()
        File(plugin, "plugin.json").writeText(
            """
            {
              "metadata": {
                "name": "$pluginName",
                "author": "Test",
                "version": 1,
                "source": "$pluginSource",
                "type": "novel"
              },
              "script": {"home": "home.js"}
            }
            """.trimIndent(),
        )
        File(plugin, "src/home.js").writeText(script)
    }
}
