package io.legado.app.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.legado.app.domain.model.OpdsMimeTypes
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class DriveOpdsProxyServer(
    private val sourceId: String,
    private val sourceName: String,
    private val listingProvider: CloudDriveListingProvider,
    private val requestedPort: Int = 0,
) {
    private var server: EmbeddedServer<*, *>? = null
    var actualPort: Int = 0
        private set

    suspend fun start(): Int = withContext(Dispatchers.IO) {
        val instance = embeddedServer(CIO, port = requestedPort, host = "127.0.0.1") {
            routing {
                get("/opds/catalog") {
                    handleCatalogRequest(call, "")
                }

                get("/opds/catalog/{path...}") {
                    val rawPath = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                    handleCatalogRequest(call, rawPath)
                }

                get("/opds/download/{path...}") {
                    val rawPath = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                    handleDownloadRequest(call, rawPath)
                }

                get("/opds/cover/{path...}") {
                    val rawPath = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                    handleCoverRequest(call, rawPath)
                }

                get("/opds/opensearch.xml") {
                    val baseServerUrl = "http://127.0.0.1:$actualPort"
                    val xml = buildOpenSearchXml(baseServerUrl)
                    call.respondText(xml, ContentType.parse(OpdsMimeTypes.SEARCH))
                }
            }
        }
        server = instance
        instance.start(wait = false)

        val boundPort = instance.engine.resolvedConnectors().firstOrNull()?.port ?: requestedPort
        actualPort = boundPort
        LogUtils.d("DriveOpdsProxyServer", "OPDS Proxy Server started on http://127.0.0.1:$actualPort/opds/catalog")
        boundPort
    }

    fun stop() {
        runCatching {
            server?.stop(500, 1000)
            server = null
            actualPort = 0
            LogUtils.d("DriveOpdsProxyServer", "OPDS Proxy Server stopped")
        }
    }

    private suspend fun handleCatalogRequest(call: ApplicationCall, rawPath: String) {
        val decodedPath = decodePath(rawPath)
        val baseServerUrl = "http://127.0.0.1:$actualPort"

        try {
            val files = listingProvider.listDirectory(decodedPath)
            val catalog = OpdsDriveMapper.mapDirectoryToCatalog(
                sourceId = sourceId,
                sourceName = sourceName,
                currentPath = decodedPath.ifEmpty { "/" },
                files = files,
                baseServerUrl = baseServerUrl,
            )
            val xml = OpdsAtomSerializer.serialize(catalog)
            call.respondText(xml, ContentType.parse(OpdsMimeTypes.NAVIGATION))
        } catch (e: Throwable) {
            LogUtils.e("DriveOpdsProxyServer", "Error generating OPDS catalog: ${e.message}")
            call.respondText(
                "Error generating catalog: ${e.message}",
                ContentType.Text.Plain,
                HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun handleDownloadRequest(call: ApplicationCall, rawPath: String) {
        val decodedPath = decodePath(rawPath)
        val filename = decodedPath.substringAfterLast("/")
        val mime = OpdsDriveMapper.resolveMimeType(filename)

        try {
            val stream = listingProvider.downloadFile(decodedPath)
            call.response.header("Content-Disposition", "attachment; filename=\"$filename\"")
            call.respondOutputStream(ContentType.parse(mime), HttpStatusCode.OK) {
                stream.use { input ->
                    input.copyTo(this)
                }
            }
        } catch (e: Throwable) {
            LogUtils.e("DriveOpdsProxyServer", "Error downloading file $decodedPath: ${e.message}")
            call.respondText(
                "Download error: ${e.message}",
                ContentType.Text.Plain,
                HttpStatusCode.NotFound
            )
        }
    }

    private suspend fun handleCoverRequest(call: ApplicationCall, rawPath: String) {
        // Fallback for cover request if companion cover exists
        val decodedPath = decodePath(rawPath)
        try {
            val stream = listingProvider.downloadFile(decodedPath)
            call.respondOutputStream(ContentType.Image.JPEG, HttpStatusCode.OK) {
                stream.use { input ->
                    input.copyTo(this)
                }
            }
        } catch (_: Throwable) {
            call.respondText("Cover not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
        }
    }

    private fun decodePath(path: String): String {
        val trimmed = path.trim().trimStart('/')
        if (trimmed.isEmpty()) return "/"
        return runCatching {
            URLDecoder.decode(trimmed, "UTF-8")
        }.getOrDefault(trimmed).let {
            if (it.startsWith("/")) it else "/$it"
        }
    }

    private fun buildOpenSearchXml(baseUrl: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <ShortName>$sourceName</ShortName>
  <Description>OPDS search for $sourceName</Description>
  <Url type="application/atom+xml;profile=opds-catalog"
       template="$baseUrl/opds/catalog?q={searchTerms}" />
</OpenSearchDescription>
""".trimIndent()
    }
}
