package io.legado.app.web

import android.graphics.Bitmap
import com.drducbook.app.BuildConfig
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.utils.io.readAvailable
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookController
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.ReplaceRuleController
import io.legado.app.api.controller.RssSourceController
import io.legado.app.domain.webservice.WebServiceErrorResponse
import io.legado.app.domain.webservice.WebServiceBackgroundUploadResponse
import io.legado.app.domain.webservice.WebServiceBookImportResponse
import io.legado.app.domain.webservice.WebServiceMediaSessionRequest
import io.legado.app.domain.webservice.WebServiceExportBookshelfRequest
import io.legado.app.domain.webservice.WebServiceExportBookTextRequest
import io.legado.app.domain.webservice.WebServiceExportChapterRequest
import io.legado.app.domain.webservice.WebServiceExportSourcesRequest
import io.legado.app.domain.webservice.WebServiceInstanceResponse
import io.legado.app.domain.webservice.WebServiceLegacyContract
import io.legado.app.domain.webservice.WebServiceOriginPolicy
import io.legado.app.domain.webservice.WebServicePairingCenter
import io.legado.app.domain.webservice.WebServicePairingBroker
import io.legado.app.domain.webservice.WebServicePairingExchangeRequest
import io.legado.app.domain.webservice.WebServicePairingExchangeResponse
import io.legado.app.domain.webservice.WebServicePairingExchangeResult
import io.legado.app.domain.webservice.WebServicePolicyPatchRequest
import io.legado.app.domain.webservice.WebServicePolicyPatchResult
import io.legado.app.domain.webservice.WebServicePolicyRevision
import io.legado.app.domain.webservice.WebServicePorts
import io.legado.app.domain.webservice.WebServiceRequestPolicy
import io.legado.app.domain.webservice.WebServiceSessionStatusResponse
import io.legado.app.domain.webservice.WebServiceTranslationJobRequest
import io.legado.app.domain.webservice.WebServiceTtsSynthesisRequest
import io.legado.app.domain.webservice.WebServiceTtsCapabilitiesResponse
import io.legado.app.domain.webservice.WebServiceTtsSynthesisResponse
import io.legado.app.model.localBook.LocalBook
import io.legado.app.service.WebService
import io.legado.app.service.CloudflareTunnelManager
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr
import io.legado.app.web.socket.BookSearchWebSocket
import io.legado.app.web.socket.BookSourceDebugWebSocket
import io.legado.app.web.socket.RssSourceDebugWebSocket
import io.legado.app.web.utils.AssetsWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File

class KtorServer(
    private val port: Int,
    private val wsPort: Int = WebServicePorts.webSocketPortFor(port),
) {
    private var server: EmbeddedServer<*, *>? = null
    private var wsServer: EmbeddedServer<*, *>? = null
    private val assetsWeb = AssetsWeb("web")

    suspend fun start() {
        // Warm the APK asset cache before accepting browser connections. This
        // prevents a page load from issuing many concurrent compressed-asset
        // reads while the Ktor event loop is already serving API requests.
        withContext(Dispatchers.IO) { assetsWeb.preload() }
        // Bind explicitly to IPv4. Android's CIO default may resolve the wildcard
        // to an IPv6-only socket, which makes 127.0.0.1 (and cloudflared's local
        // upstream) fail even while the foreground service reports as running.
        val createdServer = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            intercept(ApplicationCallPipeline.Call) {
                // Browsers on some Android builds keep local-loopback HTTP/1.1
                // connections open indefinitely. Closing each short API/static
                // response prevents the CIO worker pool from being exhausted by
                // half-closed WebView sockets.
                call.response.header(HttpHeaders.Connection, "close")
            }
            install(ContentNegotiation) {
                gson {
                    setLenient()
                }
            }
            install(WebSockets)
            if (BuildConfig.DEBUG) {
                install(CORS) {
                    allowHost("localhost:5173", schemes = listOf("http"))
                    allowHost("127.0.0.1:5173", schemes = listOf("http"))
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader(HttpHeaders.IfMatch)
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Patch)
                    allowMethod(HttpMethod.Delete)
                }
            }

            routing {
                installLegacyWebSocketRoutes()
                installV2Routes()

                post(WebServiceLegacyContract.Http.SAVE_BOOK_SOURCE) { handlePost { BookSourceController.saveSource(it) } }
                post(WebServiceLegacyContract.Http.SAVE_BOOK_SOURCES) { handlePost { BookSourceController.saveSources(it) } }
                post(WebServiceLegacyContract.Http.DELETE_BOOK_SOURCES) { handlePost { BookSourceController.deleteSources(it) } }
                post(WebServiceLegacyContract.Http.SAVE_BOOK) { handlePost { BookController.saveBook(it) } }
                post(WebServiceLegacyContract.Http.DELETE_BOOK) { handlePost { BookController.deleteBook(it) } }
                post(WebServiceLegacyContract.Http.SAVE_BOOK_PROGRESS) { handlePost { BookController.saveBookProgress(it) } }
                post(WebServiceLegacyContract.Http.ADD_LOCAL_BOOK) {
                    if (!requireWebAccess()) return@post
                    val multipart = call.receiveMultipart()
                    var fileName: String? = null
                    val tempFile = File(appCtx.cacheDir, "upload_${System.currentTimeMillis()}")
                    try {
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "fileName") fileName = part.value
                                }
                                is PartData.FileItem -> {
                                    val channel = part.provider()
                                    tempFile.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val bytesRead = channel.readAvailable(buffer)
                                            if (bytesRead == -1) break
                                        if (tempFile.length() + bytesRead > MAX_LEGACY_UPLOAD_BYTES) {
                                            throw IllegalArgumentException("UPLOAD_TOO_LARGE")
                                        }
                                        output.write(buffer, 0, bytesRead)
                                        }
                                    }
                                    if (fileName == null) {
                                        fileName = part.originalFileName
                                    }
                                }
                                else -> {}
                            }
                            part.dispose()
                        }
                        if (fileName != null && tempFile.exists()) {
                            val returnData = withContext(Dispatchers.IO) {
                                kotlin.runCatching {
                                    tempFile.inputStream().use {
                                        val uri = LocalBook.saveBookFile(it, safeUploadName(fileName!!))
                                        LocalBook.importFile(uri)
                                        ReturnData().setData(true)
                                    }
                                }.getOrElse {
                                    LogUtils.e(TAG, it.stackTraceStr)
                                    ReturnData().setErrorMsg(it.localizedMessage ?: "Save book error")
                                }
                            }
                            respondReturnData(returnData)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Missing fileName or fileData")
                        }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                }
                post(WebServiceLegacyContract.Http.SAVE_READ_CONFIG) { handlePost { BookController.saveWebReadConfig(it) } }
                post(WebServiceLegacyContract.Http.SAVE_RSS_SOURCE) { handlePost { RssSourceController.saveSource(it) } }
                post(WebServiceLegacyContract.Http.SAVE_RSS_SOURCES) { handlePost { RssSourceController.saveSources(it) } }
                post(WebServiceLegacyContract.Http.DELETE_RSS_SOURCES) { handlePost { RssSourceController.deleteSources(it) } }
                post(WebServiceLegacyContract.Http.SAVE_REPLACE_RULE) { handlePost { ReplaceRuleController.saveRule(it) } }
                post(WebServiceLegacyContract.Http.DELETE_REPLACE_RULE) { handlePost { ReplaceRuleController.delete(it) } }
                post(WebServiceLegacyContract.Http.TEST_REPLACE_RULE) { handlePost { ReplaceRuleController.testRule(it) } }

                get(WebServiceLegacyContract.Http.GET_BOOK_SOURCE) { handleGet { BookSourceController.getSource(it) } }
                get(WebServiceLegacyContract.Http.GET_BOOK_SOURCES) { handleGet { BookSourceController.sources } }
                get(WebServiceLegacyContract.Http.GET_BOOKSHELF) { handleGet { BookController.bookshelf } }
                get(WebServiceLegacyContract.Http.GET_CHAPTER_LIST) { handleGet { BookController.getChapterListAwait(it) } }
                get(WebServiceLegacyContract.Http.REFRESH_TOC) { handleGet { BookController.refreshTocAwait(it) } }
                get(WebServiceLegacyContract.Http.GET_BOOK_CONTENT) { handleGet { BookController.getBookContentAwait(it) } }
                get(WebServiceLegacyContract.Http.COVER) { handleGet { BookController.getCoverAwait(it) } }
                get(WebServiceLegacyContract.Http.IMAGE) { handleGet { BookController.getImgAwait(it) } }
                get(WebServiceLegacyContract.Http.GET_READ_CONFIG) { handleGet { BookController.getWebReadConfig() } }
                get(WebServiceLegacyContract.Http.GET_RSS_SOURCE) { handleGet { RssSourceController.getSource(it) } }
                get(WebServiceLegacyContract.Http.GET_RSS_SOURCES) { handleGet { RssSourceController.sources } }
                get(WebServiceLegacyContract.Http.GET_REPLACE_RULES) { handleGet { ReplaceRuleController.allRules } }

                    get("{...}") {
                        WebService.serve()
                        try {
                            var uri = call.request.path()
                            if (uri == "/" || uri.isBlank()) {
                                // Do not let an external browser reuse an old
                                // index.html after the bundled Vue app changes.
                                // Hashed JS/CSS assets remain cacheable, while
                                // this entry point is always revalidated.
                                call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
                                call.respondRedirect(
                                    "/vue/index.html?v=${BuildConfig.VERSION_CODE}-${System.currentTimeMillis()}#/",
                                    permanent = false,
                                )
                                return@get
                            }
                            if (uri.split('/').any { it == ".." }) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                        if (uri.endsWith("/")) uri += "index.html"
                        val assetBytes = withContext(Dispatchers.IO) {
                            assetsWeb.getBytes(uri)
                        }
                        if (assetBytes != null) {
                            if (uri.endsWith("/index.html", ignoreCase = true)) {
                                call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
                            } else {
                                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                            }
                            call.respondBytes(assetBytes, ContentType.parse(assetsWeb.getMimeType(uri)))
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    } catch (error: Throwable) {
                        LogUtils.e(TAG, error.stackTraceStr)
                        if (!call.response.isCommitted) {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
        }
        server = createdServer
        createdServer.start(wait = false)
        createdServer.engine.resolvedConnectors()
    }

    suspend fun startWebSocket(wsPort: Int) {
        val createdServer = embeddedServer(CIO, host = "0.0.0.0", port = wsPort) {
            install(WebSockets)
            routing {
                installLegacyWebSocketRoutes()
            }
        }
        wsServer = createdServer
        createdServer.start(wait = false)
        createdServer.engine.resolvedConnectors()
    }

    private fun Route.installLegacyWebSocketRoutes() {
        webSocket(WebServiceLegacyContract.WebSocket.BOOK_SOURCE_DEBUG) {
            if (!requireWebSocketAccess()) return@webSocket
            BookSourceDebugWebSocket(this).handle()
        }
        webSocket(WebServiceLegacyContract.WebSocket.RSS_SOURCE_DEBUG) {
            if (!requireWebSocketAccess()) return@webSocket
            RssSourceDebugWebSocket(this).handle()
        }
        webSocket(WebServiceLegacyContract.WebSocket.SEARCH_BOOK) {
            if (!requireWebSocketAccess()) return@webSocket
            BookSearchWebSocket(this).handle()
        }
    }

    fun stop() {
        val httpServer = server
        val webSocketServer = wsServer
        server = null
        wsServer = null
        httpServer?.stop(0, 0)
        webSocketServer?.stop(0, 0)
    }

    private suspend fun RoutingContext.handlePost(
        block: suspend (String?) -> ReturnData
    ) {
        if (!requireWebAccess()) return
        try {
            val postData = call.receiveText()
            val returnData = block(postData)
            respondReturnData(returnData)
        } catch (e: Exception) {
            LogUtils.e(TAG, e.stackTraceStr)
            call.respondText(e.message ?: "Unknown error")
        }
    }

    private suspend fun RoutingContext.handleGet(
        block: suspend (Map<String, List<String>>) -> ReturnData?
    ) {
        if (!requireWebAccess()) return
        try {
            val parameters = call.queryParameters.entries()
                .associate { it.key to it.value }
            val returnData = block(parameters)
            if (returnData != null) {
                respondReturnData(returnData)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, e.stackTraceStr)
            call.respondText(e.message ?: "Unknown error")
        }
    }

    private suspend fun RoutingContext.respondReturnData(returnData: ReturnData) {
        if (returnData.data is Bitmap) {
            val bitmap = returnData.data as Bitmap
            val outputStream = ByteArrayOutputStream()
            withContext(Dispatchers.IO) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            call.respondBytes(outputStream.toByteArray(), ContentType.Image.PNG)
        } else {
            call.respond(returnData)
        }
    }

    private fun Route.installV2Routes() {
        get("/api/v2/instance") {
            WebService.serve()
            if (!call.requireSameOrigin()) return@get
            call.respond(buildInstanceResponse(call))
        }

        get("/api/v2/discovery/home") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 48) ?: 24
            val type = call.request.queryParameters["type"]
            val refresh = call.request.queryParameters["refresh"].toBoolean()
            runCatching {
                WebServiceDiscoveryController.home(type, limit, refresh)
            }.onSuccess { response -> call.respond(response) }
                .onFailure { error ->
                    LogUtils.e(TAG, error.stackTraceStr)
                    call.respond(HttpStatusCode.ServiceUnavailable, WebServiceErrorResponse("DISCOVERY_UNAVAILABLE"))
                }
        }

        post("/api/v2/books/import") {
            WebService.serve()
            if (!requireWebAccess()) return@post
            val multipart = call.receiveMultipart()
            var fileName: String? = null
            val tempFile = File(appCtx.cacheDir, "web_import_${System.nanoTime()}")
            try {
                multipart.forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem -> if (part.name == "fileName") fileName = part.value
                            is PartData.FileItem -> {
                                fileName = fileName ?: part.originalFileName
                                val channel = part.provider()
                                tempFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        val bytesRead = channel.readAvailable(buffer)
                                        if (bytesRead == -1) break
                                        total += bytesRead
                                        if (total > MAX_LEGACY_UPLOAD_BYTES) throw IllegalArgumentException("UPLOAD_TOO_LARGE")
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }
                val safeName = fileName?.let(::safeUploadName)
                    ?: throw IllegalArgumentException("UPLOAD_FILE_REQUIRED")
                val imported = withContext(Dispatchers.IO) {
                    tempFile.inputStream().use { input ->
                        val uri = LocalBook.saveBookFile(input, safeName)
                        LocalBook.importFile(uri)
                        true
                    }
                }
                call.respond(WebServiceBookImportResponse(safeName, imported))
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, WebServiceErrorResponse(error.message ?: "UPLOAD_INVALID"))
            } catch (error: Exception) {
                LogUtils.e(TAG, error.stackTraceStr)
                call.respond(HttpStatusCode.InternalServerError, WebServiceErrorResponse("UPLOAD_FAILED"))
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

        post("/api/v2/media/sessions") {
            WebService.serve()
            if (!requireWebAccess()) return@post
            try {
                val request = receiveOrDefault(WebServiceMediaSessionRequest())
                call.respond(WebServiceMediaController.create(request.bookUrl, request.chapterIndex))
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, WebServiceErrorResponse(error.message ?: "MEDIA_REQUEST_INVALID"))
            } catch (error: Exception) {
                LogUtils.e(TAG, error.stackTraceStr)
                call.respond(HttpStatusCode.ServiceUnavailable, WebServiceErrorResponse("MEDIA_RESOLVE_FAILED"))
            }
        }

        get("/api/v2/tts/capabilities") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val (engine, language) = WebServiceTtsController.capabilities()
            call.respond(WebServiceTtsCapabilitiesResponse(true, engine, language))
        }

        post("/api/v2/tts/synthesize") {
            WebService.serve()
            if (!requireWebAccess()) return@post
            try {
                val request = receiveOrDefault(WebServiceTtsSynthesisRequest())
                val file = WebServiceTtsController.synthesize(request.text, request.language)
                call.respond(
                    WebServiceTtsSynthesisResponse(
                        audioUrl = "/api/v2/tts/audio/${file.id}",
                        engine = WebServiceTtsController.capabilities().first,
                        language = file.language,
                        expiresAt = file.expiresAt,
                    )
                )
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, WebServiceErrorResponse(error.message ?: "TTS_REQUEST_INVALID"))
            } catch (error: Exception) {
                LogUtils.e(TAG, error.stackTraceStr)
                call.respond(HttpStatusCode.ServiceUnavailable, WebServiceErrorResponse(error.message ?: "TTS_FAILED"))
            }
        }

        get("/api/v2/tts/audio/{id}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val file = WebServiceTtsController.get(call.parameters["id"].orEmpty())
            if (file == null || !file.file.isFile) {
                call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("TTS_AUDIO_NOT_FOUND"))
            } else {
                call.response.header(HttpHeaders.ContentType, "audio/wav")
                call.respondFile(file.file)
            }
        }

        get("/api/v2/media/sessions/{sessionId}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val session = WebServiceMediaController.getSession(call.parameters["sessionId"].orEmpty())
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("MEDIA_SESSION_EXPIRED"))
            } else {
                call.respond(WebServiceMediaController.responseFor(session))
            }
        }

        get("/api/v2/media/sessions/{sessionId}/variants/{variantId}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val session = WebServiceMediaController.getSession(call.parameters["sessionId"].orEmpty())
            val variant = session?.let { WebServiceMediaController.variant(it, call.parameters["variantId"].orEmpty()) }
            if (session == null || variant == null) {
                call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("MEDIA_VARIANT_NOT_FOUND"))
                return@get
            }
            val target = WebServiceMediaController.source(session, variant.uri, variant.headers)
            if (target is WebServiceMediaController.SourceTarget.Local) {
                if (!target.file.isFile) call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("MEDIA_FILE_NOT_FOUND"))
                else {
                    call.response.header(HttpHeaders.ContentType, variant.mimeType)
                    call.respondFile(target.file)
                }
                return@get
            }
            val manifest = WebServiceMediaController.openManifest(session, variant)
            if (manifest != null) {
                call.respondText(manifest.body, ContentType.parse(manifest.mimeType))
                return@get
            }
            respondMediaProxy(WebServiceMediaController.openRemote(target as WebServiceMediaController.SourceTarget.Remote, call.request.headers[HttpHeaders.Range]))
        }

        get("/api/v2/media/sessions/{sessionId}/resources/{resourceId}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val session = WebServiceMediaController.getSession(call.parameters["sessionId"].orEmpty())
            val resource = session?.let { WebServiceMediaController.resource(it, call.parameters["resourceId"].orEmpty()) }
            if (session == null || resource == null) {
                call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("MEDIA_RESOURCE_NOT_FOUND"))
                return@get
            }
            val target = WebServiceMediaController.source(session, resource.uri, resource.headers)
            if (target is WebServiceMediaController.SourceTarget.Local) {
                if (!target.file.isFile) call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("MEDIA_FILE_NOT_FOUND"))
                else call.respondFile(target.file)
            } else {
                val nestedManifest = WebServiceMediaController.openNestedManifest(
                    session,
                    resource,
                )
                if (nestedManifest != null) {
                    call.respondText(nestedManifest.body, ContentType.parse(nestedManifest.mimeType))
                } else {
                    respondMediaProxy(WebServiceMediaController.openRemote(target as WebServiceMediaController.SourceTarget.Remote, call.request.headers[HttpHeaders.Range]))
                }
            }
        }

        post("/api/v2/session") {
            WebService.serve()
            if (!call.requireSameOrigin()) return@post
            val request = runCatching {
                call.receive<WebServicePairingExchangeRequest>()
            }.getOrNull()
            when (val result = WebServicePairingCenter.exchange(request?.code ?: request?.pairingCode)) {
                is WebServicePairingExchangeResult.Success -> {
                    CloudflareTunnelManager.onPairingConsumed()
                    call.respond(
                        WebServicePairingExchangeResponse(
                            sessionToken = result.session.token,
                            expiresAt = result.session.expiresAt,
                        )
                    )
                }

                WebServicePairingExchangeResult.MissingCode -> call.respond(
                    HttpStatusCode.BadRequest,
                    WebServiceErrorResponse("PAIRING_CODE_REQUIRED"),
                )

                WebServicePairingExchangeResult.InvalidCode -> call.respond(
                    HttpStatusCode.Unauthorized,
                    WebServiceErrorResponse("PAIRING_CODE_INVALID"),
                )

                WebServicePairingExchangeResult.Expired -> call.respond(
                    HttpStatusCode.Gone,
                    WebServiceErrorResponse("PAIRING_CODE_EXPIRED"),
                )
            }
        }

        get("/api/v2/session") {
            WebService.serve()
            if (!call.requireSameOrigin()) return@get
            val session = WebServicePairingCenter.sessionFromAuthorization(authorizationHeader())
            if (session == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    WebServiceErrorResponse("SESSION_REQUIRED"),
                )
            } else {
                call.respond(
                    WebServiceSessionStatusResponse(
                        active = true,
                        expiresAt = session.expiresAt,
                    )
                )
            }
        }

        post("/api/v2/session/revoke") {
            WebService.serve()
            if (!call.requireSameOrigin()) return@post
            if (WebServicePairingCenter.revoke(authorizationHeader())) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    WebServiceErrorResponse("SESSION_REQUIRED"),
                )
            }
        }

        get("/api/v2/policy") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            respondPolicy(WebServicePolicyStore.read(appCtx))
        }

        patch("/api/v2/policy") {
            WebService.serve()
            if (!requireWebAccess()) return@patch
            val request = runCatching {
                call.receive<WebServicePolicyPatchRequest>()
            }.getOrNull() ?: WebServicePolicyPatchRequest()
            val previousPolicy = WebServicePolicyStore.read(appCtx)
            when (val result = WebServicePolicyStore.patch(
                context = appCtx,
                request = request,
                ifMatch = call.request.headers[HttpHeaders.IfMatch],
            )) {
                is WebServicePolicyPatchResult.Success -> {
                    if (previousPolicy.autoTranslationEnabled && !result.policy.autoTranslationEnabled) {
                        WebServiceTranslationJobController.cancelAll()
                    }
                    respondPolicy(result.policy)
                }

                is WebServicePolicyPatchResult.Conflict -> {
                    call.response.header(HttpHeaders.ETag, result.current.etag)
                    call.respond(
                        HttpStatusCode.Conflict,
                        result.current.toResponse(),
                    )
                }

                WebServicePolicyPatchResult.PreconditionRequired -> call.respond(
                    HttpStatusCode(428, "Precondition Required"),
                    WebServiceErrorResponse("IF_MATCH_REQUIRED"),
                )
            }
        }

        post("/api/v2/policy/reset") {
            WebService.serve()
            if (!requireWebAccess()) return@post
            val previousPolicy = WebServicePolicyStore.read(appCtx)
            val policy = WebServicePolicyStore.reset(appCtx)
            if (previousPolicy.backgroundAssetId != null) {
                WebServiceBackgroundStore.delete(appCtx, previousPolicy.backgroundAssetId)
            }
            if (previousPolicy.autoTranslationEnabled && !policy.autoTranslationEnabled) {
                WebServiceTranslationJobController.cancelAll()
            }
            respondPolicy(policy)
        }

        post("/api/v2/background") {
            WebService.serve()
            if (!requireWebAccess()) return@post
            val ifMatch = call.request.headers[HttpHeaders.IfMatch]
            val currentPolicy = WebServicePolicyStore.read(appCtx)
            if (ifMatch.isNullOrBlank()) {
                respondPolicyPreconditionRequired()
                return@post
            }
            if (!WebServicePolicyRevision.matches(ifMatch, currentPolicy.revision)) {
                call.response.header(HttpHeaders.ETag, currentPolicy.etag)
                call.respond(HttpStatusCode.Conflict, currentPolicy.toResponse())
                return@post
            }

            val upload = try {
                receiveBackgroundUpload()
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    WebServiceErrorResponse(e.message ?: "BACKGROUND_UPLOAD_INVALID"),
                )
                return@post
            }
            val asset = try {
                withContext(Dispatchers.IO) {
                    WebServiceBackgroundStore.save(
                        context = appCtx,
                        bytes = upload.bytes,
                        displayName = upload.fileName,
                        mimeType = upload.mimeType,
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    WebServiceErrorResponse(e.message ?: "BACKGROUND_UPLOAD_INVALID"),
                )
                return@post
            }

            when (val result = WebServicePolicyStore.patch(
                context = appCtx,
                request = WebServicePolicyPatchRequest(backgroundAssetId = asset.assetId),
                ifMatch = ifMatch,
            )) {
                is WebServicePolicyPatchResult.Success -> {
                    currentPolicy.backgroundAssetId
                        ?.takeIf { it != asset.assetId }
                        ?.let { WebServiceBackgroundStore.delete(appCtx, it) }
                    call.response.header(HttpHeaders.Location, "/api/v2/background/${asset.assetId}")
                    call.response.header(HttpHeaders.ETag, result.policy.etag)
                    call.respond(
                        WebServiceBackgroundUploadResponse(
                            asset = asset,
                            policy = result.policy.toResponse(),
                        )
                    )
                }

                is WebServicePolicyPatchResult.Conflict -> {
                    WebServiceBackgroundStore.delete(appCtx, asset.assetId)
                    call.response.header(HttpHeaders.ETag, result.current.etag)
                    call.respond(HttpStatusCode.Conflict, result.current.toResponse())
                }

                WebServicePolicyPatchResult.PreconditionRequired -> {
                    WebServiceBackgroundStore.delete(appCtx, asset.assetId)
                    respondPolicyPreconditionRequired()
                }
            }
        }

        get("/api/v2/background/{assetId}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            val assetId = call.parameters["assetId"]
            val file = WebServiceBackgroundStore.find(appCtx, assetId)
            val asset = WebServiceBackgroundStore.responseFor(appCtx, assetId)
            if (file == null || asset == null) {
                call.respond(HttpStatusCode.NotFound, WebServiceErrorResponse("BACKGROUND_NOT_FOUND"))
                return@get
            }
            if (call.request.headers["If-None-Match"] == asset.etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.ETag, asset.etag)
            call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
            call.respondOutputStream(ContentType.Image.PNG) {
                file.inputStream().use { it.copyTo(this) }
            }
        }

        delete("/api/v2/background") {
            WebService.serve()
            if (!requireWebAccess()) return@delete
            val previousAssetId = WebServicePolicyStore.read(appCtx).backgroundAssetId
            when (val result = WebServicePolicyStore.patch(
                context = appCtx,
                request = WebServicePolicyPatchRequest(clearBackgroundAsset = true),
                ifMatch = call.request.headers[HttpHeaders.IfMatch],
            )) {
                is WebServicePolicyPatchResult.Success -> {
                    if (previousAssetId != null) WebServiceBackgroundStore.delete(appCtx, previousAssetId)
                    respondPolicy(result.policy)
                }

                is WebServicePolicyPatchResult.Conflict -> {
                    call.response.header(HttpHeaders.ETag, result.current.etag)
                    call.respond(HttpStatusCode.Conflict, result.current.toResponse())
                }

                WebServicePolicyPatchResult.PreconditionRequired -> respondPolicyPreconditionRequired()
            }
        }

        post("/api/v2/export/sources") {
            WebService.serve()
            if (!requireExportEnabled()) return@post
            respondExportFile {
                WebServiceExportController.sources(
                    receiveOrDefault(WebServiceExportSourcesRequest())
                )
            }
        }

        post("/api/v2/export/bookshelf") {
            WebService.serve()
            if (!requireExportEnabled()) return@post
            respondExportFile {
                WebServiceExportController.bookshelf(
                    receiveOrDefault(WebServiceExportBookshelfRequest())
                )
            }
        }

        post("/api/v2/export/chapter") {
            WebService.serve()
            if (!requireExportEnabled()) return@post
            respondExportFile {
                WebServiceExportController.chapter(
                    receiveOrDefault(WebServiceExportChapterRequest())
                )
            }
        }

        post("/api/v2/export/book-txt") {
            WebService.serve()
            if (!requireExportEnabled()) return@post
            respondExportFile {
                WebServiceExportController.bookText(
                    receiveOrDefault(WebServiceExportBookTextRequest())
                )
            }
        }

        get("/api/v2/translation/content") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            respondTranslationJob {
                WebServiceTranslationJobController.getCachedContent(
                    bookUrlValue = call.request.queryParameters["bookUrl"],
                    chapterIndexValue = call.request.queryParameters["chapterIndex"],
                    providerValue = call.request.queryParameters["provider"],
                    targetLanguageValue = call.request.queryParameters["targetLanguage"],
                )
            }
        }

        get("/api/v2/translation/providers") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            call.respond(WebServiceTranslationJobController.providers())
        }

        post("/api/v2/translation/jobs") {
            WebService.serve()
            if (!requireAutoTranslationEnabled()) return@post
            respondTranslationJob {
                WebServiceTranslationJobController.create(
                    receiveOrDefault(WebServiceTranslationJobRequest())
                )
            }
        }

        get("/api/v2/translation/jobs") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            call.respond(WebServiceTranslationJobController.list())
        }

        get("/api/v2/translation/jobs/{jobId}") {
            WebService.serve()
            if (!requireWebAccess()) return@get
            respondTranslationJob {
                WebServiceTranslationJobController.get(call.parameters["jobId"])
            }
        }

        delete("/api/v2/translation/jobs/{jobId}") {
            WebService.serve()
            if (!requireWebAccess()) return@delete
            respondTranslationJob {
                WebServiceTranslationJobController.cancel(call.parameters["jobId"])
            }
        }
    }

    private fun RoutingContext.authorizationHeader(): String? =
        call.request.headers[HttpHeaders.Authorization]
            ?: call.request.queryParameters["access_token"]?.let { token -> "Bearer $token" }

    private suspend fun RoutingContext.requireWebAccess(): Boolean {
        if (!call.requireSameOrigin()) return false
        if (!call.requiresPairing()) return true
        if (WebServicePairingCenter.sessionFromAuthorization(authorizationHeader()) != null) {
            return true
        }
        call.respond(
            HttpStatusCode.Unauthorized,
            WebServiceErrorResponse("SESSION_REQUIRED"),
        )
        return false
    }

    private suspend fun DefaultWebSocketServerSession.requireWebSocketAccess(): Boolean {
        if (!call.requiresPairing()) return true
        val token = call.request.queryParameters["access_token"]
        val session = token?.let {
            WebServicePairingCenter.sessionFromAuthorization("Bearer $it")
        }
        if (session != null) return true
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "SESSION_REQUIRED"))
        return false
    }

    private fun safeUploadName(rawName: String): String {
        val baseName = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f\\\"<>|]"), "_")
            .trim()
            .take(180)
        return baseName.ifBlank { "imported-book" }
    }

    private suspend fun RoutingContext.requireExportEnabled(): Boolean {
        if (!requireWebAccess()) return false
        if (!WebServicePolicyStore.read(appCtx).exportEnabled) {
            call.respond(
                HttpStatusCode.Forbidden,
                WebServiceErrorResponse("FEATURE_DISABLED"),
            )
            return false
        }
        return true
    }

    private suspend fun RoutingContext.requireAutoTranslationEnabled(): Boolean {
        if (!requireWebAccess()) return false
        if (!WebServicePolicyStore.read(appCtx).autoTranslationEnabled) {
            call.respond(
                HttpStatusCode.Forbidden,
                WebServiceErrorResponse("FEATURE_DISABLED"),
            )
            return false
        }
        return true
    }

    private suspend fun ApplicationCall.requireSameOrigin(): Boolean {
        if (WebServiceOriginPolicy.isSameOrigin(
                origin = request.headers[HttpHeaders.Origin],
                hostHeader = request.headers[HttpHeaders.Host],
                trustedExternalUrl = CloudflareTunnelManager.publicUrl,
            )
        ) {
            return true
        }
        respond(
            HttpStatusCode.Forbidden,
            WebServiceErrorResponse("SAME_ORIGIN_REQUIRED"),
        )
        return false
    }

    private fun ApplicationCall.requiresPairing(): Boolean =
        WebServiceRequestPolicy.requiresPairing(
            tunnelRequiresPairing = CloudflareTunnelManager.requiresPairing,
            origin = request.headers[HttpHeaders.Origin],
            hostHeader = request.headers[HttpHeaders.Host],
            cloudflareRay = request.headers["CF-Ray"],
            cloudflareConnectingIp = request.headers["CF-Connecting-IP"],
            publicUrl = CloudflareTunnelManager.publicUrl,
        )

    private suspend fun RoutingContext.respondPolicy(policy: io.legado.app.domain.webservice.WebServicePolicy) {
        call.response.header(HttpHeaders.ETag, policy.etag)
        call.respond(policy.toResponse())
    }

    private suspend fun RoutingContext.respondMediaProxy(response: okhttp3.Response) {
        response.use { upstream ->
            call.response.status(HttpStatusCode.fromValue(upstream.code))
            upstream.header("Content-Type")?.let { call.response.header(HttpHeaders.ContentType, it) }
            upstream.header("Content-Length")?.let { call.response.header(HttpHeaders.ContentLength, it) }
            upstream.header("Content-Range")?.let { call.response.header(HttpHeaders.ContentRange, it) }
            upstream.header("Accept-Ranges")?.let { call.response.header(HttpHeaders.AcceptRanges, it) }
            val body = upstream.body
            if (body == null) {
                call.respondText("")
                return
            }
            call.respondOutputStream {
                withContext(Dispatchers.IO) {
                    body.byteStream().use { input -> input.copyTo(this@respondOutputStream) }
                }
            }
        }
    }

    private suspend fun RoutingContext.respondPolicyPreconditionRequired() {
        call.respond(
            HttpStatusCode(428, "Precondition Required"),
            WebServiceErrorResponse("IF_MATCH_REQUIRED"),
        )
    }

    private suspend fun RoutingContext.receiveBackgroundUpload(): BackgroundUpload {
        val multipart = call.receiveMultipart()
        var fileName: String? = null
        var mimeType: String? = null
        var bytes: ByteArray? = null
        multipart.forEachPart { part ->
            try {
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "fileName") fileName = part.value
                    }

                    is PartData.FileItem -> {
                        if (bytes != null) throw IllegalArgumentException("BACKGROUND_TOO_MANY_FILES")
                        fileName = fileName ?: part.originalFileName
                        mimeType = part.contentType?.toString()
                        bytes = readLimitedBytes(part)
                    }

                    else -> {}
                }
            } finally {
                part.dispose()
            }
        }
        return BackgroundUpload(
            bytes = bytes ?: throw IllegalArgumentException("BACKGROUND_FILE_REQUIRED"),
            fileName = fileName,
            mimeType = mimeType,
        )
    }

    private suspend fun readLimitedBytes(part: PartData.FileItem): ByteArray {
        val output = ByteArrayOutputStream()
        val channel = part.provider()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val bytesRead = channel.readAvailable(buffer)
            if (bytesRead == -1) break
            if (bytesRead == 0) continue
            total += bytesRead
            if (total > WebServiceBackgroundStore.MAX_UPLOAD_BYTES) {
                throw IllegalArgumentException("BACKGROUND_TOO_LARGE")
            }
            output.write(buffer, 0, bytesRead)
        }
        return output.toByteArray()
    }

    private data class BackgroundUpload(
        val bytes: ByteArray,
        val fileName: String?,
        val mimeType: String?,
    )

    private suspend inline fun <reified T : Any> RoutingContext.receiveOrDefault(default: T): T {
        val requestText = call.receiveText()
        if (requestText.isBlank()) return default
        return runCatching {
            GSON.fromJson(requestText, T::class.java) ?: default
        }.getOrElse {
            throw IllegalArgumentException("EXPORT_REQUEST_INVALID")
        }
    }

    private suspend fun RoutingContext.respondExportFile(
        block: suspend () -> WebServiceExportFile,
    ) {
        val file = try {
            block()
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                WebServiceErrorResponse(e.message ?: "EXPORT_REQUEST_INVALID"),
            )
            return
        }
        call.response.header(
            "Content-Disposition",
            "attachment; filename=\"${file.fileName}\"",
        )
        call.respondOutputStream(ContentType.parse(file.contentType)) {
            file.writeTo(this)
        }
    }

    private suspend fun RoutingContext.respondTranslationJob(
        block: suspend () -> Any,
    ) {
        try {
            call.respond(block())
        } catch (e: NoSuchElementException) {
            call.respond(
                HttpStatusCode.NotFound,
                WebServiceErrorResponse(e.message ?: "JOB_NOT_FOUND"),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                WebServiceErrorResponse(e.message ?: "TRANSLATION_REQUEST_INVALID"),
            )
        }
    }

    private fun buildInstanceResponse(call: ApplicationCall): WebServiceInstanceResponse =
        WebServiceInstanceResponse(
            appName = APP_NAME,
            packageName = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            instanceId = WebServiceIdentityStore.getOrCreateInstanceId(appCtx),
            apiVersion = API_VERSION_V2,
            legacyApiVersion = API_VERSION_LEGACY,
            httpPort = port,
            webSocketPort = wsPort,
            legacyHttpPort = WebServicePorts.LEGACY_HTTP_PORT,
            legacyWebSocketPort = WebServicePorts.LEGACY_WEB_SOCKET_PORT,
            requiresPairing = call.requiresPairing(),
            pairingCodeTtlMillis = WebServicePairingBroker.DEFAULT_CHALLENGE_TTL_MILLIS,
            sessionTtlMillis = WebServicePairingBroker.DEFAULT_SESSION_TTL_MILLIS,
        )

    companion object {
        private const val TAG = "KtorServer"
        private const val MAX_LEGACY_UPLOAD_BYTES = 512L * 1024L * 1024L
        private const val APP_NAME = "DrDucBook"
        private const val API_VERSION_V2 = 2
        private const val API_VERSION_LEGACY = 1
    }
}
